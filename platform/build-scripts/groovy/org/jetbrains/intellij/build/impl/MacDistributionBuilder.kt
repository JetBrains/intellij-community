// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.createTask
import com.intellij.diagnostic.telemetry.use
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SystemProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateMultiPlatformProductJson
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.substituteTemplatePlaceholders
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.tasks.NoDuplicateZipArchiveOutputStream
import org.jetbrains.intellij.build.tasks.dir
import org.jetbrains.intellij.build.tasks.entry
import org.jetbrains.intellij.build.tasks.executableFileUnixMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDate
import java.util.concurrent.ForkJoinTask
import java.util.function.BiConsumer
import java.util.zip.Deflater
import kotlin.io.path.nameWithoutExtension

class MacDistributionBuilder(override val context: BuildContext,
                             private val customizer: MacDistributionCustomizer,
                             private val ideaProperties: Path?) : OsSpecificDistributionBuilder {
  private val targetIcnsFileName: String = "${context.productProperties.baseFileName}.icns"

  override val targetOs: OsFamily
    get() = OsFamily.MACOS

  private fun getDocTypes(): String {
    val associations = mutableListOf<String>()

    if (customizer.associateIpr) {
      val association = """<dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>ipr</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>${targetIcnsFileName}</string>
        <key>CFBundleTypeName</key>
        <string>${context.applicationInfo.productName} Project File</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>"""
      associations.add(association)
    }

    for (fileAssociation in customizer.fileAssociations) {
      val iconPath = fileAssociation.iconPath
      val association = """<dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>${fileAssociation.extension}</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>${if (iconPath.isEmpty()) targetIcnsFileName else File(iconPath).name}</string>        
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>"""
      associations.add(association)
    }

    return associations.joinToString(separator = "\n      ") + customizer.additionalDocTypes
  }

  override fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    doCopyExtraFiles(macDistDir = targetPath, arch = arch, copyDistFiles = true)
  }

  private fun doCopyExtraFiles(macDistDir: Path, arch: JvmArchitecture?, copyDistFiles: Boolean) {
    @Suppress("SpellCheckingInspection")
    val platformProperties = mutableListOf(
      "\n#---------------------------------------------------------------------",
      "# macOS-specific system properties",
      "#---------------------------------------------------------------------",
      "com.apple.mrj.application.live-resize=false",
      "apple.laf.useScreenMenuBar=true",
      "jbScreenMenuBar.enabled=true",
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    )
    customizer.getCustomIdeaProperties(context.applicationInfo).forEach(BiConsumer { k, v -> platformProperties.add("$k=$v") })

    layoutMacApp(ideaProperties!!, platformProperties, getDocTypes(), macDistDir, context)

    unpackPty4jNative(context, macDistDir, "darwin")

    generateBuildTxt(context, macDistDir.resolve("Resources"))
    if (copyDistFiles) {
      copyDistFiles(context, macDistDir)
    }

    customizer.copyAdditionalFiles(context, macDistDir.toString())
    if (arch != null) {
      customizer.copyAdditionalFiles(context, macDistDir, arch)
    }

    generateUnixScripts(context, emptyList(), macDistDir.resolve("bin"), OsFamily.MACOS)
  }

  override fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    doCopyExtraFiles(macDistDir = osAndArchSpecificDistPath, arch = arch, copyDistFiles = false)
    context.executeStep(spanBuilder("build macOS artifacts").setAttribute("arch", arch.name), BuildOptions.MAC_ARTIFACTS_STEP) {
      val baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
      val publishSit = context.publishSitArchive
      val publishZipOnly = !publishSit && context.options.buildStepsToSkip.contains(BuildOptions.MAC_DMG_STEP)
      val binariesToSign = customizer.getBinariesToSign(context, arch)
      if (!binariesToSign.isEmpty()) {
        context.executeStep(spanBuilder("sign binaries for macOS distribution")
                              .setAttribute("arch", arch.name), BuildOptions.MAC_SIGN_STEP) {
          context.signFiles(binariesToSign.map(osAndArchSpecificDistPath::resolve), mapOf(
            "mac_codesign_options" to "runtime",
            "mac_codesign_force" to "true",
            "mac_codesign_deep" to "true",
          ))
        }
      }
      val macZip = (if (publishZipOnly) context.paths.artifactDir else context.paths.tempDir)
        .resolve("$baseName.mac.${arch.name}.zip")
      val macZipWithoutRuntime = macZip.resolveSibling(macZip.nameWithoutExtension + "-no-jdk.zip")
      val zipRoot = getMacZipRoot(customizer, context)
      val runtimeDist = context.bundledRuntime.extract(BundledRuntimeImpl.getProductPrefix(context), OsFamily.MACOS, arch)
      val directories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDist)
      val extraFiles = context.getDistFiles()
      val compressionLevel = if (publishSit || publishZipOnly) Deflater.DEFAULT_COMPRESSION else Deflater.BEST_SPEED
      val errorsConsumer = context.messages::warning
      if (context.options.buildMacArtifactsWithRuntime) {
        buildMacZip(
          targetFile = macZip,
          zipRoot = zipRoot,
          productJson = generateMacProductJson(builtinModule = context.builtinModule, context = context,
                                               javaExecutablePath = "../jbr/Contents/Home/bin/java"),
          directories = directories,
          extraFiles = extraFiles,
          executableFilePatterns = generateExecutableFilesPatterns(true),
          compressionLevel = compressionLevel,
          errorsConsumer = errorsConsumer
        )
      }
      if (context.options.buildMacArtifactsWithoutRuntime) {
        buildMacZip(
          targetFile = macZipWithoutRuntime,
          zipRoot = zipRoot,
          productJson = generateMacProductJson(builtinModule = context.builtinModule, context = context, javaExecutablePath = null),
          directories = directories - runtimeDist,
          extraFiles = extraFiles,
          executableFilePatterns = generateExecutableFilesPatterns(false),
          compressionLevel = compressionLevel,
          errorsConsumer = errorsConsumer
        )
      }
      if (publishZipOnly) {
        Span.current().addEvent("skip DMG and SIT artifacts producing")
        if (context.options.buildMacArtifactsWithRuntime) {
          context.notifyArtifactBuilt(macZip)
        }
        if (context.options.buildMacArtifactsWithoutRuntime) {
          context.notifyArtifactBuilt(macZipWithoutRuntime)
        }
      }
      else {
        buildAndSignDmgFromZip(macZip, macZipWithoutRuntime, arch, context.builtinModule).invoke()
      }
    }
  }

  fun buildAndSignDmgFromZip(macZip: Path, macZipWithoutRuntime: Path?, arch: JvmArchitecture, builtinModule: BuiltinModulesFileData?): ForkJoinTask<*> {
    return createBuildForArchTask(builtinModule, arch, macZip, macZipWithoutRuntime, customizer, context)
  }

  private fun layoutMacApp(ideaPropertiesFile: Path,
                           platformProperties: List<String>,
                           docTypes: String?,
                           macDistDir: Path,
                           context: BuildContext) {
    val macCustomizer = customizer
    copyDirWithFileFilter(context.paths.communityHomeDir.communityRoot.resolve("bin/mac"), macDistDir.resolve("bin"), customizer.binFilesFilter)
    copyDir(context.paths.communityHomeDir.communityRoot.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

    val executable = context.productProperties.baseFileName
    Files.move(macDistDir.resolve("MacOS/executable"), macDistDir.resolve("MacOS/$executable"))

    //noinspection SpellCheckingInspection
    val icnsPath = Path.of((if (context.applicationInfo.isEAP) customizer.icnsPathForEAP else null) ?: customizer.icnsPath)
    val resourcesDistDir = macDistDir.resolve("Resources")
    copyFile(icnsPath, resourcesDistDir.resolve(targetIcnsFileName))

    for (fileAssociation in customizer.fileAssociations) {
      if (!fileAssociation.iconPath.isEmpty()) {
        val source = Path.of(fileAssociation.iconPath)
        val dest = resourcesDistDir.resolve(source.fileName)
        Files.deleteIfExists(dest)
        copyFile(source, dest)
      }
    }

    val fullName = context.applicationInfo.productName

    //todo[nik] improve
    val minor = context.applicationInfo.minorVersion
    val isNotRelease = context.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
    val version = if (isNotRelease) "EAP ${context.fullBuildNumber}" else "${context.applicationInfo.majorVersion}.${minor}"
    val isEap = if (isNotRelease) "-EAP" else ""

    val properties = Files.readAllLines(ideaPropertiesFile)
    properties.addAll(platformProperties)
    Files.write(macDistDir.resolve("bin/idea.properties"), properties)

    val bootClassPath = context.xBootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/$it" }
    val classPath = context.bootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/$it" }

    val fileVmOptions = VmOptionsGenerator.computeVmOptions(context.applicationInfo.isEAP, context.productProperties).toMutableList()
    val additionalJvmArgs = context.getAdditionalJvmArguments(OsFamily.MACOS).toMutableList()
    if (!bootClassPath.isEmpty()) {
      //noinspection SpellCheckingInspection
      additionalJvmArgs.add("-Xbootclasspath/a:$bootClassPath")
    }
    val predicate: (String) -> Boolean = { it.startsWith("-D") }
    val launcherProperties = additionalJvmArgs.filter(predicate)
    val launcherVmOptions = additionalJvmArgs.filterNot(predicate)

    fileVmOptions.add("-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log")
    fileVmOptions.add("-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof")
    VmOptionsGenerator.writeVmOptions(macDistDir.resolve("bin/${executable}.vmoptions"), fileVmOptions, "\n")

    val urlSchemes = macCustomizer.urlSchemes
    val urlSchemesString = if (urlSchemes.isEmpty()) {
      ""
    }
    else {
      """
      <key>CFBundleURLTypes</key>
      <array>
        <dict>
          <key>CFBundleTypeRole</key>
          <string>Editor</string>
          <key>CFBundleURLName</key>
          <string>Stacktrace</string>
          <key>CFBundleURLSchemes</key>
          <array>
            ${urlSchemes.joinToString(separator = "\n") { "          <string>$it</string>" }}
          </array>
        </dict>
      </array>
    """
    }

    val todayYear = LocalDate.now().year.toString()
    //noinspection SpellCheckingInspection
    substituteTemplatePlaceholders(
      inputFile = macDistDir.resolve("Info.plist"),
      outputFile = macDistDir.resolve("Info.plist"),
      placeholder = "@@",
      values = listOf(
        Pair("build", context.fullBuildNumber),
        Pair("doc_types", docTypes ?: ""),
        Pair("executable", executable),
        Pair("icns", targetIcnsFileName),
        Pair("bundle_name", fullName),
        Pair("product_state", isEap),
        Pair("bundle_identifier", macCustomizer.bundleIdentifier),
        Pair("year", todayYear),
        Pair("version", version),
        Pair("vm_options", optionsToXml(launcherVmOptions)),
        Pair("vm_properties", propertiesToXml(launcherProperties, mapOf("idea.executable" to context.productProperties.baseFileName))),
        Pair("class_path", classPath),
        Pair("main_class_name", context.productProperties.mainClassName.replace('.', '/')),
        Pair("url_schemes", urlSchemesString),
        Pair("architectures", "<key>LSArchitecturePriority</key>\n    <array>\n" +
                              macCustomizer.architectures.joinToString(separator = "\n") { "      <string>$it</string>\n" } +
                              "    </array>"),
        Pair("min_osx", macCustomizer.minOSXVersion),
      )
    )

    val distBinDir = macDistDir.resolve("bin")
    Files.createDirectories(distBinDir)

    val sourceScriptDir = context.paths.communityHomeDir.communityRoot.resolve("platform/build-scripts/resources/mac/scripts")
    Files.newDirectoryStream(sourceScriptDir).use { stream ->
      val inspectCommandName = context.productProperties.inspectCommandName
      for (file in stream) {
        if (file.toString().endsWith(".sh")) {
          var fileName = file.fileName.toString()
          if (fileName == "inspect.sh" && inspectCommandName != "inspect") {
            fileName = "${inspectCommandName}.sh"
          }

          val sourceFileLf = Files.createTempFile(context.paths.tempDir, file.fileName.toString(), "")
          try {
            // Until CR (\r) will be removed from the repository checkout, we need to filter it out from Unix-style scripts
            // https://youtrack.jetbrains.com/issue/IJI-526/Force-git-to-use-LF-line-endings-in-working-copy-of-via-gitattri
            Files.writeString(sourceFileLf, Files.readString(file).replace("\r", ""))

            val target = distBinDir.resolve(fileName)
            substituteTemplatePlaceholders(
              sourceFileLf,
              target,
              "@@",
              listOf(
                Pair("product_full", fullName),
                Pair("script_name", executable),
                Pair("inspectCommandName", inspectCommandName),
              ),
              false,
            )
          }
          finally {
            Files.delete(sourceFileLf)
          }
        }
      }
    }
  }

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean): List<String> {
    val executableFilePatterns = mutableListOf(
      "bin/*.sh",
      "bin/*.py",
      "bin/fsnotifier",
      "bin/printenv",
      "bin/restarter",
      "bin/repair",
      "MacOS/*"
    )
    if (includeRuntime) {
      executableFilePatterns += context.bundledRuntime.executableFilesPatterns(OsFamily.MACOS)
    }
    return executableFilePatterns + customizer.extraExecutables
  }

  private fun createBuildForArchTask(builtinModule: BuiltinModulesFileData?,
                                     arch: JvmArchitecture,
                                     macZip: Path, macZipWithoutRuntime: Path?,
                                     customizer: MacDistributionCustomizer,
                                     context: BuildContext): ForkJoinTask<*> {
    return createTask(spanBuilder("build macOS artifacts for specific arch").setAttribute("arch", arch.name)) {
      val notarize = SystemProperties.getBooleanProperty("intellij.build.mac.notarize", true)
      ForkJoinTask.invokeAll(buildForArch(builtinModule, arch, macZip, macZipWithoutRuntime, notarize, customizer, context))
      Files.deleteIfExists(macZip)
    }
  }

  private fun buildForArch(builtinModule: BuiltinModulesFileData?,
                           arch: JvmArchitecture,
                           macZip: Path, macZipWithoutRuntime: Path?,
                           notarize: Boolean,
                           customizer: MacDistributionCustomizer,
                           context: BuildContext): List<ForkJoinTask<*>> {
    val tasks = mutableListOf<ForkJoinTask<*>?>()
    val suffix = if (arch == JvmArchitecture.x64) "" else "-${arch.fileSuffix}"
    val archStr = arch.name
    if (context.options.buildMacArtifactsWithRuntime) {
      tasks.add(createSkippableTask(
        spanBuilder("build DMG with Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_$archStr",
        context
      ) {
        signAndBuildDmg(builtinModule = builtinModule,
                        context = context,
                        customizer = customizer,
                        macHostProperties = context.proprietaryBuildTools.macHostProperties,
                        macZip = macZip,
                        isRuntimeBundled = true,
                        suffix = suffix,
                        notarize = notarize)
      })
    }

    if (context.options.buildMacArtifactsWithoutRuntime) {
      requireNotNull(macZipWithoutRuntime)
      tasks.add(createSkippableTask(
        spanBuilder("build DMG without Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_$archStr",
        context
      ) {
        signAndBuildDmg(builtinModule = builtinModule,
                        context = context,
                        customizer = customizer,
                        macHostProperties = context.proprietaryBuildTools.macHostProperties,
                        macZip = macZipWithoutRuntime,
                        isRuntimeBundled = false,
                        suffix = "-no-jdk$suffix",
                        notarize = notarize)
      })
    }
    return tasks.filterNotNull()
  }
}

private fun optionsToXml(options: List<String>): String {
  val buff = StringBuilder()
  for (it in options) {
    buff.append("        <string>").append(it).append("</string>\n")
  }
  return buff.toString().trim()
}

private fun propertiesToXml(properties: List<String>, moreProperties: Map<String, String>): String {
  val buff = StringBuilder()
  for (it in properties) {
    val p = it.indexOf('=')
    buff.append("        <key>").append(it.substring(2, p)).append("</key>\n")
    buff.append("        <string>").append(it.substring(p + 1)).append("</string>\n")
  }
  moreProperties.forEach { (key, value) ->
    buff.append("        <key>").append(key).append("</key>\n")
    buff.append("        <string>").append(value).append("</string>\n")
  }
  return buff.toString().trim()
}

internal fun getMacZipRoot(customizer: MacDistributionCustomizer, context: BuildContext): String =
  "${customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)}/Contents"

internal fun generateMacProductJson(builtinModule: BuiltinModulesFileData?, context: BuildContext, javaExecutablePath: String?): String {
  val executable = context.productProperties.baseFileName
  return generateMultiPlatformProductJson(
    relativePathToBin = "../bin",
    builtinModules = builtinModule,
    launch = listOf(
      ProductInfoLaunchData(
        os = OsFamily.MACOS.osName,
        launcherPath = "../MacOS/${executable}",
        javaExecutablePath = javaExecutablePath,
        vmOptionsFilePath = "../bin/${executable}.vmoptions",
        startupWmClass = null,
      )
    ), context = context
  )
}

private fun MacDistributionBuilder.buildMacZip(targetFile: Path,
                                               zipRoot: String,
                                               productJson: String,
                                               directories: List<Path>,
                                               extraFiles: Collection<Map.Entry<Path, String>>,
                                               executableFilePatterns: List<String>,
                                               compressionLevel: Int,
                                               errorsConsumer: (String) -> Unit) {
  spanBuilder("build zip archive for macOS")
    .setAttribute("file", targetFile.toString())
    .setAttribute("zipRoot", zipRoot)
    .setAttribute(AttributeKey.stringArrayKey("executableFilePatterns"), executableFilePatterns)
    .use {
      val fs = targetFile.fileSystem
      val patterns = executableFilePatterns.map { fs.getPathMatcher("glob:$it") }

      val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, file, relativePath ->
        when {
          patterns.any { it.matches(Path.of(relativePath)) } -> entry.unixMode = executableFileUnixMode
          SystemInfoRt.isUnix && PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions (file) -> {
            errorsConsumer("Executable permissions of $relativePath won't be set in $targetFile. " +
                           "Please make sure that executable file patterns are updated.")
          }
        }
      }

      writeNewFile(targetFile) { targetFileChannel ->
        NoDuplicateZipArchiveOutputStream(targetFileChannel).use { zipOutStream ->
          zipOutStream.setLevel(compressionLevel)

          zipOutStream.entry("$zipRoot/Resources/product-info.json", productJson.encodeToByteArray())

          val fileFilter: (Path, String) -> Boolean = { sourceFile, relativePath ->
            if (relativePath.endsWith(".txt") && !relativePath.contains('/')) {
              zipOutStream.entry("$zipRoot/Resources/${relativePath}", sourceFile)
              false
            }
            else {
              true
            }
          }
          for (dir in directories) {
            zipOutStream.dir(dir, "$zipRoot/", fileFilter = fileFilter, entryCustomizer = entryCustomizer)
          }

          for ((file, relativePath) in extraFiles) {
            zipOutStream.entry("$zipRoot/${FileUtilRt.toSystemIndependentName(relativePath)}${if (relativePath.isEmpty()) "" else "/"}${file.fileName}", file)
          }
        }
      }
      checkInArchive(archiveFile = targetFile, pathInArchive = "$zipRoot/Resources", context = context)
    }
}
