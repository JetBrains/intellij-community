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
  /**
   * Base name for script files (*.bat, *.sh, *.exe), usually a shortened product name in lower case (e.g. 'idea' for IntelliJ IDEA, 'datagrip' for DataGrip)
   */
  String baseFileName

  /**
   * Two-letter product code (e.g. 'IC' for IntelliJ IDEA Community Edition), will be used to produce the full build number
   */
  String productCode

  /**
   * Value of 'idea.platform.prefix' property. It's also used as prefix for 'ApplicationInfo.xml' product descriptor.
   */
  String platformPrefix

  /**
   * Name of the module containing ${platformPrefix}ApplicationInfo.xml product descriptor in 'idea' package
   */
  String applicationInfoModule

  /**
   * Name of the sh/bat script (without extension) which will contain the commands to run IDE in 'offline inspections' mode
   */
  String inspectScriptName = "inspect"

  /**
   * {@code true} if tools.jar from JDK must be added to IDE's classpath
   */
  boolean toolsJarRequired = false

  /**
   * Additional arguments which will be added to JVM command line in IDE launchers for all operating systems
   */
  String additionalIdeJvmArguments = ""

  /**
   * @return name of the product which will be shown in Windows Installer
   */
  String fullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { applicationInfo.productName }

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  abstract String systemSelector(ApplicationInfoProperties applicationInfo)

  /**
   * Paths to properties files the content of which should be appended to idea.properties file
   */
  List<String> additionalIDEPropertiesFilePaths = []

  /**
   * Paths to directories the content of which should be added to 'license' directory of IDE distribution
   */
  List<String> additionalDirectoriesWithLicenses = []

  /**
   * Base file name (without extension) for product archives and installers (*.exe, *.tar.gz, *.dmg)
   */
  abstract String baseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber)

  /**
   * @return instance of the class containing properties specific for Windows distribution or {@code null} if the product doesn't have Windows distribution
   */
  abstract WindowsDistributionCustomizer createWindowsCustomizer(String projectHome)

  /**
   * @return instance of the class containing properties specific for Linux distribution or {@code null} if the product doesn't have Linux distribution
   */
  abstract LinuxDistributionCustomizer createLinuxCustomizer(String projectHome)

  /**
   * @return instance of the class containing properties specific for Mac OS distribution or {@code null} if the product doesn't have Mac OS distribution
   */
  abstract MacDistributionCustomizer createMacCustomizer(String projectHome)

  boolean setPluginAndIDEVersionInPluginXml = true

  /**
   * Path to a directory containing yjpagent*.dll, libyjpagent-linux*.so and libyjpagent.jnilib files, which will be copied to 'bin'
   * directories of Windows, Linux and Mac OS distributions. If {@code null} no agent files will be bundled.
   */
  String yourkitAgentBinariesDirectoryPath = null

  /**
   * If {@code true} YourKit agent will be automatically attached when an EAP build of the product starts. It makes sense only if {@link #yourkitAgentBinariesDirectoryPath} is non-null.
   */
  boolean enableYourkitAgentInEAP = false

  List<String> excludedPlugins = []

  /**
   * Override this method to copy additional files to distributions of all operating systems.
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
  }
}