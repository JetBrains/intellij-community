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
package org.jetbrains.intellij.build

/**
 * @author nik
 */
class IdeaCommunityProperties extends ProductProperties {
  IdeaCommunityProperties(String home) {
    baseFileName = "idea"
    platformPrefix = "Idea"
    productCode = "IC"
    applicationInfoModule = "community-resources"
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaCE.properties"]
    toolsJarRequired = true
    buildCrossPlatformDistribution = true
  }

  @Override
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    buildContext.ant.copy(todir: targetDirectory) {
      fileset(file: "$buildContext.paths.communityHome/LICENSE.txt")
      fileset(file: "$buildContext.paths.communityHome/NOTICE.txt")
    }
    buildContext.ant.copy(todir: "$targetDirectory/bin") {
      fileset(dir: "$buildContext.paths.communityHome/build/conf/ideaCE/common/bin")
    }
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        icoPath = "$projectHome/platform/icons/src/idea_CE.ico"
        installerImagesPath = "$projectHome/build/conf/ideaCE/win/images"
        fileAssociations = [".java", ".groovy", ".kt"]
      }

      @Override
      String rootDirectoryName(String buildNumber) { "" }

      @Override
      String fullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Community Edition" }

      @Override
      String fullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Community Edition" }

      @Override
      String uninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
        "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/platform/icons/src/icon_CE_128.png"
      }

      @Override
      String rootDirectoryName(String buildNumber) { "idea-IC-$buildNumber" }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new MacDistributionCustomizer() {
      {
        helpId = "IJ"
        urlSchemes = ["idea"]
        associateIpr = true
        enableYourkitAgentInEAP = false
        bundleIdentifier = "com.jetbrains.intellij.ce"
        dmgImagePath = "$projectHome/build/conf/mac/communitydmg.png"
      }

      @Override
      String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        applicationInfo.isEAP ? "IntelliJ IDEA ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} CE EAP.app"
                              : "IntelliJ IDEA CE.app"
      }
    }
  }

  @Override
  String systemSelector(ApplicationInfoProperties applicationInfo) { "IdeaIC${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String baseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "ideaIC-$buildNumber" }
}