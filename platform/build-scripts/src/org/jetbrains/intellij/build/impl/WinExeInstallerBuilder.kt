// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.setLastModifiedTime
import kotlin.time.Duration.Companion.hours

@Suppress("SpellCheckingInspection")
internal suspend fun buildNsisInstaller(winDistPath: Path,
                                        additionalDirectoryToInclude: Path,
                                        suffix: String,
                                        customizer: WindowsDistributionCustomizer,
                                        runtimeDir: Path,
                                        context: BuildContext): Path? {
  if (SystemInfoRt.isMac && !Docker.isAvailable) {
    Span.current().addEvent("Windows installer cannot be built on macOS without Docker")
    return null
  }

  val communityHome = context.paths.communityHomeDir
  val outFileName = context.productProperties.getBaseArtifactName(context) + suffix
  Span.current().setAttribute(outFileName, outFileName)

  val box = context.paths.tempDir.resolve("winInstaller$suffix")
  //noinspection SpellCheckingInspection
  val nsiConfDir = box.resolve("nsiconf")
  withContext(Dispatchers.IO) {
    Files.createDirectories(nsiConfDir)
    copyDir(context.paths.communityHomeDir.resolve("build/conf/nsis"), nsiConfDir)

    if (!SystemInfoRt.isWindows) {
      val ideaNsiPath = nsiConfDir.resolve("idea.nsi")
      Files.writeString(ideaNsiPath, BuildUtils.replaceAll(text = Files.readString(ideaNsiPath),
                                                           replacements = mapOf("\${IMAGES_LOCATION}\\" to "\${IMAGES_LOCATION}/"),
                                                           marker = ""))
    }

    val generator = NsisFileListGenerator()
    generator.addDirectory(context.paths.distAllDir.toString())
    generator.addDirectory(winDistPath.toString(), listOf("**/idea.properties", "**/${context.productProperties.baseFileName}*.vmoptions"))
    generator.addDirectory(additionalDirectoryToInclude.toString())
    generator.addDirectory(runtimeDir.toString())
    generator.generateInstallerFile(nsiConfDir.resolve("idea_win.nsh"))
    generator.generateUninstallerFile(nsiConfDir.resolve("unidea_win.nsh"))

    prepareConfigurationFiles(nsiConfDir = nsiConfDir, winDistPath = winDistPath, customizer = customizer, context = context)
    for (it in customizer.customNsiConfigurationFiles) {
      val file = Path.of(it)
      val copy = nsiConfDir.resolve(file.fileName)
      Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING)
      copy.setLastModifiedTime(FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
    }

    // Log final nsi directory to make debugging easier
    val logDir = context.paths.buildOutputDir.resolve("log")
    val nsiLogDir = logDir.resolve("nsi$suffix")
    deleteDir(nsiLogDir)
    copyDir(nsiConfDir, nsiLogDir)

    val nsisZip = downloadFileToCacheLocation(
      url = "https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/org/jetbrains/intellij/deps/nsis/" +
            "NSIS-${context.dependenciesProperties.property("nsisBuild")}.zip",
      communityRoot = context.paths.communityHomeDirRoot,
    )
    Decompressor.Zip(nsisZip).withZipExtensions().extract(box)
    spanBuilder("run NSIS tool to build .exe installer for Windows").useWithScope {
      val timeout = 2.hours
      if (SystemInfoRt.isWindows) {
        runProcess(
          args = listOf(
            "$box/NSIS/makensis.exe",
            "/V2",
            "/DCOMMUNITY_DIR=$communityHome",
            "/DIPR=${customizer.associateIpr}",
            "/DOUT_DIR=${context.paths.artifactDir}",
            "/DOUT_FILE=$outFileName",
            "$box/nsiconf/idea.nsi",
          ),
          workingDir = box,
          timeout = timeout,
        )
      }
      else {
        val makeNsis = "$box/NSIS/Bin/makensis${if (JvmArchitecture.currentJvmArch == JvmArchitecture.x64) "" else "-${JvmArchitecture.currentJvmArch.fileSuffix}"}"
        NioFiles.setExecutable(Path.of(makeNsis))
        val args = if (SystemInfoRt.isMac) {
          arrayOf(
            "docker", "run", "--rm",
            "--volume=$communityHome:$communityHome:ro",
            "--volume=${context.paths.buildOutputDir}:${context.paths.buildOutputDir}",
            "--workdir=$box",
            "ubuntu:18.04"
          )
        }
        else emptyArray()
        runProcess(
          args = listOf(
            *args, makeNsis,
            "-V2",
            "-DCOMMUNITY_DIR=$communityHome",
            "-DIPR=${customizer.associateIpr}",
            "-DOUT_DIR=${context.paths.artifactDir}",
            "-DOUT_FILE=$outFileName",
            "$box/nsiconf/idea.nsi",
          ),
          workingDir = box,
          timeout = timeout,
          additionalEnvVariables = mapOf("NSISDIR" to "$box/NSIS"),
        )
      }
    }
  }
  val installerFile = context.paths.artifactDir.resolve("$outFileName.exe")
  check(Files.exists(installerFile)) {
    "Windows installer wasn't created."
  }
  context.executeStep(spanBuilder("sign").setAttribute("file", installerFile.toString()), BuildOptions.WIN_SIGN_STEP) {
    context.signFiles(listOf(installerFile))
  }
  context.notifyArtifactBuilt(installerFile)
  return installerFile
}

private fun prepareConfigurationFiles(nsiConfDir: Path,
                                      winDistPath: Path,
                                      customizer: WindowsDistributionCustomizer,
                                      context: BuildContext) {
  val productProperties = context.productProperties

  Files.writeString(nsiConfDir.resolve("paths.nsi"), """
!define IMAGES_LOCATION "${FileUtilRt.toSystemDependentName(customizer.installerImagesPath!!)}"
!define PRODUCT_PROPERTIES_FILE "${FileUtilRt.toSystemDependentName("$winDistPath/bin/idea.properties")}"
!define PRODUCT_VM_OPTIONS_NAME ${productProperties.baseFileName}*.exe.vmoptions
!define PRODUCT_VM_OPTIONS_FILE "${FileUtilRt.toSystemDependentName("${winDistPath}/bin/")}${'$'}{PRODUCT_VM_OPTIONS_NAME}"
""")

  val fileAssociations = if (customizer.fileAssociations.isEmpty()) "NoAssociation"
                         else customizer.fileAssociations.joinToString(separator = ",") { if (it.startsWith(".")) it else ".${it}" }
  val appInfo = context.applicationInfo
  Files.writeString(nsiConfDir.resolve("strings.nsi"), """
!define MANUFACTURER "${appInfo.shortCompanyName}"
!define MUI_PRODUCT  "${customizer.getFullNameIncludingEdition(appInfo)}"
!define PRODUCT_FULL_NAME "${customizer.getFullNameIncludingEditionAndVendor(appInfo)}"
!define PRODUCT_EXE_FILE "${productProperties.baseFileName}64.exe"
!define PRODUCT_ICON_FILE "install.ico"
!define PRODUCT_UNINST_ICON_FILE "uninstall.ico"
!define PRODUCT_LOGO_FILE "logo.bmp"
!define PRODUCT_HEADER_FILE "headerlogo.bmp"
!define ASSOCIATION "$fileAssociations"
!define UNINSTALL_WEB_PAGE "${customizer.getUninstallFeedbackPageUrl(appInfo) ?: "feedback_web_page"}"

; if SHOULD_SET_DEFAULT_INSTDIR != 0 then default installation directory will be directory where highest-numbered IDE build has been installed
; set to 1 for release build
!define SHOULD_SET_DEFAULT_INSTDIR "0"
""")

  val versionString = if (appInfo.isEAP) "\${VER_BUILD}" else "\${MUI_VERSION_MAJOR}.\${MUI_VERSION_MINOR}"
  val installDirAndShortcutName = customizer.getNameForInstallDirAndDesktopShortcut(appInfo, context.buildNumber)
  Files.writeString(nsiConfDir.resolve("version.nsi"), """
!define MUI_VERSION_MAJOR "${appInfo.majorVersion}"
!define MUI_VERSION_MINOR "${appInfo.minorVersion}"

!define VER_BUILD ${context.buildNumber}
!define INSTALL_DIR_AND_SHORTCUT_NAME "$installDirAndShortcutName"
!define PRODUCT_WITH_VER "${"$"}{MUI_PRODUCT} $versionString"
!define PRODUCT_PATHS_SELECTOR "${context.systemSelector}"
""")
}
