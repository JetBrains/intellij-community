// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.DisabledPluginsState.Companion.invalidate
import com.intellij.ide.plugins.PluginManagerCore.ULTIMATE_PLUGIN_ID
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.ide.plugins.PluginManagerCore.loadedPlugins
import com.intellij.ide.plugins.PluginManagerCore.processAllNonOptionalDependencies
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.Java11Shim
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
import java.awt.GraphicsEnvironment
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.JOptionPane
import kotlin.io.path.name

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

  private const val PLATFORM_ALIAS_DEPENDENCY_PREFIX = "com.intellij.module"

  @JvmField val CORE_ID: PluginId = PluginId.getId(CORE_PLUGIN_ID)
  @JvmField val JAVA_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.java")
  @JvmField val JAVA_MODULE_ID: PluginId = PluginId.getId("com.intellij.modules.java")
  @JvmField val ALL_MODULES_MARKER: PluginId = PluginId.getId("com.intellij.modules.all")
  @JvmField val SPECIAL_IDEA_PLUGIN_ID: PluginId = PluginId.getId("IDEA CORE")
  @ApiStatus.Internal
  @JvmField val ULTIMATE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.modules.ultimate")

  @VisibleForTesting
  @Volatile
  @JvmField
  var isIgnoreCompatibility: Boolean = java.lang.Boolean.getBoolean("idea.ignore.plugin.compatibility")

  @VisibleForTesting
  @Volatile
  @JvmField
  var isUnitTestMode: Boolean = java.lang.Boolean.getBoolean("idea.is.unit.test")

  @Volatile
  private var nullablePluginSet: PluginSet? = null
  private var pluginLoadingErrors: Map<PluginId, PluginNonLoadReason>? = null
  private val pluginErrors = ArrayList<Supplier<HtmlChunk>>()
  private var pluginsToDisable: Set<PluginId>? = null
  private var pluginsToEnable: Set<PluginId>? = null

  /**
   * Bundled plugins that were updated.
   * When we update a bundled plugin, it becomes non-bundled, so it is more challenging for analytics to use that data.
   */
  private var shadowedBundledPlugins: Set<PluginId> = Collections.emptySet()

  private var isRunningFromSources: Boolean? = null

  @Suppress("SpellCheckingInspection")
  private val QODANA_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("idea.qodana.thirdpartyplugins.accept")
  private val FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("fleet.backend.third-party.plugins.accept")

  @Volatile
  private var thirdPartyPluginsNoteAccepted: Boolean? = null

  @JvmStatic
  fun isRunningFromSources(): Boolean {
    var result = isRunningFromSources
    if (result == null) {
      result = Files.isDirectory(Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER))
      isRunningFromSources = result
    }
    return result
  }

  @Volatile
  private var initFuture: Deferred<PluginSet>? = null

  private var ourBuildNumber: BuildNumber? = null

  /**
   * Returns a list of all available plugin descriptors (bundled and custom, including disabled ones).
   * Use [loadedPlugins] if you need to get loaded plugins only.
   *
   * Do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by PluginClassLoader.
   */
  @JvmStatic
  val plugins: Array<IdeaPluginDescriptor>
    get() = getPluginSet().allPlugins.toTypedArray<IdeaPluginDescriptor>()

  @ApiStatus.Internal
  fun getPluginSet(): PluginSet = nullablePluginSet!!

  @ApiStatus.Internal
  fun getPluginSetOrNull(): PluginSet? = nullablePluginSet

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
  fun isLoaded(plugin: PluginDescriptor): Boolean = (plugin as? IdeaPluginDescriptorImpl)?.pluginClassLoader != null

  @ApiStatus.Internal
  fun getAndClearPluginLoadingErrors(): List<Supplier<HtmlChunk>> {
    synchronized(pluginErrors) {
      if (pluginErrors.isEmpty()) {
        return emptyList()
      }

      val errors = pluginErrors.toList()
      pluginErrors.clear()
      return errors
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun arePluginsInitialized(): Boolean = nullablePluginSet != null

  @ApiStatus.Internal
  @JvmStatic
  fun setPluginSet(value: PluginSet) {
    nullablePluginSet = value
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

  @ApiStatus.Internal
  @JvmStatic
  fun looksLikePlatformPluginAlias(pluginId: PluginId): Boolean {
    return pluginId.idString.startsWith(PLATFORM_ALIAS_DEPENDENCY_PREFIX)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun findPluginByPlatformAlias(id: PluginId): IdeaPluginDescriptorImpl? =
    getPluginSet().allPlugins.firstOrNull { it.pluginAliases.contains(id) }

  @ApiStatus.Internal
  @JvmStatic
  fun isPlatformClass(fqn: String): Boolean =
    fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("kotlin.") || fqn.startsWith("groovy.")

  private fun isVendorItemTrusted(vendorItem: String): Boolean =
    if (vendorItem.isEmpty()) false
    else isVendorJetBrains(vendorItem) ||
         vendorItem == ApplicationInfoImpl.getShadowInstance().companyName ||
         vendorItem == ApplicationInfoImpl.getShadowInstance().shortCompanyName

  @JvmStatic
  fun isVendorTrusted(vendor: String): Boolean =
    vendor.splitToSequence(',').any { isVendorItemTrusted(it.trim()) }

  @JvmStatic
  fun isVendorTrusted(plugin: PluginDescriptor): Boolean =
    isDevelopedByJetBrains(plugin) ||
    isVendorTrusted(plugin.vendor ?: "") ||
    isVendorTrusted(plugin.organization ?: "")

  @JvmStatic
  fun isDevelopedByJetBrains(plugin: PluginDescriptor): Boolean =
    CORE_ID == plugin.getPluginId() || SPECIAL_IDEA_PLUGIN_ID == plugin.getPluginId() ||
    isDevelopedByJetBrains(plugin.getVendor()) ||
    isDevelopedByJetBrains(plugin.organization)

  @JvmStatic
  fun isDevelopedByJetBrains(vendorString: String?): Boolean = when {
    vendorString == null -> false
    isVendorJetBrains(vendorString) -> true
    else -> vendorString.splitToSequence(',').any { isVendorJetBrains(it.trim()) }
  }

  @JvmStatic
  fun isVendorJetBrains(vendorItem: String): Boolean = VENDOR_JETBRAINS == vendorItem || VENDOR_JETBRAINS_SRO == vendorItem

  @Synchronized
  @JvmStatic
  fun invalidatePlugins() {
    nullablePluginSet = null
    val future = initFuture
    if (future != null) {
      initFuture = null
      future.cancel(CancellationException("invalidatePlugins"))
    }
    invalidate()
    shadowedBundledPlugins = Collections.emptySet()
  }

  @Suppress("LoggingSimilarMessage")
  private fun preparePluginErrors(globalErrorsSuppliers: List<Supplier<@Nls String>>): List<Supplier<HtmlChunk>> {
    val pluginLoadingErrors = pluginLoadingErrors ?: emptyMap()
    if (pluginLoadingErrors.isEmpty() && globalErrorsSuppliers.isEmpty()) {
      return emptyList()
    }
    // the log includes all messages, not only those which need to be reported to the user
    val loadingErrors = pluginLoadingErrors.values
    val logMessage =
      "Problems found loading plugins:\n  " +
      (globalErrorsSuppliers.asSequence().map { it.get() } + loadingErrors.asSequence().map { it.logMessage })
        .joinToString(separator = "\n  ")
    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      if (!isUnitTestMode) {
        logger.warn(logMessage)
      }
      else {
        logger.info(logMessage)
      }
      @Suppress("HardCodedStringLiteral") // drop after KTIJ-32161
      return (globalErrorsSuppliers.asSequence() + loadingErrors.asSequence().filter { it.shouldNotifyUser }.map { Supplier { it.detailedMessage } })
        .map { Supplier { HtmlChunk.text(it.get()) } }
        .toList()
    }
    else if (PlatformUtils.isFleetBackend()) {
      logger.warn(logMessage)
    }
    else {
      logger.error(logMessage)
    }
    return emptyList()
  }

  fun getLoadingError(pluginId: PluginId): PluginNonLoadReason? = pluginLoadingErrors!![pluginId]

  @ApiStatus.Internal
  @Synchronized
  @JvmStatic
  fun onEnable(enabled: Boolean): Boolean {
    val pluginIds = if (enabled) pluginsToEnable else pluginsToDisable
    pluginsToEnable = null
    pluginsToDisable = null
    val applied = pluginIds != null
    if (applied) {
      val descriptors = ArrayList<IdeaPluginDescriptorImpl>()
      for (descriptor in getPluginSet().allPlugins) {
        if (pluginIds.contains(descriptor.getPluginId())) {
          descriptor.isMarkedForLoading = enabled
          if (descriptor.moduleName == null) {
            descriptors.add(descriptor)
          }
        }
      }
      val pluginEnabler = PluginEnabler.getInstance()
      if (enabled) {
        pluginEnabler.enable(descriptors)
      }
      else {
        pluginEnabler.disable(descriptors)
      }
    }
    return applied
  }

  @ApiStatus.Internal
  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope) {
    scheduleDescriptorLoading(
      coroutineScope = coroutineScope,
      zipPoolDeferred = CompletableDeferred(NonShareableJavaZipFilePool()),
      mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader),
      logDeferred = null,
    )
  }

  @ApiStatus.Internal
  @Synchronized
  fun scheduleDescriptorLoading(
    coroutineScope: CoroutineScope,
    zipPoolDeferred: Deferred<ZipEntryResolverPool>,
    mainClassLoaderDeferred: Deferred<ClassLoader>?,
    logDeferred: Deferred<Logger>?,
  ): Deferred<PluginSet> {
    var result = initFuture
    if (result == null) {
      result = coroutineScope.scheduleLoading(
        zipPoolDeferred = zipPoolDeferred,
        mainClassLoaderDeferred = mainClassLoaderDeferred,
        logDeferred = logDeferred,
      )
      initFuture = result
    }
    return result
  }

  /**
   * Think twice before use and get an approval from the core team. Returns enabled plugins only.
   */
  @ApiStatus.Internal
  fun getEnabledPluginRawList(): CompletableFuture<List<IdeaPluginDescriptorImpl>> =
    initFuture!!.asCompletableFuture().thenApply { it.enabledPlugins }

  @get:ApiStatus.Internal
  val initPluginFuture: Deferred<PluginSet>
    get() = initFuture ?: throw IllegalStateException("Call scheduleDescriptorLoading() first")

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
  fun isCompatible(descriptor: IdeaPluginDescriptor): Boolean =
    isCompatible(descriptor, buildNumber = null)

  fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean =
    !isIncompatible(descriptor, buildNumber)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor): Boolean =
    isIncompatible(descriptor, buildNumber = null)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean =
    checkBuildNumberCompatibility(descriptor, buildNumber ?: PluginManagerCore.buildNumber) != null

  fun getUnfulfilledOsRequirement(descriptor: IdeaPluginDescriptor): IdeaPluginOsRequirement? =
    descriptor.getDependencies().asSequence()
      .map { IdeaPluginOsRequirement.fromModuleId(it.pluginId) }
      .firstOrNull { p -> p != null && !p.isHostOs() }

  @JvmStatic
  fun checkBuildNumberCompatibility(descriptor: IdeaPluginDescriptor, ideBuildNumber: BuildNumber): PluginNonLoadReason? {
    val requiredOs = getUnfulfilledOsRequirement(descriptor)
    if (requiredOs != null) {
      return PluginIsIncompatibleWithHostPlatform(descriptor, requiredOs, SystemInfo.getOsName())
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

  @ApiStatus.Internal
  fun initializePlugins(
    descriptorLoadingErrors: List<Supplier<@Nls String>>,
    initContext: PluginInitializationContext,
    loadingResult: PluginLoadingResult,
    coreLoader: ClassLoader,
    parentActivity: Activity?,
  ): PluginManagerState {
    val pluginErrorsById = loadingResult.copyPluginErrors()
    val globalErrors = descriptorLoadingErrors.toMutableList()
    if (loadingResult.duplicateModuleMap != null) {
      for ((key, value) in loadingResult.duplicateModuleMap!!) {
        globalErrors.add(Supplier {
          CoreBundle.message("plugin.loading.error.module.declared.by.multiple.plugins",
                             key,
                             value.joinToString(separator = ("\n  ")) { it.toString() })
        })
      }
    }

    val idMap = loadingResult.getIdMap()
    val fullIdMap = idMap + loadingResult.getIncompleteIdMap() +
                    loadingResult.getIncompleteIdMap().flatMap { (_, value) ->
                      value.pluginAliases.map { it to value }
                    }.toMap()


    if (initContext.checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw EssentialPluginMissingException(listOf("$CORE_ID (platform prefix: ${System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)})"))
    }

    checkThirdPartyPluginsPrivacyConsent(parentActivity, idMap)

    val pluginSetBuilder = PluginSetBuilder(loadingResult.enabledPluginsById.values.toSet())
    selectPluginsForLoading(descriptors = pluginSetBuilder.unsortedPlugins, idMap = idMap, errors = pluginErrorsById, initContext = initContext)
    pluginSetBuilder.checkPluginCycles(globalErrors)
    val pluginsToDisable = HashMap<PluginId, String>()
    val pluginsToEnable = HashMap<PluginId, String>()
    
    fun registerLoadingError(loadingError: PluginNonLoadReason) {
      pluginErrorsById[loadingError.plugin.pluginId] = loadingError
      pluginsToDisable[loadingError.plugin.pluginId] = loadingError.plugin.name
      if (loadingError is PluginDependencyIsDisabled) {
        val disabledDependencyId = loadingError.dependencyId
        if (initContext.isPluginDisabled(disabledDependencyId)) {
          pluginsToEnable[disabledDependencyId] = idMap[disabledDependencyId]!!.getName()
        }
      }
    }

    val additionalErrors = pluginSetBuilder.computeEnabledModuleMap(disabler = { descriptor ->
      val loadingError = pluginSetBuilder.initEnableState(descriptor, idMap, fullIdMap, initContext::isPluginDisabled, pluginErrorsById)
      if (loadingError != null) {
        registerLoadingError(loadingError)
      }
      if (loadingError != null || initContext.isPluginExpired(descriptor.getPluginId())) {
        descriptor.isMarkedForLoading = false
      }
      !descriptor.isMarkedForLoading
    })
    for (loadingError in additionalErrors) {
      registerLoadingError(loadingError)
    }

    val actions = prepareActions(pluginNamesToDisable = pluginsToDisable.values, pluginNamesToEnable = pluginsToEnable.values)
    pluginLoadingErrors = pluginErrorsById

    val errorList = preparePluginErrors(globalErrors)
    if (!errorList.isEmpty()) {
      synchronized(pluginErrors) {
        pluginErrors.addAll(errorList)
        pluginErrors.addAll(actions)
      }
    }

    if (initContext.checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap, initContext.essentialPlugins)
    }

    val pluginSet = pluginSetBuilder.createPluginSet(incompletePlugins = loadingResult.getIncompleteIdMap().values)
    ClassLoaderConfigurator(pluginSet, coreLoader).configure()
    return PluginManagerState(pluginSet, pluginIdsToDisable = pluginsToDisable.keys, pluginIdsToEnable = pluginsToEnable.keys)
  }

  /**
   * processes postponed consent check from the previous run (e.g., when the previous run was headless)
   * see usages of [com.intellij.ide.plugins.ThirdPartyPluginsWithoutConsentFile.appendAliens]
   */
  private fun checkThirdPartyPluginsPrivacyConsent(parentActivity: Activity?, idMap: Map<PluginId, IdeaPluginDescriptorImpl>) {
    val activity = parentActivity?.startChild("3rd-party plugins consent")
    val aliens = ThirdPartyPluginsWithoutConsentFile.consumeAliensFile().mapNotNull { idMap[it] }
    if (!aliens.isEmpty()) {
      checkThirdPartyPluginsPrivacyConsent(aliens)
    }
    activity?.end()
  }

  private fun selectPluginsForLoading(
    descriptors: Collection<IdeaPluginDescriptorImpl>,
    idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    errors: MutableMap<PluginId, PluginNonLoadReason>,
    initContext: PluginInitializationContext,
  ) {
    if (initContext.explicitPluginSubsetToLoad != null) {
      val rootPluginsToLoad: Set<PluginId> = initContext.explicitPluginSubsetToLoad!!.toHashSet() + initContext.essentialPlugins
      val pluginsToLoad = LinkedHashSet<IdeaPluginDescriptorImpl>(rootPluginsToLoad.size)
      for (id in rootPluginsToLoad) {
        val descriptor = idMap[id] ?: continue
        pluginsToLoad.add(descriptor)
        processAllNonOptionalDependencies(descriptor, idMap) { dependency ->
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
        errors[descriptor.getPluginId()] = PluginLoadingIsDisabledCompletely(descriptor)
      }
    }
    else {
      for (essentialId in initContext.essentialPlugins) {
        val essentialPlugin = idMap[essentialId] ?: continue
        for (incompatibleId in essentialPlugin.incompatiblePlugins) {
          val incompatiblePlugin = idMap[incompatibleId] ?: continue
          if (incompatiblePlugin.isMarkedForLoading) {
            incompatiblePlugin.isMarkedForLoading = false
            logger.info("Plugin '${incompatiblePlugin.name}' conflicts with required plugin '${essentialPlugin.name}' and won't be loaded")
          }
        }
      }
    }
  }

  private fun checkEssentialPluginsAreAvailable(idMap: Map<PluginId, IdeaPluginDescriptorImpl>, essentialPlugins: Set<PluginId>) {
    val corePlugin = idMap[CORE_ID]
    if (corePlugin != null) {
      val disabledModulesOfCorePlugin =
        corePlugin.content.modules
          .filter { it.loadingRule.required && !it.requireDescriptor().isMarkedForLoading }
      if (disabledModulesOfCorePlugin.isNotEmpty()) {
        throw EssentialPluginMissingException(disabledModulesOfCorePlugin.map { it.name })
      }
    }
    var missing: MutableList<String>? = null
    for (id in essentialPlugins) {
      val descriptor = idMap[id]
      if (descriptor == null || !descriptor.isMarkedForLoading) {
        if (missing == null) {
          missing = ArrayList()
        }
        missing.add(id.idString)
      }
    }
    if (missing != null) {
      throw EssentialPluginMissingException(missing)
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
        thirdPartyPluginsNoteAccepted = true
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
      thirdPartyPluginsNoteAccepted = false
    }
    else {
      thirdPartyPluginsNoteAccepted = true
    }
  }

  @ApiStatus.Internal
  fun consumeThirdPartyPluginsNoteAcceptedFlag(): Boolean? {
    val result = thirdPartyPluginsNoteAccepted
    thirdPartyPluginsNoteAccepted = null
    return result
  }

  private fun askThirdPartyPluginsPrivacyConsent(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    val title = CoreBundle.message("third.party.plugins.privacy.note.title")
    val pluginList = descriptors.joinToString(separator = "<br>") { "&nbsp;&nbsp;&nbsp;${getPluginNameAndVendor(it)}" }
    val text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList, ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    val buttons = arrayOf(CoreBundle.message("third.party.plugins.privacy.note.accept"), CoreBundle.message("third.party.plugins.privacy.note.disable"))
    val icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.WarningDialog)
    val choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, icon, buttons, buttons[0])
    return choice == 0
  }

  private fun checkAndPut(
    descriptor: IdeaPluginDescriptorImpl,
    id: PluginId,
    idMap: MutableMap<PluginId, IdeaPluginDescriptorImpl>,
    prevDuplicateMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>?,
  ): MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? {
    var duplicateMap = prevDuplicateMap
    if (duplicateMap != null) {
      val duplicates = duplicateMap[id]
      if (duplicates != null) {
        duplicates.add(descriptor)
        return duplicateMap
      }
    }
    val existingDescriptor = idMap.put(id, descriptor)
    if (existingDescriptor == null) {
      return null
    }

    // if duplicated, both are removed
    idMap.remove(id)
    if (duplicateMap == null) {
      duplicateMap = LinkedHashMap()
    }
    val list = ArrayList<IdeaPluginDescriptorImpl>()
    list.add(existingDescriptor)
    list.add(descriptor)
    duplicateMap[id] = list
    return duplicateMap
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
    descriptorLoadingErrors: List<Supplier<@Nls String>>,
    initContext: PluginInitializationContext,
    loadingResult: PluginLoadingResult,
  ): PluginSet {
    val tracerShim = CoroutineTracerShim.coroutineTracer
    return tracerShim.span("plugin initialization") {
      val coreLoader = PluginManagerCore::class.java.classLoader
      val initResult = initializePlugins(
        descriptorLoadingErrors = descriptorLoadingErrors,
        initContext = initContext,
        loadingResult = loadingResult,
        coreLoader = coreLoader,
        parentActivity = tracerShim.getTraceActivity()
      )
      pluginsToDisable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToDisable)
      pluginsToEnable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToEnable)
      shadowedBundledPlugins = loadingResult.shadowedBundledIds
      //activity.setDescription("plugin count: ${initResult.pluginSet.enabledPlugins.size}")
      nullablePluginSet = initResult.pluginSet
      initResult.pluginSet
    }
  }

  // do not use class reference here
  @Suppress("SSBasedInspection")
  @get:ApiStatus.Internal
  @JvmStatic
  val logger: Logger
    get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

  @Contract("null -> null")
  @JvmStatic
  fun getPlugin(id: PluginId?): IdeaPluginDescriptor? = if (id == null) null else findPlugin(id)

  @ApiStatus.Internal
  @JvmStatic
  fun findPlugin(id: PluginId): IdeaPluginDescriptorImpl? {
    val pluginSet = nullablePluginSet ?: return null
    return pluginSet.findEnabledPlugin(id) ?: pluginSet.findInstalledPlugin(id)
  }

  @JvmStatic
  fun isPluginInstalled(id: PluginId): Boolean {
    val pluginSet = nullablePluginSet ?: return false
    return pluginSet.isPluginEnabled(id) || pluginSet.isPluginInstalled(id)
  }

  @ApiStatus.Internal
  fun buildPluginIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    val idMap = HashMap<PluginId, IdeaPluginDescriptorImpl>(getPluginSet().allPlugins.size)
    var duplicateMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? = null
    for (descriptor in getPluginSet().allPlugins) {
      var newDuplicateMap = checkAndPut(descriptor, descriptor.getPluginId(), idMap, duplicateMap)
      if (newDuplicateMap != null) {
        duplicateMap = newDuplicateMap
        continue
      }
      for (pluginAlias in descriptor.pluginAliases) {
        newDuplicateMap = checkAndPut(descriptor = descriptor, id = pluginAlias, idMap = idMap, prevDuplicateMap = duplicateMap)
        if (newDuplicateMap != null) {
          duplicateMap = newDuplicateMap
        }
      }
    }
    return idMap
  }

  @ApiStatus.Internal
  fun processAllNonOptionalDependencyIds(rootDescriptor: IdeaPluginDescriptorImpl, pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>, consumer: (PluginId) -> FileVisitResult) {
    processAllNonOptionalDependencies(rootDescriptor, depProcessed = HashSet(), pluginIdMap, consumer = { pluginId, _ -> consumer(pluginId) })
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * Returns `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @ApiStatus.Internal
  fun processAllNonOptionalDependencies(
    rootDescriptor: IdeaPluginDescriptorImpl,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    consumer: (IdeaPluginDescriptorImpl) -> FileVisitResult,
  ): Boolean = processAllNonOptionalDependencies(rootDescriptor, depProcessed = HashSet(), pluginIdMap, consumer = { _, descriptor ->
    if (descriptor == null) FileVisitResult.CONTINUE else consumer(descriptor)
  })

  private fun processAllNonOptionalDependencies(
    rootDescriptor: IdeaPluginDescriptorImpl,
    depProcessed: MutableSet<in IdeaPluginDescriptorImpl>,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    consumer: (PluginId, IdeaPluginDescriptorImpl?) -> FileVisitResult,
  ): Boolean {
    for (dependencyId in getNonOptionalDependenciesIds(rootDescriptor)) {
      val descriptor = pluginIdMap[dependencyId]
      val pluginId = descriptor?.getPluginId() ?: dependencyId
      when (consumer(pluginId, descriptor)) {
        FileVisitResult.TERMINATE -> return false
        FileVisitResult.CONTINUE -> {
          if (descriptor != null && depProcessed.add(descriptor)) {
            if (!processAllNonOptionalDependencies(descriptor, depProcessed, pluginIdMap, consumer)) return false
          }
        }
        FileVisitResult.SKIP_SUBTREE -> {}
        FileVisitResult.SKIP_SIBLINGS -> throw UnsupportedOperationException("FileVisitResult.SKIP_SIBLINGS is not supported")
      }
    }
    return true
  }

  @ApiStatus.Internal
  fun getNonOptionalDependenciesIds(descriptor: IdeaPluginDescriptorImpl): Set<PluginId> {
    val dependencies = LinkedHashSet<PluginId>()
    for (dependency in descriptor.dependencies) {
      if (!dependency.isOptional) {
        dependencies.add(dependency.pluginId)
      }
    }
    for (plugin in descriptor.moduleDependencies.plugins) {
      dependencies.add(plugin.id)
    }
    return dependencies
  }

  @ApiStatus.Internal
  @Synchronized
  @JvmStatic
  fun isUpdatedBundledPlugin(plugin: PluginDescriptor): Boolean = shadowedBundledPlugins.contains(plugin.getPluginId())

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

  @ApiStatus.Internal
  fun dependsOnUltimateOptionally(pluginDescriptor: IdeaPluginDescriptor?): Boolean {
    if (pluginDescriptor == null || pluginDescriptor !is IdeaPluginDescriptorImpl || !isDisabled(ULTIMATE_PLUGIN_ID)) return false
    val idMap = buildPluginIdMap()
    return pluginDescriptor.content.modules.any {
      val descriptor = it.requireDescriptor()
      !it.loadingRule.required && !processAllNonOptionalDependencies(descriptor, idMap) { descriptorImpl ->
        when (descriptorImpl.pluginId) {
          ULTIMATE_PLUGIN_ID -> FileVisitResult.TERMINATE
          else -> FileVisitResult.CONTINUE
        }
      }
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginByClassName(className: String): PluginId? {
    val id = PluginUtils.getPluginDescriptorOrPlatformByClassName(className)?.getPluginId()
    return if (id == null || CORE_ID == id) null else id
  }

  @ApiStatus.Internal
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

@ApiStatus.Internal
fun getPluginDistDirByClass(aClass: Class<*>): Path? {
  val pluginDir = (aClass.classLoader as? PluginClassLoader)?.pluginDescriptor?.pluginPath
  if (pluginDir != null) {
    return pluginDir
  }

  val jarInsideLib = PathManager.getJarForClass(aClass) ?: error("Can't find plugin dist home for ${aClass.simpleName}")
  if (jarInsideLib.fileName.toString().endsWith("jar", ignoreCase = true)) {
    PathManager.getArchivedCompliedClassesLocation()?.let {
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

@ApiStatus.Internal
fun pluginRequiresUltimatePluginButItsDisabled(plugin: PluginId): Boolean {
  val idMap = PluginManagerCore.buildPluginIdMap()
  return pluginRequiresUltimatePluginButItsDisabled(plugin, idMap)
}

@ApiStatus.Internal
fun pluginRequiresUltimatePluginButItsDisabled(plugin: PluginId, pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>): Boolean {
  if (!isDisabled(ULTIMATE_PLUGIN_ID)) return false
  val rootDescriptor = pluginMap[plugin]
  if (rootDescriptor == null) return false
  return !processAllNonOptionalDependencies(rootDescriptor, pluginMap) { descriptorImpl ->
    when (descriptorImpl.pluginId) {
      ULTIMATE_PLUGIN_ID -> FileVisitResult.TERMINATE
      else -> FileVisitResult.CONTINUE
    }
  }
}