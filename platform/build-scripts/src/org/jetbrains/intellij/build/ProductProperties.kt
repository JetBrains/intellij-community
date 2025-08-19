// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildData.productInfo.CustomProperty
import com.intellij.platform.runtime.product.ProductMode
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationResult
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.InvalidPluginIDProblem
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.base.problems.PropertyNotSpecified
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.Locale
import java.util.function.BiPredicate

/**
 * Describes distribution of an IntelliJ-based IDE. Override this class and build a distribution of your product.
 */
abstract class ProductProperties {
  /**
   * The base name (i.e., a name without the extension and architecture suffix)
   * of launcher files ('bin/xxx64.exe', 'bin/xxx.bat', 'bin/xxx.sh', 'Contents/MacOS/xxx'),
   * usually a short product name in lower case (`"idea"` for IntelliJ IDEA, `"webstorm"` for WebStorm, etc.).
   *
   * **Important**: please make sure that this property and the `//names@script` attribute in the product's `*ApplicationInfo.xml` file
   * have the same value.
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
  var fastInstanceActivation: Boolean = true

  /**
   * An entry point into application's Java code, usually `com.intellij.idea.Main`. 
   * Use [BuildContext.ideMainClassName] if you need to access this value in the build scripts.
   */
  var mainClassName: String = "com.intellij.idea.Main"

  /**
   * Paths to directories containing images specified by 'logo/@url' and 'icon/@ico' attributes in ApplicationInfo.xml file.
   * <br>
   * todo get rid of this and make sure that these resources are located in [applicationInfoModule] instead
   */
  var brandingResourcePaths: List<Path> = emptyList()

  /**
   * Name of the command which runs IDE in 'offline inspections' mode
   * (as returned by `com.intellij.openapi.application.ApplicationStarter.getCommandName`).
   * This property will be also used to name .sh/.bat scripts which execute the command.
   */
  var inspectCommandName: String = "inspect"

  /**
   * Whether to use splash for application start-up.
   */
  var useSplash: Boolean = false

  /**
   * Class-loader that product application should use by default.
   * <p/>
   * `com.intellij.util.lang.PathClassLoader` is used by default as
   * it unifies class-loading logic of an application and allows to avoid double-loading of bootstrap classes.
   */
  var classLoader: String? = "com.intellij.util.lang.PathClassLoader"

  /**
   * If `true`, the Class Data Sharing (CDS) feature will be enabled for the JVM and system.class.loader will be disabled.
   *
   * When enabled, the JVM will generate a shared archive file for the application's classes on the first startup,
   * which can be reused in further launches.
   *
   * The location of the cds file is $SYSTEM/$shortProductName$Version.jsa
   */
  var enableCds: Boolean = false

  /**
   * Additional options to add to a `*.vmoptions` file for all operating systems.
   * A user might override these options.
   * 
   * See also [additionalIdeJvmArguments].
   */
  var additionalVmOptions: PersistentList<String> = persistentListOf()

  /**
   * Additional options to append to the JVM command line by IDE launchers for all operating systems.
   * These options have the highest precedence and cannot be overridden by a user.
   * 
   * See also [additionalVmOptions].
   */
  var additionalIdeJvmArguments: MutableList<String> = mutableListOf()

  /**
   * The specified options will be used instead of/in addition to the default JVM memory options for all operating systems.
   */
  var customJvmMemoryOptions: Map<String, String> = persistentMapOf()

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with an added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1).
   */
  open fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "${appInfo.fullProductName}${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  /**
   * If `true`, Alt+Button1 shortcut will be removed from 'Quick Evaluate Expression' action and assigned to 'Add/Remove Caret' action
   * (instead of Alt+Shift+Button1) in the default keymap.
   */
  var reassignAltClickToMultipleCarets: Boolean = false

  /**
   * Now a file containing information about third-party libraries is bundled and shown inside the IDE.
   * If `true`, HTML & JSON files of third-party libraries will be placed alongside built artifacts.
   */
  var generateLibraryLicensesTable: Boolean = true

  /**
   * List of licenses information about all libraries that can be used in the product modules.
   */
  var allLibraryLicenses: List<LibraryLicense> = CommunityLibraryLicenses.LICENSES_LIST

  /**
   * If `true`, the product's main JAR file will be scrambled using [ProprietaryBuildTools.scrambleTool].
   */
  var scrambleMainJar: Boolean = false

  /**
   * Path to an alternative scramble script which will should be used for a product.
   */
  var alternativeScrambleStubPath: Path? = null

  /**
   * Describes which modules should be included in the product's platform and which plugins should be bundled with the product.
   */
  val productLayout: ProductModulesLayout = ProductModulesLayout()

  /**
   * Base part of URL (ending with `/`) where additional product resources are located.
   * For example, for IntelliJ IDEA it's `https://download.jetbrains.com/idea/`.
   * Note that it can be used only for published builds; there will be no resources for snapshot or nightly builds.
   * It's used by the build scripts for the following:
   * * to inject URL of *.manifest file produced by [RepairUtilityBuilder][org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder] and 
   *   in `repair` executable;
   * * to specify URL of distributions in [SoftwareBillOfMaterials] files.
   */
  var baseDownloadUrl: String? = null

  /**
   * See [SoftwareBillOfMaterials]
   */
  @Suppress("SpellCheckingInspection")
  val sbomOptions: SoftwareBillOfMaterials.Options = SoftwareBillOfMaterials.Options()

  /**
   * If `true`, a cross-platform ZIP archive containing binaries for all OSes will be built.
   * The archive will be generated in [BuildPaths.artifactDir] directory and have ".portable" suffix by default
   * (override [getCrossPlatformZipFileName] to change the file name).
   * Cross-platform distribution is required for [plugins development](https://github.com/JetBrains/gradle-intellij-plugin).
   */
  var buildCrossPlatformDistribution: Boolean = false

  /**
   * Specifies the main module of a frontend variant (JetBrains Client) which distribution should be embedded into the IDE's distribution. 
   * If it's set to a non-null value and [BuildOptions.enableEmbeddedFrontend] is set to `true`, product-modules.xml from the 
   * specified module is used to compute [FrontendModuleFilter]. 
   */
  @ApiStatus.Experimental
  var embeddedFrontendRootModule: String? = null

  /**
   * Specifies a factory function which will be used to generate launchers for the embedded frontend variant (JetBrains Client). 
   */
  @ApiStatus.Experimental
  var embeddedFrontendProperties: (() -> ProductProperties)? = null

  /**
   * Set to the root product module (the one containing the "product-modules.xml" file) to enable using a module-based loader for the product. 
   * [BuildOptions.useModularLoader] will be used to determine whether the produced distribution will actually use this way.
   */
  @ApiStatus.Experimental
  var rootModuleForModularLoader: String? = null

  /**
   * Specifies the mode of this product which will be used to determine which plugin modules should be loaded at runtime by 
   * [the modular loader][com.intellij.platform.bootstrap.ModuleBasedProductLoadingStrategy].
   * This property makes sense only if [rootModuleForModularLoader] is set to a non-null value.
   */
  @Suppress("KDocUnresolvedReference")
  @ApiStatus.Experimental
  var productMode: ProductMode = ProductMode.MONOLITH

  /**
   * Specifies name of cross-platform ZIP archive if `[buildCrossPlatformDistribution]` is set to `true`.
   */
  open fun getCrossPlatformZipFileName(applicationInfo: ApplicationInfoProperties, buildNumber: String): String {
    return getBaseArtifactName(applicationInfo, buildNumber) + ".portable.zip"
  }

  /**
   * A config map for [org.jetbrains.intellij.build.impl.ClassFileChecker], when .class file version verification is necessary.
   */
  var versionCheckerConfig: Map<String, String> = mapOf()

  /**
   * Strings which are forbidden as a part of the resulting class file path. E.g.:
   * "license"
   */
  var forbiddenClassFileSubPaths: PersistentList<String> = persistentListOf()

  /**
   * Exceptions from [forbiddenClassFileSubPaths].
   * Must contain a full string with the offending class, including the .jar path.
   * E.g., "plugins/sample/lib/sample.jar!/com/sample/license/ThirdPartyLicensesDialog.class".
   */
  var forbiddenClassFileSubPathExceptions: PersistentList<String> = persistentListOf()

  /**
   * Paths to files, the content of which should be appended to the 'idea.properties' file.
   */
  var additionalIDEPropertiesFilePaths: List<Path> = emptyList()

  /**
   * Paths to directories, the content of which should be added to the 'license' directory of IDE distribution.
   */
  var additionalDirectoriesWithLicenses: List<Path> = emptyList()

  /**
   * Base file name (without an extension) for product archives and installers (*.exe, *.tar.gz, *.dmg).
   */
  abstract fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String

  /**
   * `<productName>-<buildNumber>` for any (nightly, EAP, or release) build, e.g., 'ideaIC-232.9999'
   *
   * See [getBaseArtifactName].
   */
  fun getBaseArtifactName(context: BuildContext): String {
    return getBaseArtifactName(context.applicationInfo, context.buildNumber)
  }

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
  var buildSourcesArchive: Boolean = false

  /**
   * Determines sources of which modules should be included in the source archive when [buildSourcesArchive] is `true`.
   */
  var includeIntoSourcesArchiveFilter: BiPredicate<JpsModule, BuildContext> = BiPredicate { _, _ -> true }

  /**
   * Specifies how Maven artifacts for IDE modules should be generated; by default, no artifacts are generated.
   */
  val mavenArtifacts: MavenArtifactsProperties = MavenArtifactsProperties()

  /**
   * Specified modules which tests need to be compiled when product is built.
   * todo get rid of this
   */
  var modulesToCompileTests: PersistentList<String> = persistentListOf()

  var runtimeDistribution: JetBrainsRuntimeDistribution = JetBrainsRuntimeDistribution.JCEF

  /**
   * A prefix for names of environment variables used by product distributions
   * to allow users to customize location of the product runtime (`<PRODUCT>_JDK` variable),
   * *.vmoptions file (`<PRODUCT>_VM_OPTIONS`), `idea.properties` file (`<PRODUCT>_PROPERTIES`).
   */
  open fun getEnvironmentVariableBaseName(appInfo: ApplicationInfoProperties): String = appInfo.launcherName.uppercase().replace('-', '_')

  /**
   * Override this method to copy additional files to distributions of all operating systems.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) { }

  /**
   * Override this method if the product has several editions to ensure that their artifacts won't be mixed up.
   * @return the name of a subdirectory under `projectHome/out` where build artifacts will be placed,
   * must be unique among all products built from the same sources.
   */
  open fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = appInfo.fullProductName.lowercase(Locale.ROOT)

  /**
   * Paths to externally built plugins to be included in the IDE.
   * They will be copied into the build, as well as included in the IDE classpath when launching it to build search index, .jar order, etc.
   */
  open fun getAdditionalPluginPaths(context: BuildContext): List<Path> = emptyList()

  /**
   * Override this function to provide additional JVM command line arguments which will be added to launchers along with 
   * [additionalIdeJvmArguments].
   */
  open fun getAdditionalContextDependentIdeJvmArguments(context: BuildContext): List<String> = emptyList()

  /**
   * @return custom properties for [org.jetbrains.intellij.build.impl.productInfo.ProductInfoData].
   */
  @Suppress("KDocUnresolvedReference")
  open fun generateCustomPropertiesForProductInfo(): List<CustomProperty> = emptyList()

  /**
   * If `true`, a distribution contains libraries and launcher script for running IDE in Remote Development mode.
   */
  @ApiStatus.Internal
  open suspend fun addRemoteDevelopmentLibraries(buildContext: BuildContext): Boolean {
    return buildContext.getBundledPluginModules().contains("intellij.remoteDevServer")
  }

  /**
   * Checks whether some necessary conditions specific for the product are met and report errors via [BuildContext.messages] if they aren't.
   */
  @ApiStatus.Experimental
  open fun validateLayout(platformLayout: PlatformLayout, context: BuildContext) {}

  /**
   * Copies additional localization resources to the plugin-generated localization resources directory.
   */
  @ApiStatus.Internal
  open suspend fun copyAdditionalLocalizationResourcesToPlugin(context: BuildContext, lang: String, targetDir: Path) {}

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
   * These generators produce artifacts used by documentation-authoring tools and builds.
   */
  var buildDocAuthoringAssets: Boolean = false

  /**
   * Allows to override product name in ApplicationInfo.xml
   * todo: remove me when platform is ready
   */
  @Deprecated("Do not use it. Needed only for JetBrains Client per-ide customisation + it's temporary")
  @Suppress("DEPRECATION")
  open fun applicationInfoOverride(project: JpsProject): ApplicationInfoOverrides? = null

  /**
   * For a standalone frontend distribution, specifies the platform prefix of its base IDE. In other cases returns `null`.
   */
  @get:ApiStatus.Internal
  open val baseIdePlatformPrefixForFrontend: String? 
    get() = null

  @Deprecated("Do not use it. Needed only for JetBrains Client per-ide customisation + it's temporary")
  data class ApplicationInfoOverrides(
    val fullProductName: String,
    val editionName: String?,
    val motto: String?,
    val eap: String?,
    val majorVersion: String?,
    val minorVersion: String?,
    val microVersion: String?,
    val patchVersion: String?,
    val fullVersionFormat: String?,
    val versionSuffix: String?,
    val majorReleaseDate: String?
  )

  /**
   * Returns IDs of flavors which the current product has. They will be added to the product-info.json file.  
   */
  open fun getProductFlavors(buildContext: BuildContext): List<String> = emptyList()

  /**
   * Properties required for running Qodana application with this product.
   * Should be not null if running Qodana is possible, null otherwise.
   */
  var qodanaProductProperties: QodanaProductProperties? = null

  /**
   * Custom compatible build range for all plugins build together with a product distribution
   */
  var customCompatibleBuildRange: CompatibleBuildRange? = null

  /**
   * Additional validation can be performed here for [BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED] step.
   * Please do not ignore validation failures here, they will fail CI builds anyway.
   * @param pluginId may be null if missing or a plugin descriptor is malformed
   * @return list of plugin validation errors.
   */
  open fun validatePlugin(pluginId: String?, result: PluginCreationResult<IdePlugin>, context: BuildContext): List<PluginProblem> {
    return when (result) {
      is PluginCreationSuccess -> buildList {
        addAll(result.unacceptableWarnings)
        if (result.plugin.pluginVersion == null) {
          add(PropertyNotSpecified("version"))
        }
        val id = result.plugin.pluginId
        if (id == null) {
          add(PropertyNotSpecified("id"))
        }
        else if (!PLUGIN_ID_REGEX.matches(id)) {
          add(InvalidPluginIDProblem(id))
        }
      }
      is PluginCreationFail -> result.errorsAndWarnings
    }
  }

  private companion object {
    /**
     * From https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id:
     * > Please use characters, numbers, and '.'/'-'/'_' symbols only and keep it reasonably short.
     */
    val PLUGIN_ID_REGEX: Regex = "^[\\w.-]+$".toRegex()
  }
}
