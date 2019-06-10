// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName

/**
 * @author nik
 */
class WinExeInstallerBuilder {
  private final BuildContext buildContext
  private final AntBuilder ant
  private final WindowsDistributionCustomizer customizer
  private final @Nullable String jreDirectoryPath

  WinExeInstallerBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, @Nullable String jreDirectoryPath) {
    this.buildContext = buildContext
    this.ant = buildContext.ant
    this.customizer = customizer
    this.jreDirectoryPath = jreDirectoryPath
  }

  private void generateInstallationConfigFileForSilentMode() {
    def targetFilePath = "${buildContext.paths.artifacts}/silent.config"
    if (!new File(targetFilePath).exists()) {
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

      buildContext.ant.copy(file: "$silentConfigTemplate", tofile: targetFilePath)
      File silentConfigFile = new File(targetFilePath)
      def extensionsList = getFileAssociations()
      String associations = "\n\n; List of associations. To create an association change value to 1.\n"
      if (!extensionsList.isEmpty()) {
        associations += extensionsList.collect { "$it=0\n" }.join("")
      }
      else {
        associations = "\n\n; There are no associations for the product.\n"
      }
      silentConfigFile.append(associations)
    }
  }

  /**
   * Returns list of file extensions with leading dot added
   */
  private List<String> getFileAssociations() {
    customizer.fileAssociations.collect { !it.startsWith(".") ? ".$it" : it}
  }

  void buildInstaller(String winDistPath, String additionalDirectoryToInclude, String suffix, boolean jre32BitVersionSupported) {

    if (!SystemInfo.isWindows && !SystemInfo.isLinux) {
      buildContext.messages.warning("Windows installer can be built only under Windows or Linux")
      return
    }

    String communityHome = buildContext.paths.communityHome
    String outFileName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + suffix
    buildContext.messages.progress("Building Windows installer $outFileName")

    def box = "$buildContext.paths.temp/winInstaller"
    ant.mkdir(dir: "$box/bin")
    ant.mkdir(dir: "$box/nsiconf")

    def bundleJre = customizer.bundledJreArchitecture != null
    if (bundleJre && jreDirectoryPath == null) {
      buildContext.messages.info("JRE won't be bundled with Windows installer because JRE archive is missing")
      bundleJre = false
    }

    ant.copy(todir: "$box/nsiconf") {
      fileset(dir: "$communityHome/build/conf/nsis") {
        exclude(name: "version*")
      }
    }

    generateInstallationConfigFileForSilentMode()

    if (SystemInfo.isLinux) {
      File ideaNsiPath = new File(box, "nsiconf/idea.nsi")
      ideaNsiPath.text = BuildUtils.replaceAll(ideaNsiPath.text, ["\${IMAGES_LOCATION}\\": "\${IMAGES_LOCATION}/"], "")
    }

    try {
      def generator = new NsisFileListGenerator()
      generator.addDirectory(buildContext.paths.distAll)
      generator.addDirectory(winDistPath, ["**/idea.properties", "**/*.vmoptions"])
      generator.addDirectory(additionalDirectoryToInclude)

      if (bundleJre) {
        generator.addDirectory(jreDirectoryPath)
      }

      generator.generateInstallerFile(new File(box, "nsiconf/idea_win.nsh"))

      if (buildContext.bundledJreManager.doBundleSecondJre()) {
        String jre32Dir = buildContext.bundledJreManager.extractSecondBundledJreForWin(JvmArchitecture.x32)
        if (jre32Dir != null) {
          generator.addDirectory(jre32Dir)
        }
      }

      generator.generateUninstallerFile(new File(box, "nsiconf/unidea_win.nsh"))
    }
    catch (IOException e) {
      buildContext.messages.error("Failed to generated list of files for NSIS installer: $e")
    }

    prepareConfigurationFiles(box, winDistPath, jre32BitVersionSupported)
    customizer.customNsiConfigurationFiles.each {
      ant.copy(file: it, todir: "$box/nsiconf", overwrite: "true")
    }

    ant.unzip(src: "$communityHome/build/tools/NSIS.zip", dest: box)
    buildContext.messages.progress("Running NSIS tool to build .exe installer for Windows")
    if (SystemInfo.isWindows) {
      ant.exec(command: "\"${box}/NSIS/makensis.exe\"" +
                        " /DCOMMUNITY_DIR=\"$communityHome\"" +
                        " /DIPR=\"${customizer.associateIpr}\"" +
                        " /DOUT_FILE=\"${outFileName}\"" +
                        " /DOUT_DIR=\"${buildContext.paths.artifacts}\"" +
                        " \"${box}/nsiconf/idea.nsi\"")
    }
    else if (SystemInfo.isLinux) {
      String installerToolsDir = "$box/installer"
      String installScriptPath = "$installerToolsDir/install_nsis3.sh"
      buildContext.ant.copy(file: "$communityHome/build/conf/install_nsis3.sh", tofile: installScriptPath)
      buildContext.ant.copy(todir: "$installerToolsDir") {
        fileset(dir: "${buildContext.paths.communityHome}/build/tools") {
          include(name: "nsis*.*")
          include(name: "scons*.*")
        }
      }

      buildContext.ant.fixcrlf(file: installScriptPath, eol: "unix")
      ant.exec(command: "chmod u+x \"$installScriptPath\"")
      ant.exec(command: "\"$installScriptPath\" \"$installerToolsDir\"")
      ant.exec(command: "\"${installerToolsDir}/nsis-3.02.1/bin/makensis\"" +
                        " '-X!AddPluginDir \"${box}/NSIS/Plugins/x86-unicode\"'" +
                        " '-X!AddIncludeDir \"${box}/NSIS/Include\"'" +
                        " -DNSIS_DIR=\"${box}/NSIS\"" +
                        " -DCOMMUNITY_DIR=\"$communityHome\"" +
                        " -DIPR=\"${customizer.associateIpr}\"" +
                        " -DOUT_FILE=\"${outFileName}\"" +
                        " -DOUT_DIR=\"${buildContext.paths.artifacts}\"" +
                        " \"${box}/nsiconf/idea.nsi\"")
    }

    def installerPath = "${buildContext.paths.artifacts}/${outFileName}.exe"
    if (!new File(installerPath).exists()) {
      buildContext.messages.error("Windows installer wasn't created.")
    }

    buildContext.signExeFile(installerPath)
    buildContext.notifyArtifactBuilt(installerPath)
  }

  private void prepareConfigurationFiles(String box, String winDistPath, boolean jre32BitVersionSupported) {
    def productProperties = buildContext.productProperties
    def x64LauncherName = "${productProperties.baseFileName}64.exe"
    def mainExeLauncherName = customizer.include32BitLauncher ? "${productProperties.baseFileName}.exe" : x64LauncherName

    new File(box, "nsiconf/paths.nsi").text = """
!define IMAGES_LOCATION "${toSystemDependentName(customizer.installerImagesPath)}"
!define PRODUCT_PROPERTIES_FILE "${toSystemDependentName("$winDistPath/bin/idea.properties")}"
!define PRODUCT_VM_OPTIONS_NAME ${productProperties.baseFileName}*.exe.vmoptions
!define PRODUCT_VM_OPTIONS_FILE "${toSystemDependentName("$winDistPath/bin/")}\${PRODUCT_VM_OPTIONS_NAME}"
"""

    def extensionsList = getFileAssociations()
    def fileAssociations = extensionsList.isEmpty() ? "NoAssociation" : extensionsList.join(",")
    def linkToJre = customizer.getBaseDownloadUrlForJre() != null ?
                    "${customizer.getBaseDownloadUrlForJre()}/${buildContext.bundledJreManager.archiveNameJre(buildContext)}" : null
    new File(box, "nsiconf/strings.nsi").text = """
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
!define LINK_TO_JRE "$linkToJre"
!define JRE_32BIT_VERSION_SUPPORTED "${jre32BitVersionSupported ? 1 : 0 }"

; if SHOULD_SET_DEFAULT_INSTDIR != 0 then default installation directory will be directory where highest-numbered IDE build has been installed
; set to 1 for release build
!define SHOULD_SET_DEFAULT_INSTDIR "0"
"""

    def versionString = buildContext.applicationInfo.isEAP ? "\${VER_BUILD}" : "\${MUI_VERSION_MAJOR}.\${MUI_VERSION_MINOR}"
    new File(box, "nsiconf/version.nsi").text = """
!define MUI_VERSION_MAJOR "${buildContext.applicationInfo.majorVersion}"
!define MUI_VERSION_MINOR "${buildContext.applicationInfo.minorVersion}"

!define VER_BUILD ${buildContext.buildNumber}

!define PRODUCT_WITH_VER "\${MUI_PRODUCT} $versionString"
!define PRODUCT_FULL_NAME_WITH_VER "\${PRODUCT_FULL_NAME} $versionString"
!define PRODUCT_PATHS_SELECTOR "${buildContext.systemSelector}"
!define PRODUCT_SETTINGS_DIR ".\${PRODUCT_PATHS_SELECTOR}"
"""
  }
}