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
abstract class MacDistributionCustomizer {
  /**
   * Path to icns file containing 32x32 product icon for Mac OS distribution
   */
  String icnsPath

  /**
   * The minimum version of Mac OS where the product is allowed to be installed
   */
  String minOSXVersion = "10.8"
  String helpId = ""
  String additionalDocTypes = ""
  List<String> urlSchemes = []
  List<String> architectures = ["x86_64"]

  /**
   * If {@code true} *.ipr files will be associated with the product in Info.plist
   */
  boolean associateIpr = false

  /**
   * If {@code true} YourKit agent will be automatically attached when an EAP build of the product starts under Mac OS. This property is
   * taken into account only if {@link ProductProperties#enableYourkitAgentInEAP} is {@code true}.
   */
  boolean enableYourkitAgentInEAP = true

  /**
   * Relative paths to files in Mac OS distribution which should take 'executable' permissions
   */
  List<String> extraExecutables = []

  String bundleIdentifier

  /**
   * Path to an image which will be injected into .dmg file
   */
  String dmgImagePath

  /**
   * Path to a image which will be injected into .dmg file for EAP builds (if {@code null} dmgImagePath will be used)
   */
  String dmgImagePathForEAP = null

  abstract String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber)

  Map<String, String> customIdeaProperties(ApplicationInfoProperties applicationInfo) { [:] }

  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
  }
}
