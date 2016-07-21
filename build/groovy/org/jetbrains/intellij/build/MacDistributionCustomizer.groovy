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
   * Path to icns file containing product icon bundle for Mac OS distribution
   * For full description of icns files see <a href="https://en.wikipedia.org/wiki/Apple_Icon_Image_format">Apple Icon Image Format</a>
   */
  String icnsPath

  /**
   * The minimum version of Mac OS where the product is allowed to be installed
   */
  String minOSXVersion = "10.8"
  /**
   * Help bundle identifier for bundle in <a href="https://developer.apple.com/library/mac/documentation/Carbon/Conceptual/ProvidingUserAssitAppleHelp/authoring_help/authoring_help_book.html">Apple Help Bundle</a> format
   * If there's no help bundled, leave empty
   */
  String helpId = ""
  /**
   * String with declarations of additional file types that should be automatically opened by the application
   * Example:
   * <dict>
   *   <key>CFBundleTypeExtensions</key>
   *   <array>
   *     <string>extension</string>
   *   </array>
   *   <key>CFBundleTypeIconFile</key>
   *   <string>path_to_icons.icns</string>
   *   <key>CFBundleTypeName</key>
   *   <string>File type description</string>
   *   <key>CFBundleTypeRole</key>
   *   <string>Editor</string>
   * </dict>
   */
  String additionalDocTypes = ""
  /**
   * Specify <scheme> here if you want product to be able to open urls like <scheme>://open?file=/some/file/path&line=0
   */
  List<String> urlSchemes = []
  /**
   * CPU architectures app can be launched on, currently only x86_64 is supported
   */
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
