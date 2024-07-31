// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.SystemProperties
import com.intellij.util.io.Decompressor
import org.jetbrains.intellij.build.impl.qodana.generateQodanaLaunchData
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.NativeBinaryDownloader
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.zip.Deflater
import kotlin.io.path.*

private const val NO_RUNTIME_SUFFIX = "-no-jdk"

class MacDistributionBuilder(
  override val context: BuildContext,
  private val customizer: MacDistributionCustomizer,
  private val ideaProperties: CharSequence?
) : OsSpecificDistributionBuilder {
  override val targetOs: OsFamily
    get() = OsFamily.MACOS

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
      doCopyExtraFiles(macDistDir = targetPath, arch, copyDistFiles = true)
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
    for ((k, v) in customizer.getCustomIdeaProperties(context.applicationInfo)) {
      platformProperties.add("$k=$v")
    }

    layoutMacApp(ideaProperties!!, platformProperties, getDocTypes(), macDistDir, arch)

    generateBuildTxt(context, macDistDir.resolve("Resources"))

    // if copyDistFiles false, it means that we will copy dist files directly without a stage dir
    if (copyDistFiles) {
      copyDistFiles(context, macDistDir, OsFamily.MACOS, arch)
    }

    customizer.copyAdditionalFiles(context, macDistDir, arch)
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      doCopyExtraFiles(osAndArchSpecificDistPath, arch, copyDistFiles = false)
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
        signMacBinaries(osAndArchSpecificDistPath, runtimeDist, arch)
      }

      val baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
      val publishZipOnly = !publishSitArchive && context.isStepSkipped(BuildOptions.MAC_DMG_STEP)
      val macZip = (if (publishZipOnly) context.paths.artifactDir else context.paths.tempDir).resolve("$baseName.mac.${arch.name}.zip")
      val macZipWithoutRuntime = macZip.resolveSibling(macZip.nameWithoutExtension + NO_RUNTIME_SUFFIX + ".zip")
      val zipRoot = getMacZipRoot(customizer, context)
      val compressionLevel = if (publishSitArchive || publishZipOnly) Deflater.DEFAULT_COMPRESSION else Deflater.BEST_SPEED
      val extraFiles = context.getDistFiles(os = OsFamily.MACOS, arch)
      val directories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDist)
      val builder = this@MacDistributionBuilder
      val productJson = generateProductJson(context, arch, withRuntime = true)
      buildMacZip(builder, macZip, zipRoot, arch, productJson, directories, extraFiles, includeRuntime = true, compressionLevel)

      if (customizer.buildArtifactWithoutRuntime) {
        val productJson = generateProductJson(context, arch, withRuntime = false)
        val directories = directories.filterNot { it == runtimeDist }
        buildMacZip(builder, macZipWithoutRuntime, zipRoot, arch, productJson, directories, extraFiles, includeRuntime = false, compressionLevel)
      }

      if (publishZipOnly) {
        Span.current().addEvent("skip .dmg and .sit artifacts producing")
        context.notifyArtifactBuilt(macZip)
        if (customizer.buildArtifactWithoutRuntime) {
          context.notifyArtifactBuilt(macZipWithoutRuntime)
        }
      }
      else {
        buildForArch(arch, macZip, macZipWithoutRuntime)
      }
    }
  }

  override fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture) {
    val json = generateProductJson(context, arch, withRuntime = true)
    writeProductInfoJson(targetDir.resolve("Resources/${PRODUCT_INFO_FILE_NAME}"), json, context)
  }

  private suspend fun signMacBinaries(osAndArchSpecificDistPath: Path, runtimeDist: Path, arch: JvmArchitecture) {
    val binariesToSign = customizer.getBinariesToSign(context, arch).map(osAndArchSpecificDistPath::resolve)
    val matchers = generateExecutableFilesMatchers(includeRuntime = false, arch).keys
    withContext(Dispatchers.IO) {
      signMacBinaries(files = binariesToSign, context)
      for (dir in listOf(osAndArchSpecificDistPath, runtimeDist)) {
        launch {
          recursivelySignMacBinaries(root = dir, context, executableFileMatchers = matchers)
        }
      }
    }
  }

  override fun writeVmOptions(distBinDir: Path): Path =
    writeMacOsVmOptions(distBinDir, context)

  private suspend fun layoutMacApp(
    ideaPropertyContent: CharSequence,
    platformProperties: List<String>,
    docTypes: String?,
    macDistDir: Path,
    arch: JvmArchitecture
  ) {
    val macBinDir = macDistDir.resolve("bin")
    copyDirWithFileFilter(context.paths.communityHomeDir.resolve("bin/mac"), macBinDir, customizer.binFilesFilter)
    copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.MACOS, arch), macBinDir)
    copyDir(context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

    val executable = context.productProperties.baseFileName
    val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.MACOS, arch)
    copyFile(execPath, macDistDir.resolve("MacOS/${executable}"))
    copyFile(licensePath, macDistDir.resolve("license/launcher-third-party-libraries.html"))

    val icnsPath = Path.of((if (context.applicationInfo.isEAP) customizer.icnsPathForEAP else null) ?: customizer.icnsPath)
    val resourcesDistDir = macDistDir.resolve("Resources")
    copyFile(icnsPath, resourcesDistDir.resolve(targetIcnsFileName))

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

    Files.writeString(
      macBinDir.resolve(PROPERTIES_FILE_NAME),
      (ideaPropertyContent.lineSequence() + platformProperties).joinToString(separator = "\n")
    )

    writeVmOptions(macBinDir)
    createJetBrainsClientContextForLaunchers(context)?.let { clientContext ->
      writeMacOsVmOptions(macBinDir, clientContext)
    }

    substitutePlaceholdersInInfoPlist(macDistDir, docTypes, arch)

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

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> =
    customizer.generateExecutableFilesPatterns(context, includeRuntime, arch)

  private suspend fun buildForArch(arch: JvmArchitecture, macZip: Path, macZipWithoutRuntime: Path?) {
    spanBuilder("build macOS artifacts for specific arch").setAttribute("arch", arch.name).useWithScope {
      val notarize = SystemProperties.getBooleanProperty(
        "intellij.build.mac.notarize",
        !context.isStepSkipped(BuildOptions.MAC_NOTARIZE_STEP)
      )
      withContext(Dispatchers.IO) {
        buildForArch(arch, macZip, macZipWithoutRuntime, notarize)
        Files.deleteIfExists(macZip)
      }
    }
  }

  private suspend fun buildForArch(arch: JvmArchitecture, macZip: Path, macZipWithoutRuntime: Path?, notarize: Boolean) {
    val archStr = arch.name
    coroutineScope {
      val taskId = "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_${archStr}"
      createSkippableJob(spanBuilder("build DMG with Runtime").setAttribute("arch", archStr), taskId, context) {
        signAndBuildDmg(macZip, isRuntimeBundled = true, suffix(arch), arch, notarize)
      }

      if (customizer.buildArtifactWithoutRuntime) {
        requireNotNull(macZipWithoutRuntime)
        val taskId = "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_${archStr}"
        createSkippableJob(spanBuilder("build DMG without Runtime").setAttribute("arch", archStr), taskId, context) {
          val suffix = "${NO_RUNTIME_SUFFIX}${suffix(arch)}"
          signAndBuildDmg(macZipWithoutRuntime, isRuntimeBundled = false, suffix, arch, notarize)
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
      .filter { it.exists() }
      .toList()
  }

  override fun isRuntimeBundled(file: Path): Boolean = !file.name.contains(NO_RUNTIME_SUFFIX)

  private fun generateProductJson(context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean): String =
    generateProductInfoJson("../bin", context.builtinModule, launch = listOf(createProductInfoLaunchData(context, arch, withRuntime)), context)

  private fun createProductInfoLaunchData(context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean): ProductInfoLaunchData {
    val jetbrainsClientCustomLaunchData = generateJetBrainsClientLaunchData(context, arch, OsFamily.MACOS) {
      "../bin/${it.productProperties.baseFileName}.vmoptions"
    }
    val qodanaCustomLaunchData = generateQodanaLaunchData(context, arch, OsFamily.MACOS)
    return ProductInfoLaunchData(
      os = OsFamily.MACOS.osName,
      arch.dirName,
      launcherPath = "../MacOS/${context.productProperties.baseFileName}",
      javaExecutablePath = if (withRuntime) "../jbr/Contents/Home/bin/java" else null,
      vmOptionsFilePath = "../bin/${context.productProperties.baseFileName}.vmoptions",
      startupWmClass = null,
      bootClassPathJarNames = context.bootClassPathJarNames,
      additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch),
      mainClass = context.ideMainClassName,
      customCommands = listOfNotNull(jetbrainsClientCustomLaunchData, qodanaCustomLaunchData)
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
    compressionLevel: Int
  ) {
    val executableFileMatchers = macDistributionBuilder.generateExecutableFilesMatchers(includeRuntime, arch)
    withContext(Dispatchers.IO) {
      spanBuilder("build zip archive for macOS")
        .setAttribute("file", targetFile.toString())
        .setAttribute("zipRoot", zipRoot)
        .setAttribute(AttributeKey.stringArrayKey("directories"), directories.map { it.toString() })
        .setAttribute(AttributeKey.stringArrayKey("executableFilePatterns"), executableFileMatchers.values.toList())
        .useWithScope {
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
                // file is used only for transfer to mac builder
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
                  zipOutStream.dir(startDir = dir, prefix = "${zipRoot}/", fileFilter = fileFilter, entryCustomizer = entryCustomizer)
                }
                catch (e: Exception) {
                  // provide more context to error - dir
                  throw RuntimeException("Cannot pack $dir to $targetFile (already packed: ${directories.subList(0, index)})", e)
                }
              }

              for (item in extraFiles) {
                when(val content = item.content) {
                  is LocalDistFileContent -> zipOutStream.entry("${zipRoot}/${item.relativePath}", content.file, if (content.isExecutable) executableFileUnixMode else -1)
                  is InMemoryDistFileContent -> zipOutStream.entry("${zipRoot}/${item.relativePath}", content.data)
                }
              }
            }
          }

          checkInArchive(targetFile, pathInArchive = "${zipRoot}/Resources", macDistributionBuilder.context)
        }
    }
  }

  private fun writeMacOsVmOptions(distBinDir: Path, context: BuildContext): Path {
    val executable = context.productProperties.baseFileName
    val fileVmOptions = VmOptionsGenerator.computeVmOptions(context) + listOf("-Dapple.awt.application.appearance=system")
    val vmOptionsPath = distBinDir.resolve("$executable.vmoptions")
    writeVmOptions(vmOptionsPath, fileVmOptions, separator = "\n")
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
    val urlSchemesString = if (urlSchemes.isEmpty()) "" else """
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

    @OptIn(ExperimentalPathApi::class)
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

  private fun getMacZipRoot(customizer: MacDistributionCustomizer, context: BuildContext): String =
    "${customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)}/Contents"

  private val publishSitArchive: Boolean
    get() = !context.isStepSkipped(BuildOptions.MAC_SIT_PUBLICATION_STEP)

  private val signMacOsBinaries: Boolean
    get() = !context.isStepSkipped(BuildOptions.MAC_SIGN_STEP)

  private suspend fun signAndBuildDmg(macZip: Path, isRuntimeBundled: Boolean, suffix: String, arch: JvmArchitecture, notarize: Boolean) {
    require(Files.isRegularFile(macZip))

    val baseName = context.productProperties.getBaseArtifactName(context) + suffix
    val sitFile = (if (publishSitArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$baseName.sit")
    Files.move(macZip, sitFile, StandardCopyOption.REPLACE_EXISTING)

    if (context.isMacCodeSignEnabled) {
      context.proprietaryBuildTools.signTool.signFiles(listOf(sitFile), context, signingOptions("application/x-mac-app-zip", context))
    }

    if (notarize) {
      notarize(sitFile, context)
    }

    buildDmg(sitFile, "${baseName}.dmg", staple = notarize)

    check(Files.exists(sitFile)) { "$sitFile wasn't created" }

    if (publishSitArchive) {
      context.notifyArtifactBuilt(sitFile)
    }

    val zipRoot = getMacZipRoot(customizer, context)
    checkExecutablePermissions(sitFile, zipRoot, isRuntimeBundled, arch)

    if (isRuntimeBundled) {
      generateIntegrityManifest(sitFile, zipRoot, arch)
    }
  }

  private suspend fun buildDmg(sitFile: Path, dmgName: String, staple: Boolean) {
    val tempDir = context.paths.tempDir.resolve(sitFile.name.replace(".sit", ""))
    try {
      context.executeStep(spanBuilder("build .dmg locally"), BuildOptions.MAC_DMG_STEP) {
        NioFiles.deleteRecursively(tempDir)
        NioFiles.createDirectories(tempDir)
        NioFiles.createDirectories(context.paths.artifactDir)
        val entrypoint = prepareDmgBuildScripts(tempDir, staple)
        if (!SystemInfoRt.isMac) {
          it.addEvent(".dmg can be built only on macOS")
          publishDmgBuildScripts(entrypoint, tempDir)
          return@executeStep
        }
        val tmpSit = Files.move(sitFile, tempDir.resolve(sitFile.fileName))
        runProcess(args = listOf("./${entrypoint.name}"), workingDir = tempDir, inheritOut = true)
        val dmgFile = tempDir.resolve(dmgName)
        check(dmgFile.exists()) { "$dmgFile wasn't created" }
        Files.move(dmgFile, context.paths.artifactDir.resolve(dmgFile.name), StandardCopyOption.REPLACE_EXISTING)
        context.notifyArtifactBuilt(dmgFile)
        Files.move(tmpSit, sitFile)
      }
    }
    finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }

  private fun prepareDmgBuildScripts(tempDir: Path, staple: Boolean): Path {
    NioFiles.deleteRecursively(tempDir)
    tempDir.createDirectories()
    val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    Files.copy(Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath), dmgImageCopy)
    val scriptsDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    Files.copy(scriptsDir.resolve("makedmg.sh"), tempDir.resolve("makedmg.sh"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    NioFiles.setExecutable(tempDir.resolve("makedmg.sh"))
    Files.copy(scriptsDir.resolve("makedmg.py"), tempDir.resolve("makedmg.py"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    Files.copy(scriptsDir.resolve("staple.sh"), tempDir.resolve("staple.sh"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    val entrypoint = tempDir.resolve("build.sh")
    entrypoint.writeText(
      scriptsDir.resolve("build-template.sh").readText()
        .resolveTemplateVar("staple", "$staple")
        .resolveTemplateVar("appName", context.fullBuildNumber)
        .resolveTemplateVar("contentSigned", "${signMacOsBinaries}")
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

  private fun publishDmgBuildScripts(entrypoint: Path, tempDir: Path) {
    if (publishSitArchive) {
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
  }

  private suspend fun generateIntegrityManifest(sitFile: Path, sitRoot: String, arch: JvmArchitecture) {
    if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
      val tempSit = Files.createTempDirectory(context.paths.tempDir, "sit-")
      try {
        spanBuilder("extracting ${sitFile.name}").useWithScope(Dispatchers.IO) {
          Decompressor.Zip(sitFile)
            .withZipExtensions()
            .extract(tempSit)
        }
        RepairUtilityBuilder.generateManifest(context, tempSit.resolve(sitRoot), OsFamily.MACOS, arch)
      }
      finally {
        withContext(Dispatchers.IO + NonCancellable) {
          NioFiles.deleteRecursively(tempSit)
        }
      }
    }
  }
}
