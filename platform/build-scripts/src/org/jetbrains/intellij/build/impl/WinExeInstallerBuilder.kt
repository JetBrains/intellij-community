// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.setLastModifiedTime
import kotlin.time.Duration.Companion.hours

internal suspend fun buildNsisInstaller(
  winDistPath: Path,
  additionalDirectoryToInclude: Path,
  suffix: String,
  customizer: WindowsDistributionCustomizer,
  runtimeDir: Path,
  context: BuildContext,
  arch: JvmArchitecture,
): Path? {
  if (OsFamily.currentOs == OsFamily.MACOS && !context.options.isInDevelopmentMode) {
    Span.current().addEvent("Windows installers shouldn't be built on macOS in production mode")
    return null
  }

  val communityHome = context.paths.communityHomeDir
  val outFileName = context.productProperties.getBaseArtifactName(context) + suffix
  Span.current().setAttribute(outFileName, outFileName)

  withContext(Dispatchers.IO) {
    val box = context.paths.tempDir.resolve("winInstaller${suffix}")

    val (nsisDir, nsisBin) = prepareNsis(context, box)

    val nsiConfDir = box.resolve("nsi-conf")
    Files.createDirectories(nsiConfDir)
    NioFiles.copyRecursively(context.paths.communityHomeDir.resolve("build/conf/nsis"), nsiConfDir)

    if (OsFamily.currentOs != OsFamily.WINDOWS) {
      val ideaNsiPath = nsiConfDir.resolve("idea.nsi")
      Files.writeString(ideaNsiPath, BuildUtils.replaceAll(
        text = Files.readString(ideaNsiPath),
        replacements = mapOf($$"${IMAGES_LOCATION}\\" to $$"${IMAGES_LOCATION}/"),
        marker = "")
      )
    }

    val generator = NsisFileListGenerator()
    generator.addDirectory(context.paths.distAllDir)
    generator.addDirectory(winDistPath)
    generator.addDirectory(additionalDirectoryToInclude)
    generator.addDirectory(runtimeDir)
    generator.generateInstallerFile(nsiConfDir.resolve("idea_win.nsh"))
    generator.generateUninstallerFile(nsiConfDir.resolve("un_idea_win.nsh"))

    prepareConfigurationFiles(nsiConfDir, customizer, context, arch)
    for (it in customizer.customNsiConfigurationFiles) {
      val file = Path.of(it)
      val copy = nsiConfDir.resolve(file.fileName)
      Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING)
      copy.setLastModifiedTime(FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
    }

    val nsiLogDir = context.paths.buildOutputDir.resolve("log/nsi$suffix")
    NioFiles.deleteRecursively(nsiLogDir)
    NioFiles.copyRecursively(nsiConfDir, nsiLogDir)

    spanBuilder("run NSIS tool to build .exe installer for Windows").use {
      val timeout = 2.hours
      if (OsFamily.currentOs == OsFamily.WINDOWS) {
        @Suppress("SpellCheckingInspection")
        runProcess(
          args = listOf(
            nsisBin.toString(),
            "/V2",
            "/DCOMMUNITY_DIR=${communityHome}",
            "/DIPR=${customizer.associateIpr}",
            "/DOUT_DIR=${context.paths.artifactDir}",
            "/DOUT_FILE=${outFileName}",
            "${nsiConfDir}/idea.nsi",
          ),
          workingDir = box,
          timeout
        )
      }
      else {
        @Suppress("SpellCheckingInspection")
        runProcess(
          args = listOf(
            nsisBin.toString(),
            "-V2",
            "-DCOMMUNITY_DIR=${communityHome}",
            "-DIPR=${customizer.associateIpr}",
            "-DOUT_DIR=${context.paths.artifactDir}",
            "-DOUT_FILE=${outFileName}",
            "${nsiConfDir}/idea.nsi",
          ),
          workingDir = box,
          timeout,
          additionalEnvVariables = mapOf("NSISDIR" to nsisDir.toString(), "LC_CTYPE" to "C.UTF-8"),
        )
      }
    }
  }

  val installerFile = context.paths.artifactDir.resolve("$outFileName.exe")
  val uninstallerFile = uninstallerPath(context, arch)
  check(Files.exists(installerFile)) { "Windows installer wasn't created." }
  check(Files.exists(uninstallerFile)) { "Windows uninstaller is missing." }
  context.executeStep(spanBuilder("sign").setAttribute("file", installerFile.toString()), BuildOptions.WIN_SIGN_STEP) {
    context.signFiles(listOf(installerFile))
  }
  context.notifyArtifactBuilt(installerFile)
  context.notifyArtifactBuilt(uninstallerFile)
  return installerFile
}

private suspend fun prepareNsis(context: BuildContext, tempDir: Path): Pair<Path, Path> {
  val nsisDir = context.options.useLocalNSIS?.let { Path.of(it) } ?: run {
    val nsisVersion = context.dependenciesProperties.property("nsisBuild")
    val nsisUrl = "https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/org/jetbrains/intellij/deps/nsis/NSIS-${nsisVersion}.zip"
    val nsisZip = downloadFileToCacheLocation(nsisUrl, context.paths.communityHomeDirRoot)
    Decompressor.Zip(nsisZip).withZipExtensions().extract(tempDir)
    val nsisDir = tempDir.resolve("NSIS")
    require(nsisDir.isDirectory()) { "'${nsisDir.fileName}' is missing from ${nsisUrl}" }
    nsisDir
  }
  val ext = if (OsFamily.currentOs == OsFamily.WINDOWS) ".exe" else "-${OsFamily.currentOs.dirName}-${JvmArchitecture.currentJvmArch.dirName}"
  @Suppress("SpellCheckingInspection") val nsisBin = nsisDir.resolve("Bin/makensis${ext}")
  require(nsisBin.isRegularFile()) { "'${nsisDir.fileName}' is missing" }
  NioFiles.setExecutable(nsisBin)
  return nsisDir to nsisBin
}

private suspend fun prepareConfigurationFiles(nsiConfDir: Path, customizer: WindowsDistributionCustomizer, context: BuildContext, arch: JvmArchitecture) {
  val expectedArch = when (arch) {  // https://learn.microsoft.com/en-us/windows/win32/sysinfo/image-file-machine-constants
    JvmArchitecture.x64 -> 0x8664  // IMAGE_FILE_MACHINE_AMD64
    JvmArchitecture.aarch64 -> 0xAA64  // IMAGE_FILE_MACHINE_ARM64
  }
  val fileAssociations =
    if (customizer.fileAssociations.isEmpty()) "NoAssociation"
    else customizer.fileAssociations.joinToString(separator = ",") { if (it.startsWith(".")) it else ".${it}" }
  val appInfo = context.applicationInfo
  val uninstallFeedbackPage = if (appInfo.isEAP) null else customizer.getUninstallFeedbackPageUrl(appInfo)
  val installDirAndShortcutName = customizer.getNameForInstallDirAndDesktopShortcut(appInfo, context.buildNumber)
  val fileVersionNum = amendVersionNumber(context.buildNumber.replace(".SNAPSHOT", ".0"))
  val productVersionNum = amendVersionNumber(appInfo.majorVersion + '.' + appInfo.minorVersion)
  val versionString = if (appInfo.isEAP) context.buildNumber else "${appInfo.majorVersion}.${appInfo.minorVersion}"

  val uninstallerCopy = context.paths.artifactDir.resolve("Uninstall-${context.applicationInfo.productCode}-${arch.dirName}.exe")
  val uninstallerSignCmd = when {
    !context.isStepSkipped(BuildOptions.WIN_SIGN_STEP) -> {
      val signTool = prepareSignTool(nsiConfDir, context, uninstallerCopy)
      "'${signTool}' '%1'"
    }
    OsFamily.currentOs == OsFamily.WINDOWS -> {
      "COPY /B /Y '%1' '${uninstallerCopy}'"
    }
    else -> {
      "cp -f '%1' '${uninstallerCopy}'"
    }
  }

  Files.writeString(nsiConfDir.resolve("config.nsi"), $$"""
    !define INSTALLER_ARCH $${expectedArch}
    !define IMAGES_LOCATION "$${Path.of(customizer.installerImagesPath!!)}"

    !define MANUFACTURER "$${appInfo.shortCompanyName}"
    !define MUI_PRODUCT "$${customizer.getFullNameIncludingEdition(appInfo)}"
    !define PRODUCT_FULL_NAME "$${customizer.getFullNameIncludingEditionAndVendor(appInfo)}"
    !define PRODUCT_EXE_FILE "$${context.productProperties.baseFileName}64.exe"
    !define PRODUCT_ICON_FILE "install.ico"
    !define PRODUCT_UNINSTALL_ICON_FILE "uninstall.ico"
    !define PRODUCT_LOGO_FILE "logo.bmp"
    !define PRODUCT_HEADER_FILE "headerlogo.bmp"
    !define ASSOCIATION "$${fileAssociations}"
    !define UNINSTALL_WEB_PAGE "$${uninstallFeedbackPage ?: ""}"

    !define MUI_VERSION_MAJOR "$${appInfo.majorVersion}"
    !define MUI_VERSION_MINOR "$${appInfo.minorVersion}"
    !define VER_BUILD $${context.buildNumber}
    !define FILE_VERSION_NUM $${fileVersionNum}
    !define PRODUCT_VERSION_NUM $${productVersionNum}
    !define INSTALL_DIR_AND_SHORTCUT_NAME "$${installDirAndShortcutName}"
    !define PRODUCT_WITH_VER "${MUI_PRODUCT} $${versionString}"
    !define PRODUCT_PATHS_SELECTOR "$${context.systemSelector}"

    !uninstfinalize "$${uninstallerSignCmd}"
    """.trimIndent())
}

private fun amendVersionNumber(base: String): String = base + ".0".repeat(3 - base.count { it == '.' })

private suspend fun prepareSignTool(nsiConfDir: Path, context: BuildContext, uninstallerCopy: Path): Path {
  val toolFile =
    context.proprietaryBuildTools.signTool.commandLineClient(context, OsFamily.currentOs, JvmArchitecture.currentJvmArch)
    ?: error("No command line sign tool is configured")
  val scriptFile = Files.writeString(nsiConfDir.resolve("sign-tool.cmd"), when (OsFamily.currentOs) {
    // moving the file back and forth is required for NSIS to fail if signing didn't happen
    OsFamily.WINDOWS -> """
      @ECHO OFF
      MOVE /Y "%1" "${nsiConfDir}\\Uninstall.exe"
      "${toolFile}" -denoted-content-type application/x-exe -signed-files-dir "${nsiConfDir}\\_signed" "${nsiConfDir}\\Uninstall.exe"
      COPY /B /Y "${nsiConfDir}\\_signed\\Uninstall.exe" "${uninstallerCopy}"
      MOVE /Y "${nsiConfDir}\\_signed\\Uninstall.exe" "%1"
      """.trimIndent()
    else -> $$"""
      #!/bin/sh
      mv -f "$1" "$${nsiConfDir}/Uninstall.exe"
      "$${toolFile}" -denoted-content-type application/x-exe -signed-files-dir "$${nsiConfDir}/_signed" "$${nsiConfDir}/Uninstall.exe"
      cp -f "$${nsiConfDir}/_signed/Uninstall.exe" "$${uninstallerCopy}"
      mv -f "$${nsiConfDir}/_signed/Uninstall.exe" "$1"
      """.trimIndent()
  })
  NioFiles.setExecutable(scriptFile)
  return scriptFile
}

private fun uninstallerPath(context: BuildContext, arch: JvmArchitecture): Path =
  context.paths.artifactDir.resolve("Uninstall-${context.applicationInfo.productCode}-${arch.dirName}.exe")
