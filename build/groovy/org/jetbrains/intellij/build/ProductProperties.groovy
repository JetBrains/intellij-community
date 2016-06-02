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

  abstract def String appInfoFile()

  /**
   * Return {@code true} if tools.jar from JDK must be added to IDE's classpath
   */
  boolean toolsJarRequired = false

  String fullNameIncludingEdition = null

  abstract def String systemSelector(ApplicationInfoProperties applicationInfo)

  String additionalIDEPropertiesFilePath
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

  boolean includeYourkitAgentInEAP = false
  boolean buildUpdater = false
  List<String> excludedPlugins = []

  def customLayout(targetDirectory) {}

  def customWinLayout(targetDirectory) {}

  def customLinLayout(targetDirectory) {}

  def customMacLayout(targetDirectory) {}

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
  boolean includeYourkitAgentInEAP = true
  List<String> extraMacBins = []
  String bundleIdentifier

  /**
   * Path to an image which will be injected into .dmg file
   */
  String dmgImagePath
}

class LinuxProductProperties {
  List<String> extraLinuxBins = []
}