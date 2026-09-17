package org.jetbrains.intellij.build.dev

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.JpsCompilationData
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.plugins.PluginAutoPublishList
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.time.Duration

/** Exposes only the lookups declared for one original callback. It never delegates to a platform build context. */
internal class DevPluginLayoutPreparationContext(
  private val preparation: DevPluginPreparationContext,
  private val binding: DevPluginLayoutPatcherInputs,
) : BuildContext {
  override val outputProvider: ModuleOutputProvider = object : ModuleOutputProvider {
    override val useTestCompilationOutput: Boolean get() = unavailable("test compilation outputs")
    override fun getAllModules(): List<JpsModule> = unavailable("all modules")
    override fun findModule(name: String): JpsModule = unavailable("module '$name'")
    override fun findRequiredModule(name: String): JpsModule = unavailable("module '$name'")
    override fun getModuleImlFile(module: JpsModule): Path = unavailable("module files")
    override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = unavailable("module outputs")
    override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray = unavailable("module outputs")
    override fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray {
      return unavailable("searching module outputs")
    }

    override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
      val library = binding.libraries.singleOrNull { it.name == libraryName && it.moduleName == moduleLibraryModuleName }
        ?: unavailable("undeclared library '$libraryName' of '$moduleLibraryModuleName'")
      return library.roots.map(preparation::inputPath)
    }
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean): Path? {
    require(!forTests) { "Original layout preparation does not expose test sources" }
    val source = binding.sources.singleOrNull { it.moduleName == moduleName && it.path == relativePath }
      ?: unavailable("undeclared source '$moduleName/$relativePath'")
    return source.input?.let(preparation::inputPath)
  }

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean): Path? {
    return findFileInModuleSources(module.name, relativePath, forTests)
  }

  override val lifetime: BuildLifetime get() = unavailable("lifetime")
  override val productProperties: ProductProperties get() = unavailable("product properties")
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer get() = unavailable("Windows customizer")
  override val macDistributionCustomizer: MacDistributionCustomizer get() = unavailable("macOS customizer")
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer get() = unavailable("Linux customizer")
  override val proprietaryBuildTools: ProprietaryBuildTools get() = unavailable("proprietary tools")
  override val applicationInfo: ApplicationInfoProperties get() = unavailable("application info")
  override val buildNumber: String get() = unavailable("build number")
  override val pluginBuildNumber: String get() = unavailable("plugin build number")
  override val fullBuildNumber: String get() = unavailable("full build number")
  override val systemSelector: String get() = unavailable("system selector")
  override var bootClassPathJarNames: List<String>
    get() = unavailable("boot classpath")
    set(value) { unavailable("boot classpath") }
  override val ideMainClassName: String get() = unavailable("main class")
  override val useModularLoader: Boolean get() = unavailable("modular loader")
  override val generateRuntimeModuleRepository: Boolean get() = unavailable("module repository")
  override val appInfoXml: String get() = unavailable("application descriptor")
  override val nonBundledPlugins: Path get() = unavailable("non-bundled plugins")
  override val nonBundledPluginsToBePublished: Path get() = unavailable("published plugins")
  override val isEmbeddedFrontendEnabled: Boolean get() = unavailable("embedded frontend")
  override val pluginAutoPublishList: PluginAutoPublishList get() = unavailable("plugin publish list")
  override val isNightlyBuild: Boolean get() = unavailable("nightly build")
  override val options: BuildOptions get() = unavailable("build options")
  override val messages: BuildMessages get() = unavailable("build messages")
  override val paths: BuildPaths get() = unavailable("build paths")
  override val project: JpsProject get() = unavailable("project")
  override val projectModel: JpsModel get() = unavailable("project model")
  override val dependenciesProperties: DependenciesProperties get() = unavailable("dependency properties")
  override val bundledRuntime: BundledRuntime get() = unavailable("bundled runtime")
  override val compilationData: JpsCompilationData get() = unavailable("compilation data")
  override val stableJavaExecutable: Path get() = unavailable("Java executable")
  override val classesOutputDirectory: Path get() = unavailable("classes output directory")
  override fun getStableJdkHome(): Path = unavailable("JDK home")
  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): Collection<Path> = unavailable("module classpath")
  override fun notifyArtifactBuilt(artifactPath: Path): Unit = unavailable("artifact notification")
  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths, lifetime: BuildLifetime?): CompilationContext = unavailable("context copy")
  override fun prepareForBuild(): Unit = unavailable("build preparation")
  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?): Unit = unavailable("compilation")
  override fun withCompilationLock(block: () -> Unit): Unit = unavailable("compilation lock")
  override fun addExtraExecutablePattern(os: OsFamily, pattern: String): Unit = unavailable("executable patterns")
  override fun getExtraExecutablePattern(os: OsFamily): List<String> = unavailable("executable patterns")
  override fun getBundledPluginModules(): List<String> = unavailable("bundled modules")
  override fun builtinModules(): BuiltinModulesFileData = unavailable("built-in modules")
  override fun addDistFile(file: DistFile): Unit = unavailable("distribution files")
  override fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?, libcImpl: LibcImpl?): Collection<DistFile> = unavailable("distribution files")
  override fun patchInspectScript(path: Path): Unit = unavailable("inspect script")
  override fun getAdditionalJvmArguments(os: OsFamily, arch: JvmArchitecture, isScript: Boolean, isPortableDist: Boolean, isQodana: Boolean): List<String> {
    return unavailable("JVM arguments")
  }
  override fun findApplicationInfoModule(): JpsModule = unavailable("application info module")
  override fun getFrontendModuleFilter(): FrontendModuleFilter = unavailable("frontend module filter")
  override fun getEmbeddedFrontendProductContext(): BuildContext = unavailable("frontend context")
  override fun getLayoutOfAdditionalFrontendOnlyPlugins(): List<PluginBuildResult> = unavailable("frontend plugins")
  override fun getContentModuleFilter(): ContentModuleFilter = unavailable("content module filter")
  override fun shouldBuildDistributions(): Boolean = unavailable("build distributions")
  override fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean = unavailable("build distributions")
  override fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path, prepareForBuild: Boolean): BuildContext {
    return unavailable("product context")
  }
  override fun reportDistributionBuildNumber(): Unit = unavailable("distribution build number")
  override fun cleanupJarCache(): Unit = unavailable("jar cache")
  override fun createProductRunner(additionalPluginModules: List<String>): IntellijProductRunner = unavailable("product runner")
  override fun runProcess(args: List<String>, workingDir: Path?, timeout: Duration, additionalEnvVariables: Map<String, String>, attachStdOutToException: Boolean): Unit {
    unavailable("process execution")
  }
  override fun distributionState(): DistributionBuilderState = unavailable("distribution state")

  private fun unavailable(operation: String): Nothing = error("Original layout preparation cannot access $operation; declare a supported dependency")
}
