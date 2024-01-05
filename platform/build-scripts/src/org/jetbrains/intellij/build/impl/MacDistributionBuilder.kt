// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope2
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
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.io.*
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
                             private val ideaProperties: CharSequence?) : OsSpecificDistributionBuilder {
  internal companion object {
    const val NO_RUNTIME_SUFFIX = "-no-jdk"
  }

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
        <string>${context.productProperties.targetIcnsFileName}</string>
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
        <string>${if (iconPath.isEmpty()) context.productProperties.targetIcnsFileName else File(iconPath).name}</string>        
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
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    )
    customizer.getCustomIdeaProperties(context.applicationInfo).forEach(BiConsumer { k, v -> platformProperties.add("$k=$v") })

    layoutMacApp(ideaPropertyContent = ideaProperties!!,
                 platformProperties = platformProperties,
                 docTypes = getDocTypes(),
                 macDistDir = macDistDir,
                 arch = arch)

    generateBuildTxt(context, macDistDir.resolve("Resources"))

    // if copyDistFiles false, it means that we will copy dist files directly without a stage dir
    if (copyDistFiles) {
      copyDistFiles(context = context, newDir = macDistDir, os = OsFamily.MACOS, arch = arch)
    }

    customizer.copyAdditionalFiles(context = context, targetDir = macDistDir, arch = arch)
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyExtraFiles(macDistDir = osAndArchSpecificDistPath, arch = arch, copyDistFiles = false)
    }

    context.executeStep(spanBuilder("build macOS artifacts").setAttribute("arch", arch.name), BuildOptions.MAC_ARTIFACTS_STEP) {
      setLastModifiedTime(osAndArchSpecificDistPath, context)
      val runtimeDist = context.bundledRuntime.extract(os = OsFamily.MACOS, arch = arch)

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
      val macZipWithoutRuntime = macZip.resolveSibling(macZip.nameWithoutExtension + NO_RUNTIME_SUFFIX + ".zip")
      val zipRoot = getMacZipRoot(customizer, context)
      val compressionLevel = if (publishSit || publishZipOnly) Deflater.DEFAULT_COMPRESSION else Deflater.BEST_SPEED
      val extraFiles = context.getDistFiles(os = OsFamily.MACOS, arch = arch)
      val directories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDist)
      buildMacZip(
        macDistributionBuilder = this@MacDistributionBuilder,
        targetFile = macZip,
        zipRoot = zipRoot,
        arch = arch,
        productJson = generateProductJson(context, arch),
        directories = directories,
        extraFiles = extraFiles,
        includeRuntime = true,
        compressionLevel = compressionLevel
      )
      if (customizer.buildArtifactWithoutRuntime) {
        buildMacZip(
          macDistributionBuilder = this@MacDistributionBuilder,
          targetFile = macZipWithoutRuntime,
          zipRoot = zipRoot,
          arch = arch,
          productJson = generateProductJson(context, arch, withRuntime = false),
          directories = directories.filterNot { it == runtimeDist },
          extraFiles = extraFiles,
          includeRuntime = false,
          compressionLevel = compressionLevel
        )
      }
      if (publishZipOnly) {
        Span.current().addEvent("skip .dmg and .sit artifacts producing")
        context.notifyArtifactBuilt(macZip)
        if (customizer.buildArtifactWithoutRuntime) {
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

  override fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture) {
    val json = generateProductJson(context, arch)
    writeProductInfoJson(targetDir.resolve("Resources/${PRODUCT_INFO_FILE_NAME}"), json, context)
  }

  private suspend fun signMacBinaries(osAndArchSpecificDistPath: Path, runtimeDist: Path, arch: JvmArchitecture) {
    val binariesToSign = customizer.getBinariesToSign(context, arch).map(osAndArchSpecificDistPath::resolve)
    val matchers = generateExecutableFilesMatchers(includeRuntime = false, arch = arch).keys
    withContext(Dispatchers.IO) {
      signMacBinaries(files = binariesToSign, context = context)
      for (dir in listOf(osAndArchSpecificDistPath, runtimeDist)) {
        launch {
          recursivelySignMacBinaries(root = dir, context = context, executableFileMatchers = matchers)
        }
      }
    }
  }

  override fun writeVmOptions(distBinDir: Path): Path {
    return writeMacOsVmOptions(distBinDir, context)
  }

  private suspend fun layoutMacApp(ideaPropertyContent: CharSequence,
                                   platformProperties: List<String>,
                                   docTypes: String?,
                                   macDistDir: Path,
                                   arch: JvmArchitecture) {
    val macCustomizer = customizer
    val macBinDir = macDistDir.resolve("bin")
    copyDirWithFileFilter(context.paths.communityHomeDir.resolve("bin/mac"), macBinDir, customizer.binFilesFilter)
    copyFileToDir(NativeBinaryDownloader.downloadRestarter(context, OsFamily.MACOS, arch), macBinDir)
    copyDir(context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

    val executable = context.productProperties.baseFileName
    if (macCustomizer.useXPlatLauncher) {
      Files.delete(macDistDir.resolve("MacOS/executable"))
      val (execPath, licensePath) = NativeLauncherDownloader.downloadLauncher(context, OsFamily.MACOS, arch)
      copyFile(execPath, macDistDir.resolve("MacOS/${executable}"))
      copyFile(licensePath, macDistDir.resolve("license/launcher-third-party-libraries.html"))
    }
    else {
      Files.move(macDistDir.resolve("MacOS/executable"), macDistDir.resolve("MacOS/${executable}"))
    }

    //noinspection SpellCheckingInspection
    val icnsPath = Path.of((if (context.applicationInfo.isEAP) customizer.icnsPathForEAP else null) ?: customizer.icnsPath)
    val resourcesDistDir = macDistDir.resolve("Resources")
    copyFile(icnsPath, resourcesDistDir.resolve(context.productProperties.targetIcnsFileName))

    val alternativeIcon = (if (context.applicationInfo.isEAP) customizer.icnsPathForAlternativeIconForEAP else null)
                          ?: customizer.icnsPathForAlternativeIcon
    if (alternativeIcon != null) {
      copyFile(Path.of(alternativeIcon), resourcesDistDir.resolve("custom.icns"))
    }

    for (fileAssociation in customizer.fileAssociations) {
      if (!fileAssociation.iconPath.isEmpty()) {
        val source = Path.of(fileAssociation.iconPath)
        val dest = resourcesDistDir.resolve(source.fileName)
        Files.deleteIfExists(dest)
        copyFile(source, dest)
      }
    }

    Files.writeString(macBinDir.resolve(PROPERTIES_FILE_NAME),
                      (ideaPropertyContent.lineSequence() + platformProperties).joinToString(separator = "\n"))


    writeVmOptions(macBinDir)
    val jetBrainsClientContext = createJetBrainsClientContextForLaunchers(context)
    if (jetBrainsClientContext != null) {
      writeMacOsVmOptions(macBinDir, jetBrainsClientContext)
    }

    substitutePlaceholdersInInfoPlist(macDistDir, docTypes, arch, macCustomizer, context)

    Files.createDirectories(macBinDir)

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

            val target = macBinDir.resolve(fileName)
            substituteTemplatePlaceholders(
              sourceFileLf,
              target,
              "@@",
              listOf(
                Pair("product_full", context.applicationInfo.productName),
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

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> =
    customizer.generateExecutableFilesPatterns(context, includeRuntime, arch)

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
                                   macZipWithoutRuntime: Path?,
                                   notarize: Boolean,
                                   customizer: MacDistributionCustomizer,
                                   context: BuildContext) {
    val archStr = arch.name
    coroutineScope {
      createSkippableJob(
        spanBuilder("build DMG with Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_$archStr",
        context
      ) {
        signAndBuildDmg(builder = this@MacDistributionBuilder,
                        context = context,
                        customizer = customizer,
                        macZip = macZip,
                        isRuntimeBundled = true,
                        suffix = suffix(arch),
                        arch = arch,
                        notarize = notarize)
      }

      if (customizer.buildArtifactWithoutRuntime) {
        requireNotNull(macZipWithoutRuntime)
        createSkippableJob(
          spanBuilder("build DMG without Runtime").setAttribute("arch", archStr), "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_$archStr",
          context
        ) {
          signAndBuildDmg(builder = this@MacDistributionBuilder,
                          context = context,
                          customizer = customizer,
                          macZip = macZipWithoutRuntime,
                          isRuntimeBundled = false,
                          suffix = "$NO_RUNTIME_SUFFIX${suffix(arch)}",
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

private fun generateProductJson(context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean = true): String {
  return generateProductInfoJson(
    relativePathToBin = "../bin",
    builtinModules = context.builtinModule,
    launch = listOf(createProductInfoLaunchData(context, arch, withRuntime)),
    context = context
  )
}

private fun createProductInfoLaunchData(context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean): ProductInfoLaunchData {
  val jetbrainsClientCustomLaunchData = createJetBrainsClientContextForLaunchers(context)?.let {
    CustomCommandLaunchData(
      commands = listOf("thinClient", "thinClient-headless"),
      vmOptionsFilePath = "../bin/${it.productProperties.baseFileName}.vmoptions",
      bootClassPathJarNames = it.bootClassPathJarNames,
      additionalJvmArguments = it.getAdditionalJvmArguments(OsFamily.MACOS, arch) + ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS,
      mainClass = it.ideMainClassName,
    )
  }

  return ProductInfoLaunchData(
    os = OsFamily.MACOS.osName,
    arch = arch.dirName,
    launcherPath = "../MacOS/${context.productProperties.baseFileName}",
    javaExecutablePath = if (withRuntime) "../jbr/Contents/Home/bin/java" else null,
    vmOptionsFilePath = "../bin/${context.productProperties.baseFileName}.vmoptions",
    startupWmClass = null,
    bootClassPathJarNames = context.bootClassPathJarNames,
    additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch),
    mainClass = context.ideMainClassName,
    customCommands = listOfNotNull(jetbrainsClientCustomLaunchData)
  )
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

            zipOutStream.entry("$zipRoot/Resources/${PRODUCT_INFO_FILE_NAME}", productJson.encodeToByteArray())

            val fileFilter: (Path, String) -> Boolean = { sourceFile, relativePath ->
              val isContentDir = !relativePath.contains('/')
              when {
                isContentDir && relativePath.endsWith(".txt") -> {
                  zipOutStream.entry("$zipRoot/Resources/$relativePath", sourceFile)
                  false
                }
                sourceFile.fileName.toString() == ".DS_Store" -> false
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

private fun writeMacOsVmOptions(distBinDir: Path, context: BuildContext): Path {
  val executable = context.productProperties.baseFileName
  val fileVmOptions = VmOptionsGenerator.computeVmOptions(context) +
                      listOf("-Dapple.awt.application.appearance=system")
  val vmOptionsPath = distBinDir.resolve("$executable.vmoptions")
  VmOptionsGenerator.writeVmOptions(vmOptionsPath, fileVmOptions, "\n")

  return vmOptionsPath
}

private fun substitutePlaceholdersInInfoPlist(macAppDir: Path,
                                              docTypes: String?,
                                              arch: JvmArchitecture,
                                              macCustomizer: MacDistributionCustomizer,
                                              context: BuildContext) {
  val executable = context.productProperties.baseFileName
  val bootClassPath = context.xBootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/${it}" }
  val classPath = context.bootClassPathJarNames.joinToString(separator = ":") { "\$APP_PACKAGE/Contents/lib/${it}" }
  val fullName = context.applicationInfo.productName

  //todo improve
  val minor = context.applicationInfo.minorVersion
  val isNotRelease = context.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
  val version = if (isNotRelease) "EAP ${context.fullBuildNumber}" else "${context.applicationInfo.majorVersion}.${minor}"
  val isEap = if (isNotRelease) "-EAP" else ""

  val errorFilePath = "-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log"
  val heapDumpPath = "-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof"
  val additionalJvmArgs = context.getAdditionalJvmArguments(OsFamily.MACOS, arch).toMutableList()
  if (!bootClassPath.isEmpty()) {
    //noinspection SpellCheckingInspection
    additionalJvmArgs.add("-Xbootclasspath/a:${bootClassPath}")
  }
  val (launcherProperties, launcherVmOptions) = additionalJvmArgs.partition { it.startsWith("-D") }

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
            ${urlSchemes.joinToString(separator = "\n          ") { "<string>${it}</string>" }}
          </array>
        </dict>
      </array>"""
  }

  val architectures = (if (!macCustomizer.useXPlatLauncher) listOf("arm64", "x86_64")
  else when (arch) {
    JvmArchitecture.x64 -> listOf("x86_64")
    JvmArchitecture.aarch64 -> listOf("arm64")
  }).joinToString(separator = "\n      ") { "<string>${it}</string>" }

  val todayYear = LocalDate.now().year.toString()
  //noinspection SpellCheckingInspection
  substituteTemplatePlaceholders(
    inputFile = macAppDir.resolve("Info.plist"),
    outputFile = macAppDir.resolve("Info.plist"),
    placeholder = "@@",
    values = listOf(
      Pair("build", context.fullBuildNumber),
      Pair("doc_types", docTypes ?: ""),
      Pair("executable", executable),
      Pair("icns", context.productProperties.targetIcnsFileName),
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
      Pair("main_class_name", context.ideMainClassName.replace('.', '/')),
      Pair("url_schemes", urlSchemesString),
      Pair("architectures", architectures),
      Pair("min_osx", macCustomizer.minOSXVersion),
    )
  )
}

private val ProductProperties.targetIcnsFileName: String
  get() = "$baseFileName.icns"