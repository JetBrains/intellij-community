// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.PluginCompatibilityUtils.checkBuildNumberCompatibility
import com.intellij.ide.plugins.PluginUtils.findEnabledOrInstalledPlugin
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.lang.ref.WeakReference
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

private const val PLATFORM_ALIAS_DEPENDENCY_PREFIX = "com.intellij.module"

internal val QODANA_PLUGINS_THIRD_PARTY_ACCEPT = System.getProperty("idea.qodana.thirdpartyplugins.accept").toBoolean()
internal val FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT = System.getProperty("fleet.backend.third-party.plugins.accept").toBoolean()

/**
 * See [Plugin Model](https://youtrack.jetbrains.com/articles/IJPL-A-31/Plugin-Model) documentation.
 *
 * @implNote Prefer to use only JDK classes. Any post-start-up functionality should be placed in [PluginManager] class.
 * @see PluginDetailsService for information about plugins for applied functionality
 */
object PluginManagerCore {
  const val META_INF: String = "META-INF/"
  const val CORE_PLUGIN_ID: String = "com.intellij"
  const val PLUGIN_XML: String = "plugin.xml"
  const val PLUGIN_XML_PATH: String = META_INF + PLUGIN_XML
  const val VENDOR_JETBRAINS: String = "JetBrains"
  const val VENDOR_JETBRAINS_SRO: String = "JetBrains s.r.o."
  @ApiStatus.ScheduledForRemoval // drop after 26.3 release
  @Deprecated("replace with literal at use site")
  const val DISABLE: String = "disable"
  @ApiStatus.ScheduledForRemoval // drop after 26.3 release
  @Deprecated("replace with literal at use site")
  const val ENABLE: String = "enable"
  @ApiStatus.ScheduledForRemoval // drop after 26.3 release
  @Deprecated("replace with literal at use site")
  const val EDIT: String = "edit"

  @JvmField val CORE_ID: PluginId = PluginId.getId(CORE_PLUGIN_ID)
  @JvmField val JAVA_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.java")

  @ApiStatus.Internal
  @JvmField val JAVA_PLUGIN_ALIAS_ID: PluginId = PluginId.getId("com.intellij.modules.java")
  @ApiStatus.Internal
  @JvmField val ALL_MODULES_MARKER: PluginId = PluginId.getId("com.intellij.modules.all")
  @ApiStatus.Internal
  @JvmField val SPECIAL_IDEA_PLUGIN_ID: PluginId = PluginId.getId("IDEA CORE")
  @ApiStatus.Internal
  @JvmField val ULTIMATE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.modules.ultimate")
  @ApiStatus.Internal
  @JvmField val MARKETPLACE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.marketplace")

  @Volatile
  @VisibleForTesting
  @ApiStatus.Internal
  @JvmField var isIgnoreCompatibility: Boolean = System.getProperty("idea.ignore.plugin.compatibility").toBoolean()

  /** Use [com.intellij.openapi.application.Application.isUnitTestMode] instead */
  @Volatile
  @VisibleForTesting
  @ApiStatus.Internal
  @JvmField var isUnitTestMode: Boolean = System.getProperty("idea.is.unit.test").toBoolean()

  @ApiStatus.Internal
  class PluginsMutableState {
    @Volatile
    var nullablePluginSet: PluginSet? = null

    @Volatile
    var initFuture: Deferred<PluginSet>? = null
  }

  private var isRunningFromSources: Boolean? = null
  private var ourBuildNumber: BuildNumber? = null

  @ApiStatus.Internal
  var pluginsStateSupplier: (() -> PluginsMutableState)? = null

  private val pluginsStateLazy = lazy { PluginsMutableState() }
  private val pluginsState: PluginsMutableState
    get() = pluginsStateSupplier?.invoke() ?: pluginsStateLazy.value


  private class ShadowedBundledPluginsCache(
    val pluginSetRef: WeakReference<PluginSet>,
    val pluginIds: Set<PluginId>,
  )

  /**
   * Bundled plugins that were updated.
   * When we update a bundled plugin, it becomes non-bundled, so it is more challenging for analytics to use that data.
   */
  private var shadowedBundledPluginsCache: ShadowedBundledPluginsCache? = null // TODO consider moving this out of here somewhere to PluginManager

  /**
   * Returns `true` if the IDE is running from source code **without using 'dev build'**.
   * In this mode a single classloader is used to load all modules and plugins, and the actual layout of class-files and resources differs from the real production layout.
   * The IDE can be started in this mode from source code using a run configuration without the 'dev build' suffix. Also, tests are often started in this mode.
   *
   * See also [AppMode.isRunningFromDevBuild].
   */
  @JvmStatic
  fun isRunningFromSources(): Boolean {
    var result = isRunningFromSources
    if (result == null) {
      // MPS is always loading platform classes from jars even though there is a project directory present
      result = !PlatformUtils.isMPS() && Files.isDirectory(PathManager.getHomeDir().resolve(Project.DIRECTORY_STORE_FOLDER))
      isRunningFromSources = result
    }
    return result
  }

  /**
   * Returns a list of all available plugin descriptors (bundled and custom, including disabled ones).
   * Use [loadedPlugins] if you need to get loaded plugins only.
   *
   * Do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by PluginClassLoader.
   */
  @get:ApiStatus.Internal
  @JvmStatic
  val plugins: Array<IdeaPluginDescriptor>
    get() = getPluginSet().allPlugins.toTypedArray<IdeaPluginDescriptor>()

  @ApiStatus.Internal
  @JvmStatic
  fun getPluginSet(): PluginSet = pluginsState.nullablePluginSet!!

  @ApiStatus.Internal
  @JvmStatic
  fun getPluginSetOrNull(): PluginSet? = pluginsState.nullablePluginSet

  /**
   * Returns descriptors of plugins which are successfully loaded into the IDE.
   * The result is sorted in a way that if each plugin comes after the plugins it depends on.
   */
  @get:ApiStatus.Internal
  @JvmStatic
  val loadedPlugins: List<IdeaPluginDescriptor>
    get() = getPluginSet().enabledPlugins

  @JvmStatic
  fun getLoadedPluginIds(): Collection<PluginId> {
    return getPluginSet().enabledPlugins.map { it.pluginId }
  }

  @JvmStatic
  fun isLoaded(id: PluginId): Boolean {
    val plugin = loadedPlugins.find { it.pluginId == id }
    return plugin != null && isLoaded(plugin)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun isLoaded(plugin: PluginDescriptor): Boolean = (plugin as? IdeaPluginDescriptorImpl)?.isLoaded ?: false

  @ApiStatus.Internal
  @JvmStatic
  fun arePluginsInitialized(): Boolean = pluginsState.nullablePluginSet != null

  @ApiStatus.Internal
  @JvmStatic
  fun setPluginSet(value: PluginSet) {
    pluginsState.nullablePluginSet = value
  }

  /**
   * Checks if the plugin with a given id is marked as disabled.
   */
  @JvmStatic
  fun isDisabled(pluginId: PluginId): Boolean = PluginEnabler.HEADLESS.isDisabled(pluginId)

  /**
   * Marks the plugin with a given id as disabled (a persistent setting). Note that this method does not unload the plugin.
   *
   * Internal. Plugins may not disable plugins this way.
   */
  @JvmStatic
  @ApiStatus.Internal
  fun disablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.disableById(setOf(id))

  /**
   * Marks the plugin with a given id as enabled (a persistent setting). Note that this method does not load the plugin.
   *
   * Internal. Plugins may not enable plugins this way.
   */
  @JvmStatic
  @ApiStatus.Internal
  fun enablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.enableById(setOf(id))

  @ApiStatus.Internal
  @JvmStatic
  fun looksLikePlatformPluginAlias(pluginId: PluginId): Boolean = pluginId.idString.startsWith(PLATFORM_ALIAS_DEPENDENCY_PREFIX)

  @ApiStatus.Internal
  @JvmStatic
  fun findPluginByPlatformAlias(id: PluginId): IdeaPluginDescriptorImpl? = getPluginSet().allPlugins.firstOrNull { it.pluginAliases.contains(id) }

  @ApiStatus.Internal
  @JvmStatic
  fun isPlatformClass(fqn: String): Boolean = fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("kotlin.") || fqn.startsWith("groovy.")

  @ApiStatus.Internal
  fun isVendorItemTrusted(vendorItem: String): Boolean =
    isVendorJetBrains(vendorItem) ||
    vendorItem == ApplicationInfoImpl.getShadowInstance().companyName ||
    vendorItem == ApplicationInfoImpl.getShadowInstance().shortCompanyName

  @JvmStatic
  fun isVendorTrusted(vendor: String): Boolean = vendor.splitToSequence(',').any { isVendorItemTrusted(it.trim()) }

  @JvmStatic
  fun isVendorTrusted(plugin: PluginDescriptor): Boolean =
    isDevelopedByJetBrains(plugin) ||
    isVendorTrusted(plugin.vendor ?: "") ||
    isVendorTrusted(plugin.organization ?: "")

  @JvmStatic
  fun isDevelopedByJetBrains(plugin: PluginDescriptor): Boolean = isDevelopedByJetBrains(pluginId = plugin.pluginId, vendor = plugin.vendor, organization = plugin.organization)

  @ApiStatus.Internal
  @JvmStatic
  fun isDevelopedByJetBrains(pluginId: PluginId, vendor: @NlsSafe String?, organization: @NlsSafe String?): Boolean =
    CORE_ID == pluginId ||
    SPECIAL_IDEA_PLUGIN_ID == pluginId ||
    isDevelopedByJetBrains(vendor) ||
    isDevelopedByJetBrains(organization)

  @JvmStatic
  @ApiStatus.Internal
  fun isDevelopedExclusivelyByJetBrains(plugin: PluginDescriptor): Boolean =
    CORE_ID == plugin.getPluginId() ||
    SPECIAL_IDEA_PLUGIN_ID == plugin.getPluginId() ||
    isDevelopedExclusivelyByJetBrains(plugin.getVendor()) ||
    isDevelopedExclusivelyByJetBrains(plugin.organization)

  @JvmStatic
  fun isDevelopedByJetBrains(vendorString: String?): Boolean = isDevelopedByJetBrains(vendorString, exclusively = false)

  @JvmStatic
  @ApiStatus.Internal
  fun isDevelopedExclusivelyByJetBrains(vendorString: String?): Boolean = isDevelopedByJetBrains(vendorString, exclusively = true)

  @JvmStatic
  private fun isDevelopedByJetBrains(vendorString: String?, exclusively: Boolean): Boolean = when {
    vendorString == null -> false
    isVendorJetBrains(vendorString) -> true
    else -> vendorString.splitToSequence(',').run { if (exclusively) all { isVendorJetBrains(it.trim()) } else any { isVendorJetBrains(it.trim()) } }
  }

  @JvmStatic
  fun isVendorJetBrains(vendorItem: String): Boolean = VENDOR_JETBRAINS == vendorItem || VENDOR_JETBRAINS_SRO == vendorItem

  @ApiStatus.Internal
  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope) {
    val mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader)
    scheduleDescriptorLoading(coroutineScope, CompletableDeferred(NonShareableJavaZipFilePool()), mainClassLoaderDeferred, logDeferred = null)
  }

  @ApiStatus.Internal
  @Synchronized
  fun scheduleDescriptorLoading(
    coroutineScope: CoroutineScope,
    zipPoolDeferred: Deferred<ZipEntryResolverPool>,
    mainClassLoaderDeferred: Deferred<ClassLoader>?,
    logDeferred: Deferred<Logger>?,
  ): Deferred<PluginSet> {
    var result = pluginsState.initFuture
    if (result == null) {
      result = coroutineScope.scheduleLoading(zipPoolDeferred, mainClassLoaderDeferred, logDeferred)
      pluginsState.initFuture = result
    }
    return result
  }

  /**
   * Think twice before use and get an approval from the core team. Returns enabled plugins only.
   */
  @ApiStatus.Internal
  fun getEnabledPluginRawList(): CompletableFuture<List<IdeaPluginDescriptorImpl>> = pluginsState.initFuture!!.asCompletableFuture().thenApply { it.enabledPlugins }

  @get:ApiStatus.Internal
  val initPluginFuture: Deferred<PluginSet>
    get() = pluginsState.initFuture ?: throw IllegalStateException("Call scheduleDescriptorLoading() first")

  @JvmStatic
  val buildNumber: BuildNumber
    get() {
      var result = ourBuildNumber
      if (result == null) {
        result = BuildNumber.fromPluginCompatibleBuild()
        if (logger.isDebugEnabled()) {
          logger.debug("getBuildNumber: fromPluginsCompatibleBuild=" + (result?.asString() ?: "null"))
        }
        if (result == null) {
          result = if (isUnitTestMode) {
            BuildNumber.currentVersion()
          }
          else {
            try {
              ApplicationInfoImpl.getShadowInstance().getApiVersionAsNumber()
            }
            catch (_: RuntimeException) {
              // no need to log error - ApplicationInfo is required in production in any case, so, will be logged if really needed
              BuildNumber.currentVersion()
            }
          }
        }
        ourBuildNumber = result
      }
      return result
    }

  @JvmStatic
  fun isCompatible(descriptor: IdeaPluginDescriptor): Boolean = isCompatible(descriptor, buildNumber = null)

  fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean = !isIncompatible(descriptor, buildNumber)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor): Boolean = isIncompatible(descriptor, buildNumber = null)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean =
    checkBuildNumberCompatibility(descriptor, buildNumber ?: PluginManagerCore.buildNumber) != null

  @ApiStatus.Internal
  fun initializePlugins(
    initContext: PluginInitializationContext,
    discoveredPlugins: PluginsDiscoveryResult,
    coreLoader: ClassLoader,
    parentActivity: Activity?,
    configureClassLoaders: Boolean,
  ): PluginSet {
    var initStagesActivity = parentActivity?.startChild("computeTargetState") // no safe end() call, because if it fails, it won't matter
    val pluginSet = initContext.computeTargetState(discoveredPlugins, isStartupInit = true, parentActivity = initStagesActivity)

    if (configureClassLoaders) {
      initStagesActivity = initStagesActivity?.endAndStart("ClassLoaderConfigurator")
      ClassLoaderConfigurator(pluginSet, coreLoader).configure()
    }

    initStagesActivity?.end()
    return pluginSet
  }

  @JvmStatic
  fun getPluginNameAndVendor(descriptor: IdeaPluginDescriptor): @Nls String {
    val vendor = descriptor.vendor ?: descriptor.organization
    if (vendor.isNullOrEmpty()) {
      return CoreBundle.message("plugin.name.and.unknown.vendor", descriptor.name)
    }
    else {
      return CoreBundle.message("plugin.name.and.vendor", descriptor.name, vendor)
    }
  }

  internal suspend fun initializeAndSetPlugins(
    initContext: PluginInitializationContext,
    discoveredPlugins: PluginsDiscoveryResult,
  ): PluginSet {
    val tracerShim = CoroutineTracerShim.coroutineTracer
    return tracerShim.span("plugin initialization") {
      val coreLoader = PluginManagerCore::class.java.classLoader
      val parentActivity = tracerShim.getTraceActivity()
      val pluginSet = initializePlugins(
        initContext = initContext,
        discoveredPlugins = discoveredPlugins,
        coreLoader = coreLoader,
        parentActivity = parentActivity,
        configureClassLoaders = true,
      )
      pluginsState.nullablePluginSet = pluginSet
      pluginSet
    }
  }

  // do not use class reference here
  @Suppress("SSBasedInspection")
  @get:ApiStatus.Internal
  @JvmStatic
  val logger: Logger
    get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

  @ApiStatus.Internal
  @Contract("null -> null")
  @JvmStatic
  fun getPlugin(id: PluginId?): IdeaPluginDescriptor? = if (id == null) null else findPlugin(id)

  @ApiStatus.Internal
  @JvmStatic
  fun findPlugin(id: PluginId): IdeaPluginDescriptorImpl? {
    return pluginsState.nullablePluginSet?.findEnabledOrInstalledPlugin(id)
  }

  @JvmStatic
  fun isPluginInstalled(id: PluginId): Boolean {
    val pluginSet = pluginsState.nullablePluginSet ?: return false
    return pluginSet.isPluginEnabled(id) || pluginSet.isPluginInstalled(id)
  }

  @ApiStatus.Internal
  fun buildPluginIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> {
    // FIXME deduplicate with com.intellij.ide.plugins.ModulesWithDependenciesKt.createModulesWithDependenciesAndAdditionalEdges
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    return getPluginSet().buildPluginIdMap()
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * @return `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @ApiStatus.Internal
  fun processAllNonOptionalDependencyIds(
    rootDescriptor: IdeaPluginDescriptorImpl,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    consumer: (PluginId) -> FileVisitResult,
  ): Boolean = processAllNonOptionalDependencies(rootDescriptor, depProcessed = HashSet(), pluginIdMap, contentModuleIdMap) { pluginId, _ ->
    if (pluginId == null) FileVisitResult.CONTINUE else consumer(pluginId)
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * Returns `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @ApiStatus.Internal
  fun processAllNonOptionalDependencies(
    rootDescriptor: IdeaPluginDescriptorImpl,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    consumer: (IdeaPluginDescriptorImpl) -> FileVisitResult,
  ): Boolean = processAllNonOptionalDependencies(rootDescriptor, depProcessed = HashSet(), pluginIdMap, contentModuleIdMap) { _, descriptor ->
    if (descriptor == null) FileVisitResult.CONTINUE else consumer(descriptor)
  }

  @Deprecated("Use [processAllNonOptionalDependencyIds] instead, this function doesn't process dependencies on modules")
  @ApiStatus.Internal
  fun getNonOptionalDependenciesIds(descriptor: IdeaPluginDescriptorImpl): Set<PluginId> {
    val dependencies = LinkedHashSet<PluginId>()
    for (dependency in descriptor.dependencies) {
      if (!dependency.isOptional) {
        dependencies.add(dependency.pluginId)
      }
    }
    for (plugin in descriptor.moduleDependencies.plugins) {
      dependencies.add(plugin)
    }
    if (descriptor is PluginMainDescriptor) {
      for (contentModule in descriptor.contentModules) {
        if (contentModule.moduleLoadingRule.required) {
          for (contentModuleDependency in contentModule.moduleDependencies.plugins) {
            dependencies.add(contentModuleDependency)
          }
        }
      }
    }
    return dependencies
  }

  @ApiStatus.Internal
  @JvmStatic
  fun isUpdatedBundledPlugin(plugin: PluginDescriptor): Boolean =
    !plugin.isBundled && getShadowedBundledPluginIds(getPluginSet()).contains(plugin.getPluginId())

  @Synchronized
  private fun getShadowedBundledPluginIds(pluginSet: PluginSet): Set<PluginId> {
    val cached = shadowedBundledPluginsCache
    if (cached != null && cached.pluginSetRef.get() === pluginSet) {
      return cached.pluginIds
    }

    val result = HashSet<PluginId>()
    for (pluginList in pluginSet.input.discoveryResult.pluginLists) {
      if (
        pluginList.source != PluginsSourceContext.Bundled &&
        pluginList.source != PluginsSourceContext.ClassPathProvided // FIXME checking only Bundled should be sufficient here
      ) {
        continue
      }
      for (plugin in pluginList.plugins) {
        if (pluginSet.excludedFromCandidateSubset[plugin] is PluginVersionIsSuperseded) {
          result.add(plugin.pluginId)
        }
      }
    }
    shadowedBundledPluginsCache = ShadowedBundledPluginsCache(pluginSetRef = WeakReference(pluginSet), pluginIds = result)
    return result
  }

  @ApiStatus.Internal
  fun dependsOnUltimateOptionally(pluginDescriptor: IdeaPluginDescriptor?): Boolean {
    if (pluginDescriptor == null || pluginDescriptor !is IdeaPluginDescriptorImpl || !isDisabled(ULTIMATE_PLUGIN_ID)) return false
    val pluginIdMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    @Suppress("DEPRECATION")
    return pluginDescriptor.contentModules.any { contentModule ->
      !contentModule.moduleLoadingRule.required && !processAllNonOptionalDependencies(contentModule, pluginIdMap, contentModuleIdMap) { descriptorImpl ->
        when (descriptorImpl.pluginId) {
          ULTIMATE_PLUGIN_ID -> FileVisitResult.TERMINATE
          else -> FileVisitResult.CONTINUE
        }
      }
    }
  }

  /**
   * @return `true` If any required dependency of some essential plugin (both plugin or modular, including transitive) is provided by [pluginDescriptor].
   * Note that `pluginDescriptor is essential` does not imply `isRequiredForEssentialPlugin(pluginDescriptor) == true`.
   */
  @ApiStatus.Internal
  fun isRequiredForEssentialPlugin(pluginDescriptor: PluginMainDescriptor): Boolean {
    // FIXME id map building should be lifted out (likewise in other methods too)
    //  this method should actually be an extension on ActivePluginSet or something
    val initContext = PluginInitContextFactory.getInstance().createActualContext()
    val pluginIdMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    for (essentialPluginId in initContext.essentialPlugins) {
      val essentialPlugin = pluginIdMap[essentialPluginId] ?: continue
      val isRequiredDependency = !processAllNonOptionalDependencies(essentialPlugin, pluginIdMap, contentModuleIdMap) { dependency ->
        if (dependency.getMainDescriptor() === pluginDescriptor) {
          logger.debug { "Plugin ${pluginDescriptor.pluginId} is required for essential plugin $essentialPluginId" }
          FileVisitResult.TERMINATE
        }
        else {
          FileVisitResult.CONTINUE
        }
      }
      if (isRequiredDependency) {
        return true
      }
    }
    return false
  }

  @ApiStatus.Internal
  fun isDisableAllowed(descriptor: IdeaPluginDescriptor): Boolean {
    if (descriptor !is PluginMainDescriptor) {
      return true // TODO does not really make sense ?
    }
    if (descriptor.isImplementationDetail() ||
        ApplicationInfo.getInstance().isEssentialPlugin(descriptor.pluginId) ||
        isRequiredForEssentialPlugin(descriptor)) {
      return false
    }
    return true
  }

  //<editor-fold desc="Deprecated stuff.">
  @ApiStatus.ScheduledForRemoval
  @Deprecated("The platform code should use [JAVA_PLUGIN_ALIAS_ID] instead, plugins aren't supposed to use this", level = DeprecationLevel.ERROR)
  @JvmField val JAVA_MODULE_ID: PluginId = JAVA_PLUGIN_ALIAS_ID

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun getPluginByClassName(className: String): PluginId? = PluginUtils.getPluginDescriptorOrPlatformByClassName(className)?.getPluginId()?.takeIf { it != CORE_ID }

  @ApiStatus.Internal
  @Deprecated("Moved to PluginUtils", replaceWith = ReplaceWith("PluginUtils.getPluginDescriptorOrPlatformByClassName(className)"), level = DeprecationLevel.ERROR)
  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: String): PluginDescriptor? = PluginUtils.getPluginDescriptorOrPlatformByClassName(className)

  @Deprecated("Use {@link #disablePlugin(PluginId)}", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun disablePlugin(id: String): Boolean = disablePlugin(PluginId.getId(id))

  //</editor-fold>

  private fun processAllNonOptionalDependencies(
    rootDescriptor: IdeaPluginDescriptorImpl,
    depProcessed: MutableSet<in IdeaPluginDescriptorImpl>,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    consumer: (PluginId?, IdeaPluginDescriptorImpl?) -> FileVisitResult,
  ): Boolean {
    fun processDependency(pluginId: PluginId?, moduleId: PluginModuleId?): Boolean {
      val descriptor = if (pluginId != null) pluginIdMap[pluginId] else contentModuleIdMap[moduleId]
      val pluginId = descriptor?.getPluginId() ?: pluginId
      when (consumer(pluginId, descriptor)) {
        FileVisitResult.TERMINATE -> return false
        FileVisitResult.CONTINUE -> {
          if (descriptor != null && depProcessed.add(descriptor) &&
              !processAllNonOptionalDependencies(descriptor, depProcessed, pluginIdMap, contentModuleIdMap, consumer)) {
            return false
          }
        }
        FileVisitResult.SKIP_SUBTREE -> {}
        FileVisitResult.SKIP_SIBLINGS -> throw UnsupportedOperationException("FileVisitResult.SKIP_SIBLINGS is not supported")
      }
      return true
    }

    fun processModuleDependencies(moduleDependencies: ModuleDependencies): Boolean {
      for (plugin in moduleDependencies.plugins) {
        if (!processDependency(plugin, null)) {
          return false
        }
      }
      for (module in moduleDependencies.modules) {
        if (!processDependency(null, module)) {
          return false
        }
      }
      return true
    }

    for (dependency in rootDescriptor.dependencies) {
      if (!dependency.isOptional && !processDependency(dependency.pluginId, null)) {
        return false
      }
    }

    if (!processModuleDependencies(rootDescriptor.moduleDependencies)) {
      return false
    }

    if (rootDescriptor is PluginMainDescriptor) {
      for (contentModule in rootDescriptor.contentModules) {
        if (contentModule.moduleLoadingRule.required && !processModuleDependencies(contentModule.moduleDependencies)) {
          return false
        }
      }
    }
    return true
  }
}

/**
 * @see com.intellij.openapi.application.PluginPathManager instead
 */
@ApiStatus.Internal
fun getPluginDistDirByClass(aClass: Class<*>): Path? {
  val pluginDir = (aClass.classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.pluginPath
  if (pluginDir != null) {
    return pluginDir
  }

  val jarInsideLib = PathManager.getJarForClass(aClass) ?: error("Can't find plugin dist home for ${aClass.simpleName}")
  if (jarInsideLib.fileName.toString().endsWith("jar", ignoreCase = true)) {
    ArchivedCompilationContextUtil.archivedCompiledClassesLocation?.let {
      if (jarInsideLib.startsWith(it)) return null
    }
    return jarInsideLib
      .parent
      .let { if (it.name == "modules") it.parent else it }
      .also { check(it.name == "lib") { "$it should be lib directory" } }
      .parent
  }
  else {
    // for now, we support only plugins that for some reason pack plugin.xml into JAR (e.g., kotlin)
    return null
  }
}

@ApiStatus.Internal
fun pluginRequiresUltimatePluginButItsDisabled(plugin: PluginId): Boolean {
  val idMap = PluginManagerCore.buildPluginIdMap()
  val contentModuleIdMap = PluginManagerCore.getPluginSet().buildContentModuleIdMap()
  return pluginRequiresUltimatePluginButItsDisabled(plugin, idMap, contentModuleIdMap)
}

@ApiStatus.Internal
fun pluginRequiresUltimatePluginButItsDisabled(
  rootPlugin: IdeaPluginDescriptorImpl,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean = PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID) && pluginRequiresUltimatePlugin(rootPlugin, pluginMap, contentModuleIdMap)

@ApiStatus.Internal
fun pluginRequiresUltimatePluginButItsDisabled(
  plugin: PluginId,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean = PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID) && pluginRequiresUltimatePlugin(plugin, pluginMap, contentModuleIdMap)

@ApiStatus.Internal
fun pluginRequiresUltimatePlugin(
  plugin: PluginId,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  val rootDescriptor = pluginMap[plugin]
  return rootDescriptor != null && pluginRequiresUltimatePlugin(rootDescriptor, pluginMap, contentModuleMap)
}

@ApiStatus.Internal
fun pluginRequiresUltimatePlugin(
  rootDescriptor: IdeaPluginDescriptorImpl,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  return !PluginManagerCore.processAllNonOptionalDependencies(rootDescriptor, pluginMap, contentModuleMap) { descriptorImpl ->
    when (descriptorImpl.pluginId) {
      PluginManagerCore.ULTIMATE_PLUGIN_ID -> FileVisitResult.TERMINATE
      else -> FileVisitResult.CONTINUE
    }
  }
}

/**
 * Checks if the class is a part of the platform or included in a built-in plugin provided by the JetBrains vendor.
 */
@ApiStatus.Internal
fun isPlatformOrJetBrainsDistributionPlugin(aClass: Class<*>): Boolean {
  val classLoader = aClass.classLoader
  return when {
    classLoader is PluginAwareClassLoader -> {
      val plugin = classLoader.pluginDescriptor
      (plugin.isBundled || PluginManagerCore.isUpdatedBundledPlugin(plugin)) && PluginManagerCore.isDevelopedByJetBrains(plugin)
    }
    PluginManagerCore.isRunningFromSources() -> true
    else -> PluginUtils.getPluginDescriptorIfIdeaClassLoaderIsUsed(aClass) == null
  }
}
