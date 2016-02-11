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
  String appInfoModulePath

  abstract def String appInfoFile()

  /**
   * @return build number with product code (e.g. IC-142.239 for IDEA Community)
   */
  abstract def String fullBuildNumber()

  abstract def String systemSelector()

  String exe_launcher_properties
  String exe64_launcher_properties
  String platformPrefix = null
  String bundleIdentifier

  abstract def String macAppRoot()

  abstract def String winAppRoot()

  abstract def String linuxAppRoot()

  abstract def String archiveName()

  boolean setPluginAndIDEVersionInPluginXml = true

  String ideJvmArgs = null
  boolean maySkipAndroidPlugin
  String relativeAndroidHome
  String relativeAndroidToolsBaseHome

  boolean includeYourkitAgentInEAP = false
  boolean includeBatchLauncher = true
  boolean buildUpdater = false
  List<String> excludedPlugins = []
  List<String> extraMacBins = []
  List<String> extraLinuxBins = []

  def customLayout(targetDirectory) {}

  def customWinLayout(targetDirectory) {}

  def customLinLayout(targetDirectory) {}

  def customMacLayout(targetDirectory) {}

  String icon128
  String ico
  String icns
}
