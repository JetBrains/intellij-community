// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.FileSet
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.NativeBinaryDownloader
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.WindowsLibcImpl
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.createFrontendContextForLaunchers
import org.jetbrains.intellij.build.impl.languageServer.generateLspServerLaunchData
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.generateEmbeddedFrontendLaunchData
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.productInfo.resolveProductInfoJsonSibling
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.impl.productInfo.writeProductInfoJson
import org.jetbrains.intellij.build.impl.qodana.generateQodanaLaunchData
import org.jetbrains.intellij.build.impl.stdioMcpRunner.generateStdioMcpRunnerLaunchData
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.runJava
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.io.substituteTemplatePlaceholders
import org.jetbrains.intellij.build.io.transformFile
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.productLayout.util.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Arrays
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

private val CompareDistributionsSemaphore = Semaphore(Integer.getInteger("intellij.build.win.compare.concurrency", 1))

internal class WindowsDistributionBuilder(
  private val customizer: WindowsDistributionCustomizer,
  private val ideaProperties: CharSequence?,
  private val context: BuildContext,
) : OsSpecificDistributionBuilder {
  override val targetOs: OsFamily
    get() = OsFamily.WINDOWS

  override val targetLibcImpl: LibcImpl
    get() = WindowsLibcImpl.DEFAULT

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    withContext(Dispatchers.IO) {
      val distBinDir = targetPath.resolve("bin").createDirectories()
      writeVmOptions(distBinDir)

      context.executeStep(spanBuilder("copy product bin files"), BuildOptions.PRODUCT_BIN_DIR_STEP) {
        val sourceBinDir = context.paths.communityHomeDir.resolve("bin/win")

        copyDir(sourceBinDir.resolve(arch.dirName), distBinDir)
        copyDir(sourceBinDir, distBinDir, dirFilter = { it == sourceBinDir })

        copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.WINDOWS, arch), distBinDir)

        createFrontendContextForLaunchers(context)?.let { clientContext ->
          writeWindowsVmOptions(distBinDir, clientContext)
          buildWinLauncher(winDistPath = targetPath, arch = arch, copyLicense = false, customizer = customizer, context = clientContext)
        }

        if (customizer.includeBatchLaunchers) {
          generateScripts(distBinDir, arch, context)
        }
      }

      generateBuildTxt(targetDirectory = targetPath, context = context)
      copyDistFiles(newDir = targetPath, os = OsFamily.WINDOWS, arch = arch, libcImpl = WindowsLibcImpl.DEFAULT, context = context)

      Files.writeString(distBinDir.resolve(PROPERTIES_FILE_NAME), StringUtilRt.convertLineSeparators(ideaProperties!!, "\r\n"))

      if (context.options.isLanguageServer) {
        // no icon
      }
      else {
        val icoFile = locateIcoFileForWindowsLauncher(customizer, context)
        Files.copy(icoFile, distBinDir.resolve("${context.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
      }

      buildWinLauncher(winDistPath = targetPath, arch = arch, copyLicense = true, customizer = customizer, context = context)

      customizer.copyAdditionalFiles(targetPath, arch, context)
    }

    context.executeStep(spanBuilder("sign windows"), BuildOptions.WIN_SIGN_STEP) {
      val binFiles = withContext(Dispatchers.IO) {
        val distBinDir = targetPath.resolve("bin")
        Files.walk(distBinDir, Int.MAX_VALUE).use { stream ->
          stream.filter { it.extension in setOf("exe", "dll", "ps1") && Files.isRegularFile(it) }.toList()
        }
      }
      Span.current().setAttribute(AttributeKey.stringArrayKey("files"), binFiles.map(Path::toString))

      val additionalFiles = customizer.getBinariesToSign(context).map { targetPath.resolve(it) }

      if (binFiles.isNotEmpty() || additionalFiles.isNotEmpty()) {
        withContext(Dispatchers.IO) {
          context.signFiles(binFiles + additionalFiles, BuildOptions.WIN_SIGN_OPTIONS)
        }
      }
    }
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    val runtimeDir = context.bundledRuntime.extract(OsFamily.WINDOWS, arch, WindowsLibcImpl.DEFAULT)

    val (zipWithJbrPath, exePath) = context.executeStep(spanBuilder("build Windows artifacts"), stepId = BuildOptions.WINDOWS_ARTIFACTS_STEP) {
      var zipWithJbrPath: Path? = null
      var exePath: Path? = null

      setLastModifiedTime(osAndArchSpecificDistPath, context)

      if (customizer.buildZipArchiveWithBundledJre && !context.isStepSkipped(BuildOptions.WINDOWS_ZIP_STEP)) {
        val zipNameSuffix = suffix(arch) + customizer.zipArchiveWithBundledJreSuffix
        launch(Dispatchers.IO + CoroutineName("build Windows ${zipNameSuffix}.zip distribution")) {
          zipWithJbrPath = createBuildWinZipTask(
            runtimeDir = runtimeDir,
            zipNameSuffix = zipNameSuffix,
            winDistPath = osAndArchSpecificDistPath,
            arch = arch,
            customizer = customizer,
            context = context,
          )
        }
      }

      if (customizer.buildZipArchiveWithoutBundledJre && !context.isStepSkipped(BuildOptions.WINDOWS_ZIP_STEP)) {
        val zipNameSuffix = suffix(arch) + customizer.zipArchiveWithoutBundledJreSuffix
        launch(Dispatchers.IO + CoroutineName("build Windows ${zipNameSuffix}.zip distribution")) {
          createBuildWinZipTask(runtimeDir = null, zipNameSuffix = zipNameSuffix, winDistPath = osAndArchSpecificDistPath, arch = arch, customizer = customizer, context = context)
        }
      }

      context.executeStep(spanBuilder("build Windows installer").setAttribute("arch", arch.dirName), BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
        val productJsonDir = Files.createTempDirectory(context.paths.tempDir, "win-product-info")
        val productJsonFile = writeProductJsonFile(productJsonDir, arch, withRuntime = true, context)
        val installationDirectories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDir)
        validateProductJson(jsonText = productJsonFile.readText(), installationDirectories, installationArchives = emptyList(), context)
        launch(Dispatchers.IO + CoroutineName("build Windows ${arch.dirName} installer")) {
          exePath = buildNsisInstaller(
            winDistPath = osAndArchSpecificDistPath,
            productInfoJsonFile = productJsonFile,
            additionalDirectoryToInclude = productJsonDir,
            suffix = suffix(arch),
            customizer = customizer,
            runtimeDir = runtimeDir,
            context = context,
            arch = arch,
          )
        }
      }

      zipWithJbrPath to exePath
    } ?: (null to null)

    if (zipWithJbrPath != null && exePath != null) {
      if (context.options.isInDevelopmentMode) {
        Span.current().addEvent("comparing .zip and .exe skipped in development mode")
      }
      else if (!SystemInfoRt.isLinux) {
        Span.current().addEvent("comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      }
      else {
        checkThatExeInstallerAndZipWithJbrAreTheSame(zipPath = zipWithJbrPath, exePath = exePath, arch = arch, tempDir = context.paths.tempDir, context = context)
      }
    }
  }

  override suspend fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture): Path {
    return writeProductJsonFile(targetDir = targetDir, arch = arch, withRuntime = true, context = context)
  }

  override fun writeVmOptions(distBinDir: Path): Path = writeWindowsVmOptions(distBinDir, context)

  override fun distributionFilesBuilt(arch: JvmArchitecture): List<Path> {
    val archSuffix = suffix(arch)
    return sequenceOf(
      "${archSuffix}.exe",
      "${archSuffix}${customizer.zipArchiveWithBundledJreSuffix}.zip",
      "${archSuffix}${customizer.zipArchiveWithoutBundledJreSuffix}.zip"
    )
      .map { suffix -> context.productProperties.getBaseArtifactName(context) + suffix }
      .map(context.paths.artifactDir::resolve)
      .filter { it.exists() }
      .toList()
  }

  override fun isRuntimeBundled(file: Path): Boolean = !file.name.contains(customizer.zipArchiveWithoutBundledJreSuffix)
}

private fun generateScripts(distBinDir: Path, arch: JvmArchitecture, context: BuildContext) {
  val fullName = context.applicationInfo.fullProductName
  val baseName = context.productProperties.baseFileName
  val scriptName = "${baseName}.bat"
  val vmOptionsFileName = "${baseName}64.exe"

  val classPathJars = context.bootClassPathJarNames
  var classPath = ""
  for (jar in classPathJars) {
    classPath += "\nECHO|SET /P=\"\"%IDE_HOME:\\=/%/lib/${jar}\";\" >> \"%ARG_FILE%\""
  }

  val additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch, isScript = true)
  val winScripts = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")
  val actualScriptNames = Files.newDirectoryStream(winScripts).use { dirStream -> dirStream.map { it.fileName.toString() }.sorted() }

  val expectedScriptNames = listOf("executable-template.bat", "format.bat", "inspect.bat", "ltedit.bat")
  check(actualScriptNames == expectedScriptNames) {
    "Expected script names '${expectedScriptNames.joinToString(separator = " ")}', " +
    "but got '${actualScriptNames.joinToString(separator = " ")}' " +
    "in ${winScripts}. Please review ${WindowsDistributionBuilder::class.java.name} and update accordingly"
  }

  substituteTemplatePlaceholders(
    inputFile = winScripts.resolve("executable-template.bat"),
    outputFile = distBinDir.resolve(scriptName),
    placeholder = "@@",
    values = listOf(
      Pair("product_full", fullName),
      Pair("product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)),
      Pair("product_vendor", context.applicationInfo.shortCompanyName),
      Pair("vm_options", vmOptionsFileName),
      Pair("system_selector", context.systemSelector),
      Pair("ide_jvm_args", additionalJvmArguments.joinToString(separator = " ")),
      Pair("class_path", classPath),
      Pair("base_name", baseName),
      Pair("main_class_name", context.ideMainClassName),
    )
  )

  val inspectScript = context.productProperties.inspectCommandName
  for (fileName in listOf("format.bat", "inspect.bat", "ltedit.bat")) {
    substituteTemplatePlaceholders(
      inputFile = winScripts.resolve(fileName),
      outputFile = distBinDir.resolve(fileName),
      placeholder = "@@",
      values = listOf(
        Pair("product_full", fullName),
        Pair("script_name", scriptName),
      )
    )
  }

  if (inspectScript != "inspect") {
    val targetPath = distBinDir.resolve("${inspectScript}.bat")
    Files.move(distBinDir.resolve("inspect.bat"), targetPath)
    context.patchInspectScript(targetPath)
  }

  FileSet(distBinDir)
    .include("*.bat")
    .enumerate()
    .forEach { file ->
      transformFile(file) { target ->
        Files.writeString(target, toDosLineEndings(Files.readString(file)))
      }
    }
}

private suspend fun createBuildWinZipTask(
  runtimeDir: Path?,
  zipNameSuffix: String,
  winDistPath: Path,
  arch: JvmArchitecture,
  customizer: WindowsDistributionCustomizer,
  context: BuildContext,
): Path {
  val baseName = context.productProperties.getBaseArtifactName(context)
  val targetFile = context.paths.artifactDir.resolve("${baseName}${zipNameSuffix}.zip")
  val targetFileProductInfoJson = targetFile.resolveProductInfoJsonSibling()

  spanBuilder("build Windows ${zipNameSuffix}.zip distribution")
    .setAttribute("targetFile", targetFile.toString())
    .setAttribute("arch", arch.dirName)
    .use {
      val dirs = mutableListOf(context.paths.distAllDir, winDistPath)

      if (runtimeDir != null) {
        dirs.add(runtimeDir)
      }

      val productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.zip${zipNameSuffix}")
      val productJsonFile = writeProductJsonFile(productJsonDir, arch, withRuntime = runtimeDir != null, context)
      dirs.add(productJsonDir)
      copyFile(productJsonFile, targetFileProductInfoJson)

      val zipPrefix = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)

      val dirMap = dirs.associateWithTo(LinkedHashMap(dirs.size)) { zipPrefix }
      if (context.options.compressZipFiles) {
        zipWithCompression(targetFile, dirMap)
      }
      else {
        zip(targetFile, dirMap, AddDirEntriesMode.NONE)
      }
      validateProductJson(targetFile, zipPrefix, context)
      context.notifyArtifactBuilt(targetFile)
      context.notifyArtifactBuilt(targetFileProductInfoJson)
      targetFile
    }

  return targetFile
}

private suspend fun buildWinLauncher(winDistPath: Path, arch: JvmArchitecture, copyLicense: Boolean, customizer: WindowsDistributionCustomizer, context: BuildContext) {
  spanBuilder("build Windows executable").use {
    val communityHome = context.paths.communityHomeDir
    val appInfo = context.applicationInfo
    val executableBaseName = "${context.productProperties.baseFileName}64"
    val launcherPropertiesPath = context.paths.tempDir.resolve("launcher-${arch.dirName}.properties")
    val icoFile =
      if (context.options.isLanguageServer) null
      else locateIcoFileForWindowsLauncher(customizer, context)

    val productVersion = context.buildNumber
      .replace(".SNAPSHOT", ".0")
      .replace("-SNAPSHOT", ".0")
      .let { version ->
        version + ".0".repeat(3 - version.count { it == '.' })
      }

    val launcherProperties = listOf(
      "CompanyName" to appInfo.companyName,
      "LegalCopyright" to "Copyright 2000-${LocalDate.now().year} ${appInfo.companyName}",
      "FileDescription" to appInfo.productNameWithEdition,
      "ProductName" to appInfo.productNameWithEdition,
      "ProductVersion" to "$productVersion-${appInfo.productCode}", // "242.1234.56.0-IU"
    )
    Files.writeString(launcherPropertiesPath, launcherProperties.joinToString(separator = System.lineSeparator()) { (k, v) -> "${k}=${v}" })

    val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.WINDOWS, arch)
    val outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")

    if (copyLicense) {
      copyFile(licensePath, winDistPath.resolve("license/launcher-third-party-libraries.html"))
    }

    val generatorModule = context.findRequiredModule("intellij.tools.launcherGenerator")
    runJava(
      mainClass = "com.pme.launcher.LauncherGeneratorMain",
      args = listOf(
        execPath.absolutePathString(),
        "${communityHome}/native/XPlatLauncher/resources/windows/resource.h",
        launcherPropertiesPath.absolutePathString(),
        icoFile?.absolutePathString() ?: "no-icon",
        outputPath.absolutePathString(),
      ),
      jvmArgs = listOf("-Djava.awt.headless=true"),
      classPath = context.getModuleRuntimeClasspath(module = generatorModule, forTests = false).map { it.toString() },
      javaExe = context.stableJavaExecutable,
    )
  }
}

private suspend fun checkThatExeInstallerAndZipWithJbrAreTheSame(
  zipPath: Path,
  exePath: Path,
  arch: JvmArchitecture,
  tempDir: Path,
  context: BuildContext,
) = CompareDistributionsSemaphore.withPermit {
  fun compareStreams(stream1: InputStream, stream2: InputStream): Boolean {
    val b1 = ByteArray(DEFAULT_BUFFER_SIZE)
    val b2 = ByteArray(DEFAULT_BUFFER_SIZE)
    stream1.use { s1 ->
      stream2.use { s2 ->
        while (true) {
          val l1 = s1.readNBytes(b1, 0, b1.size)
          val l2 = s2.readNBytes(b2, 0, b2.size)
          if (l1 != l2) return false
          if (l1 <= 0) return true
          if (!Arrays.equals(b1, 0, l1, b2, 0, l2)) return false
        }
      }
    }
  }

  val tempExe = withContext(Dispatchers.IO) { Files.createTempDirectory(tempDir, "exe-${arch.dirName}") }
  try {
    withContext(Dispatchers.IO) {
      spanBuilder("compare zip and exe contents")
        .setAttribute("zipPath", zipPath.toString())
        .setAttribute("exePath", exePath.toString())
        .use {
          runProcess(args = listOf("7z", "x", "-bd", exePath.toString()), workingDir = tempExe)
          // deleting NSIS-related files that appear after manual unpacking of .exe installer and do not belong to its contents
          @Suppress("SpellCheckingInspection")
          NioFiles.deleteRecursively(tempExe.resolve($$"$PLUGINSDIR"))
          Files.deleteIfExists(tempExe.resolve("bin/Uninstall.exe.nsis"))
          Files.deleteIfExists(tempExe.resolve("bin/Uninstall.exe"))

          val differences = ZipFile.Builder().setSeekableByteChannel(Files.newByteChannel(zipPath)).get().use { zipFile ->
            zipFile.entries.asSequence()
              .filter { !it.isDirectory }
              .toList()
              .mapConcurrent { entry ->
                val entryPath = Path.of(entry.name)
                val fileInExe = tempExe.resolve(entryPath)
                if (Files.notExists(fileInExe)) {
                  entryPath.toString() to true
                }
                else {
                  val isDifferent = if (fileInExe.fileSize() != entry.size) {
                    true
                  }
                  else if (entry.size < 2 * FileUtilRt.MEGABYTE) {
                    !fileInExe.readBytes().contentEquals(zipFile.getInputStream(entry).readAllBytes())
                  }
                  else {
                    !compareStreams(fileInExe.inputStream().buffered(FileUtilRt.MEGABYTE), zipFile.getInputStream(entry).buffered(FileUtilRt.MEGABYTE))
                  }
                  NioFiles.deleteRecursively(fileInExe)
                  if (isDifferent) entryPath.toString() to false else null
                }
              }
              .filterNotNull()
          }

          val extraInZip = ArrayList<String>()
          val differ = ArrayList<String>()
          for ((entryPath, isOnlyInZip) in differences) {
            if (isOnlyInZip) {
              extraInZip.add(entryPath)
            }
            else {
              differ.add(entryPath)
            }
          }

          val extraInExe = Files.walk(tempExe)
            .filter { Files.isRegularFile(it) }
            .map { tempExe.relativize(it).toString() }
            .toList()

          if (extraInExe.isNotEmpty() || extraInZip.isNotEmpty() || differ.isNotEmpty()) {
            error(buildString {
              if (extraInZip.isNotEmpty()) {
                append("Files present only in ZIP:\n")
                extraInZip.forEach { append("  ").append(it).append('\n') }
              }
              if (extraInExe.isNotEmpty()) {
                append("Files present only in EXE:\n")
                extraInExe.forEach { append("  ").append(it).append('\n') }
              }
              if (differ.isNotEmpty()) {
                append("Files with different content:\n")
                differ.forEach { append("  ").append(it).append('\n') }
              }
            })
          }
        }
    }
    if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
      RepairUtilityBuilder.generateManifest(unpackedDistribution = tempExe, os = OsFamily.WINDOWS, arch = arch, context = context)
    }
  }
  finally {
    withContext(Dispatchers.IO + NonCancellable) {
      NioFiles.deleteRecursively(tempExe)
    }
  }
}

private fun writeWindowsVmOptions(distBinDir: Path, context: BuildContext): Path {
  val vmOptionsFile = distBinDir.resolve("${context.productProperties.baseFileName}64.exe.vmoptions")
  val vmOptions = generateVmOptions(context).asSequence()
  writeVmOptions(file = vmOptionsFile, vmOptions = vmOptions, separator = "\r\n")
  return vmOptionsFile
}

private suspend fun writeProductJsonFile(targetDir: Path, arch: JvmArchitecture, withRuntime: Boolean, context: BuildContext): Path {
  val json = generateProductInfoJson(
    relativePathToBin = "bin",
    builtinModules = context.builtinModule,
    launch = listOf(
      ProductInfoLaunchData.create(
        os = OsFamily.WINDOWS.osName,
        arch = arch.dirName,
        launcherPath = "bin/${context.productProperties.baseFileName}64.exe",
        javaExecutablePath = if (withRuntime) "jbr/bin/java.exe" else null,
        vmOptionsFilePath = "bin/${context.productProperties.baseFileName}64.exe.vmoptions",
        bootClassPathJarNames = context.bootClassPathJarNames,
        additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch),
        mainClass = context.ideMainClassName,
        customCommands = when {
          context.options.isLanguageServer -> listOf(
            generateLspServerLaunchData(context)
          )
          else -> listOfNotNull(
            generateEmbeddedFrontendLaunchData(arch, OsFamily.WINDOWS, context) {
              "bin/${it.productProperties.baseFileName}64.exe.vmoptions"
            },
            generateQodanaLaunchData(context, arch, OsFamily.WINDOWS),
            generateStdioMcpRunnerLaunchData(context)
          )
        },
      )
    ),
    context
  )
  val file = targetDir.resolve(PRODUCT_INFO_FILE_NAME)
  writeProductInfoJson(file, json, context)
  return file
}

private fun toDosLineEndings(x: String): String = x.replace("\r", "").replace("\n", "\r\n")
