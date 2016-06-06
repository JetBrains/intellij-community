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
    prefix = "idea"
    platformPrefix = "Idea"
    code = "IC"
    appInfoModule = "community-resources"
    fullNameIncludingEdition = "IntelliJ IDEA Community Edition"
    additionalIDEPropertiesFilePath = "$home/build/conf/ideaCE.properties"
    exe_launcher_properties = "$home/build/conf/ideaCE-launcher.properties"
    exe64_launcher_properties = "$home/build/conf/ideaCE64-launcher.properties"
    maySkipAndroidPlugin = true
    relativeAndroidHome = "android"
    relativeAndroidToolsBaseHome = "android/tools-base"
    toolsJarRequired = true

    icon128 = "$home/platform/icons/src/icon_CE_128.png"
    ico = "$home/platform/icons/src/idea_CE.ico"

    windows.bundleJre = true
    windows.installerImagesPath = "$home/build/conf/ideaCE/win/images"
    windows.fileAssociations = [".java", ".groovy", ".kt"]

    mac.helpId = "IJ"
    mac.urlSchemes = ["idea"]
    mac.includeYourkitAgentInEAP = false
    mac.bundleIdentifier = "com.jetbrains.intellij.ce"
    mac.dmgImagePath = "$home/build/conf/mac/communitydmg.png"
  }

  @Override
  String uninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    return "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
  }

  def String appInfoFile() {
    "${projectBuilder.moduleOutput(findModule("community-resources"))}/idea/IdeaApplicationInfo.xml"
  }

  def String systemSelector(ApplicationInfoProperties applicationInfo) { "IdeaIC$applicationInfo.majorVersion" }

  def String macAppRoot(ApplicationInfoProperties applicationInfo, String buildNumber) {
    applicationInfo.isEAP ? "IntelliJ IDEA ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} CE EAP.app/Contents"
                          : "IntelliJ IDEA CE.app/Contents"
  }

  def String winAppRoot(String buildNumber) { "" }

  def String linuxAppRoot(String buildNumber) { "idea-IC-$buildNumber" }

  def String archiveName(String buildNumber) { "ideaIC-$buildNumber" }
}