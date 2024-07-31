// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.ijent.community.buildConstants.ENABLE_IJENT_WSL_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isIjentWslFsEnabledByDefaultForProduct
import com.jetbrains.plugin.structure.base.utils.exists
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.qodana.generateQodanaLaunchData
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name

internal class WindowsDistributionBuilder(
  override val context: BuildContext,
  private val customizer: WindowsDistributionCustomizer,
  private val ideaProperties: CharSequence?,
) : OsSpecificDistributionBuilder {
  override val targetOs: OsFamily
    get() = OsFamily.WINDOWS

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    val distBinDir = targetPath.resolve("bin")
    withContext(Dispatchers.IO) {
      val sourceBinDir = context.paths.communityHomeDir.resolve("bin/win")

      copyDir(sourceBinDir.resolve(arch.dirName), distBinDir)

      copyDir(sourceBinDir, distBinDir, dirFilter = { it == sourceBinDir }, fileFilter = { file ->
        @Suppress("SpellCheckingInspection")
        context.includeBreakGenLibraries() || !file.name.startsWith("breakgen")
      })

      copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.WINDOWS, arch), distBinDir)

      generateBuildTxt(context, targetPath)
      copyDistFiles(context, newDir = targetPath, OsFamily.WINDOWS, arch)

      Files.writeString(distBinDir.resolve(PROPERTIES_FILE_NAME), StringUtilRt.convertLineSeparators(ideaProperties!!, "\r\n"))

      Files.copy(computeIcoPath(context), distBinDir.resolve("${context.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)

      if (customizer.includeBatchLaunchers) {
        generateScripts(distBinDir, arch)
      }

      writeVmOptions(distBinDir)

      buildWinLauncher(targetPath, arch, additionalNonCustomizableJvmArgs = emptyList(), context)

      createJetBrainsClientContextForLaunchers(context)?.let { clientContext ->
        writeWindowsVmOptions(distBinDir, clientContext)
        buildWinLauncher(targetPath, arch, additionalNonCustomizableJvmArgs = ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS, clientContext, copyLicense = false)
      }

      customizer.copyAdditionalFiles(context, targetPath, arch)
    }

    context.executeStep(spanBuilder("sign windows"), BuildOptions.WIN_SIGN_STEP) {
      val binFiles = withContext(Dispatchers.IO) {
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
    val runtimeDir = context.bundledRuntime.extract(os = OsFamily.WINDOWS, arch = arch)

    @Suppress("SpellCheckingInspection")
    val vcRtDll = runtimeDir.resolve("jbr/bin/msvcp140.dll")
    check(Files.exists(vcRtDll)) {
      "VS C++ Runtime DLL (${vcRtDll.fileName}) not found in ${vcRtDll.parent}.\n" +
      "If JBR uses a newer version, please correct the path in this code and update Windows Launcher build configuration.\n" +
      "If DLL was relocated to another place, please correct the path in this code."
    }
    copyFileToDir(vcRtDll, osAndArchSpecificDistPath.resolve("bin"))

    val (zipWithJbrPath, exePath) = coroutineScope {
      var zipWithJbrPath: Path? = null
      var exePath: Path? = null

      setLastModifiedTime(osAndArchSpecificDistPath, context)

      if (customizer.buildZipArchiveWithBundledJre) {
        val zipNameSuffix = suffix(arch) + customizer.zipArchiveWithBundledJreSuffix
        launch(Dispatchers.IO) {
          zipWithJbrPath = createBuildWinZipTask(runtimeDir, zipNameSuffix, osAndArchSpecificDistPath, arch, customizer, context)
        }
      }

      if (customizer.buildZipArchiveWithoutBundledJre) {
        val zipNameSuffix = suffix(arch) + customizer.zipArchiveWithoutBundledJreSuffix
        launch(Dispatchers.IO) {
          createBuildWinZipTask(runtimeDir = null, zipNameSuffix, osAndArchSpecificDistPath, arch, customizer, context)
        }
      }

      context.executeStep(spanBuilder("build Windows installer").setAttribute("arch", arch.dirName), BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
        val productJsonDir = Files.createTempDirectory(context.paths.tempDir, "win-product-info")
        val jsonText = generateProductJson(productJsonDir, context, arch)
        val installationDirectories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDir)
        validateProductJson(jsonText, relativePathToProductJson = "", installationDirectories, installationArchives = emptyList(), context)
        launch(Dispatchers.IO) {
          exePath = buildNsisInstaller(osAndArchSpecificDistPath, additionalDirectoryToInclude = productJsonDir, suffix(arch), customizer, runtimeDir, context)
        }
      }

      zipWithJbrPath to exePath
    }

    if (zipWithJbrPath != null && exePath != null) {
      if (context.options.isInDevelopmentMode) {
        Span.current().addEvent("comparing .zip and .exe skipped in development mode")
      }
      else if (!SystemInfoRt.isLinux) {
        Span.current().addEvent("comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      }
      else {
        checkThatExeInstallerAndZipWithJbrAreTheSame(zipWithJbrPath, exePath, arch, context.paths.tempDir)
      }
    }
  }

  override fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture) {
    generateProductJson(targetDir, context, arch)
  }

  private fun generateScripts(distBinDir: Path, arch: JvmArchitecture) {
    val fullName = context.applicationInfo.fullProductName
    val baseName = context.productProperties.baseFileName
    val scriptName = "${baseName}.bat"
    val vmOptionsFileName = "${baseName}64.exe"

    val classPathJars = context.bootClassPathJarNames
    var classPath = "SET \"CLASS_PATH=%IDE_HOME%\\lib\\${classPathJars[0]}\""
    for (i in 1 until classPathJars.size) {
      classPath += "\nSET \"CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\${classPathJars[i]}\""
    }

    var additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch, isScript = true)
    if (!context.xBootClassPathJarNames.isEmpty()) {
      additionalJvmArguments = additionalJvmArguments.toMutableList()
      val bootCp = context.xBootClassPathJarNames.joinToString(separator = ";") { "%IDE_HOME%\\lib\\${it}" }
      additionalJvmArguments.add("\"-Xbootclasspath/a:$bootCp\"")
    }

    val winScripts = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")
    val actualScriptNames = Files.newDirectoryStream(winScripts).use { dirStream -> dirStream.map { it.fileName.toString() }.sorted() }

    @Suppress("SpellCheckingInspection")
    val expectedScriptNames = listOf("executable-template.bat", "format.bat", "inspect.bat", "ltedit.bat")
    check(actualScriptNames == expectedScriptNames) {
      "Expected script names '${expectedScriptNames.joinToString(separator = " ")}', " +
      "but got '${actualScriptNames.joinToString(separator = " ")}' " +
      "in $winScripts. Please review ${WindowsDistributionBuilder::class.java.name} and update accordingly"
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
    @Suppress("SpellCheckingInspection")
    for (fileName in listOf("format.bat", "inspect.bat", "ltedit.bat")) {
      val sourceFile = winScripts.resolve(fileName)
      val targetFile = distBinDir.resolve(fileName)

      substituteTemplatePlaceholders(
        inputFile = sourceFile,
        outputFile = targetFile,
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

  override fun writeVmOptions(distBinDir: Path) : Path =
    writeWindowsVmOptions(distBinDir, context)

  private suspend fun createBuildWinZipTask(
    runtimeDir: Path?,
    zipNameSuffix: String,
    winDistPath: Path,
    arch: JvmArchitecture,
    customizer: WindowsDistributionCustomizer,
    context: BuildContext
  ): Path {
    val baseName = context.productProperties.getBaseArtifactName(context)
    val targetFile = context.paths.artifactDir.resolve("${baseName}${zipNameSuffix}.zip")

    spanBuilder("build Windows ${zipNameSuffix}.zip distribution")
      .setAttribute("targetFile", targetFile.toString())
      .setAttribute("arch", arch.dirName)
      .useWithScope {
        val dirs = mutableListOf(context.paths.distAllDir, winDistPath)

        if (runtimeDir != null) {
          dirs.add(runtimeDir)
        }

        val productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.zip${zipNameSuffix}")
        generateProductJson(productJsonDir, context, arch, withRuntime = runtimeDir != null)
        dirs.add(productJsonDir)

        val zipPrefix = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)

        val dirMap = dirs.associateWithTo(LinkedHashMap(dirs.size)) { zipPrefix }
        if (context.options.compressZipFiles) {
          zipWithCompression(targetFile = targetFile, dirs = dirMap)
        }
        else {
          zip(targetFile = targetFile, dirs = dirMap, addDirEntriesMode = AddDirEntriesMode.NONE)
        }
        checkInArchive(archiveFile = targetFile, pathInArchive = zipPrefix, context = context)
        context.notifyArtifactBuilt(targetFile)
        targetFile
      }

    return targetFile
  }

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

  private suspend fun buildWinLauncher(
    winDistPath: Path,
    arch: JvmArchitecture,
    additionalNonCustomizableJvmArgs: List<String>,
    context: BuildContext,
    copyLicense: Boolean = true
  ) {
    spanBuilder("build Windows executable").useWithScope {
      val communityHome = context.paths.communityHomeDir
      val appInfo = context.applicationInfo
      val executableBaseName = "${context.productProperties.baseFileName}64"
      val launcherPropertiesPath = context.paths.tempDir.resolve("launcher-${arch.dirName}.properties")
      val icoFile = computeIcoPath(context)

      val productVersion = context.buildNumber.replace(".SNAPSHOT", ".0") + ".0".repeat(3 - context.buildNumber.count { it == '.' })
      val launcherProperties = mutableListOf(
        "CompanyName" to appInfo.companyName,
        "LegalCopyright" to "Copyright 2000-${LocalDate.now().year} ${appInfo.companyName}",
        "FileDescription" to appInfo.productNameWithEdition,
        "ProductName" to appInfo.productNameWithEdition,
        "ProductVersion" to "$productVersion-${appInfo.productCode}", // "242.1234.56.0-IU"
      )
      if (!customizer.useXPlatLauncher) {
        val vmOptions = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch) + additionalNonCustomizableJvmArgs + listOf("-Dide.native.launcher=true")
        val classPath = context.bootClassPathJarNames.joinToString(separator = ";") { "%IDE_HOME%\\\\lib\\\\${it}" }
        val bootClassPath = context.xBootClassPathJarNames.joinToString(separator = ";") { "%IDE_HOME%\\\\lib\\\\${it}" }
        val envVarBaseName = context.productProperties.getEnvironmentVariableBaseName(appInfo)
        @Suppress("SpellCheckingInspection")
        launcherProperties += listOf(
          "IDS_JDK_ONLY" to "${context.productProperties.toolsJarRequired}",
          "IDS_JDK_ENV_VAR" to "${envVarBaseName}_JDK",
          "IDS_VM_OPTIONS_PATH" to "%APPDATA%\\\\\\\\${appInfo.shortCompanyName}\\\\\\\\${context.systemSelector}",
          "IDS_VM_OPTION_ERRORFILE" to "-XX:ErrorFile=%USERPROFILE%\\\\\\\\java_error_in_${executableBaseName}_%p.log",
          "IDS_VM_OPTION_HEAPDUMPPATH" to "-XX:HeapDumpPath=%USERPROFILE%\\\\\\\\java_error_in_${executableBaseName}.hprof",
          "IDS_PROPS_ENV_VAR" to "${envVarBaseName}_PROPERTIES",
          "IDS_VM_OPTIONS_ENV_VAR" to "${envVarBaseName}_VM_OPTIONS",
          "IDS_ERROR_LAUNCHING_APP" to "Error launching ${appInfo.shortProductName}",
          "IDS_VM_OPTIONS" to vmOptions.joinToString(separator = " "),
          "IDS_CLASSPATH_LIBS" to classPath,
          "IDS_BOOTCLASSPATH_LIBS" to bootClassPath,
          "IDS_INSTANCE_ACTIVATION" to "${context.productProperties.fastInstanceActivation}",
          "IDS_MAIN_CLASS" to context.ideMainClassName.replace('.', '/')
        )
      }
      Files.writeString(launcherPropertiesPath, launcherProperties.joinToString(separator = System.lineSeparator()) { (k, v) -> "${k}=${v}" })

      val inputPath = if (customizer.useXPlatLauncher) {
        val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.WINDOWS, arch)
        if (copyLicense) {
          copyFile(licensePath, winDistPath.resolve("license/launcher-third-party-libraries.html"))
        }
        execPath
      }
      else {
        communityHome.resolve("platform/build-scripts/resources/win/launcher/${arch.dirName}/WinLauncher.exe")
      }

      val outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")

      val generatorModule = context.findRequiredModule("intellij.tools.launcherGenerator")
      runJava(
        mainClass = "com.pme.launcher.LauncherGeneratorMain",
        args = listOf(
          inputPath.absolutePathString(),
          "${communityHome}/native/${if (customizer.useXPlatLauncher) "XPlatLauncher/resources/windows" else "WinLauncher"}/resource.h",
          launcherPropertiesPath.absolutePathString(),
          icoFile.absolutePathString(),
          outputPath.absolutePathString(),
        ),
        jvmArgs = listOf("-Djava.awt.headless=true"),
        context.getModuleRuntimeClasspath(generatorModule, forTests = false),
        context.stableJavaExecutable
      )
    }
  }

  private fun computeIcoPath(context: BuildContext): Path {
    val customizer = context.windowsDistributionCustomizer!!
    val icoPath = (if (context.applicationInfo.isEAP) customizer.icoPathForEAP else null) ?: customizer.icoPath
    require(icoPath != null) { "`WindowsDistributionCustomizer#icoPath` must be set" }
    return Path.of(icoPath)
  }

  private suspend fun checkThatExeInstallerAndZipWithJbrAreTheSame(zipPath: Path, exePath: Path, arch: JvmArchitecture, tempDir: Path) {
    Span.current().addEvent("compare ${zipPath.fileName} vs. ${exePath.fileName}")

    val tempZip = withContext(Dispatchers.IO) { Files.createTempDirectory(tempDir, "zip-${arch.dirName}") }
    val tempExe = withContext(Dispatchers.IO) { Files.createTempDirectory(tempDir, "exe-${arch.dirName}") }
    try {
      withContext(Dispatchers.IO) {
        try {
          runProcess(args = listOf("7z", "x", "-bd", exePath.toString()), workingDir = tempExe)
          // deleting NSIS-related files that appear after manual unpacking of .exe installer and do not belong to its contents
          @Suppress("SpellCheckingInspection")
          NioFiles.deleteRecursively(tempExe.resolve("\$PLUGINSDIR"))
          Files.deleteIfExists(tempExe.resolve("bin/Uninstall.exe.nsis"))
          Files.deleteIfExists(tempExe.resolve("bin/Uninstall.exe"))

          runProcess(args = listOf("unzip", "-q", zipPath.toString()), workingDir = tempZip)

          runProcess(args = listOf("diff", "-q", "-r", tempZip.toString(), tempExe.toString()))
        }
        finally {
          withContext(Dispatchers.IO + NonCancellable) {
            NioFiles.deleteRecursively(tempZip)
          }
        }
      }
      if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
        RepairUtilityBuilder.generateManifest(context, tempExe, OsFamily.WINDOWS, arch)
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
    var vmOptions = VmOptionsGenerator.computeVmOptions(context)
    if (isIjentWslFsEnabledByDefaultForProduct(context.productProperties.platformPrefix)) {
      vmOptions = vmOptions + ENABLE_IJENT_WSL_FILE_SYSTEM_VMOPTIONS
    }
    writeVmOptions(vmOptionsFile, vmOptions, separator = "\r\n")
    return vmOptionsFile
  }

  private fun generateProductJson(targetDir: Path, context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean = true): String {
    val jetbrainsClientCustomLaunchData = generateJetBrainsClientLaunchData(context, arch, OsFamily.WINDOWS) {
      "bin/${it.productProperties.baseFileName}64.exe.vmoptions"
    }
    val qodanaCustomLaunchData = generateQodanaLaunchData(context, arch, OsFamily.WINDOWS)

    val json = generateProductInfoJson(
      relativePathToBin = "bin",
      builtinModules = context.builtinModule,
      launch = listOf(ProductInfoLaunchData(
        os = OsFamily.WINDOWS.osName,
        arch = arch.dirName,
        launcherPath = "bin/${context.productProperties.baseFileName}64.exe",
        javaExecutablePath = if (withRuntime) "jbr/bin/java.exe" else null,
        vmOptionsFilePath = "bin/${context.productProperties.baseFileName}64.exe.vmoptions",
        startupWmClass = null,
        bootClassPathJarNames = context.bootClassPathJarNames,
        additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch),
        mainClass = context.ideMainClassName,
        customCommands = listOfNotNull(jetbrainsClientCustomLaunchData, qodanaCustomLaunchData),
      )),
      context)
    writeProductInfoJson(targetDir.resolve(PRODUCT_INFO_FILE_NAME), json, context)
    return json
  }

  private fun toDosLineEndings(x: String): String =
    x.replace("\r", "").replace("\n", "\r\n")
}
