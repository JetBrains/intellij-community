// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.productInfo.CustomProperty
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.*
import java.util.function.BiPredicate

/**
 * Describes distribution of an IntelliJ-based IDE. Override this class and build distribution of your product.
 */
abstract class ProductProperties() {
  /**
   *  The base name for script files (*.bat, *.sh, *.exe), usually a shortened product name in lower case
   * (e.g. 'idea' for IntelliJ IDEA, 'datagrip' for DataGrip).
   */
  abstract val baseFileName: String

  /**
   * Deprecated: specify product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file instead (see its schema for details);
   * if you need to get the product code in the build scripts, use [ApplicationInfoProperties.productCode] instead;
   * if you need to override product code value from *ApplicationInfo.xml - [ProductProperties.customProductCode] can be used.
   */
  @Deprecated("see the doc")
  var productCode: String? = null

  /**
   * This value overrides specified product code in 'number' attribute in 'build' tag in *ApplicationInfo.xml file.
   */
  open val customProductCode: String?
    get() = null

  /**
   * Value of 'idea.platform.prefix' property. It's also used as a prefix for 'ApplicationInfo.xml' product descriptor.
   */
  var platformPrefix: String? = null

  /**
   * Name of the module containing ${platformPrefix}ApplicationInfo.xml product descriptor in 'idea' package.
   */
  lateinit var applicationInfoModule: String

  /**
   * Enables fast activation of a running IDE instance from the launcher
   * (at the moment, it is only implemented in the native Windows one).
   */
  var fastInstanceActivation = true

  /**
   * An entry point into application's Java code, usually [com.intellij.idea.Main].
   */
  var mainClassName = "com.intellij.idea.Main"

  /**
   * Paths to directories containing images specified by 'logo/@url' and 'icon/@ico' attributes in ApplicationInfo.xml file.
   * <br>
   * todo(nik) get rid of this and make sure that these resources are located in [applicationInfoModule] instead
   */
  var brandingResourcePaths: List<Path> = emptyList()

  /**
   * Name of the command which runs IDE in 'offline inspections' mode
   * (returned by [com.intellij.openapi.application.ApplicationStarter.getCommandName]).
   * This property will be also used to name sh/bat scripts which execute this command.
   */
  var inspectCommandName = "inspect"

  /**
   * `true` if tools.jar from JDK must be added to the IDE classpath.
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
   * Additional arguments which will be added to JVM command line in IDE launchers for all operating systems.
   */
  var additionalIdeJvmArguments: MutableList<String> = mutableListOf()

  /**
   * The specified options will be used instead of/in addition to the default JVM memory options for all operating systems.
   */
  var customJvmMemoryOptions: PersistentMap<String, String> = persistentMapOf()

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with an added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1).
   */
  open fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "${appInfo.productName}${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  /**
   * If `true`, Alt+Button1 shortcut will be removed from 'Quick Evaluate Expression' action and assigned to 'Add/Remove Caret' action
   * (instead of Alt+Shift+Button1) in the default keymap.
   */
  var reassignAltClickToMultipleCarets = false

  /**
   * Now file containing information about third-party libraries is bundled and shown inside the IDE.
   * If `true`, HTML & JSON files of third-party libraries will be placed alongside built artifacts.
   */
  var generateLibraryLicensesTable = true

  /**
   * List of licenses information about all libraries which can be used in the product modules.
   */
  var allLibraryLicenses: List<LibraryLicense> = CommunityLibraryLicenses.LICENSES_LIST

  /**
   * If `true`, the product's main JAR file will be scrambled using [ProprietaryBuildTools.scrambleTool].
   */
  var scrambleMainJar = false

  @ApiStatus.Experimental
  var useProductJar = true

  /**
   * If `false`, names of private fields won't be scrambled (to avoid problems with serialization).
   * This field is ignored if [scrambleMainJar] is `false`.
   */
  var scramblePrivateFields = true

  /**
   * Path to an alternative scramble script which will should be used for a product.
   */
  var alternativeScrambleStubPath: Path? = null

  /**
   * Describes which modules should be included in the product's platform and which plugins should be bundled with the product.
   */
  val productLayout = ProductModulesLayout()

  /**
   * If `true`, a cross-platform ZIP archive containing binaries for all OSes will be built.
   * The archive will be generated in [BuildPaths.artifactDir] directory and have ".portable" suffix by default
   * (override [getCrossPlatformZipFileName] to change the file name).
   * Cross-platform distribution is required for [plugins development](https://github.com/JetBrains/gradle-intellij-plugin).
   */
  var buildCrossPlatformDistribution = false

  /**
   * Specifies name of cross-platform ZIP archive if `[buildCrossPlatformDistribution]` is set to `true`.
   */
  open fun getCrossPlatformZipFileName(applicationInfo: ApplicationInfoProperties, buildNumber: String): String =
    getBaseArtifactName(applicationInfo, buildNumber) + ".portable.zip"

  /**
   * A config map for [org.jetbrains.intellij.build.impl.ClassVersionChecker],
   * when .class file version verification inside [buildCrossPlatformDistribution] is needed.
   */
  var versionCheckerConfig: PersistentMap<String, String> = persistentMapOf()

  /**
   * Strings which are forbidden as a part of resulting class file path
   */
  var forbiddenClassFileSubPaths: List<String> = emptyList()

  /**
   * Paths to properties files the content of which should be appended to idea.properties file.
   */
  var additionalIDEPropertiesFilePaths: List<Path> = emptyList()

  /**
   * Paths to directories the content of which should be added to 'license' directory of IDE distribution.
   */
  var additionalDirectoriesWithLicenses: List<Path> = emptyList()

  /**
   * Base file name (without an extension) for product archives and installers (*.exe, *.tar.gz, *.dmg).
   */
  abstract fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String

  /**
   * @return an instance of the class containing properties specific for Windows distribution,
   * or `null` if the product doesn't have Windows distribution.
   */
  abstract fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer?

  /**
   * @return an instance of the class containing properties specific for Linux distribution,
   * or `null` if the product doesn't have Linux distribution.
   */
  abstract fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer?

  /**
   * @return an instance of the class containing properties specific for macOS distribution,
   * or `null` if the product doesn't have macOS distribution.
   */
  abstract fun createMacCustomizer(projectHome: String): MacDistributionCustomizer?

  /**
   * If `true`, a .zip archive containing sources of modules included in the product will be produced.
   * See also [includeIntoSourcesArchiveFilter].
   */
  var buildSourcesArchive = false

  /**
   * Determines sources of which modules should be included in the source archive when [buildSourcesArchive] is `true`.
   */
  var includeIntoSourcesArchiveFilter: BiPredicate<JpsModule, BuildContext> = BiPredicate { _, _ -> true }

  /**
   * Specifies how Maven artifacts for IDE modules should be generated; by default, no artifacts are generated.
   */
  val mavenArtifacts = MavenArtifactsProperties()

  /**
   * Specified additional modules (not included into the product layout) which need to be compiled when product is built.
   * todo(nik) get rid of this
   */
  var additionalModulesToCompile: PersistentList<String> = persistentListOf()

  /**
   * Specified modules which tests need to be compiled when product is built.
   * todo(nik) get rid of this
   */
  var modulesToCompileTests: List<String> = emptyList()

  var runtimeDistribution: JetBrainsRuntimeDistribution = JetBrainsRuntimeDistribution.JCEF

  /**
   * A prefix for names of environment variables used by Windows and Linux distributions
   * to allow users to customize location of the product runtime (`<PRODUCT>_JDK` variable),
   * *.vmoptions file (`<PRODUCT>_VM_OPTIONS`), `idea.properties` file (`<PRODUCT>_PROPERTIES`).
   */
  open fun getEnvironmentVariableBaseName(appInfo: ApplicationInfoProperties) = appInfo.upperCaseProductName

  /**
   * Override this method to copy additional files to distributions of all operating systems.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
  }

  /**
   * Override this method if the product has several editions to ensure that their artifacts won't be mixed up.
   * @return the name of a subdirectory under `projectHome/out` where build artifacts will be placed,
   * must be unique among all products built from the same sources.
   */
  open fun getOutputDirectoryName(appInfo: ApplicationInfoProperties) = appInfo.productName.lowercase(Locale.ROOT)

  /**
   * Paths to externally built plugins to be included in the IDE.
   * They will be copied into the build, as well as included in the IDE classpath when launching it to build search index, .jar order, etc.
   */
  open fun getAdditionalPluginPaths(context: BuildContext): List<Path> = emptyList()

  /**
   * @return custom properties for [org.jetbrains.intellij.build.impl.productInfo.ProductInfoData].
   */
  open fun generateCustomPropertiesForProductInfo(): List<CustomProperty> = emptyList()

  /**
   * If `true`, a distribution contains libraries and launcher script for running IDE in Remote Development mode.
   */
  @ApiStatus.Internal
  open fun addRemoteDevelopmentLibraries(): Boolean =
    productLayout.bundledPluginModules.contains("intellij.remoteDevServer")

  /**
   * Build steps which are always skipped for this product.
   * Can be extended via [org.jetbrains.intellij.build.BuildOptions.buildStepsToSkip], but not overridden.
   */
  var incompatibleBuildSteps: List<String> = emptyList()

  /**
   * Names of JARs inside IDE_HOME/lib directory which need to be added to the Xbootclasspath to start the IDE
   */
  var xBootClassPathJarNames: List<String> = emptyList()

  /**
   * Allows customizing `PRODUCT_CODE-builtinModules.json` file, which contains information about product modules,
   * bundled plugins, and file extensions. The file is used to populate marketplace settings of the product.
   * <p>
   * It's particularly useful when you want to limit modules used to calculate compatible plugins on the marketplace.
   */
  open fun customizeBuiltinModules(context: BuildContext, builtinModulesFile: Path) {}

  /**
   * When set to true, invokes keymap and inspections description generators during build.
   * These generators produce artifacts utilized by documentation
   * authoring tools and builds.
   */
  var buildDocAuthoringAssets: Boolean = false
}
