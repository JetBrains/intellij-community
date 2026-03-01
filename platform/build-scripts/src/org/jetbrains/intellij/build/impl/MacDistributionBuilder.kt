// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.InMemoryDistFileContent
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LocalDistFileContent
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.MacLibcImpl
import org.jetbrains.intellij.build.NativeBinaryDownloader
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.createFrontendContextForLaunchers
import org.jetbrains.intellij.build.impl.languageServer.generateLspServerLaunchData
import org.jetbrains.intellij.build.impl.macOS.MachOUuid
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.generateEmbeddedFrontendLaunchData
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.productInfo.resolveProductInfoJsonSibling
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.impl.productInfo.writeProductInfoJson
import org.jetbrains.intellij.build.impl.qodana.generateQodanaLaunchData
import org.jetbrains.intellij.build.impl.stdioMcpRunner.generateStdioMcpRunnerLaunchData
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.io.substituteTemplatePlaceholders
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.zip.Deflater
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import kotlin.io.path.writeText

private const val NO_RUNTIME_SUFFIX = "-no-jdk"

class MacDistributionBuilder(
  private val customizer: MacDistributionCustomizer,
  private val ideaProperties: CharSequence?,
  private val context: BuildContext,
) : OsSpecificDistributionBuilder {
  override val targetOs: OsFamily
    get() = OsFamily.MACOS

  override val targetLibcImpl: LibcImpl
    get() = MacLibcImpl.DEFAULT

  private val targetIcnsFileName: String
    get() = "${context.productProperties.baseFileName}.icns"

  private fun getDocTypes(): String {
    val associations = mutableListOf<String>()

    if (customizer.associateIpr) {
      associations += """
        |<dict>
        |        <key>CFBundleTypeExtensions</key>
        |        <array>
        |          <string>ipr</string>
        |        </array>
        |        <key>CFBundleTypeIconFile</key>
        |        <string>${targetIcnsFileName}</string>
        |        <key>CFBundleTypeName</key>
        |        <string>${context.applicationInfo.fullProductName} Project File</string>
        |        <key>CFBundleTypeRole</key>
        |        <string>Editor</string>
        |      </dict>""".trimMargin()
    }

    for (fileAssociation in customizer.fileAssociations) {
      val iconPath = fileAssociation.iconPath
      associations += """
        |<dict>
        |        <key>CFBundleTypeExtensions</key>
        |        <array>
        |          <string>${fileAssociation.extension}</string>
        |        </array>
        |        <key>CFBundleTypeIconFile</key>
        |        <string>${if (iconPath.isEmpty()) targetIcnsFileName else Path.of(iconPath).name}</string>        
        |        <key>CFBundleTypeRole</key>
        |        <string>Editor</string>
        |      </dict>""".trimMargin()
    }

    return associations.joinToString(separator = "\n      ", postfix = customizer.additionalDocTypes)
  }

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyFilesForOsDistribution(targetPath = targetPath, arch = arch, copyDistFiles = true)
    }
  }

  private suspend fun doCopyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture, copyDistFiles: Boolean) {
    val macBinDir = targetPath.resolve("bin").createDirectories()
    writeVmOptions(macBinDir)

    context.executeStep(spanBuilder("copy product bin files"), BuildOptions.PRODUCT_BIN_DIR_STEP) {
      copyDirWithFileFilter(context.paths.communityHomeDir.resolve("bin/mac"), macBinDir, customizer.binFilesFilter)
      copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.MACOS, arch), macBinDir)

      createFrontendContextForLaunchers(context)?.let { clientContext ->
        writeMacOsVmOptions(macBinDir, clientContext)
      }
      val executable = context.productProperties.baseFileName
      generateScripts(macBinDir, executable, context)
    }

    val platformProperties = mutableListOf(
      "\n#---------------------------------------------------------------------",
      "# macOS-specific system properties",
      "#---------------------------------------------------------------------",
      "com.apple.mrj.application.live-resize=false",
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    )
    for ((k, v) in customizer.getCustomIdeaProperties(context.applicationInfo)) {
      platformProperties.add("$k=$v")
    }

    val ideaPropertyContent = ideaProperties!!
    Files.writeString(
      macBinDir.resolve(PROPERTIES_FILE_NAME),
      (ideaPropertyContent.lineSequence() + platformProperties).joinToString(separator = "\n")
    )

    if (context.options.isLanguageServer) {
       layoutMacCli(macDistDir = targetPath, arch = arch)
    }
    else {
      layoutMacApp(docTypes = getDocTypes(), macDistDir = targetPath, arch = arch)
    }
    generateBuildTxt(targetPath.resolve("Resources"), context)

    // if copyDistFiles false, it means that we will copy dist files directly without a stage dir
    if (copyDistFiles) {
      copyDistFiles(newDir = targetPath, os = OsFamily.MACOS, arch = arch, libcImpl = MacLibcImpl.DEFAULT, context = context)
    }

    customizer.copyAdditionalFiles(context, targetDir = targetPath, arch = arch)
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyFilesForOsDistribution(osAndArchSpecificDistPath, arch, false)
    }

    context.executeStep(spanBuilder("build macOS artifacts").setAttribute("arch", arch.name), BuildOptions.MAC_ARTIFACTS_STEP) {
      setLastModifiedTime(osAndArchSpecificDistPath, context)

      val executableFileMatchers = generateExecutableFilesMatchers(includeRuntime = true, arch = arch).keys
      updateExecutablePermissions(osAndArchSpecificDistPath, executableFileMatchers)

      val runtimeDir = context.bundledRuntime.extract(os = OsFamily.MACOS, arch = arch, libc = MacLibcImpl.DEFAULT)
      updateExecutablePermissions(runtimeDir, executableFileMatchers)

      if (context.isMacCodeSignEnabled) {
        /**
         * [BuildPaths.distAllDir] content is expected to be already signed in [buildOsSpecificDistributions]
         * preventing concurrent modifications by different [OsSpecificDistributionBuilder]s,
         * otherwise zip/tar build may fail due to FS change (changed attributes) while reading a file.
         */
        signMacBinaries(osAndArchSpecificDistPath, runtimeDir, arch)
      }

      val baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
      val publishZipOnly = !publishSitArchive && context.isStepSkipped(BuildOptions.MAC_DMG_STEP)
      val macZip = (if (publishZipOnly) context.paths.artifactDir else context.paths.tempDir).resolve("$baseName.mac.${arch.name}.zip")
      val macZipProductInfoJson = macZip.resolveProductInfoJsonSibling()
      val macZipWithoutRuntime = macZip.resolveSibling(macZip.nameWithoutExtension + NO_RUNTIME_SUFFIX + ".zip")
      val macZipWithoutRuntimeProductInfoJson = macZipWithoutRuntime.resolveProductInfoJsonSibling()
      val zipRoot = getMacZipRoot(customizer, context)
      val compressionLevel = if (publishSitArchive || publishZipOnly) Deflater.DEFAULT_COMPRESSION else Deflater.BEST_SPEED
      val extraFiles = context.getDistFiles(OsFamily.MACOS, arch, MacLibcImpl.DEFAULT)
      val directories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDir)
      val builder = this@MacDistributionBuilder

      val productJson = generateProductJson(arch = arch, withRuntime = true, context = context)
      val productJsonWithoutRuntime = generateProductJson(arch = arch, withRuntime = false, context = context)
      withContext(Dispatchers.IO) {
        macZipProductInfoJson.writeText(productJson)
        macZipWithoutRuntimeProductInfoJson.writeText(productJsonWithoutRuntime)
      }

      buildMacZip(
        macDistributionBuilder = builder,
        targetFile = macZip,
        zipRoot = zipRoot,
        arch = arch,
        productJson = productJson,
        directories = directories,
        extraFiles = extraFiles,
        includeRuntime = true,
        compressionLevel = compressionLevel
      )

      if (customizer.buildArtifactWithoutRuntime) {
        val directoriesSansRuntime = directories.filterNot { it == runtimeDir }
        buildMacZip(
          macDistributionBuilder = builder,
          targetFile = macZipWithoutRuntime,
          zipRoot = zipRoot,
          arch = arch,
          productJson = productJsonWithoutRuntime,
          directories = directoriesSansRuntime,
          extraFiles = extraFiles,
          includeRuntime = false,
          compressionLevel = compressionLevel
        )
      }

      if (publishZipOnly) {
        Span.current().addEvent("skip .dmg and .sit artifacts producing")
        context.notifyArtifactBuilt(macZip)
        context.notifyArtifactBuilt(macZipWithoutRuntimeProductInfoJson)
        if (customizer.buildArtifactWithoutRuntime) {
          context.notifyArtifactBuilt(macZipWithoutRuntime)
          context.notifyArtifactBuilt(macZipWithoutRuntimeProductInfoJson)
        }
      }
      else {
        buildForArch(arch, macZip, macZipProductInfoJson, macZipWithoutRuntime, macZipWithoutRuntimeProductInfoJson)
      }
    }
  }

  override suspend fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture): Path {
    val json = generateProductJson(arch, withRuntime = true, context)
    val file = targetDir.resolve("Resources/${PRODUCT_INFO_FILE_NAME}")
    writeProductInfoJson(file, json, context)
    return file
  }

  private suspend fun signMacBinaries(osAndArchSpecificDistPath: Path, runtimeDist: Path, arch: JvmArchitecture) {
    val binariesToSign = customizer.getBinariesToSign(context, arch).map(osAndArchSpecificDistPath::resolve)
    val matchers = generateExecutableFilesMatchers(includeRuntime = false, arch).keys
    withContext(Dispatchers.IO) {
      signMacBinaries(binariesToSign, context)
      for (dir in listOf(osAndArchSpecificDistPath, runtimeDist)) {
        launch(CoroutineName("recursively signing macOS binaries in $dir")) {
          recursivelySignMacBinaries(coroutineScope = this, dir, context, matchers)
        }
      }
    }
  }

  override fun writeVmOptions(distBinDir: Path): Path = writeMacOsVmOptions(distBinDir, context)

  private suspend fun layoutMacCli(macDistDir: Path, arch: JvmArchitecture) {
    val executable = context.productProperties.baseFileName
    val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.MACOS, arch)
    val copy = macDistDir.resolve("bin/$executable")
    context.addExtraExecutablePattern(OsFamily.MACOS, "bin/${context.productProperties.baseFileName}")
    copyFile(execPath, copy)
    MachOUuid(copy, customizer, context).patch()
    copyFile(licensePath, macDistDir.resolve("license/launcher-third-party-libraries.html"))
    macDistDir.resolve("Resources").createDirectories()
  }

  private suspend fun layoutMacApp(
    docTypes: String?,
    macDistDir: Path,
    arch: JvmArchitecture,
  ) {
    copyDir(context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

    val executable = context.productProperties.baseFileName
    val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.MACOS, arch)
    val copy = macDistDir.resolve("MacOS/$executable")
    copyFile(execPath, copy)
    MachOUuid(copy, customizer, context).patch()
    copyFile(licensePath, macDistDir.resolve("license/launcher-third-party-libraries.html"))

    val icnsPath = locateIcnsForMacApp(customizer, context)
    val resourcesDistDir = macDistDir.resolve("Resources")
    copyFile(icnsPath, resourcesDistDir.resolve(targetIcnsFileName))

    @Suppress("DEPRECATION")
    val alternativeIcon = customizer.icnsPathForAlternativeIconForEAP?.takeIf { context.applicationInfo.isEAP } ?: customizer.icnsPathForAlternativeIcon
    if (alternativeIcon != null) {
      copyFile(alternativeIcon, resourcesDistDir.resolve("custom.icns"))
    }
    if (context.isEmbeddedFrontendEnabled) {
      val icnsForFrontendApp = locateIcnsForFrontendMacApp(context)
      if (icnsForFrontendApp != null) {
        //path to the copied file will be passed as the value of `apple.awt.application.icon` property in `getAdditionalEmbeddedClientVmOptions`
        copyFile(icnsForFrontendApp, resourcesDistDir.resolve("frontend.icns"))
      }
    }

    for (fileAssociation in customizer.fileAssociations) {
      if (!fileAssociation.iconPath.isEmpty()) {
        val source = Path.of(fileAssociation.iconPath)
        val dest = resourcesDistDir.resolve(source.fileName)
        Files.deleteIfExists(dest)
        copyFile(source, dest)
      }
    }

    substitutePlaceholdersInInfoPlist(macDistDir, docTypes, arch)
  }

  override suspend fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, libc: LibcImpl): Sequence<String> {
    val base = customizer.generateExecutableFilesPatterns(includeRuntime, arch, context)
    val pluginPatterns = collectPluginExecutablePatterns(context, OsFamily.MACOS, arch, libc)
    return base + pluginPatterns
  }

  private suspend fun buildForArch(
    arch: JvmArchitecture,
    macZip: Path, macZipProductInfoJson: Path,
    macZipWithoutRuntime: Path, macZipWithoutRuntimeProductInfoJson: Path,
  ) {
    spanBuilder("build macOS artifacts for specific arch").setAttribute("arch", arch.name).use(Dispatchers.IO) {
      val notarize =
        (System.getProperty("intellij.build.mac.notarize")?.toBoolean() ?: !context.isStepSkipped(BuildOptions.MAC_NOTARIZE_STEP)) &&
        !context.isStepSkipped(BuildOptions.MAC_SIGN_STEP)
      buildForArch(arch, macZip, macZipProductInfoJson, macZipWithoutRuntime, macZipWithoutRuntimeProductInfoJson, notarize)
      Files.deleteIfExists(macZip)
    }
  }

  private suspend fun buildForArch(
    arch: JvmArchitecture,
    macZip: Path, macZipProductInfoJson: Path,
    macZipWithoutRuntime: Path, macZipWithoutRuntimeProductInfoJson: Path,
    notarize: Boolean,
  ) {
    val archStr = arch.name
    coroutineScope {
      val taskId = "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_${archStr}"
      createSkippableJob(spanBuilder("build DMG with Runtime").setAttribute("arch", archStr), taskId, context) {
        signAndBuildDmg(macZip, macZipProductInfoJson, isRuntimeBundled = true, suffix(arch), arch, notarize)
      }

      if (customizer.buildArtifactWithoutRuntime) {
        createSkippableJob(
          spanBuilder("build DMG without Runtime").setAttribute("arch", archStr),
          stepId = "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_${archStr}",
          context
        ) {
          val suffix = "${NO_RUNTIME_SUFFIX}${suffix(arch)}"
          signAndBuildDmg(macZipWithoutRuntime, macZipWithoutRuntimeProductInfoJson, isRuntimeBundled = false, suffix, arch, notarize)
        }
      }
    }
  }

  override fun distributionFilesBuilt(arch: JvmArchitecture): List<Path> {
    val archSuffix = suffix(arch)
    return sequenceOf(
      "${archSuffix}.dmg",
      "${archSuffix}.sit",
      ".mac.${arch.name}.zip",
      "${NO_RUNTIME_SUFFIX}${archSuffix}.dmg",
      "${NO_RUNTIME_SUFFIX}${archSuffix}.sit",
      ".mac.${arch.name}${NO_RUNTIME_SUFFIX}.zip"
    )
      .map { suffix -> context.productProperties.getBaseArtifactName(context) + suffix }
      .map(context.paths.artifactDir::resolve)
      .filter { Files.exists(it) }
      .toList()
  }

  override fun isRuntimeBundled(file: Path): Boolean = !file.name.contains(NO_RUNTIME_SUFFIX)

  private suspend fun generateProductJson(arch: JvmArchitecture, withRuntime: Boolean, context: BuildContext): String {
    return generateProductInfoJson(
      relativePathToBin = "../bin",
      builtinModules = context.builtinModule,
      launch = listOf(
        ProductInfoLaunchData.create(
          os = OsFamily.MACOS.osName,
          arch = arch.dirName,
          launcherPath =
            if (context.options.isLanguageServer) "../bin/${context.productProperties.baseFileName}"
            else "../MacOS/${context.productProperties.baseFileName}",
          javaExecutablePath = if (withRuntime) "../jbr/Contents/Home/bin/java" else null,
          vmOptionsFilePath = "../bin/${context.productProperties.baseFileName}.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch),
          mainClass = context.ideMainClassName,
          customCommands = when {
            context.options.isLanguageServer -> listOf(
              generateLspServerLaunchData(context)
            )
            else -> listOfNotNull(
              generateEmbeddedFrontendLaunchData(arch, OsFamily.MACOS, context) {
                "../bin/${it.productProperties.baseFileName}.vmoptions"
              },
              generateQodanaLaunchData(context, arch, OsFamily.MACOS),
              generateStdioMcpRunnerLaunchData(context)
            )
          }
        )
      ),
      context
    )
  }

  private suspend fun buildMacZip(
    macDistributionBuilder: MacDistributionBuilder,
    targetFile: Path,
    zipRoot: String,
    arch: JvmArchitecture,
    productJson: String,
    directories: List<Path>,
    extraFiles: Collection<DistFile>,
    includeRuntime: Boolean,
    compressionLevel: Int,
  ) {
    val executableFileMatchers = macDistributionBuilder.generateExecutableFilesMatchers(includeRuntime, arch)
    spanBuilder("build zip archive for macOS")
      .setAttribute("file", targetFile.toString())
      .setAttribute("zipRoot", zipRoot)
      .setAttribute(AttributeKey.stringArrayKey("directories"), directories.map { it.toString() })
      .setAttribute(AttributeKey.stringArrayKey("executableFilePatterns"), executableFileMatchers.values.toList())
      .use(Dispatchers.IO) {
        val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, file, relativePathString ->
          val relativePath = Path.of(relativePathString)
          if (executableFileMatchers.any { it.key.matches(relativePath) } || (SystemInfoRt.isUnix && Files.isExecutable(file))) {
            entry.unixMode = executableFileUnixMode
          }
        }

        writeNewFile(targetFile) { targetFileChannel ->
          NoDuplicateZipArchiveOutputStream(targetFileChannel, macDistributionBuilder.context.options.compressZipFiles).use { zipOutStream ->
            zipOutStream.setLevel(compressionLevel)
            if (compressionLevel == Deflater.BEST_SPEED) {
              zipOutStream.setUseZip64(Zip64Mode.Never)
            }

            zipOutStream.entry("${zipRoot}/Resources/${PRODUCT_INFO_FILE_NAME}", productJson.encodeToByteArray())

            val fileFilter: (Path, String) -> Boolean = { sourceFile, relativePath ->
              val isContentDir = !relativePath.contains('/')
              when {
                isContentDir && relativePath.endsWith(".txt") -> {
                  zipOutStream.entry("${zipRoot}/Resources/${relativePath}", sourceFile)
                  false
                }
                sourceFile.fileName.toString() == ".DS_Store" -> false
                isContentDir && sourceFile.fileName.toString() != "Info.plist" -> {
                  error("Only Info.plist file is allowed in ${zipRoot} directory but found ${zipRoot}/${relativePath}")
                }
                !isContentDir && relativePath.startsWith("bin/") && sourceFile.extension == "jnilib" -> {
                  val dylib = "${sourceFile.nameWithoutExtension}.dylib"
                  check(Files.exists(sourceFile.resolveSibling(dylib))) {
                    "$dylib->${sourceFile.fileName} symlink is expected in ${zipRoot}/bin"
                  }
                  true
                }
                else -> true
              }
            }
            for ((index, dir) in directories.withIndex()) {
              try {
                zipOutStream.dir(dir, prefix = "${zipRoot}/", fileFilter, entryCustomizer)
              }
              catch (e: Exception) {
                // provide more context to error - dir
                throw RuntimeException("Cannot pack $dir to $targetFile (already packed: ${directories.subList(0, index)})", e)
              }
            }

            for (item in extraFiles) {
              when (val content = item.content) {
                is LocalDistFileContent -> {
                  zipOutStream.entry("${zipRoot}/${item.relativePath}", content.file, if (content.isExecutable) executableFileUnixMode else -1)
                }
                is InMemoryDistFileContent -> {
                  zipOutStream.entry("${zipRoot}/${item.relativePath}", content.data)
                }
              }
            }
          }
        }

        validateProductJson(targetFile, pathInArchive = "${zipRoot}/Resources", macDistributionBuilder.context)
      }
  }

  private fun writeMacOsVmOptions(distBinDir: Path, context: BuildContext): Path {
    val executable = context.productProperties.baseFileName
    val vmOptions = generateVmOptions(context).asSequence() + sequenceOf("-Dapple.awt.application.appearance=system")
    val vmOptionsPath = distBinDir.resolve("${executable}.vmoptions")
    writeVmOptions(vmOptionsPath, vmOptions, separator = "\n")
    return vmOptionsPath
  }

  private fun substitutePlaceholdersInInfoPlist(macAppDir: Path, docTypes: String?, arch: JvmArchitecture) {
    val fullName = context.applicationInfo.fullProductName
    //todo improve
    val minor = context.applicationInfo.minorVersion
    val isNotRelease = context.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
    val version = if (isNotRelease) "EAP ${context.fullBuildNumber}" else "${context.applicationInfo.majorVersion}.${minor}"
    val isEap = if (isNotRelease) "-EAP" else ""
    val urlSchemes = customizer.urlSchemes
    val urlSchemesString = if (urlSchemes.isEmpty()) ""
    else """
      |<key>CFBundleURLTypes</key>
      |    <array>
      |      <dict>
      |        <key>CFBundleTypeRole</key>
      |        <string>Editor</string>
      |        <key>CFBundleURLName</key>
      |        <string>Stacktrace</string>
      |        <key>CFBundleURLSchemes</key>
      |        <array>
      |          ${urlSchemes.joinToString(separator = "\n          ") { "<string>${it}</string>" }}
      |        </array>
      |      </dict>
      |    </array>""".trimMargin()
    val architecture = when (arch) {
      JvmArchitecture.x64 -> "x86_64"
      JvmArchitecture.aarch64 -> "arm64"
    }
    val todayYear = LocalDate.now().year.toString()

    substituteTemplatePlaceholders(
      inputFile = macAppDir.resolve("Info.plist"),
      outputFile = macAppDir.resolve("Info.plist"),
      placeholder = "@@",
      values = listOf(
        Pair("build", context.fullBuildNumber),
        Pair("doc_types", docTypes ?: ""),
        Pair("executable", context.productProperties.baseFileName),
        Pair("icns", targetIcnsFileName),
        Pair("bundle_name", fullName),
        Pair("product_state", isEap),
        Pair("bundle_identifier", customizer.bundleIdentifier),
        Pair("year", todayYear),
        Pair("version", version),
        Pair("url_schemes", urlSchemesString),
        Pair("architecture", architecture),
        Pair("min_osx", customizer.minOSXVersion),
      )
    )

    macAppDir.resolve("Resources").walk()
      .filter { it.extension == "strings" && it.isRegularFile() }
      .forEach { file ->
        substituteTemplatePlaceholders(
          inputFile = file,
          outputFile = file,
          placeholder = "@@",
          values = listOf(
            Pair("bundle_name", fullName),
            Pair("version", version),
            Pair("year", todayYear),
            Pair("build", context.fullBuildNumber)
          )
        )
      }
  }

  private fun getMacZipRoot(customizer: MacDistributionCustomizer, context: BuildContext): String {
    return "${customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)}/Contents"
  }

  private val publishSitArchive: Boolean
    get() = !context.isStepSkipped(BuildOptions.MAC_SIT_PUBLICATION_STEP)

  private suspend fun signAndBuildDmg(macZip: Path, productInfoJson: Path, isRuntimeBundled: Boolean, suffix: String, arch: JvmArchitecture, notarize: Boolean) {
    require(Files.isRegularFile(macZip))

    val baseName = context.productProperties.getBaseArtifactName(context) + suffix
    val sitFile = (if (publishSitArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$baseName.sit")
    val sitProductInfoFile = sitFile.resolveProductInfoJsonSibling()
    copyFile(productInfoJson, sitProductInfoFile)
    Files.move(macZip, sitFile, StandardCopyOption.REPLACE_EXISTING)

    if (context.isMacCodeSignEnabled) {
      context.proprietaryBuildTools.signTool.signFiles(listOf(sitFile), context, macSigningOptions("application/x-mac-app-zip", context))
    }

    if (notarize) {
      notarize(sitFile, context)
    }

    buildDmg(sitFile = sitFile, productInfoJson = productInfoJson, dmgName = "${baseName}.dmg", staple = notarize)

    if (publishSitArchive) {
      context.notifyArtifactBuilt(sitFile)
      context.notifyArtifactBuilt(sitProductInfoFile)
    }

    val zipRoot = getMacZipRoot(customizer, context)
    checkExecutablePermissions(distribution = sitFile, root = zipRoot, includeRuntime = isRuntimeBundled, arch = arch, libc = targetLibcImpl, context = context)

    if (isRuntimeBundled) {
      generateIntegrityManifest(sitFile, zipRoot, arch, context)
    }
  }

  private suspend fun buildDmg(sitFile: Path, productInfoJson: Path, dmgName: String, staple: Boolean) {
    val tempDir = context.paths.tempDir.resolve(sitFile.name.replace(".sit", ""))
    try {
      context.executeStep(spanBuilder("build .dmg locally"), BuildOptions.MAC_DMG_STEP) {
        NioFiles.deleteRecursively(tempDir)
        NioFiles.createDirectories(tempDir)
        NioFiles.createDirectories(context.paths.artifactDir)
        val entrypoint = prepareDmgBuildScripts(tempDir, staple, customizer, context)
        if (!SystemInfoRt.isMac) {
          it.addEvent(".dmg can be built only on macOS")
          if (publishSitArchive) {
            publishDmgBuildScripts(entrypoint, tempDir, context)
          }
          return@executeStep
        }
        val tmpSit = Files.move(sitFile, tempDir.resolve(sitFile.fileName))
        runProcess(args = listOf("./${entrypoint.name}"), workingDir = tempDir, inheritOut = true)
        val dmgFile = tempDir.resolve(dmgName)
        val dmgProductInfoJson = dmgFile.resolveProductInfoJsonSibling()
        copyFile(productInfoJson, dmgProductInfoJson)
        check(Files.exists(dmgFile)) { "$dmgFile wasn't created" }
        Files.move(dmgFile, context.paths.artifactDir.resolve(dmgFile.name), StandardCopyOption.REPLACE_EXISTING)
        context.notifyArtifactBuilt(dmgFile)
        context.notifyArtifactBuilt(dmgProductInfoJson)
        Files.move(tmpSit, sitFile)
      }
    }
    finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }
}

private fun generateScripts(macBinDir: Path, executable: String, context: BuildContext) {
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
              Pair("product_full", context.applicationInfo.fullProductName),
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

private fun prepareDmgBuildScripts(tempDir: Path, staple: Boolean, customizer: MacDistributionCustomizer, context: BuildContext): Path {
  NioFiles.deleteRecursively(tempDir)
  Files.createDirectories(tempDir)
  val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    Files.copy(locateDmgImageForMacApp(customizer, context), dmgImageCopy)
  val scriptsDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
  Files.copy(scriptsDir.resolve("makedmg.sh"), tempDir.resolve("makedmg.sh"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  NioFiles.setExecutable(tempDir.resolve("makedmg.sh"))
  Files.copy(scriptsDir.resolve("makedmg.py"), tempDir.resolve("makedmg.py"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  Files.copy(scriptsDir.resolve("staple.sh"), tempDir.resolve("staple.sh"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  val entrypoint = tempDir.resolve("build.sh")
  Files.writeString(
    entrypoint,
    Files.readString(scriptsDir.resolve("build-template.sh"))
      .resolveTemplateVar("staple", "$staple")
      .resolveTemplateVar("appName", context.fullBuildNumber)
      .resolveTemplateVar("contentSigned", "${context.isMacCodeSignEnabled}")
      .resolveTemplateVar("buildDateInSeconds", "${context.options.buildDateInSeconds}")
  )
  NioFiles.setExecutable(entrypoint)
  return entrypoint
}

private fun String.resolveTemplateVar(variable: String, value: String): String {
  val reference = "%$variable%"
  check(contains(reference)) { "No $reference is found in:\n'$this'" }
  return replace(reference, value)
}

private fun publishDmgBuildScripts(entrypoint: Path, tempDir: Path, context: BuildContext) {
  val artifactDir = context.paths.artifactDir.resolve("macos-dmg-build")
  artifactDir.createDirectories()
  synchronized("$artifactDir".intern()) {
    tempDir.listDirectoryEntries().forEach {
      Files.copy(it, artifactDir.resolve(it.name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }
    val message = """
  To build .dmg(s):
  1. transfer .sit(s) to macOS host;
  2. transfer ${artifactDir.name}/ content to the same folder;
  3. execute ${entrypoint.name} from Terminal. 
  .dmg(s) will be built in the same folder.
""".trimIndent()
    artifactDir.resolve("README.txt").writeText(message)
    context.messages.info(message)
    context.notifyArtifactBuilt(artifactDir)
  }
}

private suspend fun generateIntegrityManifest(sitFile: Path, sitRoot: String, arch: JvmArchitecture, context: BuildContext) {
  if (context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
    return
  }

  val tempSit = Files.createTempDirectory(context.paths.tempDir, "sit-")
  try {
    spanBuilder("extracting ${sitFile.name}").use(Dispatchers.IO) {
      Decompressor.Zip(sitFile)
        .withZipExtensions()
        .extract(tempSit)
    }
    RepairUtilityBuilder.generateManifest(tempSit.resolve(sitRoot), OsFamily.MACOS, arch, context)
  }
  finally {
    withContext(Dispatchers.IO + NonCancellable) {
      @OptIn(ExperimentalPathApi::class)
      tempSit.deleteRecursively()
    }
  }
}
