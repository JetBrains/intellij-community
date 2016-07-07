/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import org.codehaus.gant.GantBuilder
import org.jetbrains.intellij.build.BuildContext

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName
/**
 * @author nik
 */
class WinExeInstallerBuilder {
  private final BuildContext buildContext
  private final GantBuilder ant
  private final String jreDirectoryPath

  WinExeInstallerBuilder(BuildContext buildContext, String jreDirectoryPath) {
    this.buildContext = buildContext
    ant = buildContext.ant
    this.jreDirectoryPath = jreDirectoryPath
  }

  void buildInstaller(String winDistPath) {
    if (!SystemInfoRt.isWindows && !SystemInfoRt.isLinux) {
      buildContext.messages.warning("Windows installer can be built only under Windows or Linux")
      return
    }

    String communityHome = buildContext.paths.communityHome
    String outFileName = buildContext.productProperties.baseArtifactName(buildContext.buildNumber)
    buildContext.messages.progress("Building Windows installer $outFileName")
    ant.taskdef(name: "nsis", classname: "com.intellij.internalUtilities.ant.NsiFiles", classpath: "$communityHome/build/lib/NsiFiles.jar")

    def box = "$buildContext.paths.temp/winInstaller"
    ant.mkdir(dir: "$box/bin")
    ant.mkdir(dir: "$box/nsiconf")

    def bundleJre = buildContext.windowsDistributionCustomizer.bundledJreArchitecture != null
    if (bundleJre && jreDirectoryPath == null) {
      buildContext.messages.info("JRE won't be bundled with Windows installer because JRE archive is missing")
      bundleJre = false
    }

    if (bundleJre) {
      ant.copy(todir: "$box/bin") {
        fileset(dir: "$jreDirectoryPath/jre") {
          include(name: "**/msvcr71.dll")
        }
      }
    }

    ant.copy(todir: "$box/nsiconf") {
      fileset(dir: "$communityHome/build/conf/nsis") {
        include(name: "*")
        exclude(name: "strings*")
        exclude(name: "paths*")
        exclude(name: "version*")
      }
    }
    if (SystemInfoRt.isLinux) {
      File ideaNsiPath = new File(box, "nsiconf/idea.nsi")
      ideaNsiPath.text = BuildUtils.replaceAll(ideaNsiPath.text, ["\${IMAGES_LOCATION}\\": "\${IMAGES_LOCATION}/"], "")
    }

    ant.nsis(instfile: "$box/nsiconf/idea_win.nsh", uninstfile: "$box/nsiconf/unidea_win.nsh") {
      [buildContext.paths.distAll, winDistPath].each {
        ant.fileset(dir: it, includes: "**/*") {
          exclude(name: "**/idea.properties")
          exclude(name: "**/*.vmoptions")
        }
      }
      if (bundleJre) {
        ant.fileset(dir: box, includes: "bin/msvcr71.dll")
        ant.fileset(dir: jreDirectoryPath, includes: "jre/**/*")
      }
    }

    prepareConfigurationFiles(box, winDistPath)

    ant.unzip(src: "$communityHome/build/tools/NSIS.zip", dest: box)
    if (SystemInfoRt.isWindows) {
      ant.exec(command: "\"${box}/NSIS/makensis.exe\"" +
                        " /DCOMMUNITY_DIR=\"$communityHome\"" +
                        " /DIPR=\"${buildContext.windowsDistributionCustomizer.associateIpr}\"" +
                        " /DOUT_FILE=\"${outFileName}\"" +
                        " /DOUT_DIR=\"${buildContext.paths.artifacts}\"" +
                        " \"${box}/nsiconf/idea.nsi\"")
    }
    else if (SystemInfoRt.isLinux) {
      ant.exec(command: "makensis" +
                        " '-X!AddPluginDir \"${box}/NSIS/Plugins\"'" +
                        " '-X!AddIncludeDir \"${box}/NSIS/Include\"'" +
                        " -DCOMMUNITY_DIR=\"$communityHome\"" +
                        " -DIPR=\"${buildContext.windowsDistributionCustomizer.associateIpr}\"" +
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

  private void prepareConfigurationFiles(String box, String winDistPath) {
    def productProperties = buildContext.productProperties
    new File(box, "nsiconf/paths.nsi").text = """
!define IMAGES_LOCATION "${toSystemDependentName(buildContext.windowsDistributionCustomizer.installerImagesPath)}"
!define PRODUCT_PROPERTIES_FILE "${toSystemDependentName("$winDistPath/bin/idea.properties")}"
!define PRODUCT_VM_OPTIONS_NAME ${productProperties.baseFileName}*.exe.vmoptions
!define PRODUCT_VM_OPTIONS_FILE "${toSystemDependentName("$winDistPath/bin/")}\${PRODUCT_VM_OPTIONS_NAME}"
"""

    String fullProductName = productProperties.fullNameIncludingEdition(buildContext.applicationInfo)
    def extensionsList = buildContext.windowsDistributionCustomizer.fileAssociations
    def fileAssociations = extensionsList.isEmpty() ? "NoAssociation" : extensionsList.join(",")
    new File(box, "nsiconf/strings.nsi").text = """
!define MANUFACTURER "JetBrains"
!define MUI_PRODUCT  "$fullProductName"
!define PRODUCT_FULL_NAME "$fullProductName"
!define PRODUCT_EXE_FILE "${productProperties.baseFileName}.exe"
!define PRODUCT_EXE_FILE_64 "${productProperties.baseFileName}64.exe"
!define PRODUCT_ICON_FILE "install.ico"
!define PRODUCT_UNINST_ICON_FILE "uninstall.ico"
!define PRODUCT_LOGO_FILE "logo.bmp"
!define PRODUCT_HEADER_FILE "headerlogo.bmp"
!define ASSOCIATION "$fileAssociations"
!define UNINSTALL_WEB_PAGE "${buildContext.windowsDistributionCustomizer.uninstallFeedbackPageUrl(buildContext.applicationInfo) ?: "feedback_web_page"}"

; if SHOULD_SET_DEFAULT_INSTDIR != 0 then default installation directory will be directory where highest-numbered IDEA build has been installed
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