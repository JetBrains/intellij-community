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

import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
abstract class MacDistributionCustomizer {
  /**
   * Path to icns file containing product icon bundle for macOS distribution
   * For full description of icns files see <a href="https://en.wikipedia.org/wiki/Apple_Icon_Image_format">Apple Icon Image Format</a>
   */
  String icnsPath

  /**
   * Path to icns file for EAP builds (if {@code null} {@link #icnsPath} will be used)
   */
  String icnsPathForEAP = null

  /**
   * An unique identifier string that specifies the app type of the bundle. The string should be in reverse DNS format using only the Roman alphabet in upper and lower case (A-Z, a-z), the dot ("."), and the hyphen ("-")
   * See <a href="https://developer.apple.com/library/ios/documentation/General/Reference/InfoPlistKeyReference/Articles/CoreFoundationKeys.html#//apple_ref/doc/uid/20001431-102070">CFBundleIdentifier</a> for details
   */
  String bundleIdentifier

  /**
   * Path to an image which will be injected into .dmg file
   */
  String dmgImagePath

  /**
   * The minimum version of macOS where the product is allowed to be installed
   */
  String minOSXVersion = "10.8"

  /**
   * String with declarations of additional file types that should be automatically opened by the application.
   * Example:
   * <pre>
   * &lt;dict&gt;
   *   &lt;key&gt;CFBundleTypeExtensions&lt;/key&gt;
   *   &lt;array&gt;
   *     &lt;string&gt;extension&lt;/string&gt;
   *   &lt;/array&gt;
   *   &lt;key&gt;CFBundleTypeIconFile&lt;/key&gt;
   *   &lt;string&gt;path_to_icons.icns&lt;/string&gt;
   *   &lt;key&gt;CFBundleTypeName&lt;/key&gt;
   *   &lt;string&gt;File type description&lt;/string&gt;
   *   &lt;key&gt;CFBundleTypeRole&lt;/key&gt;
   *   &lt;string&gt;Editor&lt;/string&gt;
   * &lt;/dict&gt;
   * </pre>
   */
  String additionalDocTypes = ""

  /**
   * Specify &lt;scheme&gt; here if you want product to be able to open urls like <scheme>://open?file=/some/file/path&line=0
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
   * If {@code true} YourKit agent will be automatically attached when an EAP build of the product starts under macOS. This property is
   * taken into account only if {@link ProductProperties#enableYourkitAgentInEAP} is {@code true}.
   */
  boolean enableYourkitAgentInEAP = true

  /**
   * Relative paths to files in macOS distribution which should take 'executable' permissions
   */
  List<String> extraExecutables = []

  /**
   * Relative paths to files in macOS distribution which should be signed
   */
  List<String> binariesToSign = []

  /**
   * Path to a image which will be injected into .dmg file for EAP builds (if {@code null} dmgImagePath will be used)
   */
  String dmgImagePathForEAP = null

  /**
   * Application bundle name: &lt;name&gt;.app. Current convention is to have ProductName.app for release and ProductName Version EAP.app.
   * @param applicationInfo application info that can be used to check for EAP and building version
   * @param buildNumber current build number
   * @return application bundle directory name
   */
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    String suffix = applicationInfo.isEAP ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart} EAP" : ""
    "$applicationInfo.productName${suffix}.app"
  }

  /**
   * Custom properties to be added to the properties file. They will be used for launched product, e.g. you can add additional logging in EAP builds
   * @param applicationInfo application info that can be used to check for EAP and building version
   * @return map propertyName-&gt;propertyValue
   */
  Map<String, String> getCustomIdeaProperties(ApplicationInfoProperties applicationInfo) { [:] }

  /**
   * Help bundle identifier for bundle in <a href="https://developer.apple.com/library/mac/documentation/Carbon/Conceptual/ProvidingUserAssitAppleHelp/authoring_help/authoring_help_book.html">Apple Help Bundle</a> format.
   * If this field has non-null value, {@link #getPathToHelpZip} must be overriden to specify path to archive with help files.
   */
  String helpId = null

  /**
   * Override this method if you need to bundle help with macOS distribution of the product.
   * @return path to zip archive containing directory "{@link #helpId}.help" with bundled help files inside.
   */
  String getPathToHelpZip(BuildContext context) {
    null
  }

  /**
   * Additional files to be copied to the distribution, e.g. help bundle or debugger binaries
   * @param context build context that contains information about build directories, product properties and application info
   * @param targetDirectory application bundle directory
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
  }
}
