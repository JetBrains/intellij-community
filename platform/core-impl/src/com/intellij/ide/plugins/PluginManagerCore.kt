// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.DisabledPluginsState.Companion.invalidate
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.ide.plugins.PluginManagerCore.DISABLE
import com.intellij.ide.plugins.PluginManagerCore.EDIT
import com.intellij.ide.plugins.PluginManagerCore.ENABLE
import com.intellij.ide.plugins.PluginManagerCore.ULTIMATE_PLUGIN_ID
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.ide.plugins.PluginManagerCore.loadedPlugins
import com.intellij.ide.plugins.PluginManagerCore.logger
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
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.Java11Shim
import com.intellij.util.lang.ZipEntryResolverPool
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
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
import java.util.*
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
    var pluginLoadingErrors: Map<PluginId, PluginNonLoadReason>? = null
    val pluginErrors: ArrayList<PluginLoadingError> = ArrayList<PluginLoadingError>()
    var pluginsToDisable: Set<PluginId>? = null
    var pluginsToEnable: Set<PluginId>? = null

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
    fun addPluginLoadingErrors(errors: List<PluginLoadingError>) {
      pluginErrors.addAll(errors)
    }

    @Synchronized
    fun getAndClearPluginLoadingErrors(): List<PluginLoadingError> {
      val result = pluginErrors.toList()
      pluginErrors.clear()
      return result
    }

    @Synchronized
    fun consumeStartupActionsPluginsToEnableDisable(): Pair<Set<PluginId>, Set<PluginId>> {
      val toEnable = pluginsToEnable ?: emptySet()
      val toDisable = pluginsToDisable ?: emptySet()
      pluginsToEnable = null
      pluginsToDisable = null
      return toEnable to toDisable
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
    return CORE_ID == plugin.getPluginId() || SPECIAL_IDEA_PLUGIN_ID == plugin.getPluginId() ||
           isDevelopedByJetBrains(plugin.getVendor()) ||
           isDevelopedByJetBrains(plugin.organization)
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
  private fun preparePluginErrors(globalErrors: List<PluginLoadingError>): List<PluginLoadingError> {
    val pluginLoadingErrors = pluginsState.pluginLoadingErrors ?: emptyMap()
    if (pluginLoadingErrors.isEmpty() && globalErrors.isEmpty()) {
      return emptyList()
    }

    // the log includes all messages, not only those which need to be reported to the user
    val loadingErrors = pluginLoadingErrors.values
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
  fun getLoadingError(pluginId: PluginId): PluginNonLoadReason? = pluginsState.pluginLoadingErrors!!.get(pluginId)

  @Internal
  fun clearLoadingErrorsFor(pluginId: PluginId) {
    pluginsState.pluginLoadingErrors = pluginsState.pluginLoadingErrors?.minus(pluginId)
  }

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
      result = coroutineScope.scheduleLoading(
        zipPoolDeferred = zipPoolDeferred,
        mainClassLoaderDeferred = mainClassLoaderDeferred,
        logDeferred = logDeferred,
      )
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
    discoveredPlugins: PluginDescriptorLoadingResult,
    coreLoader: ClassLoader,
    parentActivity: Activity?,
  ): PluginManagerState {
    val globalErrors = ArrayList<PluginLoadingError>()
    for (descriptorLoadingError in descriptorLoadingErrors) {
      globalErrors.add(PluginLoadingError(
        reason = null,
        htmlMessageSupplier = Supplier {
          HtmlChunk.text(CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor",
                                            PluginUtils.pluginPathToUserString(descriptorLoadingError.path)))
        },
        error = descriptorLoadingError.error,
      ))
    }

    val loadingResult = PluginLoadingResult()
    loadingResult.initAndAddAll(descriptorLoadingResult = discoveredPlugins, initContext = initContext)
    val pluginErrorsById = loadingResult.copyPluginErrors()
    if (loadingResult.duplicateModuleMap != null) {
      for ((key, value) in loadingResult.duplicateModuleMap!!) {
        globalErrors.add(PluginLoadingError(
          reason = null,
          htmlMessageSupplier = Supplier {
            HtmlChunk.text(CoreBundle.message("plugin.loading.error.module.declared.by.multiple.plugins",
                                              key,
                                              value.joinToString(separator = ("\n  ")) { it.toString() }))
          },
          error = null,
        ))
      }
    }

    val idMap = loadingResult.getIdMap()
    val fullIdMap = idMap + loadingResult.getIncompleteIdMap() +
                    loadingResult.getIncompleteIdMap().flatMap { (_, value) ->
                      value.pluginAliases.map { it to value }
                    }.toMap()
    val fullContentModuleIdMap = HashMap<PluginModuleId, ContentModuleDescriptor>()
    for (descriptor in loadingResult.getIncompleteIdMap().values) {
      descriptor.contentModules.associateByTo(fullContentModuleIdMap) { it.moduleId }
    }
    for (descriptor in idMap.values) {
      descriptor.contentModules.associateByTo(fullContentModuleIdMap) { it.moduleId }
    }

    if (initContext.checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw EssentialPluginMissingException(listOf("$CORE_ID (platform prefix: ${System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)})"))
        .apply { (pluginErrorsById.get(CORE_ID))?.let { addSuppressed(Exception(it.logMessage)) } }
    }

    checkThirdPartyPluginsPrivacyConsent(parentActivity, idMap)

    val pluginSetBuilder = PluginSetBuilder(loadingResult.getPluginsToAttemptLoading())
    selectPluginsForLoading(descriptors = pluginSetBuilder.unsortedPlugins, idMap = idMap, errors = pluginErrorsById, initContext = initContext)
    pluginSetBuilder.checkPluginCycles(globalErrors)
    val pluginsToDisable = HashMap<PluginId, String>()
    val pluginsToEnable = HashMap<PluginId, String>()
    
    fun registerLoadingError(loadingError: PluginNonLoadReason) {
      pluginErrorsById.put(loadingError.plugin.pluginId, loadingError)
      pluginsToDisable.put(loadingError.plugin.pluginId, loadingError.plugin.name)
      if (loadingError is PluginDependencyIsDisabled) {
        val disabledDependencyId = loadingError.dependencyId
        if (initContext.isPluginDisabled(disabledDependencyId)) {
          pluginsToEnable.put(disabledDependencyId, fullIdMap.get(disabledDependencyId)!!.getName())
        }
      }
    }

    val additionalErrors = pluginSetBuilder.computeEnabledModuleMap(
      incompletePlugins = loadingResult.getIncompleteIdMap().values,
      initContext = initContext,
      disabler = { descriptor, disabledModuleToProblematicPlugin ->
        val loadingError = pluginSetBuilder.initEnableState(
          descriptor = descriptor,
          idMap = idMap,
          fullIdMap = fullIdMap,
          fullContentModuleIdMap = fullContentModuleIdMap,
          isPluginDisabled = initContext::isPluginDisabled,
          errors = pluginErrorsById,
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

    val actions = prepareActions(pluginNamesToDisable = pluginsToDisable.values, pluginNamesToEnable = pluginsToEnable.values)
    pluginsState.pluginLoadingErrors = pluginErrorsById

    val errorList = preparePluginErrors(globalErrors)
    if (!errorList.isEmpty()) { // FIXME why actions is not checked here?
      pluginsState.addPluginLoadingErrors(errorList + actions.map { PluginLoadingError(reason = null, htmlMessageSupplier = it, error = null) })
    }

    if (initContext.checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap, initContext.essentialPlugins)
    }

    val pluginSet = pluginSetBuilder.createPluginSet(incompletePlugins = loadingResult.getIncompleteIdMap().values)
    ClassLoaderConfigurator(pluginSet, coreLoader).configure()
    return PluginManagerState(
      pluginSet = pluginSet,
      pluginIdsToDisable = pluginsToDisable.keys,
      pluginIdsToEnable = pluginsToEnable.keys,
      incompleteIdMapForLogging = loadingResult.getIncompleteIdMap(),
      shadowedBundledPlugins = loadingResult.shadowedBundledIds
    )
  }

  /**
   * processes postponed consent check from the previous run (e.g., when the previous run was headless)
   * see usages of [ThirdPartyPluginsWithoutConsentFile.appendAliens]
   */
  private fun checkThirdPartyPluginsPrivacyConsent(parentActivity: Activity?, idMap: Map<PluginId, IdeaPluginDescriptorImpl>) {
    val activity = parentActivity?.startChild("3rd-party plugins consent")
    val aliens = ThirdPartyPluginsWithoutConsentFile.consumeAliensFile().mapNotNull { idMap.get(it) }
    if (!aliens.isEmpty()) {
      checkThirdPartyPluginsPrivacyConsent(aliens)
    }
    activity?.end()
  }

  private fun checkEssentialPluginsAreAvailable(idMap: Map<PluginId, IdeaPluginDescriptorImpl>, essentialPlugins: Set<PluginId>) {
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
        missing.add(id.idString to pluginsState.pluginLoadingErrors?.get(id))
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
    discoveredPlugins: PluginDescriptorLoadingResult,
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
      pluginState.pluginsToDisable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToDisable)
      pluginState.pluginsToEnable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToEnable)
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
  fun consumeStartupActionsPluginsToEnableDisable(): Pair<Set<PluginId>, Set<PluginId>> = pluginsState.consumeStartupActionsPluginsToEnableDisable()

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

private fun selectPluginsForLoading(
  descriptors: Collection<PluginMainDescriptor>,
  idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  errors: MutableMap<PluginId, PluginNonLoadReason>,
  initContext: PluginInitializationContext,
) {
  if (initContext.explicitPluginSubsetToLoad != null) {
    val rootPluginsToLoad: Set<PluginId> = initContext.explicitPluginSubsetToLoad!!.toHashSet() + initContext.essentialPlugins
    val pluginsToLoad = LinkedHashSet<IdeaPluginDescriptorImpl>(rootPluginsToLoad.size)
    val contentModuleIdMap = HashMap<PluginModuleId, ContentModuleDescriptor>()
    for (descriptor in descriptors) {
      descriptor.contentModules.associateByTo(contentModuleIdMap) { it.moduleId }
    }
    for (id in rootPluginsToLoad) {
      val descriptor = idMap.get(id) ?: continue
      pluginsToLoad.add(descriptor)
      processAllNonOptionalDependencies(descriptor, idMap, contentModuleIdMap) { dependency ->
        pluginsToLoad.add(dependency)
        FileVisitResult.CONTINUE
      }
    }

    for (descriptor in descriptors) {
      if (descriptor.pluginId == CORE_ID) {
        continue
      }
      if (!pluginsToLoad.contains(descriptor)) {
        descriptor.isMarkedForLoading = false
        logger.info("Plugin '" + descriptor.getName() + "' is not in 'idea.load.plugins.id' system property and won't be loaded")
      }
    }
  }
  else if (initContext.disablePluginLoadingCompletely) {
    for (descriptor in descriptors) {
      if (descriptor.pluginId == CORE_ID) {
        continue
      }
      descriptor.isMarkedForLoading = false
      errors.put(descriptor.getPluginId(), PluginLoadingIsDisabledCompletely(descriptor))
    }
  }
  else {
    for (essentialId in initContext.essentialPlugins) {
      val essentialPlugin = idMap.get(essentialId) ?: continue
      for (incompatibleId in essentialPlugin.incompatiblePlugins) {
        val incompatiblePlugin = idMap.get(incompatibleId) ?: continue
        if (incompatiblePlugin.isMarkedForLoading) {
          incompatiblePlugin.isMarkedForLoading = false
          logger.info("Plugin '${incompatiblePlugin.name}' conflicts with required plugin '${essentialPlugin.name}' and won't be loaded")
        }
      }
    }
  }
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

private fun prepareActions(pluginNamesToDisable: Collection<String>, pluginNamesToEnable: Collection<String>): List<Supplier<HtmlChunk>> {
  if (pluginNamesToDisable.isEmpty()) {
    return emptyList()
  }

  val actions = ArrayList<Supplier<HtmlChunk>>()
  val pluginNameToDisable = pluginNamesToDisable.singleOrNull()
  val disableMessage = if (pluginNameToDisable != null) {
    CoreBundle.message("link.text.disable.plugin", pluginNameToDisable)
  }
  else {
    CoreBundle.message("link.text.disable.not.loaded.plugins")
  }
  actions.add(Supplier<HtmlChunk> { HtmlChunk.link(DISABLE, disableMessage) })
  if (!pluginNamesToEnable.isEmpty()) {
    val pluginNameToEnable = pluginNamesToEnable.singleOrNull()
    val enableMessage = if (pluginNameToEnable != null) {
      CoreBundle.message("link.text.enable.plugin", pluginNameToEnable)
    }
    else {
      CoreBundle.message("link.text.enable.all.necessary.plugins")
    }
    actions.add(Supplier<HtmlChunk> { HtmlChunk.link(ENABLE, enableMessage) })
  }
  actions.add(Supplier<HtmlChunk> { HtmlChunk.link(EDIT, CoreBundle.message("link.text.open.plugin.manager")) })
  return actions
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
