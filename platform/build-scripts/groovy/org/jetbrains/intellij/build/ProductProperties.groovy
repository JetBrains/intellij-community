// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Describes distribution of an IntelliJ-based IDE. Override this class and call {@link BuildTasks#buildProduct} from a build script to build
 * distribution of your product.
 */
@CompileStatic
abstract class ProductProperties {
  /**
   * Base name for script files (*.bat, *.sh, *.exe), usually a shortened product name in lower case (e.g. 'idea' for IntelliJ IDEA, 'datagrip' for DataGrip)
   */
  String baseFileName

  /**
   * @deprecated specify product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file instead (see its schema for details);
   * if you need to get the product code in the build scripts, use {@link ApplicationInfoProperties#productCode} instead;
   * if you need to override product code value from *ApplicationInfo.xml - {@link org.jetbrains.intellij.build.ProductProperties#customProductCode} can be used.
   */
  String productCode

  /**
   * This value overrides specified product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file
   */
  String customProductCode

  /**
   * Value of 'idea.platform.prefix' property. It's also used as prefix for 'ApplicationInfo.xml' product descriptor.
   */
  String platformPrefix

  /**
   * Name of the module containing ${platformPrefix}ApplicationInfo.xml product descriptor in 'idea' package
   */
  String applicationInfoModule

  /**
   * Paths to directories containing images specified by 'logo/@url' and 'icon/@ico' attributes in ApplicationInfo.xml file
   * <br>
   * todo[nik] get rid of this and make sure that these resources are located in {@link #applicationInfoModule} instead
   */
  List<String> brandingResourcePaths = []

  /**
   * Name of the command which runs IDE in 'offline inspections' mode (returned by 'getCommandName' in com.intellij.openapi.application.ApplicationStarter).
   * This property will be also used to name sh/bat scripts which execute this command.
   */
  String inspectCommandName = "inspect"

  /**
   * {@code true} if tools.jar from JDK must be added to IDE classpath
   */
  boolean toolsJarRequired = false

  /**
   * Additional arguments which will be added to JVM command line in IDE launchers for all operating systems
   */
  String additionalIdeJvmArguments = ""

  /**
   * If not null the specified options will be used instead the default memory options in JVM command line (for 64-bit JVM) in IDE launchers
   * for all operating systems.
   */
  String customJvmMemoryOptionsX64 = null

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  String getSystemSelector(ApplicationInfoProperties applicationInfo) {
    "${applicationInfo.productName}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
  }

  /**
   * If {@code true} Alt+Button1 shortcut will be removed from 'Quick Evaluate Expression' action and assigned to 'Add/Remove Caret' action
   * (instead of Alt+Shift+Button1) in the default keymap
   */
  boolean reassignAltClickToMultipleCarets = false

  /**
   * Now file containing information about third-party libraries is bundled and shown inside IDE.
   * If {@code true} html & json files of third-party libraries will be placed alongside with build artifacts.
   */
  boolean generateLibrariesLicensesTable = true

  /**
   * List of licenses information about all libraries which can be used in the product modules
   */
  List<LibraryLicense> allLibraryLicenses = CommunityLibraryLicenses.LICENSES_LIST

  /**
   * If {@code true} the main product JAR file will be scrambled using {@link ProprietaryBuildTools#scrambleTool}
   */
  boolean scrambleMainJar = false

  /**
   * If {@code false} names of private fields won't be scrambled (to avoid problems with serialization). This field is ignored if
   * {@link #scrambleMainJar} is {@code false}.
   */
  boolean scramblePrivateFields = true

  /**
   * Describes which modules should be included into the product's platform and which plugins should be bundled with the product
   */
  ProductModulesLayout productLayout = new ProductModulesLayout()

  /**
   * If {@code true} cross-platform ZIP archive containing binaries for all OS will be built. The archive will be generated in {@link BuildPaths#artifacts}
   * directory and have ".portable" suffix by default, override {@link #getCrossPlatformZipFileName} to change the file name.
   */
  boolean buildCrossPlatformDistribution = false

  /**
   * Specifies name of cross-platform ZIP archive if {@link #buildCrossPlatformDistribution} is set to {@code true}
   */
  String getCrossPlatformZipFileName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    getBaseArtifactName(applicationInfo, buildNumber) + ".portable.zip"
  }

  /**
   * A {@link org.jetbrains.intellij.build.impl.ClassVersionChecker class version checker} config map
   * when .class file version verification inside {@link #buildCrossPlatformDistribution cross-platform distribution} is needed.
   */
  Map<String, String> versionCheckerConfig = null

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
  abstract String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber)

  /**
   * @return instance of the class containing properties specific for Windows distribution or {@code null} if the product doesn't have Windows distribution
   */
  abstract WindowsDistributionCustomizer createWindowsCustomizer(String projectHome)

  /**
   * @return instance of the class containing properties specific for Linux distribution or {@code null} if the product doesn't have Linux distribution
   */
  abstract LinuxDistributionCustomizer createLinuxCustomizer(String projectHome)

  /**
   * @return instance of the class containing properties specific for macOS distribution or {@code null} if the product doesn't have macOS distribution
   */
  abstract MacDistributionCustomizer createMacCustomizer(String projectHome)

  /**
   * If {@code true} a zip archive containing sources of all modules included into the product will be produced.
   */
  boolean buildSourcesArchive = false

  /**
   * Specifies how Maven artifacts for IDE modules should be generated, by default no artifacts are generated.
   */
  MavenArtifactsProperties mavenArtifacts = new MavenArtifactsProperties()

  /**
   * Specified additional modules (not included into the product layout) which need to be compiled when product is built.
   * todo[nik] get rid of this
   */
  List<String> additionalModulesToCompile = []

  /**
   * Specified modules which tests need to be compiled when product is built.
   * todo[nik] get rid of this
   */
  List<String> modulesToCompileTests = []

  /**
   * Specify list of modules on which some modules packaged into the main jar depend, but which aren't included into the main jar. These
   * modules will be added to the classpath to properly scramble the main jar.
   * <strong>This is a temporary hack added specifically for AppCode. It's strongly recommended to either include these modules into the
   * main jar or get rid of such dependencies.</strong> <br>
   * todo[nik] get rid of this
   */
  List<String> additionalModulesRequiredForScrambling = []

  JetBrainsRuntimeDistribution jbrDistribution = JetBrainsRuntimeDistribution.VANILLA

  /**
   * Prefix for names of environment variables used by Windows and Linux distributions to allow users customize location of the product JDK
   * (&lt;PRODUCT&gt;_JDK variable), *.vmoptions file (&lt;PRODUCT&gt;_VM_OPTIONS variable), idea.properties file (&lt;PRODUCT&gt;_PROPERTIES variable)
   */
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) { applicationInfo.upperCaseProductName }

  /**
   * Override this method to copy additional files to distributions of all operating systems.
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
  }

  /**
   * Override this method if the product has several editions to ensure that their artifacts won't be mixed up.
   * @return name of sub-directory under projectHome/out where build artifacts will be placed, must be unique among all products built from
   * the same sources
   */
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { applicationInfo.productName.toLowerCase() }
}