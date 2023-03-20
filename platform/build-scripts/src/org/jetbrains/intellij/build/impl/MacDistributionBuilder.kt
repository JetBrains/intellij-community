// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.function.BiConsumer
import java.util.zip.Deflater
import kotlin.io.path.extension
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

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyExtraFiles(macDistDir = targetPath, arch = arch, copyDistFiles = true)
    }
  }

  private suspend fun doCopyExtraFiles(macDistDir: Path, arch: JvmArchitecture, copyDistFiles: Boolean) {
    @Suppress("SpellCheckingInspection")
    val platformProperties = mutableListOf(
      "\n#---------------------------------------------------------------------",
      "# macOS-specific system properties",
      "#---------------------------------------------------------------------",
      "com.apple.mrj.application.live-resize=false",
      "jbScreenMenuBar.enabled=true",
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    )
    customizer.getCustomIdeaProperties(context.applicationInfo).forEach(BiConsumer { k, v -> platformProperties.add("$k=$v") })

    layoutMacApp(ideaPropertiesFile = ideaProperties!!,
                 platformProperties = platformProperties,
                 docTypes = getDocTypes(),
                 macDistDir = macDistDir,
                 arch = arch,
                 context = context)

    generateBuildTxt(context, macDistDir.resolve("Resources"))
    // if copyDistFiles false, it means that we will copy dist files directly without stage dir
    if (copyDistFiles) {
      copyDistFiles(context = context, newDir = macDistDir, os = OsFamily.MACOS, arch = arch)
    }

    customizer.copyAdditionalFiles(context = context, targetDirectory = macDistDir)
    customizer.copyAdditionalFiles(context = context, targetDirectory = macDistDir, arch = arch)
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyExtraFiles(macDistDir = osAndArchSpecificDistPath, arch = arch, copyDistFiles = false)
    }

    context.executeStep(spanBuilder("build macOS artifacts").setAttribute("arch", arch.name), BuildOptions.MAC_ARTIFACTS_STEP) {
      setLastModifiedTime(osAndArchSpecificDistPath, context)
      val runtimeDist = context.bundledRuntime.extract(prefix = BundledRuntimeImpl.getProductPrefix(context),
                                                       os = OsFamily.MACOS,
                                                       arch = arch)

      if (context.isMacCodeSignEnabled) {
        /**
         * [BuildPaths.distAllDir] content is expected to be already signed in [buildOsSpecificDistributions]
         * preventing concurrent modifications by different [OsSpecificDistributionBuilder]s,
         * otherwise zip/tar build may fail due to FS change (changed attributes) while reading a file.
         */
        signMacBinaries(osAndArchSpecificDistPath = osAndArchSpecificDistPath, runtimeDist = runtimeDist, arch = arch)
      }

      val baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
      val publishSit = context.publishSitArchive
      val publishZipOnly = !publishSit && context.isStepSkipped(BuildOptions.MAC_DMG_STEP)
      val macZip = (if (publishZipOnly) context.paths.artifactDir else context.paths.tempDir).resolve("$baseName.mac.${arch.name}.zip")
      val macZipWithoutRuntime = macZip.resolveSibling(macZip.nameWithoutExtension + "-no-jdk.zip")
      val zipRoot = getMacZipRoot(customizer, context)
      val compressionLevel = if (publishSit || publishZipOnly) Deflater.DEFAULT_COMPRESSION else Deflater.BEST_SPEED
      val extraFiles = context.getDistFiles(os = OsFamily.MACOS, arch = arch)
      val directories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDist)
      if (context.options.buildMacArtifactsWithRuntime) {
        buildMacZip(
          macDistributionBuilder = this@MacDistributionBuilder,
          targetFile = macZip,
          zipRoot = zipRoot,
          arch = arch,
          productJson = generateMacProductJson(builtinModule = context.builtinModule,
                                               arch = arch,
                                               javaExecutablePath = "../jbr/Contents/Home/bin/java",
                                               context = context),
          directories = directories,
          extraFiles = extraFiles,
          includeRuntime = true,
          compressionLevel = compressionLevel
        )
      }
      if (context.options.buildMacArtifactsWithoutRuntime) {
        buildMacZip(
          macDistributionBuilder = this@MacDistributionBuilder,
          targetFile = macZipWithoutRuntime,
          zipRoot = zipRoot,
          arch = arch,
          productJson = generateMacProductJson(builtinModule = context.builtinModule,
                                               arch = arch,
                                               javaExecutablePath = null,
                                               context = context),
          directories = directories.filterNot { it == runtimeDist },
          extraFiles = extraFiles,
          includeRuntime = false,
          compressionLevel = compressionLevel
        )
      }
      if (publishZipOnly) {
        Span.current().addEvent("skip .dmg and .sit artifacts producing")
        if (context.options.buildMacArtifactsWithRuntime) {
          context.notifyArtifactBuilt(macZip)
        }
        if (context.options.buildMacArtifactsWithoutRuntime) {
          context.notifyArtifactBuilt(macZipWithoutRuntime)
        }
      }
      else {
        buildForArch(arch = arch,
                     macZip = macZip,
                     macZipWithoutRuntime = macZipWithoutRuntime,
                     customizer = customizer,
                     context = context)
      }
    }
  }

  private suspend fun signMacBinaries(osAndArchSpecificDistPath: Path, runtimeDist: Path, arch: JvmArchitecture) {
    val binariesToSign = customizer.getBinariesToSign(context, arch).map(osAndArchSpecificDistPath::resolve)
    withContext(Dispatchers.IO) {
      signMacBinaries(files = binariesToSign, context = context)

      for (dir in listOf(osAndArchSpecificDistPath, runtimeDist)) {
        launch {
          recursivelySignMacBinaries(root = dir,
                                     context = context,
                                     executableFileMatchers = generateExecutableFilesMatchers(includeRuntime = false, arch = arch).keys)
        }
      }
    }
  }

  private fun layoutMacApp(ideaPropertiesFile: Path,
                           platformProperties: List<String>,
                           docTypes: String?,
                           macDistDir: Path,
                           arch: JvmArchitecture,
                           context: BuildContext) {
    val macCustomizer = customizer
    copyDirWithFileFilter(context.paths.communityHomeDir.resolve("bin/mac"), macDistDir.resolve("bin"), customizer.binFilesFilter)
    copyDir(context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

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

    val bootClassPath = context.xBootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/${it}" }
    val classPath = context.bootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/${it}" }

    val fileVmOptions = VmOptionsGenerator.computeVmOptions(context.applicationInfo.isEAP, context.productProperties)
    VmOptionsGenerator.writeVmOptions(macDistDir.resolve("bin/${executable}.vmoptions"), fileVmOptions, "\n")

    val errorFilePath = "-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log"
    val heapDumpPath = "-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof"
    val additionalJvmArgs = context.getAdditionalJvmArguments(OsFamily.MACOS, arch).toMutableList()
    if (!bootClassPath.isEmpty()) {
      //noinspection SpellCheckingInspection
      additionalJvmArgs.add("-Xbootclasspath/a:${bootClassPath}")
    }
    val predicate: (String) -> Boolean = { it.startsWith("-D") }
    val launcherProperties = additionalJvmArgs.filter(predicate)
    val launcherVmOptions = additionalJvmArgs.filterNot(predicate)

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
            ${urlSchemes.joinToString(separator = "\n") { "          <string>${it}</string>" }}
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
        Pair("xx_error_file", errorFilePath),
        Pair("xx_heap_dump", heapDumpPath),
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

    val sourceScriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/scripts")
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

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
    return customizer.generateExecutableFilesPatterns(context, includeRuntime, arch)
  }

  private suspend fun buildForArch(arch: JvmArchitecture,
                                   macZip: Path,
                                   macZipWithoutRuntime: Path?, customizer: MacDistributionCustomizer,
                                   context: BuildContext) {
    spanBuilder("build macOS artifacts for specific arch").setAttribute("arch", arch.name).useWithScope2 {
      val notarize = SystemProperties.getBooleanProperty(
        "intellij.build.mac.notarize",
        !context.isStepSkipped(BuildOptions.MAC_NOTARIZE_STEP)
      )
      withContext(Dispatchers.IO) {
        buildForArch(arch, macZip, macZipWithoutRuntime, notarize, customizer, context)
        Files.deleteIfExists(macZip)
      }
    }
  }

  private suspend fun buildForArch(arch: JvmArchitecture,
                                   macZip: Path,
                                   macZipWithoutRuntime: Path?, notarize: Boolean,
                                   customizer: MacDistributionCustomizer,
                                   context: BuildContext) {
    val suffix = if (arch == JvmArchitecture.x64) "" else "-${arch.fileSuffix}"
    val archStr = arch.name
    coroutineScope {
      if (context.options.buildMacArtifactsWithRuntime) {
        createSkippableJob(
          spanBuilder("build DMG with Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_$archStr",
          context
        ) {
          signAndBuildDmg(builder = this@MacDistributionBuilder,
                          context = context,
                          customizer = customizer,
                          macHostProperties = context.proprietaryBuildTools.macHostProperties,
                          macZip = macZip,
                          isRuntimeBundled = true,
                          suffix = suffix,
                          arch = arch,
                          notarize = notarize)
        }
      }

      if (context.options.buildMacArtifactsWithoutRuntime) {
        requireNotNull(macZipWithoutRuntime)
        createSkippableJob(
          spanBuilder("build DMG without Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_$archStr",
          context
        ) {
          signAndBuildDmg(builder = this@MacDistributionBuilder,
                          context = context,
                          customizer = customizer,
                          macHostProperties = context.proprietaryBuildTools.macHostProperties,
                          macZip = macZipWithoutRuntime,
                          isRuntimeBundled = false,
                          suffix = "-no-jdk$suffix",
                          arch = arch,
                          notarize = notarize)
        }
      }
    }
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

internal fun generateMacProductJson(builtinModule: BuiltinModulesFileData?,
                                    arch: JvmArchitecture,
                                    javaExecutablePath: String?,
                                    context: BuildContext): String {
  val executable = context.productProperties.baseFileName
  return generateMultiPlatformProductJson(
    relativePathToBin = "../bin",
    builtinModules = builtinModule,
    launch = listOf(ProductInfoLaunchData(
      os = OsFamily.MACOS.osName,
      arch = arch.dirName,
      launcherPath = "../MacOS/${executable}",
      javaExecutablePath = javaExecutablePath,
      vmOptionsFilePath = "../bin/${executable}.vmoptions",
      startupWmClass = null,
      bootClassPathJarNames = context.bootClassPathJarNames,
      additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch))),
    context = context)
}

private suspend fun buildMacZip(macDistributionBuilder: MacDistributionBuilder,
                                targetFile: Path,
                                zipRoot: String,
                                arch: JvmArchitecture,
                                productJson: String,
                                directories: List<Path>,
                                extraFiles: Collection<DistFile>,
                                includeRuntime: Boolean,
                                compressionLevel: Int) {
  val executableFileMatchers = macDistributionBuilder.generateExecutableFilesMatchers(includeRuntime, arch)
  withContext(Dispatchers.IO) {
    spanBuilder("build zip archive for macOS")
      .setAttribute("file", targetFile.toString())
      .setAttribute("zipRoot", zipRoot)
      .setAttribute(AttributeKey.stringArrayKey("directories"), directories.map { it.toString() })
      .setAttribute(AttributeKey.stringArrayKey("executableFilePatterns"), executableFileMatchers.values.toList())
      .useWithScope2 {
        val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, file, relativePathString ->
          val relativePath = Path.of(relativePathString)
          if (executableFileMatchers.any { it.key.matches(relativePath) } || (SystemInfoRt.isUnix && Files.isExecutable(file))) {
            entry.unixMode = executableFileUnixMode
          }
        }

        writeNewFile(targetFile) { targetFileChannel ->
          NoDuplicateZipArchiveOutputStream(channel = targetFileChannel,
                                            compress = macDistributionBuilder.context.options.compressZipFiles).use { zipOutStream ->
            zipOutStream.setLevel(compressionLevel)
            if (compressionLevel == Deflater.BEST_SPEED) {
              // file is used only for transfer to mac builder
              zipOutStream.setUseZip64(Zip64Mode.Never)
            }

            zipOutStream.entry("$zipRoot/Resources/product-info.json", productJson.encodeToByteArray())

            val fileFilter: (Path, String) -> Boolean = { sourceFile, relativePath ->
              val isContentDir = !relativePath.contains('/')
              when {
                isContentDir && relativePath.endsWith(".txt") -> {
                  zipOutStream.entry("$zipRoot/Resources/$relativePath", sourceFile)
                  false
                }
                isContentDir && sourceFile.fileName.toString() != "Info.plist" -> {
                  error("Only Info.plist file is allowed in $zipRoot directory but found $zipRoot/$relativePath")
                }
                !isContentDir && relativePath.startsWith("bin/") && sourceFile.extension == "jnilib" -> {
                  val dylib = "${sourceFile.nameWithoutExtension}.dylib"
                  check(Files.exists(sourceFile.resolveSibling(dylib))) {
                    "$dylib->${sourceFile.fileName} symlink is expected in $zipRoot/bin"
                  }
                  true
                }
                else -> {
                  true
                }
              }
            }
            for ((index, dir) in directories.withIndex()) {
              try {
                zipOutStream.dir(startDir = dir, prefix = "$zipRoot/", fileFilter = fileFilter, entryCustomizer = entryCustomizer)
              }
              catch (e: Exception) {
                // provide more context to error - dir
                throw RuntimeException("Cannot pack $dir to $targetFile (already packed: ${directories.subList(0, index)})", e)
              }
            }

            for (item in extraFiles) {
              zipOutStream.entry(name = "$zipRoot/${item.relativePath}", file = item.file)
            }
          }
        }
        checkInArchive(archiveFile = targetFile, pathInArchive = "$zipRoot/Resources", context = macDistributionBuilder.context)
      }
  }
}
