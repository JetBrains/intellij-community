// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@CompileStatic
final class WinExeInstallerBuilder {
  private final BuildContext buildContext
  private final AntBuilder ant
  private final WindowsDistributionCustomizer customizer
  private final @Nullable Path jreDir

  WinExeInstallerBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, @Nullable Path jreDir) {
    this.buildContext = buildContext
    this.ant = buildContext.ant
    this.customizer = customizer
    this.jreDir = jreDir
  }

  private void generateInstallationConfigFileForSilentMode() {
    Path targetFilePath = Paths.get(buildContext.paths.artifacts, "silent.config")
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
    customizer.fileAssociations.collect {it.startsWith(".") ? it : ("." + it) }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  String buildInstaller(Path winDistPath, Path additionalDirectoryToInclude, String suffix, boolean jre32BitVersionSupported) {
    if (!SystemInfoRt.isWindows && !SystemInfoRt.isLinux) {
      buildContext.messages.warning("Windows installer can be built only under Windows or Linux")
      return null
    }

    String communityHome = buildContext.paths.communityHome
    String outFileName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + suffix
    buildContext.messages.progress("Building Windows installer $outFileName")

    Path box = buildContext.paths.tempDir.resolve("winInstaller")
    Path nsiConfDir = box.resolve("nsiconf")
    Files.createDirectories(nsiConfDir)

    boolean bundleJre = jreDir != null
    if (!bundleJre) {
      buildContext.messages.info("JRE won't be bundled with Windows installer because JRE archive is missing")
    }

    ant.copy(todir: nsiConfDir.toString()) {
      fileset(dir: "$communityHome/build/conf/nsis")
    }

    generateInstallationConfigFileForSilentMode()

    if (SystemInfoRt.isLinux) {
      Path ideaNsiPath = nsiConfDir.resolve("idea.nsi")
      Files.writeString(ideaNsiPath, BuildUtils.replaceAll(ideaNsiPath.text, ["\${IMAGES_LOCATION}\\": "\${IMAGES_LOCATION}/"], ""))
    }

    try {
      def generator = new NsisFileListGenerator()
      generator.addDirectory(buildContext.paths.distAll)
      generator.addDirectory(winDistPath.toString(), ["**/idea.properties", "**/*.vmoptions"])
      generator.addDirectory(additionalDirectoryToInclude.toString())

      if (bundleJre) {
        generator.addDirectory(jreDir.toString())
      }

      generator.generateInstallerFile(nsiConfDir.resolve("idea_win.nsh").toFile())

      generator.generateUninstallerFile(nsiConfDir.resolve("unidea_win.nsh").toFile())
    }
    catch (IOException e) {
      buildContext.messages.error("Failed to generated list of files for NSIS installer: $e")
    }

    prepareConfigurationFiles(nsiConfDir, winDistPath, jre32BitVersionSupported)
    customizer.customNsiConfigurationFiles.each {
      Path file = Paths.get(it)
      Files.copy(file, nsiConfDir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
    }

    ant.unzip(src: "$communityHome/build/tools/NSIS.zip", dest: box)
    buildContext.messages.block("Running NSIS tool to build .exe installer for Windows") {
      if (SystemInfoRt.isWindows) {
        ant.exec(executable: "${box}/NSIS/makensis.exe") {
          arg(value: "/V2")
          arg(value: "/DCOMMUNITY_DIR=${communityHome}")
          arg(value: "/DIPR=${customizer.associateIpr}")
          arg(value: "/DOUT_DIR=${buildContext.paths.artifacts}")
          arg(value: "/DOUT_FILE=${outFileName}")
          arg(value: "${box}/nsiconf/idea.nsi")
        }
      }
      else {
        ant.chmod(file: "${box}/NSIS/Bin/makensis", perm: "u+x")
        ant.exec(executable: "${box}/NSIS/Bin/makensis") {
          arg(value: "-V2")
          arg(value: "-DCOMMUNITY_DIR=${communityHome}")
          arg(value: "-DIPR=${customizer.associateIpr}")
          arg(value: "-DOUT_DIR=${buildContext.paths.artifacts}")
          arg(value: "-DOUT_FILE=${outFileName}")
          arg(value: "${box}/nsiconf/idea.nsi")
          env(key: "NSISDIR", value: "${box}/NSIS")
        }
      }
    }

    def installerPath = "${buildContext.paths.artifacts}/${outFileName}.exe"
    if (!new File(installerPath).exists()) {
      buildContext.messages.error("Windows installer wasn't created.")
    }

    buildContext.signExeFile(installerPath)
    buildContext.notifyArtifactBuilt(installerPath)
    return installerPath
  }

  private void prepareConfigurationFiles(Path nsiConfDir, Path winDistPath, boolean jre32BitVersionSupported) {
    def productProperties = buildContext.productProperties
    def x64LauncherName = "${productProperties.baseFileName}64.exe"
    def mainExeLauncherName = customizer.include32BitLauncher ? "${productProperties.baseFileName}.exe" : x64LauncherName

    Files.writeString(nsiConfDir.resolve("paths.nsi"), """
!define IMAGES_LOCATION "${FileUtilRt.toSystemDependentName(customizer.installerImagesPath)}"
!define PRODUCT_PROPERTIES_FILE "${FileUtilRt.toSystemDependentName("$winDistPath/bin/idea.properties")}"
!define PRODUCT_VM_OPTIONS_NAME ${productProperties.baseFileName}*.exe.vmoptions
!define PRODUCT_VM_OPTIONS_FILE "${FileUtilRt.toSystemDependentName("$winDistPath/bin/")}\${PRODUCT_VM_OPTIONS_NAME}"
""")

    def extensionsList = getFileAssociations()
    def fileAssociations = extensionsList.isEmpty() ? "NoAssociation" : extensionsList.join(",")
    def linkToX86Jre = customizer.include32BitLauncher ? buildContext.bundledJreManager.x86JreDownloadUrl(OsFamily.WINDOWS) : null
    Files.writeString(nsiConfDir.resolve("strings.nsi"), """
!define MANUFACTURER "${buildContext.applicationInfo.shortCompanyName}"
!define MUI_PRODUCT  "${customizer.getFullNameIncludingEdition(buildContext.applicationInfo)}"
!define PRODUCT_FULL_NAME "${customizer.getFullNameIncludingEditionAndVendor(buildContext.applicationInfo)}"
!define PRODUCT_EXE_FILE "$mainExeLauncherName"
!define PRODUCT_EXE_FILE_64 "$x64LauncherName"
!define PRODUCT_ICON_FILE "install.ico"
!define PRODUCT_UNINST_ICON_FILE "uninstall.ico"
!define PRODUCT_LOGO_FILE "logo.bmp"
!define PRODUCT_HEADER_FILE "headerlogo.bmp"
!define ASSOCIATION "$fileAssociations"
!define UNINSTALL_WEB_PAGE "${customizer.getUninstallFeedbackPageUrl(buildContext.applicationInfo) ?: "feedback_web_page"}"
!define LINK_TO_JRE "$linkToX86Jre"
!define JRE_32BIT_VERSION_SUPPORTED "${jre32BitVersionSupported ? 1 : 0 }"

; if SHOULD_SET_DEFAULT_INSTDIR != 0 then default installation directory will be directory where highest-numbered IDE build has been installed
; set to 1 for release build
!define SHOULD_SET_DEFAULT_INSTDIR "0"
""")

    def versionString = buildContext.applicationInfo.isEAP ? "\${VER_BUILD}" : "\${MUI_VERSION_MAJOR}.\${MUI_VERSION_MINOR}"
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
