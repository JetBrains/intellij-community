package org.jetbrains.intellij.build
/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class IdeaEduProperties extends IdeaCommunityProperties {
  private final String dependenciesPath

  IdeaEduProperties(String home) {
    super(home)
    productCode = "IE"
    dependenciesPath = "$home/edu/dependencies"
  }

  @Override
  @CompileDynamic
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    super.copyAdditionalFiles(buildContext, targetDirectory)

    EduUtils.copyEduToolsPlugin(dependenciesPath, buildContext, targetDirectory)
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    WindowsDistributionCustomizer base = super.createWindowsCustomizer(projectHome)

    return new WindowsDistributionCustomizer() {
      {
        icoPath = base.icoPath
        icoPathForEAP = base.icoPathForEAP
        installerImagesPath = base.installerImagesPath
        fileAssociations = base.fileAssociations
      }

      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Educational Edition" }

      @Override
      String getFullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Educational Edition" }

      @Override
      String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
        "https://www.jetbrains.com/idea/uninstall/?edition=IE-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }

      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/idea" }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    LinuxDistributionCustomizer base = super.createLinuxCustomizer(projectHome)

    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = base.iconPngPath
        iconPngPathForEAP = base.iconPngPathForEAP
        snapName = "intellij-idea-educational"
        snapDescription =
          "IDEA Edu combines interactive learning with a powerful real-world professional development tool to provide " +
          "a platform for the most effective learning and teaching experience."
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "idea-IE-$buildNumber" }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    MacDistributionCustomizer base = super.createMacCustomizer(projectHome)

    return new MacDistributionCustomizer() {
      {
        icnsPath = base.icnsPath
        urlSchemes = base.urlSchemes
        associateIpr = base.associateIpr
        fileAssociations = base.fileAssociations
        enableYourkitAgentInEAP = base.enableYourkitAgentInEAP
        bundleIdentifier = "com.jetbrains.intellij.ie"
        dmgImagePath = base.dmgImagePath
        icnsPathForEAP = base.icnsPathForEAP
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        applicationInfo.isEAP ? "IntelliJ IDEA ${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart} EDU EAP.app" :
        "IntelliJ IDEA EDU.app"
      }
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) {
    "IdeaIE${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "ideaIE-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "idea-edu" }
}