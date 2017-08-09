/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
class IdeaCommunityProperties extends BaseIdeaProperties {
  IdeaCommunityProperties(String home) {
    baseFileName = "idea"
    platformPrefix = "Idea"
    productCode = "IC"
    applicationInfoModule = "community-resources"
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaCE.properties".toString()]
    toolsJarRequired = true
    buildCrossPlatformDistribution = true

    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + JAVA_API_MODULES
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + JAVA_IMPLEMENTATION_MODULES +
                                                  ["duplicates-analysis", "structuralsearch", "structuralsearch-java", "typeMigration", "platform-main"] -
                                                  ["jps-model-impl", "jps-model-serialization"]
    productLayout.additionalPlatformJars.put("resources.jar", "community-resources")
    productLayout.bundledPluginModules = BUNDLED_PLUGIN_MODULES
    productLayout.mainModules = ["community-main"]
    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      CommunityRepositoryModules.androidPlugin([:]),
      CommunityRepositoryModules.groovyPlugin([])
    ]
    productLayout.classesLoadingOrderFilePath = "$home/build/order.txt"
  }

  @Override
  @CompileDynamic
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    super.copyAdditionalFiles(buildContext, targetDirectory)
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
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Community Edition" }

      @Override
      String getFullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { "IntelliJ IDEA Community Edition" }

      @Override
      String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
        "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }

      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/idea" }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/platform/icons/src/icon_CE_128.png"
        //snapName = "intellij-idea-community"
        //snapDescription =
        //  "The most intelligent Java IDE. Every aspect of IntelliJ IDEA is specifically designed to maximize developer productivity. " +
        //  "Together, powerful static code analysis and ergonomic design make development not only productive but also an enjoyable experience."
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "idea-IC-$buildNumber" }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new MacDistributionCustomizer() {
      {
        icnsPath = "$projectHome/build/conf/ideaCE/mac/images/idea.icns"
        urlSchemes = ["idea"]
        associateIpr = true
        enableYourkitAgentInEAP = false
        bundleIdentifier = "com.jetbrains.intellij.ce"
        dmgImagePath = "$projectHome/build/conf/ideaCE/mac/images/communitydmg.png"
        icnsPathForEAP = "$projectHome/build/conf/ideaCE/mac/images/communityEAP.icns"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        applicationInfo.isEAP ? "IntelliJ IDEA ${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart} CE EAP.app"
                              : "IntelliJ IDEA CE.app"
      }
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) { "IdeaIC${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "ideaIC-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "idea-ce" }
}