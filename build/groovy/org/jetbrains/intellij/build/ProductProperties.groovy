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
public abstract class ProductProperties {
  String prefix
  String code
  String appInfoModule
  String customInspectScriptName

  /**
   * Return {@code true} if tools.jar from JDK must be added to IDE's classpath
   */
  boolean toolsJarRequired = false

  String fullNameIncludingEdition = null

  abstract def String systemSelector(ApplicationInfoProperties applicationInfo)

  List<String> additionalIDEPropertiesFilePaths = []
  List<String> additionalDirectoriesWithLicenses = []
  String exe_launcher_properties
  String exe64_launcher_properties
  String platformPrefix = null

  abstract def String macAppRoot(ApplicationInfoProperties applicationInfo, String buildNumber)

  abstract def String winAppRoot(String buildNumber)

  abstract def String linuxAppRoot(String buildNumber)

  abstract def String archiveName(String buildNumber)

  String uninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    return null
  }

  boolean setPluginAndIDEVersionInPluginXml = true

  String ideJvmArgs = ""
  boolean maySkipAndroidPlugin
  String relativeAndroidHome
  String relativeAndroidToolsBaseHome

  /**
   * Path to a directory containing yjpagent*.dll, libyjpagent-linux*.so and libyjpagent.jnilib files, which will be copied to 'bin' directories of Windows, Linux and Mac OS X distributions
   */
  String yourkitAgentBinariesDirectoryPath
  boolean enableYourkitAgentInEAP = false
  List<String> excludedPlugins = []

  void customLayout(BuildContext context, String targetDirectory) {}

  void customWinLayout(BuildContext context, String targetDirectory) {}

  void customLinLayout(BuildContext context, String targetDirectory) {}

  void customMacLayout(BuildContext context, String targetDirectory) {}

  String icon128
  String ico
  String icns

  WindowsProductProperties windows = new WindowsProductProperties()
  MacProductProperties mac = new MacProductProperties()
  LinuxProductProperties linux = new LinuxProductProperties()
}

class WindowsProductProperties {
  boolean includeBatchLauncher = true
  boolean bundleJre = true
  boolean buildZipWithBundledOracleJre = false
  boolean associateIpr = true
  /**
   * Path to a directory containing images for installer: logo.bpm, headerlogo.bpm, install.icon, uninstall.ico
   */
  String installerImagesPath
  /**
   * List of file extensions (starting with dot) which need to be associated with the product
   */
  List<String> fileAssociations = []
}

class MacProductProperties {
  String minOSXVersion = "10.8"
  String helpId = ""
  String docTypes = null
  List<String> urlSchemes = []
  List<String> architectures = ["x86_64"]
  boolean enableYourkitAgentInEAP = true
  List<String> extraMacBins = []
  String bundleIdentifier

  /**
   * Path to an image which will be injected into .dmg file
   */
  String dmgImagePath

  /**
   * Path to a image which will be injected into .dmg file for EAP builds (if {@code null} dmgImagePath will be used)
   */
  String dmgImagePathForEAP = null
}

class LinuxProductProperties {
  List<String> extraLinuxBins = []
}