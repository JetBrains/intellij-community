// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.DisabledPluginsState.Companion.invalidate
import com.intellij.ide.plugins.PluginManagerCore.ULTIMATE_PLUGIN_ID
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.ide.plugins.PluginManagerCore.loadedPlugins
import com.intellij.ide.plugins.PluginManagerCore.processAllNonOptionalDependencies
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
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.ZipEntryResolverPool
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GraphicsEnvironment
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.JOptionPane
import kotlin.io.path.name

private const val PLATFORM_ALIAS_DEPENDENCY_PREFIX = "com.intellij.module"

private val QODANA_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("idea.qodana.thirdpartyplugins.accept")
private val FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("fleet.backend.third-party.plugins.accept")

/**
 * See [Plugin Model](https://youtrack.jetbrains.com/articles/IJPL-A-31/Plugin-Model) documentation.
 *
 * @implNote Prefer to use only JDK classes. Any post-start-up functionality should be placed in [PluginManager] class.
 */
object PluginManagerCore {
  const val META_INF: String = "META-INF/"
  const val CORE_PLUGIN_ID: String = "com.intellij"
  const val PLUGIN_XML: String = "plugin.xml"
  const val PLUGIN_XML_PATH: String = META_INF + PLUGIN_XML
  const val VENDOR_JETBRAINS: String = "JetBrains"
  const val VENDOR_JETBRAINS_SRO: String = "JetBrains s.r.o."
  const val DISABLE: String = "disable"
  const val ENABLE: String = "enable"
  const val EDIT: String = "edit"

  @JvmField val CORE_ID: PluginId = PluginId.getId(CORE_PLUGIN_ID)
  @JvmField val JAVA_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.java")
  @Internal
  @JvmField val JAVA_PLUGIN_ALIAS_ID: PluginId = PluginId.getId("com.intellij.modules.java")
  @JvmField val ALL_MODULES_MARKER: PluginId = PluginId.getId("com.intellij.modules.all")
  @JvmField val SPECIAL_IDEA_PLUGIN_ID: PluginId = PluginId.getId("IDEA CORE")
  @Internal
  @JvmField val ULTIMATE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.modules.ultimate")
  @Internal
  @JvmField val MARKETPLACE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.marketplace")

  @VisibleForTesting
  @Volatile
  @JvmField
  var isIgnoreCompatibility: Boolean = java.lang.Boolean.getBoolean("idea.ignore.plugin.compatibility")

  @VisibleForTesting
  @Volatile
  @JvmField
  var isUnitTestMode: Boolean = java.lang.Boolean.getBoolean("idea.is.unit.test")

  @Internal
  class PluginsMutableState {
    @Volatile
    var nullablePluginSet: PluginSet? = null
    private val pluginNonLoadReasons: MutableMap<PluginId, PluginNonLoadReason> = hashMapOf()
    private val pluginErrors: ArrayList<PluginLoadingError> = ArrayList<PluginLoadingError>()
    var pluginsToDisable: List<PluginStateChangeData> = emptyList()
    var pluginsToEnable: List<PluginStateChangeData> = emptyList()

    /**
     * Bundled plugins that were updated.
     * When we update a bundled plugin, it becomes non-bundled, so it is more challenging for analytics to use that data.
     */
    var shadowedBundledPlugins: Set<PluginId> = Collections.emptySet()
    @Volatile
    var thirdPartyPluginsNoteAccepted: Boolean? = null
    @Volatile
    var initFuture: Deferred<PluginSet>? = null

    @Synchronized
    fun setErrorsForNotificationReporterAndLogger(errors: List<PluginLoadingError>) {
      pluginErrors.clear()
      pluginErrors.addAll(errors)
    }

    @Synchronized
    fun getAndClearPluginLoadingErrors(): List<PluginLoadingError> {
      val result = pluginErrors.toList()
      pluginErrors.clear()
      return result
    }

    @Synchronized
    fun getStartupActionsPluginsToEnableDisable(): Pair<List<PluginStateChangeData>, List<PluginStateChangeData>> {
      val toEnable = pluginsToEnable
      val toDisable = pluginsToDisable
      return toEnable to toDisable
    }

    fun getPluginNonLoadReason(pluginId: PluginId): PluginNonLoadReason? = pluginNonLoadReasons[pluginId]

    fun clearPluginNonLoadReason(pluginId: PluginId) {
      pluginNonLoadReasons.remove(pluginId)
    }

    fun addPluginNonLoadReasons(pluginNonLoadReasons: Map<PluginId, PluginNonLoadReason>) {
      this.pluginNonLoadReasons.putAll(pluginNonLoadReasons)
    }
  }

  private var isRunningFromSources: Boolean? = null
  private var ourBuildNumber: BuildNumber? = null

  @Internal
  var pluginsStateSupplier: (() -> PluginsMutableState)? = null

  private val pluginsStateLazy = lazy { PluginsMutableState() }
  private val pluginsState: PluginsMutableState
    get() = pluginsStateSupplier?.invoke() ?: pluginsStateLazy.value

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
  @JvmStatic
  val plugins: Array<IdeaPluginDescriptor>
    get() = getPluginSet().allPlugins.toTypedArray<IdeaPluginDescriptor>()

  @Internal
  @JvmStatic
  fun getPluginSet(): PluginSet = pluginsState.nullablePluginSet!!

  @Internal
  @JvmStatic
  fun getPluginSetOrNull(): PluginSet? = pluginsState.nullablePluginSet

  /**
   * Returns descriptors of plugins which are successfully loaded into the IDE.
   * The result is sorted in a way that if each plugin comes after the plugins it depends on.
   */
  @JvmStatic
  val loadedPlugins: List<IdeaPluginDescriptor>
    get() = getPluginSet().enabledPlugins

  @JvmStatic
  fun isLoaded(id: PluginId): Boolean {
    val plugin = loadedPlugins.find { it.pluginId == id }
    return plugin != null && isLoaded(plugin)
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun isLoaded(plugin: PluginDescriptor): Boolean = (plugin as? IdeaPluginDescriptorImpl)?.isLoaded ?: false

  @Internal
  fun getAndClearPluginLoadingErrors(): List<PluginLoadingError> = pluginsState.getAndClearPluginLoadingErrors()

  @Internal
  @JvmStatic
  fun arePluginsInitialized(): Boolean = pluginsState.nullablePluginSet != null

  @Internal
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
   */
  @JvmStatic
  fun disablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.disableById(setOf(id))

  /**
   * Marks the plugin with a given id as enabled (a persistent setting). Note that this method does not load the plugin.
   */
  @JvmStatic
  fun enablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.enableById(setOf(id))

  @Internal
  @JvmStatic
  fun looksLikePlatformPluginAlias(pluginId: PluginId): Boolean {
    return pluginId.idString.startsWith(PLATFORM_ALIAS_DEPENDENCY_PREFIX)
  }

  @Internal
  @JvmStatic
  fun findPluginByPlatformAlias(id: PluginId): IdeaPluginDescriptorImpl? = getPluginSet().allPlugins.firstOrNull { it.pluginAliases.contains(id) }

  @Internal
  @JvmStatic
  fun isPlatformClass(fqn: String): Boolean = fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("kotlin.") || fqn.startsWith("groovy.")

  @Internal
  fun isVendorItemTrusted(vendorItem: String): Boolean {
    if (vendorItem.isBlank()) {
      return false
    }
    else {
      return isVendorJetBrains(vendorItem) ||
             vendorItem == ApplicationInfoImpl.getShadowInstance().companyName ||
             vendorItem == ApplicationInfoImpl.getShadowInstance().shortCompanyName
    }
  }

  @JvmStatic
  fun isVendorTrusted(vendor: String): Boolean = vendor.splitToSequence(',').any { isVendorItemTrusted(it.trim()) }

  @JvmStatic
  fun isVendorTrusted(plugin: PluginDescriptor): Boolean {
    return isDevelopedByJetBrains(plugin) ||
           isVendorTrusted(plugin.vendor ?: "") ||
           isVendorTrusted(plugin.organization ?: "")
  }

  @JvmStatic
  fun isDevelopedByJetBrains(plugin: PluginDescriptor): Boolean {
    return isDevelopedByJetBrains(pluginId = plugin.pluginId, vendor = plugin.vendor, organization = plugin.organization)
  }

  @Internal
  @JvmStatic
  fun isDevelopedByJetBrains(pluginId: PluginId, vendor: @NlsSafe String?, organization: @NlsSafe String?): Boolean {
    return CORE_ID == pluginId || SPECIAL_IDEA_PLUGIN_ID == pluginId ||
           isDevelopedByJetBrains(vendor) ||
           isDevelopedByJetBrains(organization)
  }

  @JvmStatic
  @Internal
  fun isDevelopedExclusivelyByJetBrains(plugin: PluginDescriptor): Boolean {
    return CORE_ID == plugin.getPluginId() || SPECIAL_IDEA_PLUGIN_ID == plugin.getPluginId() ||
           isDevelopedExclusivelyByJetBrains(plugin.getVendor()) ||
           isDevelopedExclusivelyByJetBrains(plugin.organization)
  }

  @JvmStatic
  fun isDevelopedByJetBrains(vendorString: String?): Boolean = isDevelopedByJetBrains(vendorString = vendorString, exclusively = false)

  @JvmStatic
  @Internal
  fun isDevelopedExclusivelyByJetBrains(vendorString: String?): Boolean = isDevelopedByJetBrains(vendorString = vendorString, exclusively = true)

  @JvmStatic
  private fun isDevelopedByJetBrains(vendorString: String?, exclusively: Boolean): Boolean {
    return when {
      vendorString == null -> false
      isVendorJetBrains(vendorString) -> true
      else -> vendorString.splitToSequence(',').run { if (exclusively) all { isVendorJetBrains(it.trim()) } else any { isVendorJetBrains(it.trim()) } }
    }
  }

  @JvmStatic
  fun isVendorJetBrains(vendorItem: String): Boolean = VENDOR_JETBRAINS == vendorItem || VENDOR_JETBRAINS_SRO == vendorItem

  @Synchronized
  @JvmStatic
  fun invalidatePlugins() {
    pluginsState.nullablePluginSet = null
    val future = pluginsState.initFuture
    if (future != null) {
      pluginsState.initFuture = null
      future.cancel(CancellationException("invalidatePlugins"))
    }
    invalidate()
    pluginsState.shadowedBundledPlugins = Collections.emptySet()
  }

  @Suppress("LoggingSimilarMessage")
  private fun preparePluginErrors(
    pluginNonLoadReasons: Map<PluginId, PluginNonLoadReason>,
    descriptorLoadingErrors: List<PluginDescriptorLoadingError>,
    cycleErrors: List<PluginLoadingError>,
    initContext: PluginInitializationContext,
  ): List<PluginLoadingError> {
    // name shadowing is intended
    val pluginNonLoadReasons = pluginNonLoadReasons.filterValues {
      it !is PluginIsMarkedDisabled && !initContext.isPluginDisabled(it.plugin.pluginId)
    }
    val globalErrors = ArrayList<PluginLoadingError>().apply {
      for (descriptorLoadingError in descriptorLoadingErrors) {
        add(PluginLoadingError(
          reason = null,
          htmlMessageSupplier = Supplier {
            HtmlChunk.text(CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor",
                                              PluginUtils.pluginPathToUserString(descriptorLoadingError.path)))
          },
          error = descriptorLoadingError.error,
        ))
      }
      addAll(cycleErrors)
    }
    if (pluginNonLoadReasons.isEmpty() && globalErrors.isEmpty()) {
      return emptyList()
    }

    // the log includes all messages, not only those which need to be reported to the user
    val loadingErrors = pluginNonLoadReasons.values
    val logMessage =
      "Problems found loading plugins:\n  " +
      (globalErrors.asSequence().map { it.htmlMessage.toString() } + loadingErrors.asSequence().map { it.logMessage })
        .joinToString(separator = "\n  ")
    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      if (!isUnitTestMode) {
        logger.warn(logMessage)
      }
      else {
        logger.info(logMessage)
      }
      val mappedLoadingErrors = loadingErrors.asSequence()
        .filter { it.shouldNotifyUser }
        .map { reason -> PluginLoadingError(reason = reason, htmlMessageSupplier = { HtmlChunk.text(reason.detailedMessage) }, error = null) }
      return (globalErrors.asSequence() + mappedLoadingErrors).toList()
    }
    else if (PlatformUtils.isFleetBackend()) {
      logger.warn(logMessage)
    }
    else {
      logger.error(logMessage)
    }
    return emptyList()
  }

  @Internal
  fun getPluginNonLoadReason(pluginId: PluginId): PluginNonLoadReason? = pluginsState.getPluginNonLoadReason(pluginId)

  @Internal
  fun clearPluginNonLoadReasonFor(pluginId: PluginId): Unit = pluginsState.clearPluginNonLoadReason(pluginId)

  @Internal
  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope) {
    scheduleDescriptorLoading(
      coroutineScope = coroutineScope,
      zipPoolDeferred = CompletableDeferred(NonShareableJavaZipFilePool()),
      mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader),
      logDeferred = null,
    )
  }

  @Internal
  @Synchronized
  fun scheduleDescriptorLoading(
    coroutineScope: CoroutineScope,
    zipPoolDeferred: Deferred<ZipEntryResolverPool>,
    mainClassLoaderDeferred: Deferred<ClassLoader>?,
    logDeferred: Deferred<Logger>?,
  ): Deferred<PluginSet> {
    var result = pluginsState.initFuture
    if (result == null) {
      result = coroutineScope.scheduleLoading(zipPoolDeferred = zipPoolDeferred, mainClassLoaderDeferred = mainClassLoaderDeferred, logDeferred = logDeferred)
      pluginsState.initFuture = result
    }
    return result
  }

  /**
   * Think twice before use and get an approval from the core team. Returns enabled plugins only.
   */
  @Internal
  fun getEnabledPluginRawList(): CompletableFuture<List<IdeaPluginDescriptorImpl>> {
    return pluginsState.initFuture!!.asCompletableFuture().thenApply { it.enabledPlugins }
  }

  @get:Internal
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
  fun isIncompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean {
    return checkBuildNumberCompatibility(descriptor, buildNumber ?: PluginManagerCore.buildNumber) != null
  }

  @Internal
  fun getUnfulfilledOsRequirement(descriptor: IdeaPluginDescriptor): IdeaPluginOsRequirement? {
    return descriptor.getDependencies().asSequence()
      .mapNotNull { dep -> IdeaPluginOsRequirement.fromModuleId(dep.pluginId).takeIf { !dep.isOptional } }
      .firstOrNull { osReq -> !osReq.isHostOs() }
  }

  @Internal
  fun getUnfulfilledCpuArchRequirement(descriptor: IdeaPluginDescriptor): PluginCpuArchRequirement? {
    return descriptor.getDependencies().asSequence()
      .mapNotNull { dep -> PluginCpuArchRequirement.fromPluginId(dep.pluginId).takeIf { !dep.isOptional } }
      .firstOrNull { osReq -> !osReq.isHostArch() }
  }

  @JvmStatic
  fun checkBuildNumberCompatibility(descriptor: IdeaPluginDescriptor, ideBuildNumber: BuildNumber): PluginNonLoadReason? {
    val requiredOs = getUnfulfilledOsRequirement(descriptor)
    if (requiredOs != null) {
      return PluginIsIncompatibleWithHostPlatform(descriptor, requiredOs, OS.CURRENT.name)
    }

    val requiredArch = getUnfulfilledCpuArchRequirement(descriptor)
    if (requiredArch != null) {
      return PluginIsIncompatibleWithHostCpu(descriptor, requiredArch, CpuArch.CURRENT)
    }

    if (isIgnoreCompatibility) {
      return null
    }

    try {
      val sinceBuild = descriptor.getSinceBuild()
      if (sinceBuild != null) {
        val pluginName = descriptor.getName()
        val sinceBuildNumber = try {
          BuildNumber.fromString(sinceBuild, pluginName, null)
        }
        catch (e: RuntimeException) {
          logger.error(e)
          null
        }
        if (sinceBuildNumber != null && sinceBuildNumber > ideBuildNumber) {
          return PluginSinceBuildConstraintViolation(descriptor, ideBuildNumber)
        }
      }

      val untilBuild = descriptor.getUntilBuild()
      if (untilBuild != null) {
        val pluginName = descriptor.getName()
        val untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null)
        if (untilBuildNumber != null && untilBuildNumber < ideBuildNumber) {
          return PluginUntilBuildConstraintViolation(descriptor, ideBuildNumber)
        }
      }
    }
    catch (e: Exception) {
      logger.error(e)
      return PluginMalformedSinceUntilConstraints(descriptor)
    }
    return null
  }

  @Internal
  fun initializePlugins(
    descriptorLoadingErrors: List<PluginDescriptorLoadingError>,
    initContext: PluginInitializationContext,
    discoveredPlugins: PluginsDiscoveryResult,
    coreLoader: ClassLoader,
    parentActivity: Activity?,
  ): PluginManagerState {
    val excludedFromLoading = IdentityHashMap<PluginMainDescriptor, PluginNonLoadReason>()
    val pluginsToLoad = initContext.selectPluginsToLoad(discoveredPlugins.pluginLists) { plugin, reason ->
      excludedFromLoading.put(plugin, reason)
    }
    val incompletePlugins = HashMap<PluginId, PluginMainDescriptor>()
    val shadowedBundledIds = HashSet<PluginId>()
    for (pluginList in discoveredPlugins.pluginLists) {
      for (plugin in pluginList.plugins) {
        val exclusionReason = excludedFromLoading[plugin]
        if (exclusionReason != null) {
          plugin.isMarkedForLoading = false
        }
        if (pluginsToLoad.resolvePluginId(plugin.pluginId) == null && exclusionReason != null && exclusionReason !is PluginVersionIsSuperseded) {
          val existing = incompletePlugins[plugin.pluginId]
          if (existing == null || VersionComparatorUtil.compare(plugin.version, existing.version) > 0) {
            incompletePlugins[plugin.pluginId] = plugin
          }
        }
        if ((pluginList.source == PluginsSourceContext.Bundled ||
             pluginList.source == PluginsSourceContext.ClassPathProvided) && // FIXME checking only Bundled should be sufficient here
            exclusionReason is PluginVersionIsSuperseded) {
          shadowedBundledIds.add(plugin.pluginId)
        }
      }
    }
    val ambiguousPluginSet = AmbiguousPluginSet.build(pluginsToLoad.plugins + incompletePlugins.values)
    val pluginNonLoadReasons = incompletePlugins.values.associateByTo(mutableMapOf(), { it.pluginId }, { excludedFromLoading[it]!! })
    val fullIdMap = ambiguousPluginSet.buildFullPluginIdMapping().mapValues { it.value.first() }
    val fullContentModuleIdMap = ambiguousPluginSet.buildFullContentModuleIdMapping().mapValues { it.value.first() }

    if (initContext.checkEssentialPlugins && pluginsToLoad.resolvePluginId(CORE_ID) == null) {
      throw EssentialPluginMissingException(listOf("$CORE_ID (platform prefix: ${System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)})"))
        .apply { pluginNonLoadReasons.get(CORE_ID)?.let { addSuppressed(Exception(it.logMessage)) } }
    }

    checkThirdPartyPluginsPrivacyConsent(parentActivity, pluginsToLoad)

    val pluginSetBuilder = PluginSetBuilder(pluginsToLoad.plugins.toSet())
    val cycleErrors = pluginSetBuilder.checkPluginCycles()
    val pluginsToDisable = HashMap<PluginId, PluginStateChangeData>()
    val pluginsToEnable = HashMap<PluginId, PluginStateChangeData>()
    
    fun registerLoadingError(loadingError: PluginNonLoadReason) {
      pluginNonLoadReasons.put(loadingError.plugin.pluginId, loadingError)
      pluginsToDisable.put(loadingError.plugin.pluginId, PluginStateChangeData(loadingError.plugin.pluginId, loadingError.plugin.name))
      if (loadingError is PluginDependencyIsDisabled) {
        val disabledDependencyId = loadingError.dependencyId
        if (initContext.isPluginDisabled(disabledDependencyId)) {
          val disabledPlugin = fullIdMap.get(disabledDependencyId)!!
          pluginsToEnable.put(disabledDependencyId, PluginStateChangeData(disabledPlugin.pluginId, disabledPlugin.name))
        }
      }
    }

    val idMap = pluginsToLoad.buildFullPluginIdMapping()
    val additionalErrors = pluginSetBuilder.computeEnabledModuleMap(
      incompletePlugins = incompletePlugins.values.toList(),
      initContext = initContext,
      disabler = { descriptor, disabledModuleToProblematicPlugin ->
        val loadingError = pluginSetBuilder.initEnableState(
          descriptor = descriptor,
          idMap = idMap,
          fullIdMap = fullIdMap,
          fullContentModuleIdMap = fullContentModuleIdMap,
          isPluginDisabled = initContext::isPluginDisabled,
          errors = pluginNonLoadReasons,
          disabledModuleToProblematicPlugin = disabledModuleToProblematicPlugin,
        )
        if (loadingError != null) {
          registerLoadingError(loadingError)
        }
        if (loadingError != null || initContext.isPluginExpired(descriptor.getPluginId())) {
          descriptor.isMarkedForLoading = false
        }
        !descriptor.isMarkedForLoading
      }
    )
    for (loadingError in additionalErrors) {
      registerLoadingError(loadingError)
    }

    pluginsState.addPluginNonLoadReasons(pluginNonLoadReasons.filter { it.value !is PluginIsMarkedDisabled })
    pluginsState.setErrorsForNotificationReporterAndLogger(preparePluginErrors(
      pluginNonLoadReasons = pluginNonLoadReasons,
      descriptorLoadingErrors = descriptorLoadingErrors,
      cycleErrors = cycleErrors,
      initContext = initContext,
    ))

    if (initContext.checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap, initContext.essentialPlugins, pluginNonLoadReasons)
    }

    val pluginSet = pluginSetBuilder.createPluginSet(incompletePlugins = incompletePlugins.values.toList())
    ClassLoaderConfigurator(pluginSet, coreLoader).configure()
    return PluginManagerState(
      pluginSet = pluginSet,
      pluginToDisable = pluginsToDisable.values.toList(),
      pluginToEnable = pluginsToEnable.values.toList(),
      incompletePluginsForLogging = incompletePlugins.values.toList(),
      shadowedBundledPlugins = shadowedBundledIds
    )
  }

  /**
   * processes postponed consent check from the previous run (e.g., when the previous run was headless)
   * see usages of [ThirdPartyPluginsWithoutConsentFile.appendAliens]
   */
  private fun checkThirdPartyPluginsPrivacyConsent(parentActivity: Activity?, idMap: UnambiguousPluginSet) {
    val activity = parentActivity?.startChild("3rd-party plugins consent")
    val aliens = ThirdPartyPluginsWithoutConsentFile.consumeAliensFile().mapNotNull { idMap.resolvePluginId(it)?.getMainDescriptor() }
    if (!aliens.isEmpty()) {
      checkThirdPartyPluginsPrivacyConsent(aliens)
    }
    activity?.end()
  }

  private fun checkEssentialPluginsAreAvailable(idMap: Map<PluginId, IdeaPluginDescriptorImpl>, essentialPlugins: Set<PluginId>, pluginNonLoadReasons: Map<PluginId, PluginNonLoadReason>) {
    val corePlugin = idMap.get(CORE_ID)
    if (corePlugin != null) {
      val disabledModulesOfCorePlugin = corePlugin.contentModules.filter { it.moduleLoadingRule.required && !it.isMarkedForLoading }
      if (disabledModulesOfCorePlugin.isNotEmpty()) {
        throw EssentialPluginMissingException(disabledModulesOfCorePlugin.map { it.moduleId.name })
      }
    }
    var missing: MutableList<Pair<String, PluginNonLoadReason?>>? = null
    for (id in essentialPlugins) {
      val descriptor = idMap.get(id)
      if (descriptor == null || !descriptor.isMarkedForLoading) {
        if (missing == null) {
          missing = ArrayList()
        }
        missing.add(id.idString to pluginNonLoadReasons[id])
      }
    }
    if (missing != null) {
      throw EssentialPluginMissingException(missing.map { it.first })
        .apply { missing.forEach { (_, reason) -> if (reason != null) addSuppressed(Exception(reason.logMessage)) } }
    }
  }

  private fun checkThirdPartyPluginsPrivacyConsent(aliens: List<IdeaPluginDescriptorImpl>) {
    fun disableThirdPartyPlugins() {
      for (descriptor in aliens) {
        descriptor.isMarkedForLoading = false
      }
      PluginEnabler.HEADLESS.disable(aliens)
    }

    if (GraphicsEnvironment.isHeadless()) {
      if (QODANA_PLUGINS_THIRD_PARTY_ACCEPT || FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT) {
        pluginsState.thirdPartyPluginsNoteAccepted = true
        return
      }
      logger.info("3rd-party plugin privacy note not accepted yet; disabling plugins for this headless session")
      for (descriptor in aliens) {
        descriptor.isMarkedForLoading = false
      }
      //write the list of third-party plugins back to ensure that the privacy note will be shown next time
      ThirdPartyPluginsWithoutConsentFile.appendAliens(aliens.map { it.pluginId })
    }
    else if (AppMode.isRemoteDevHost()) {
      logger.warn("""
        |New third-party plugins were installed, they will be disabled because asking for consent to use third-party plugins during startup isn't supported in remote development mode:
        | ${aliens.joinToString(separator = "\n ") { it.name }} 
        |Use '--give-consent-to-use-third-party-plugins' option in 'installPlugins' option to approve installed third-party plugins automatically.
        |""".trimMargin())
      disableThirdPartyPlugins()
    }
    else if (!askThirdPartyPluginsPrivacyConsent(aliens)) {
      logger.info("3rd-party plugin privacy note declined; disabling plugins")
      disableThirdPartyPlugins()
      pluginsState.thirdPartyPluginsNoteAccepted = false
    }
    else {
      pluginsState.thirdPartyPluginsNoteAccepted = true
    }
  }

  @Internal
  fun consumeThirdPartyPluginsNoteAcceptedFlag(): Boolean? {
    val result = pluginsState.thirdPartyPluginsNoteAccepted
    pluginsState.thirdPartyPluginsNoteAccepted = null
    return result
  }

  private fun askThirdPartyPluginsPrivacyConsent(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    val title = CoreBundle.message("third.party.plugins.privacy.note.title")
    val pluginList = descriptors.joinToString(separator = "<br>") { "&nbsp;&nbsp;&nbsp;${getPluginNameAndVendor(it)}" }
    val text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList, ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    val buttons = arrayOf(CoreBundle.message("third.party.plugins.privacy.note.accept"), CoreBundle.message("third.party.plugins.privacy.note.disable"))
    val icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.WarningDialog)
    val choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, icon, buttons, buttons.get(0))
    return choice == 0
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
    descriptorLoadingErrors: List<PluginDescriptorLoadingError>,
    initContext: PluginInitializationContext,
    discoveredPlugins: PluginsDiscoveryResult,
  ): PluginManagerState {
    val tracerShim = CoroutineTracerShim.coroutineTracer
    return tracerShim.span("plugin initialization") {
      val coreLoader = PluginManagerCore::class.java.classLoader
      val initResult = initializePlugins(
        descriptorLoadingErrors = descriptorLoadingErrors,
        initContext = initContext,
        discoveredPlugins = discoveredPlugins,
        coreLoader = coreLoader,
        parentActivity = tracerShim.getTraceActivity(),
      )
      val pluginState = pluginsState
      pluginState.pluginsToDisable = initResult.pluginToDisable
      pluginState.pluginsToEnable = initResult.pluginToEnable
      pluginState.shadowedBundledPlugins = initResult.shadowedBundledPlugins
      //activity.setDescription("plugin count: ${initResult.pluginSet.enabledPlugins.size}")
      pluginState.nullablePluginSet = initResult.pluginSet
      initResult
    }
  }

  // do not use class reference here
  @Suppress("SSBasedInspection")
  @get:Internal
  @JvmStatic
  val logger: Logger
    get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

  @Contract("null -> null")
  @JvmStatic
  fun getPlugin(id: PluginId?): IdeaPluginDescriptor? = if (id == null) null else findPlugin(id)

  @Internal
  @JvmStatic
  fun findPlugin(id: PluginId): IdeaPluginDescriptorImpl? {
    val pluginSet = pluginsState.nullablePluginSet ?: return null
    return pluginSet.findEnabledPlugin(id) ?: pluginSet.findInstalledPlugin(id)
  }

  @JvmStatic
  fun isPluginInstalled(id: PluginId): Boolean {
    val pluginSet = pluginsState.nullablePluginSet ?: return false
    return pluginSet.isPluginEnabled(id) || pluginSet.isPluginInstalled(id)
  }

  @Internal
  fun buildPluginIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> {
    // FIXME deduplicate with com.intellij.ide.plugins.ModulesWithDependenciesKt.createModulesWithDependenciesAndAdditionalEdges
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    return getPluginSet().buildPluginIdMap()
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * @return `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @Internal
  fun processAllNonOptionalDependencyIds(
    rootDescriptor: IdeaPluginDescriptorImpl,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    consumer: (PluginId) -> FileVisitResult,
  ): Boolean = processAllNonOptionalDependencies(
    rootDescriptor = rootDescriptor,
    depProcessed = HashSet(),
    pluginIdMap = pluginIdMap,
    contentModuleIdMap = contentModuleIdMap,
  ) { pluginId, _ ->
    if (pluginId == null) FileVisitResult.CONTINUE else consumer(pluginId)
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * Returns `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @Internal
  fun processAllNonOptionalDependencies(
    rootDescriptor: IdeaPluginDescriptorImpl,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    consumer: (IdeaPluginDescriptorImpl) -> FileVisitResult,
  ): Boolean = processAllNonOptionalDependencies(
    rootDescriptor = rootDescriptor,
    depProcessed = HashSet(),
    pluginIdMap = pluginIdMap,
    contentModuleIdMap = contentModuleIdMap,
    consumer = { _, descriptor ->
      if (descriptor == null) FileVisitResult.CONTINUE else consumer(descriptor)
    },
  )

  @Deprecated("Use [processAllNonOptionalDependencyIds] instead, this function doesn't process dependencies on modules")
  @Internal
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

  @Internal
  @Synchronized
  @JvmStatic
  fun isUpdatedBundledPlugin(plugin: PluginDescriptor): Boolean = !plugin.isBundled && pluginsState.shadowedBundledPlugins.contains(plugin.getPluginId())

  @Internal
  fun dependsOnUltimateOptionally(pluginDescriptor: IdeaPluginDescriptor?): Boolean {
    if (pluginDescriptor == null || pluginDescriptor !is IdeaPluginDescriptorImpl || !isDisabled(ULTIMATE_PLUGIN_ID)) return false
    val pluginIdMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
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
  @Internal
  fun isRequiredForEssentialPlugin(pluginDescriptor: PluginMainDescriptor): Boolean {
    // FIXME id map building should be lifted out (likewise in other methods too)
    //  this method should actually be an extension on ActivePluginSet or something
    val initContext = ProductPluginInitContext()
    val pluginIdMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    for (essentialPluginId in initContext.essentialPlugins) {
      val essentialPlugin = pluginIdMap.get(essentialPluginId) ?: continue
      val isRequiredDependency = !processAllNonOptionalDependencies(essentialPlugin, pluginIdMap, contentModuleIdMap) { dependency ->
        if (dependency.getMainDescriptor() === pluginDescriptor) {
          logger.debug { "Plugin ${pluginDescriptor.pluginId} is required for essential plugin $essentialPluginId" }
          FileVisitResult.TERMINATE
        } else {
          FileVisitResult.CONTINUE
        }
      }
      if (isRequiredDependency) {
        return true
      }
    }
    return false
  }

  @Internal
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

  @Internal
  fun getStartupActionsPluginsToEnableDisable(): Pair<List<PluginStateChangeData>, List<PluginStateChangeData>> = pluginsState.getStartupActionsPluginsToEnableDisable()

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("The platform code should use [JAVA_PLUGIN_ALIAS_ID] instead, plugins aren't supposed to use this")
  @JvmField val JAVA_MODULE_ID: PluginId = JAVA_PLUGIN_ALIAS_ID

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginByClassName(className: String): PluginId? {
    val id = PluginUtils.getPluginDescriptorOrPlatformByClassName(className)?.getPluginId()
    return if (id == null || CORE_ID == id) null else id
  }

  @Internal
  @Deprecated("Moved to PluginUtils", replaceWith = ReplaceWith("PluginUtils.getPluginDescriptorOrPlatformByClassName(className)"))
  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: String): PluginDescriptor? {
    return PluginUtils.getPluginDescriptorOrPlatformByClassName(className)
  }

  @Deprecated("Use {@link #disablePlugin(PluginId)}", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun disablePlugin(id: String): Boolean = disablePlugin(PluginId.getId(id))

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #enablePlugin(PluginId)}", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun enablePlugin(id: String): Boolean = enablePlugin(PluginId.getId(id))

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link DisabledPluginsState#addDisablePluginListener} directly", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun addDisablePluginListener(listener: Runnable) {
    DisabledPluginsState.addDisablePluginListener(listener)
  }
  //</editor-fold>
}

private fun processAllNonOptionalDependencies(
  rootDescriptor: IdeaPluginDescriptorImpl,
  depProcessed: MutableSet<in IdeaPluginDescriptorImpl>,
  pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
  consumer: (PluginId?, IdeaPluginDescriptorImpl?) -> FileVisitResult,
): Boolean {
  fun processDependency(pluginId: PluginId?, moduleId: PluginModuleId?): Boolean {
    val descriptor = if (pluginId != null) pluginIdMap.get(pluginId) else contentModuleIdMap.get(moduleId)
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

@Internal
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
      .also { check(it.name == "lib") { "$it should be lib directory" } }
      .parent
  }
  else {
    // for now, we support only plugins that for some reason pack plugin.xml into JAR (e.g., kotlin)
    return null
  }
}

@Internal
fun pluginRequiresUltimatePluginButItsDisabled(plugin: PluginId): Boolean {
  val idMap = PluginManagerCore.buildPluginIdMap()
  val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
  return pluginRequiresUltimatePluginButItsDisabled(plugin, idMap, contentModuleIdMap)
}

@Internal
fun pluginRequiresUltimatePluginButItsDisabled(
  rootPlugin: IdeaPluginDescriptorImpl,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  if (!isDisabled(ULTIMATE_PLUGIN_ID)) {
    return false
  }
  return pluginRequiresUltimatePlugin(rootPlugin, pluginMap, contentModuleIdMap)
}

@Internal
fun pluginRequiresUltimatePluginButItsDisabled(
  plugin: PluginId,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  if (!isDisabled(ULTIMATE_PLUGIN_ID)) {
    return false
  }
  return pluginRequiresUltimatePlugin(plugin, pluginMap, contentModuleIdMap)
}

@Internal
fun pluginRequiresUltimatePlugin(
  plugin: PluginId,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  val rootDescriptor = pluginMap.get(plugin)
  if (rootDescriptor == null) {
    return false
  }
  return pluginRequiresUltimatePlugin(rootDescriptor, pluginMap, contentModuleMap)
}

@Internal
fun pluginRequiresUltimatePlugin(
  rootDescriptor: IdeaPluginDescriptorImpl,
  pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleMap: Map<PluginModuleId, ContentModuleDescriptor>,
): Boolean {
  return !processAllNonOptionalDependencies(rootDescriptor, pluginMap, contentModuleMap) { descriptorImpl ->
    when (descriptorImpl.pluginId) {
      ULTIMATE_PLUGIN_ID -> FileVisitResult.TERMINATE
      else -> FileVisitResult.CONTINUE
    }
  }
}

/**
 * Checks if the class is a part of the platform or included in a built-in plugin provided by the JetBrains vendor.
 */
@Internal
@IntellijInternalApi
fun isPlatformOrJetBrainsDistributionPlugin(aClass: Class<*>): Boolean {
  val classLoader = aClass.classLoader
  when {
    classLoader is PluginAwareClassLoader -> {
      val plugin = classLoader.pluginDescriptor
      return (plugin.isBundled || PluginManagerCore.isUpdatedBundledPlugin(plugin))
             && PluginManagerCore.isDevelopedByJetBrains(plugin)
    }
    PluginManagerCore.isRunningFromSources() -> {
      return true
    }
    else -> {
      return PluginUtils.getPluginDescriptorIfIdeaClassLoaderIsUsed(aClass) == null
    }
  }
}
