// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.productInfo.CustomProperty
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.*
import java.util.function.BiPredicate

/**
 * Describes distribution of an IntelliJ-based IDE. Override this class and build distribution of your product.
 * Refer to e.g. [PyCharmCommunityInstallersBuildTarget]
 */
abstract class ProductProperties {
  /**
   * Base name for script files (*.bat, *.sh, *.exe), usually a shortened product name in lower case (e.g. 'idea' for IntelliJ IDEA, 'datagrip' for DataGrip)
   */
  lateinit var baseFileName: String

  /**
   * @deprecated specify product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file instead (see its schema for details);
   * if you need to get the product code in the build scripts, use {@link ApplicationInfoProperties#productCode} instead;
   * if you need to override product code value from *ApplicationInfo.xml - {@link org.jetbrains.intellij.build.ProductProperties#customProductCode} can be used.
   */
  var productCode: String? = null

  /**
   * This value overrides specified product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file
   */
  var customProductCode: String? = null

  /**
   * Value of 'idea.platform.prefix' property. It's also used as prefix for 'ApplicationInfo.xml' product descriptor.
   */
  var platformPrefix: String? = null

  /**
   * Name of the module containing ${platformPrefix}ApplicationInfo.xml product descriptor in 'idea' package
   */
  lateinit var applicationInfoModule: String

  /**
   * Paths to directories containing images specified by 'logo/@url' and 'icon/@ico' attributes in ApplicationInfo.xml file
   * <br>
   * todo[nik] get rid of this and make sure that these resources are located in {@link #applicationInfoModule} instead
   */
  var brandingResourcePaths: List<Path> = emptyList()

  /**
   * Name of the command which runs IDE in 'offline inspections' mode (returned by 'getCommandName' in com.intellij.openapi.application.ApplicationStarter).
   * This property will be also used to name sh/bat scripts which execute this command.
   */
  var inspectCommandName = "inspect"

  /**
   * {@code true} if tools.jar from JDK must be added to IDE classpath
   */
  var toolsJarRequired = false

  var isAntRequired = false

  /**
   * Whether to use splash for application start-up.
   */
  var useSplash = false

  /**
   * Class-loader that product application should use by default.
   * <p/>
   * `com.intellij.util.lang.PathClassLoader` is used by default as
   * it unifies class-loading logic of an application and allows to avoid double-loading of bootstrap classes.
   */
  var classLoader: String? = "com.intellij.util.lang.PathClassLoader"

  /**
   * Additional arguments which will be added to JVM command line in IDE launchers for all operating systems
   */
  var additionalIdeJvmArguments: MutableList<String> = mutableListOf()

  /**
   * The specified options will be used instead of/in addition to the default JVM memory options for all operating systems.
   */
  var customJvmMemoryOptions: MutableMap<String, String> = mutableMapOf()

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  open fun getSystemSelector(applicationInfo: ApplicationInfoProperties, buildNumber: String): String =
    "${applicationInfo.productName}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"

  /**
   * If {@code true} Alt+Button1 shortcut will be removed from 'Quick Evaluate Expression' action and assigned to 'Add/Remove Caret' action
   * (instead of Alt+Shift+Button1) in the default keymap
   */
  var reassignAltClickToMultipleCarets = false

  /**
   * Now file containing information about third-party libraries is bundled and shown inside IDE.
   * If {@code true} html & json files of third-party libraries will be placed alongside with build artifacts.
   */
  var generateLibrariesLicensesTable = true

  /**
   * List of licenses information about all libraries which can be used in the product modules
   */
  var allLibraryLicenses: MutableList<LibraryLicense> = CommunityLibraryLicenses.LICENSES_LIST.toMutableList()

  /**
   * If {@code true} the main product JAR file will be scrambled using {@link ProprietaryBuildTools#scrambleTool}
   */
  var scrambleMainJar = false

  @ApiStatus.Experimental
  var useProductJar = true

  /**
   * If {@code false} names of private fields won't be scrambled (to avoid problems with serialization). This field is ignored if
   * {@link #scrambleMainJar} is {@code false}.
   */
  var scramblePrivateFields = true

  /**
   * Path to an alternative scramble script which will should be used for a product
   */
  var alternativeScrambleStubPath: String? = null

  /**
   * Describes which modules should be included into the product's platform and which plugins should be bundled with the product
   */
  val productLayout = ProductModulesLayout()

  /**
   * If true cross-platform ZIP archive containing binaries for all OS will be built. The archive will be generated in [BuildPaths.artifactDir]
   * directory and have ".portable" suffix by default, override [getCrossPlatformZipFileName] to change the file name.
   * Cross-platform distribution is required for [plugins development](https://github.com/JetBrains/gradle-intellij-plugin).
   */
  var buildCrossPlatformDistribution = false

  /**
   * Specifies name of cross-platform ZIP archive if {@link #buildCrossPlatformDistribution} is set to {@code true}
   */
  open fun getCrossPlatformZipFileName(applicationInfo: ApplicationInfoProperties, buildNumber: String): String =
    getBaseArtifactName(applicationInfo, buildNumber) + ".portable.zip"

  /**
   * A {@link org.jetbrains.intellij.build.impl.ClassVersionChecker class version checker} config map
   * when .class file version verification inside {@link #buildCrossPlatformDistribution cross-platform distribution} is needed.
   */
  var versionCheckerConfig: Map<String, String>? = null

  /**
   * Paths to properties files the content of which should be appended to idea.properties file
   */
  var additionalIDEPropertiesFilePaths: List<Path> = emptyList()

  /**
   * Paths to directories the content of which should be added to 'license' directory of IDE distribution
   */
  var additionalDirectoriesWithLicenses: List<Path> = emptyList()

  /**
   * Base file name (without extension) for product archives and installers (*.exe, *.tar.gz, *.dmg)
   */
  abstract fun getBaseArtifactName(applicationInfo: ApplicationInfoProperties, buildNumber: String): String

  /**
   * @return instance of the class containing properties specific for Windows distribution or {@code null} if the product doesn't have Windows distribution
   */
  abstract fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer?

  /**
   * @return instance of the class containing properties specific for Linux distribution or {@code null} if the product doesn't have Linux distribution
   */
  abstract fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer?

  /**
   * @return instance of the class containing properties specific for macOS distribution or {@code null} if the product doesn't have macOS distribution
   */
  abstract fun createMacCustomizer(projectHome: String): MacDistributionCustomizer?

  /**
   * If {@code true} a zip archive containing sources of modules included into the product will be produced.
   */
  var buildSourcesArchive = false

  /**
   * Determines sources of which modules should be included into the sources archive if {@link #buildSourcesArchive} is {@code true}
   */
  var includeIntoSourcesArchiveFilter: BiPredicate<JpsModule, BuildContext> = BiPredicate { _, _ -> true }

  /**
   * Specifies how Maven artifacts for IDE modules should be generated, by default no artifacts are generated.
   */
  val mavenArtifacts = MavenArtifactsProperties()

  /**
   * Specified additional modules (not included into the product layout) which need to be compiled when product is built.
   * todo[nik] get rid of this
   */
  var additionalModulesToCompile: MutableList<String> = mutableListOf()

  /**
   * Specified modules which tests need to be compiled when product is built.
   * todo[nik] get rid of this
   */
  var modulesToCompileTests: MutableList<String> = mutableListOf()

  var runtimeDistribution: JetBrainsRuntimeDistribution = JetBrainsRuntimeDistribution.JCEF

  /**
   * Prefix for names of environment variables used by Windows and Linux distributions to allow users customize location of the product JDK
   * (&lt;PRODUCT&gt;_JDK variable), *.vmoptions file (&lt;PRODUCT&gt;_VM_OPTIONS variable), idea.properties file (&lt;PRODUCT&gt;_PROPERTIES variable)
   */
  open fun getEnvironmentVariableBaseName(applicationInfo: ApplicationInfoProperties) = applicationInfo.upperCaseProductName

  /**
   * Override this method to copy additional files to distributions of all operating systems.
   */
  open fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
  }

  /**
   * Override this method if the product has several editions to ensure that their artifacts won't be mixed up.
   * @return name of sub-directory under projectHome/out where build artifacts will be placed, must be unique among all products built from
   * the same sources
   */
  open fun getOutputDirectoryName(applicationInfo: ApplicationInfoProperties) = applicationInfo.productName.lowercase(Locale.ROOT)

  /**
   * Paths to externally built plugins to be included into the IDE. They will be copied into the build, as well as included into
   * the IDE classpath when launching it to build search index, jar order, etc
   */
  open fun getAdditionalPluginPaths(context: BuildContext): List<Path> = emptyList()

  /**
   * @return custom properties for {@link org.jetbrains.intellij.build.impl.productInfo.ProductInfoData}
   */
  open fun generateCustomPropertiesForProductInfo(): List<CustomProperty> = emptyList()

  /**
   * If {@code true} a distribution contains libraries and launcher script for running IDE in Remote Development mode.
   */
  @ApiStatus.Internal
  open fun addRemoteDevelopmentLibraries(): Boolean =
    productLayout.bundledPluginModules.contains("intellij.remoteDevServer")

  /**
   * Build steps which are always skipped for this product. Can be extended via {@link org.jetbrains.intellij.build.BuildOptions#buildStepsToSkip} but not overridden.
   */
  var incompatibleBuildSteps: List<String> = emptyList()

  /**
   * Names of JARs inside IDE_HOME/lib directory which need to be added to the Xbootclasspath to start the IDE
   */
  var xBootClassPathJarNames: List<String> = emptyList()

  /**
   * Customize PRODUCT_CODE-builtinModules.json which contains information about product modules,
   * bundled plugins, and file extensions. builtinModules.json is used to populate marketplace settings
   * for the product.
   * <p>
   * It's particularly useful when you want to limit modules used to calculate compatible plugins on marketplace.
   */
  open fun customizeBuiltinModules(context: BuildContext, builtinModulesFile: Path) {
  }
}
