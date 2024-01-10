// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
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
      Files.createDirectories(distBinDir)

      val sourceBinDir = context.paths.communityHomeDir.resolve("bin/win")

      FileSet(sourceBinDir.resolve(arch.dirName))
        .includeAll()
        .copyToDir(distBinDir)

      @Suppress("SpellCheckingInspection")
      FileSet(sourceBinDir)
        .include("*.*")
        .also { if (!context.includeBreakGenLibraries()) it.exclude("breakgen*.dll") }
        .copyToDir(distBinDir)

      generateBuildTxt(context, targetPath)
      copyDistFiles(context = context, newDir = targetPath, os = OsFamily.WINDOWS, arch = arch)

      Files.writeString(distBinDir.resolve(PROPERTIES_FILE_NAME), StringUtilRt.convertLineSeparators(ideaProperties!!, "\r\n"))

      val icoFile = computeIcoPath(context)
      if (icoFile != null) {
        Files.copy(icoFile, distBinDir.resolve("${context.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
      }
      if (customizer.includeBatchLaunchers) {
        generateScripts(distBinDir, arch)
      }
      writeVmOptions(distBinDir)
      buildWinLauncher(targetPath, arch, additionalNonCustomizableJvmArgs = emptyList(), context)
      val jetBrainsClientContext = createJetBrainsClientContextForLaunchers(context)
      if (jetBrainsClientContext != null) {
        writeWindowsVmOptions(distBinDir, jetBrainsClientContext)
        buildWinLauncher(targetPath, arch, additionalNonCustomizableJvmArgs = ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS,
                         jetBrainsClientContext)
      }

      customizer.copyAdditionalFiles(context, targetPath, arch)
    }

    context.executeStep(spanBuilder = spanBuilder("sign windows"), stepId = BuildOptions.WIN_SIGN_STEP) {
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
    var exePath: Path? = null
    val zipWithJbrPath = coroutineScope {
      setLastModifiedTime(osAndArchSpecificDistPath, context)
      val zipWithJbrPathTask = if (customizer.buildZipArchiveWithBundledJre) {
        createBuildWinZipTask(runtimeDir = runtimeDir,
                              zipNameSuffix = suffix(arch) + customizer.zipArchiveWithBundledJreSuffix,
                              winDistPath = osAndArchSpecificDistPath,
                              arch = arch,
                              customizer = customizer,
                              context = context)
      }
      else {
        null
      }

      if (customizer.buildZipArchiveWithoutBundledJre) {
        createBuildWinZipTask(runtimeDir = null,
                              zipNameSuffix = suffix(arch) + customizer.zipArchiveWithoutBundledJreSuffix,
                              winDistPath = osAndArchSpecificDistPath,
                              arch = arch,
                              customizer = customizer,
                              context = context)
      }

      context.executeStep(spanBuilder = spanBuilder("build Windows installer").setAttribute("arch", arch.dirName),
                          stepId = BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
        val productJsonDir = Files.createTempDirectory(context.paths.tempDir, "win-product-info")
        val jsonText = generateProductJson(productJsonDir, context, arch)
        validateProductJson(jsonText = jsonText,
                            relativePathToProductJson = "",
                            installationDirectories = listOf(context.paths.distAllDir, osAndArchSpecificDistPath, runtimeDir),
                            installationArchives = emptyList(),
                            context = context)

        exePath = buildNsisInstaller(winDistPath = osAndArchSpecificDistPath,
                                     additionalDirectoryToInclude = productJsonDir,
                                     suffix = suffix(arch),
                                     customizer = customizer,
                                     runtimeDir = runtimeDir,
                                     context = context)
      }

      zipWithJbrPathTask?.await()
    }

    if (zipWithJbrPath != null && exePath != null) {
      if (context.options.isInDevelopmentMode) {
        Span.current().addEvent("comparing .zip and .exe skipped in development mode")
      }
      else if (!SystemInfoRt.isLinux) {
        Span.current().addEvent("comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      }
      else {
        checkThatExeInstallerAndZipWithJbrAreTheSame(zipPath = zipWithJbrPath,
                                                     exePath = exePath!!,
                                                     arch = arch,
                                                     tempDir = context.paths.tempDir,
                                                     context = context)
      }
      return
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

  override fun writeVmOptions(distBinDir: Path) : Path {
    return writeWindowsVmOptions(distBinDir, context)
  }

  private fun CoroutineScope.createBuildWinZipTask(runtimeDir: Path?,
                                                   zipNameSuffix: String,
                                                   winDistPath: Path,
                                                   arch: JvmArchitecture,
                                                   customizer: WindowsDistributionCustomizer,
                                                   context: BuildContext): Deferred<Path> {
    return async(Dispatchers.IO) {
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
    }
  }

  override fun distributionFilesBuilt(arch: JvmArchitecture): List<Path> {
    val archSuffix = suffix(arch)
    return sequenceOf(
      "$archSuffix.exe",
      archSuffix + customizer.zipArchiveWithBundledJreSuffix + ".zip",
      archSuffix + customizer.zipArchiveWithoutBundledJreSuffix + ".zip"
    ).map { suffix ->
      context.productProperties.getBaseArtifactName(context) + suffix
    }.map(context.paths.artifactDir::resolve)
      .filter { it.exists() }
      .toList()
  }

  override fun isRuntimeBundled(file: Path): Boolean {
    return !file.name.contains(customizer.zipArchiveWithoutBundledJreSuffix)
  }
}

private fun computeIcoPath(context: BuildContext): Path? {
  val customizer = context.windowsDistributionCustomizer
  val icoPath = (if (context.applicationInfo.isEAP) customizer?.icoPathForEAP else null) ?: customizer?.icoPath
  return icoPath?.let { Path.of(icoPath) }
}

private suspend fun buildWinLauncher(winDistPath: Path,
                                     arch: JvmArchitecture,
                                     additionalNonCustomizableJvmArgs: List<String>,
                                     context: BuildContext) {
  spanBuilder("build Windows executable").useWithScope {
    val executableBaseName = "${context.productProperties.baseFileName}64"
    val launcherPropertiesPath = context.paths.tempDir.resolve("launcher-${arch.dirName}.properties")
    val icoFile = computeIcoPath(context)

    @Suppress("SpellCheckingInspection")
    val vmOptions = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch) + listOf("-Dide.native.launcher=true") + 
                    additionalNonCustomizableJvmArgs
    val productName = context.applicationInfo.shortProductName
    val classPath = context.bootClassPathJarNames.joinToString(separator = ";") { "%IDE_HOME%\\\\lib\\\\${it}" }
    val bootClassPath = context.xBootClassPathJarNames.joinToString(separator = ";") { "%IDE_HOME%\\\\lib\\\\${it}" }
    val envVarBaseName = context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)
    val icoFilesDirectory = context.paths.tempDir.resolve("win-launcher-ico-${arch.dirName}")
    val appInfoForLauncher = generateApplicationInfoForLauncher(context.applicationInfo.appInfoXml, icoFilesDirectory, icoFile)
    @Suppress("SpellCheckingInspection")
    Files.writeString(launcherPropertiesPath, """
        IDS_JDK_ONLY=${context.productProperties.toolsJarRequired}
        IDS_JDK_ENV_VAR=${envVarBaseName}_JDK
        IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${context.applicationInfo.shortCompanyName}\\\\${context.systemSelector}
        IDS_VM_OPTION_ERRORFILE=-XX:ErrorFile=%USERPROFILE%\\\\java_error_in_${executableBaseName}_%p.log
        IDS_VM_OPTION_HEAPDUMPPATH=-XX:HeapDumpPath=%USERPROFILE%\\\\java_error_in_${executableBaseName}.hprof
        IDS_PROPS_ENV_VAR=${envVarBaseName}_PROPERTIES
        IDS_VM_OPTIONS_ENV_VAR=${envVarBaseName}_VM_OPTIONS
        IDS_ERROR_LAUNCHING_APP=Error launching $productName
        IDS_VM_OPTIONS=${vmOptions.joinToString(separator = " ")}
        IDS_CLASSPATH_LIBS=${classPath}
        IDS_BOOTCLASSPATH_LIBS=${bootClassPath}
        IDS_INSTANCE_ACTIVATION=${context.productProperties.fastInstanceActivation}
        IDS_MAIN_CLASS=${context.ideMainClassName.replace('.', '/')}
        """.trimIndent().trim())

    val communityHome = context.paths.communityHome
    val inputPath = "${communityHome}/platform/build-scripts/resources/win/launcher/${arch.dirName}/WinLauncher.exe"
    val outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")
    val classpath = ArrayList<String>()

    val generatorClasspath = context.getModuleRuntimeClasspath(
      module = context.findRequiredModule("intellij.tools.launcherGenerator"),
      forTests = false)
    classpath.addAll(generatorClasspath)

    sequenceOf(context.findApplicationInfoModule(), context.findRequiredModule("intellij.platform.icons"))
      .flatMap { it.sourceRoots }
      .forEach { root ->
        classpath.add(root.file.absolutePath)
      }

    for (p in context.productProperties.brandingResourcePaths) {
      classpath.add(p.toString())
    }
    classpath.add(icoFilesDirectory.toString())

    runIdea(
      context = context,
      mainClass = "com.pme.launcher.LauncherGeneratorMain",
      args = listOf(
        inputPath,
        appInfoForLauncher.toString(),
        "$communityHome/native/WinLauncher/resource.h",
        launcherPropertiesPath.toString(),
        icoFile?.fileName?.toString() ?: " ",
        outputPath.toString(),
      ),
      jvmArgs = listOf("-Djava.awt.headless=true"),
      classPath = classpath
    )
  }
}

/**
 * Generates ApplicationInfo.xml file for launcher generator which contains link to proper *.ico file.
 * todo pass path to ico file to LauncherGeneratorMain directly (probably after IDEA-196705 is fixed).
 */
private fun generateApplicationInfoForLauncher(appInfo: String, icoFilesDirectory: Path, icoFile: Path?): Path {
  Files.createDirectories(icoFilesDirectory)
  if (icoFile != null) {
    Files.copy(icoFile, icoFilesDirectory.resolve(icoFile.fileName), StandardCopyOption.REPLACE_EXISTING)
  }
  val patchedFile = icoFilesDirectory.resolve("win-launcher-application-info.xml")
  Files.writeString(patchedFile, appInfo)
  return patchedFile
}

private suspend fun checkThatExeInstallerAndZipWithJbrAreTheSame(zipPath: Path,
                                                                 exePath: Path,
                                                                 arch: JvmArchitecture,
                                                                 tempDir: Path,
                                                                 context: BuildContext) {
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
  val vmOptionsPath = distBinDir.resolve("${context.productProperties.baseFileName}64.exe.vmoptions")
  val vmOptions = VmOptionsGenerator.computeVmOptions(context)
  writeVmOptions(file = vmOptionsPath, vmOptions = vmOptions, separator = "\r\n")

  return vmOptionsPath
}

private fun generateProductJson(targetDir: Path, context: BuildContext, arch: JvmArchitecture, withRuntime: Boolean = true): String {
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
      mainClass = context.ideMainClassName)),
    context)
  writeProductInfoJson(targetDir.resolve(PRODUCT_INFO_FILE_NAME), json, context)
  return json
}

private fun toDosLineEndings(x: String): String =
  x.replace("\r", "").replace("\n", "\r\n")
