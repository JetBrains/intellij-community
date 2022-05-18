// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.io.FileKt
import org.jetbrains.intellij.build.io.ProcessKt

import java.nio.file.*
import java.util.concurrent.TimeUnit

@CompileStatic
final class WinExeInstallerBuilder {
  private final BuildContext buildContext
  private final WindowsDistributionCustomizer customizer
  private final @Nullable Path jreDir

  WinExeInstallerBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, @Nullable Path jreDir) {
    this.buildContext = buildContext
    this.customizer = customizer
    this.jreDir = jreDir
  }

  private void generateInstallationConfigFileForSilentMode() {
    Path targetFilePath = buildContext.paths.artifactDir.resolve("silent.config")
    if (Files.exists(targetFilePath)) {
      return
    }

    String silentConfigTemplate
    def customConfigPath = customizer.silentInstallationConfig
    if (customConfigPath != null) {
      if (!new File(customConfigPath).exists()) {
        buildContext.messages.error("WindowsDistributionCustomizer.silentInstallationConfig points to a file which doesn't exist: $customConfigPath")
      }
      silentConfigTemplate = customConfigPath
    }
    else {
      silentConfigTemplate = "$buildContext.paths.communityHome/platform/build-scripts/resources/win/nsis/silent.config"
    }

    Files.createDirectories(targetFilePath.parent)
    Files.copy(Paths.get(silentConfigTemplate), targetFilePath)
    List<String> extensionsList = getFileAssociations()
    String associations = "\n\n; List of associations. To create an association change value to 1.\n"
    if (extensionsList.isEmpty()) {
      associations = "\n\n; There are no associations for the product.\n"
    }
    else {
      associations += extensionsList.collect { "$it=0\n" }.join("")
    }
    Files.writeString(targetFilePath, associations, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
  }

  /**
   * Returns list of file extensions with leading dot added
   */
  private List<String> getFileAssociations() {
    return customizer.fileAssociations.collect {it.startsWith(".") ? it : ("." + it) }
  }

  Path buildInstaller(Path winDistPath, Path additionalDirectoryToInclude, String suffix, BuildContext context) {
    if (!SystemInfoRt.isWindows && !SystemInfoRt.isLinux) {
      context.messages.warning("Windows installer can be built only under Windows or Linux")
      return null
    }

    String communityHome = context.paths.communityHome
    String outFileName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
    context.messages.progress("Building Windows installer $outFileName")

    Path box = context.paths.tempDir.resolve("winInstaller")
    Path nsiConfDir = box.resolve("nsiconf")
    Files.createDirectories(nsiConfDir)

    boolean bundleJre = jreDir != null
    if (!bundleJre) {
      context.messages.info("JRE won't be bundled with Windows installer because JRE archive is missing")
    }

    FileKt.copyDir(context.paths.communityHomeDir.resolve("build/conf/nsis"), nsiConfDir)

    generateInstallationConfigFileForSilentMode()

    if (SystemInfoRt.isLinux) {
      Path ideaNsiPath = nsiConfDir.resolve("idea.nsi")
      Files.writeString(ideaNsiPath, BuildUtils.replaceAll(Files.readString(ideaNsiPath), ["\${IMAGES_LOCATION}\\": "\${IMAGES_LOCATION}/"], ""))
    }

    try {
      def generator = new NsisFileListGenerator()
      generator.addDirectory(context.paths.distAll)
      generator.addDirectory(winDistPath.toString(), ["**/idea.properties", "**/*.vmoptions"])
      generator.addDirectory(additionalDirectoryToInclude.toString())

      if (bundleJre) {
        generator.addDirectory(jreDir.toString())
      }

      generator.generateInstallerFile(nsiConfDir.resolve("idea_win.nsh").toFile())

      generator.generateUninstallerFile(nsiConfDir.resolve("unidea_win.nsh").toFile())
    }
    catch (IOException e) {
      context.messages.error("Failed to generated list of files for NSIS installer: $e", e)
    }

    prepareConfigurationFiles(nsiConfDir, winDistPath)
    customizer.customNsiConfigurationFiles.each {
      Path file = Paths.get(it)
      Files.copy(file, nsiConfDir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
    }

    // Log final nsi directory to make debugging easier
    def logDir = new File(context.paths.buildOutputRoot, "log")
    def nsiLogDir = new File(logDir, "nsi")
    NioFiles.deleteRecursively(nsiLogDir.toPath())
    FileUtil.copyDir(nsiConfDir.toFile(), nsiLogDir)

    new Decompressor.Zip(Path.of(communityHome, "build/tools/NSIS.zip")).withZipExtensions().extract(box)
    context.messages.block("Running NSIS tool to build .exe installer for Windows") {
      long timeoutMs = TimeUnit.HOURS.toMillis(2)
      if (SystemInfoRt.isWindows) {
        ProcessKt.runProcess(
          [
            "${box}/NSIS/makensis.exe".toString(),
            "/V2",
            "/DCOMMUNITY_DIR=${communityHome}".toString(),
            "/DIPR=${customizer.associateIpr}".toString(),
            "/DOUT_DIR=${context.paths.artifacts}".toString(),
            "/DOUT_FILE=${outFileName}".toString(),
            "${box}/nsiconf/idea.nsi".toString(),
          ],
          box,
          context.messages,
          timeoutMs,
        )
      }
      else {
        String makeNsis = "${box}/NSIS/Bin/makensis"
        NioFiles.setExecutable(Path.of(makeNsis))
        ProcessKt.runProcess(
          [
            makeNsis,
            "-V2",
            "-DCOMMUNITY_DIR=${communityHome}".toString(),
            "-DIPR=${customizer.associateIpr}".toString(),
            "-DOUT_DIR=${context.paths.artifacts}".toString(),
            "-DOUT_FILE=${outFileName}".toString(),
            "${box}/nsiconf/idea.nsi".toString(),
          ],
          box,
          context.messages,
          timeoutMs,
          Map.of("NSISDIR", "${box}/NSIS".toString()),
        )
      }
    }

    Path installerFile = context.paths.artifactDir.resolve(outFileName + ".exe")
    if (Files.notExists(installerFile)) {
      context.messages.error("Windows installer wasn't created.")
    }
    context.executeStep(TraceManager.spanBuilder("sign").setAttribute("file", installerFile.toString()), BuildOptions.WIN_SIGN_STEP) {
      context.signFiles(List.of(installerFile), Collections.emptyMap())
    }
    context.notifyArtifactWasBuilt(installerFile)
    return installerFile
  }

  private void prepareConfigurationFiles(Path nsiConfDir, Path winDistPath) {
    def productProperties = buildContext.productProperties

    Files.writeString(nsiConfDir.resolve("paths.nsi"), """
!define IMAGES_LOCATION "${FileUtilRt.toSystemDependentName(customizer.installerImagesPath)}"
!define PRODUCT_PROPERTIES_FILE "${FileUtilRt.toSystemDependentName("$winDistPath/bin/idea.properties")}"
!define PRODUCT_VM_OPTIONS_NAME ${productProperties.baseFileName}*.exe.vmoptions
!define PRODUCT_VM_OPTIONS_FILE "${FileUtilRt.toSystemDependentName("$winDistPath/bin/")}\${PRODUCT_VM_OPTIONS_NAME}"
""")

    def extensionsList = getFileAssociations()
    def fileAssociations = extensionsList.isEmpty() ? "NoAssociation" : extensionsList.join(",")
    Files.writeString(nsiConfDir.resolve("strings.nsi"), """
!define MANUFACTURER "${buildContext.applicationInfo.shortCompanyName}"
!define MUI_PRODUCT  "${customizer.getFullNameIncludingEdition(buildContext.applicationInfo)}"
!define PRODUCT_FULL_NAME "${customizer.getFullNameIncludingEditionAndVendor(buildContext.applicationInfo)}"
!define PRODUCT_EXE_FILE "${productProperties.baseFileName}64.exe"
!define PRODUCT_ICON_FILE "install.ico"
!define PRODUCT_UNINST_ICON_FILE "uninstall.ico"
!define PRODUCT_LOGO_FILE "logo.bmp"
!define PRODUCT_HEADER_FILE "headerlogo.bmp"
!define ASSOCIATION "$fileAssociations"
!define UNINSTALL_WEB_PAGE "${customizer.getUninstallFeedbackPageUrl(buildContext.applicationInfo) ?: "feedback_web_page"}"

; if SHOULD_SET_DEFAULT_INSTDIR != 0 then default installation directory will be directory where highest-numbered IDE build has been installed
; set to 1 for release build
!define SHOULD_SET_DEFAULT_INSTDIR "0"
""")

    def versionString = buildContext.applicationInfo.isEAP() ? "\${VER_BUILD}" : "\${MUI_VERSION_MAJOR}.\${MUI_VERSION_MINOR}"
    def installDirAndShortcutName = customizer.getNameForInstallDirAndDesktopShortcut(buildContext.applicationInfo, buildContext.buildNumber)
    Files.writeString(nsiConfDir.resolve("version.nsi"), """
!define MUI_VERSION_MAJOR "${buildContext.applicationInfo.majorVersion}"
!define MUI_VERSION_MINOR "${buildContext.applicationInfo.minorVersion}"

!define VER_BUILD ${buildContext.buildNumber}
!define INSTALL_DIR_AND_SHORTCUT_NAME "${installDirAndShortcutName}"
!define PRODUCT_WITH_VER "\${MUI_PRODUCT} $versionString"
!define PRODUCT_PATHS_SELECTOR "${buildContext.systemSelector}"
""")
  }
}
