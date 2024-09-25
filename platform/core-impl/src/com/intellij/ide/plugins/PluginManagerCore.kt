// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplacePutWithAssignment", "ReplaceGetOrSet", "LoggingSimilarMessage")

package com.intellij.ide.plugins

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.DisabledPluginsState.Companion.invalidate
import com.intellij.ide.plugins.PluginManagerCore.loadedPlugins
import com.intellij.ide.plugins.PluginManagerCore.write3rdPartyPlugins
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
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.Java11Shim
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.nio.file.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.JOptionPane
import kotlin.io.path.name
import kotlin.streams.asSequence

@Suppress("SpellCheckingInspection")
private val QODANA_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("idea.qodana.thirdpartyplugins.accept")
private val FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("fleet.backend.third-party.plugins.accept")
private const val THIRD_PARTY_PLUGINS_FILE = "alien_plugins.txt"

@Volatile
private var thirdPartyPluginsNoteAccepted: Boolean? = null

/**
 * See [Plugin Model](https://youtrack.jetbrains.com/articles/IJPL-A-31/Plugin-Model) documentation.
 *
 * @implNote Prefer to use only JDK classes. Any post-start-up functionality should be placed in [PluginManager] class.
 */
object PluginManagerCore {
  const val META_INF: @NonNls String = "META-INF/"
  const val CORE_PLUGIN_ID: String = "com.intellij"

  @JvmField
  val CORE_ID: PluginId = PluginId.getId(CORE_PLUGIN_ID)

  @JvmField
  val JAVA_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.java")

  @JvmField
  val JAVA_MODULE_ID: PluginId = PluginId.getId("com.intellij.modules.java")
  const val PLUGIN_XML: String = "plugin.xml"
  const val PLUGIN_XML_PATH: String = META_INF + PLUGIN_XML

  @JvmField
  val ALL_MODULES_MARKER: PluginId = PluginId.getId("com.intellij.modules.all")
  const val VENDOR_JETBRAINS: String = "JetBrains"
  const val VENDOR_JETBRAINS_SRO: String = "JetBrains s.r.o."
  private const val MODULE_DEPENDENCY_PREFIX = "com.intellij.module"

  @JvmField
  val SPECIAL_IDEA_PLUGIN_ID: PluginId = PluginId.getId("IDEA CORE")
  const val DISABLE: @NonNls String = "disable"
  const val ENABLE: @NonNls String = "enable"
  const val EDIT: @NonNls String = "edit"

  @VisibleForTesting
  @Volatile
  @JvmField
  var isIgnoreCompatibility: Boolean = java.lang.Boolean.getBoolean("idea.ignore.plugin.compatibility")

  @Volatile
  var nullablePluginSet: PluginSet? = null
    private set
  private var pluginLoadingErrors: Map<PluginId, PluginLoadingError>? = null

  @VisibleForTesting
  @Volatile
  @JvmField
  var isUnitTestMode: Boolean = java.lang.Boolean.getBoolean("idea.is.unit.test")

  @Internal
  private val pluginErrors = ArrayList<Supplier<HtmlChunk>>()
  private var pluginsToDisable: Set<PluginId>? = null
  private var pluginsToEnable: Set<PluginId>? = null

  /**
   * Bundled plugins that were updated.
   * When we update a bundled plugin, it becomes non-bundled, so it is more challenging for analytics to use that data.
   */
  private var shadowedBundledPlugins: Set<PluginId> = Collections.emptySet()

  private var isRunningFromSources: Boolean? = null

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


  private val findLoadedClassHandle: MethodHandle by lazy(LazyThreadSafetyMode.NONE) {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    MethodHandles.lookup().unreflect(method)
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
  fun getPluginSet(): PluginSet = nullablePluginSet!!

  /**
   * Returns descriptors of plugins which are successfully loaded into the IDE.
   * The result is sorted in a way that if each plugin comes after the plugins it depends on.
   */
  @JvmStatic
  val loadedPlugins: List<IdeaPluginDescriptor>
    get() = getPluginSet().enabledPlugins

  @Internal
  fun getAndClearPluginLoadingErrors(): List<HtmlChunk> {
    synchronized(pluginErrors) {
      if (pluginErrors.isEmpty()) {
        return emptyList()
      }

      val errors = pluginErrors.map { it.get() }
      pluginErrors.clear()
      return errors
    }
  }

  @Internal
  @JvmStatic
  fun arePluginsInitialized(): Boolean = nullablePluginSet != null

  @Internal
  @JvmStatic
  fun setPluginSet(value: PluginSet) {
    nullablePluginSet = value
  }

  @JvmStatic
  fun isDisabled(pluginId: PluginId): Boolean = PluginEnabler.HEADLESS.isDisabled(pluginId)

  @Internal
  @JvmStatic
  fun disablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.disableById(setOf(id))

  @Internal
  @JvmStatic
  fun enablePlugin(id: PluginId): Boolean = PluginEnabler.HEADLESS.enableById(setOf(id))

  @Internal
  @JvmStatic
  fun isModuleDependency(dependentPluginId: PluginId): Boolean {
    val idString = dependentPluginId.idString
    return idString.startsWith(MODULE_DEPENDENCY_PREFIX)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginByClassName(className: String): PluginId? {
    val id = getPluginDescriptorOrPlatformByClassName(className)?.getPluginId()
    return if (id == null || CORE_ID == id) null else id
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginOrPlatformByClassName(className: String): PluginId? = getPluginDescriptorOrPlatformByClassName(className)?.getPluginId()

  @Internal
  @JvmStatic
  fun isPlatformClass(className: @NonNls String): Boolean {
    return className.startsWith("java.") ||
           className.startsWith("javax.") ||
           className.startsWith("kotlin.") ||
           className.startsWith("groovy.")
  }

  @Internal
  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: @NonNls String): PluginDescriptor? {
    val pluginSet = nullablePluginSet
    if (pluginSet == null || isPlatformClass(className) || !className.contains('.')) {
      return null
    }

    var result: IdeaPluginDescriptorImpl? = null
    for (descriptor in pluginSet.getEnabledModules()) {
      val classLoader = descriptor.getPluginClassLoader()
      if (classLoader is UrlClassLoader) {
        if (classLoader.hasLoadedClass(className)) {
          result = descriptor
          break
        }
      }
      else if (classLoader != null && findLoadedClassHandle.invoke(classLoader, className) != null) {
        result = descriptor
        break
      }
    }

    if (result == null) {
      return null
    }

    // return if the found plugin is not `core`, or the package is unambiguously "core"
    if (CORE_ID != result.getPluginId() ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result
    }
    else {
      return findClassInPluginThatUsesCoreClassloader(className, pluginSet)
    }

    // otherwise, we need to check plugins with use-idea-classloader="true"
  }

  @Internal
  fun getPluginDescriptorIfIdeaClassLoaderIsUsed(aClass: Class<*>): PluginDescriptor? {
    val className = aClass.getName()
    val pluginSet = nullablePluginSet
    if (pluginSet == null || isPlatformClass(className) || !className.contains('.')) {
      return null
    }
    else {
      return findClassInPluginThatUsesCoreClassloader(className, pluginSet)
    }
  }

  private fun isVendorItemTrusted(vendorItem: String): Boolean {
    if (vendorItem.isEmpty()) {
      return false
    }
    return isVendorJetBrains(vendorItem) ||
           vendorItem == ApplicationInfoImpl.getShadowInstance().companyName ||
           vendorItem == ApplicationInfoImpl.getShadowInstance().shortCompanyName
  }

  @JvmStatic
  fun isVendorTrusted(vendor: String): Boolean {
    return vendor.splitToSequence(',').any { isVendorItemTrusted(it.trim()) }
  }

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
  fun isDevelopedByJetBrains(vendorString: String?): Boolean {
    if (vendorString == null) {
      return false
    }
    else if (isVendorJetBrains(vendorString)) {
      return true
    }
    return vendorString.splitToSequence(',').any { isVendorJetBrains(it.trim()) }
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

  private fun preparePluginErrors(globalErrorsSuppliers: List<Supplier<String>>): List<Supplier<HtmlChunk>> {
    val pluginLoadingErrors = pluginLoadingErrors ?: emptyMap()
    if (pluginLoadingErrors.isEmpty() && globalErrorsSuppliers.isEmpty()) {
      return emptyList()
    }

    // the log includes all messages, not only those which need to be reported to the user
    val loadingErrors = pluginLoadingErrors.entries.map { it.value }
    val logMessage = "Problems found loading plugins:\n  " +
                     (globalErrorsSuppliers.asSequence().map { it.get() } + loadingErrors.asSequence().map { it.internalMessage })
                       .joinToString(separator = "\n  ")
    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      if (!isUnitTestMode) {
        logger.warn(logMessage)
      }

      @Suppress("HardCodedStringLiteral")
      return (globalErrorsSuppliers.asSequence() +
              loadingErrors.asSequence().filter(PluginLoadingError::isNotifyUser).map(PluginLoadingError::detailedMessageSupplier))
        .map { Supplier { HtmlChunk.text(it!!.get()) } }
        .toList()
    }
    else if (PlatformUtils.isFleetBackend() && !SystemInfoRt.isMac) {
      logger.warn(logMessage) // some Mercury backend plugins cannot be loaded on Windows and Linux
    }
    else {
      logger.error(logMessage)
    }
    return emptyList()
  }

  fun getLoadingError(pluginId: PluginId): PluginLoadingError? = pluginLoadingErrors!!.get(pluginId)

  @Internal
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
        if (pluginIds!!.contains(descriptor.getPluginId())) {
          descriptor.isEnabled = enabled
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

  @Internal
  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope) {
    scheduleDescriptorLoading(
      coroutineScope = coroutineScope,
      zipFilePoolDeferred = CompletableDeferred(NonShareableJavaZipFilePool()),
      mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader),
      logDeferred = null,
    )
  }

  @Internal
  @Synchronized
  fun scheduleDescriptorLoading(
    coroutineScope: CoroutineScope,
    zipFilePoolDeferred: Deferred<ZipFilePool>,
    mainClassLoaderDeferred: Deferred<ClassLoader>?,
    logDeferred: Deferred<Logger>?,
  ): Deferred<PluginSet> {
    var result = initFuture
    if (result == null) {
      result = coroutineScope.scheduleLoading(
        zipFilePoolDeferred = zipFilePoolDeferred,
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
  @Internal
  fun getEnabledPluginRawList(): CompletableFuture<List<IdeaPluginDescriptorImpl>> =
    initFuture!!.asCompletableFuture().thenApply { it.enabledPlugins }

  @get:Internal
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

  private fun disableIncompatiblePlugins(descriptors: Collection<IdeaPluginDescriptorImpl>,
                                         idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                         errors: MutableMap<PluginId, PluginLoadingError>) {
    val selectedIds = System.getProperty("idea.load.plugins.id")
    val shouldLoadPlugins = System.getProperty("idea.load.plugins", "true").toBoolean()

    val explicitlyEnabled = if (selectedIds == null) null else {
      val set = HashSet<PluginId>()
      for (it in selectedIds.split(',')) {
        if (it.isNotEmpty()) {
          set.add(PluginId.getId(it))
        }
      }
      set.addAll(ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds())
      val selectedPlugins = LinkedHashSet<IdeaPluginDescriptorImpl>(set.size)
      for (id in set) {
        val descriptor = idMap.get(id) ?: continue
        selectedPlugins.add(descriptor)
        processAllNonOptionalDependencies(descriptor, idMap) { dependency ->
          selectedPlugins.add(dependency)
          FileVisitResult.CONTINUE
        }
      }
      selectedPlugins
    }

    val coreDescriptor = idMap.get(CORE_ID)
    for (descriptor in descriptors) {
      if (descriptor === coreDescriptor) {
        continue
      }
      if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor)) {
          descriptor.isEnabled = false
          logger.info("Plugin '" + descriptor.getName() + "' is not in 'idea.load.plugins.id' system property")
        }
      }
      else if (!shouldLoadPlugins) {
        descriptor.isEnabled = false
        errors.put(descriptor.getPluginId(), PluginLoadingError(descriptor,
                                                                message("plugin.loading.error.long.plugin.loading.disabled", descriptor.getName()),
                                                                message("plugin.loading.error.short.plugin.loading.disabled")))
      }
    }

    if (explicitlyEnabled == null && shouldLoadPlugins) {
      for (essentialId in ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds()) {
        val essentialPlugin = idMap[essentialId] ?: continue
        for (incompatibleId in essentialPlugin.incompatibilities) {
          val incompatiblePlugin = idMap[incompatibleId] ?: continue
          if (incompatiblePlugin.isEnabled) {
            incompatiblePlugin.isEnabled = false
            logger.info("Plugin '${incompatiblePlugin.name}' conflicts with required plugin '${essentialPlugin.name}', hence disabled")
          }
        }
      }
    }
  }

  @JvmStatic
  fun isCompatible(descriptor: IdeaPluginDescriptor): Boolean = isCompatible(descriptor = descriptor, buildNumber = null)

  fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean =
    !isIncompatible(descriptor = descriptor, buildNumber = buildNumber)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor): Boolean = isIncompatible(descriptor = descriptor, buildNumber = null)

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean =
    checkBuildNumberCompatibility(descriptor, buildNumber ?: PluginManagerCore.buildNumber) != null

  fun getIncompatibleOs(descriptor: IdeaPluginDescriptor): IdeaPluginOsRequirement? =
    descriptor.getDependencies().asSequence()
      .map { IdeaPluginOsRequirement.fromModuleId(it.pluginId) }
      .firstOrNull { p -> p != null && !p.isHostOs() }

  @JvmStatic
  fun checkBuildNumberCompatibility(descriptor: IdeaPluginDescriptor, ideBuildNumber: BuildNumber): PluginLoadingError? {
    val incompatibleOs = getIncompatibleOs(descriptor)
    if (incompatibleOs != null) {
      return PluginLoadingError(descriptor,
                                message("plugin.loading.error.long.incompatible.with.platform", descriptor.getName(),
                                        descriptor.getVersion(), incompatibleOs, SystemInfo.getOsName()),
                                message("plugin.loading.error.short.incompatible.with.platform", incompatibleOs))
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
          return PluginLoadingError(
            plugin = descriptor,
            detailedMessageSupplier = message("plugin.loading.error.long.incompatible.since.build", pluginName,
                                              descriptor.getVersion(), sinceBuild, ideBuildNumber),
            shortMessageSupplier = message("plugin.loading.error.short.incompatible.since.build", sinceBuild),
          )
        }
      }

      val untilBuild = descriptor.getUntilBuild()
      if (untilBuild != null) {
        val pluginName = descriptor.getName()
        val untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null)
        if (untilBuildNumber != null && untilBuildNumber < ideBuildNumber) {
          return PluginLoadingError(descriptor,
                                    message("plugin.loading.error.long.incompatible.until.build", pluginName,
                                            descriptor.getVersion(), untilBuild, ideBuildNumber),
                                    message("plugin.loading.error.short.incompatible.until.build", untilBuild))
        }
      }
    }
    catch (e: Exception) {
      logger.error(e)
      return PluginLoadingError(descriptor,
                                message("plugin.loading.error.long.failed.to.load.requirements.for.ide.version",
                                        descriptor.getName()),
                                message("plugin.loading.error.short.failed.to.load.requirements.for.ide.version"))
    }
    return null
  }

  private fun checkEssentialPluginsAreAvailable(idMap: Map<PluginId, IdeaPluginDescriptorImpl>) {
    val required = ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds()
    var missing: MutableList<String>? = null
    for (id in required) {
      val descriptor = idMap.get(id)
      if (descriptor == null || !descriptor.isEnabled()) {
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

  @Internal
  var pluginDescriptorDebugData: PluginDescriptorsDebugData? = null

  fun initializePlugins(context: DescriptorListLoadingContext,
                        loadingResult: PluginLoadingResult,
                        coreLoader: ClassLoader,
                        checkEssentialPlugins: Boolean,
                        parentActivity: Activity?): PluginManagerState {
    val pluginErrorsById = loadingResult.copyPluginErrors()
    val globalErrors = context.copyGlobalErrors()
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
    if (checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw EssentialPluginMissingException(listOf("$CORE_ID (platform prefix: ${System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)})"))
    }

    check3rdPartyPluginsPrivacyConsent(parentActivity, idMap)

    val pluginSetBuilder = PluginSetBuilder(loadingResult.enabledPluginsById.values)
    disableIncompatiblePlugins(descriptors = pluginSetBuilder.unsortedPlugins, idMap = idMap, errors = pluginErrorsById)
    pluginSetBuilder.checkPluginCycles(globalErrors)
    val pluginsToDisable = HashMap<PluginId, String>()
    val pluginsToEnable = HashMap<PluginId, String>()
    pluginSetBuilder.computeEnabledModuleMap { descriptor ->
      val disabledPlugins = context.disabledPlugins
      val loadingError = pluginSetBuilder.initEnableState(descriptor = descriptor,
                                                          idMap = idMap,
                                                          disabledPlugins = disabledPlugins,
                                                          errors = pluginErrorsById)
      val pluginId = descriptor.getPluginId()
      val isLoadable = loadingError == null
      if (!isLoadable) {
        pluginErrorsById.put(pluginId, loadingError!!)
        pluginsToDisable.put(pluginId, descriptor.getName())
        val disabledDependencyId = loadingError.disabledDependency
        if (disabledDependencyId != null && disabledPlugins.contains(disabledDependencyId)) {
          pluginsToEnable.put(disabledDependencyId, idMap.get(disabledDependencyId)!!.getName())
        }
      }
      descriptor.isEnabled = (descriptor.isEnabled() && isLoadable && !context.expiredPlugins.contains(pluginId))
      !descriptor.isEnabled()
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

    if (checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap)
    }

    val pluginSet = pluginSetBuilder.createPluginSet(incompletePlugins = loadingResult.getIncompleteIdMap().values)
    ClassLoaderConfigurator(pluginSet = pluginSet, coreLoader = coreLoader).configure()
    pluginDescriptorDebugData = context.debugData
    return PluginManagerState(pluginSet = pluginSet, pluginIdsToDisable = pluginsToDisable.keys, pluginIdsToEnable = pluginsToEnable.keys)
  }

  /**
   * processes postponed consent check from the previous run (e.g., when the previous run was headless)
   * see usages of [write3rdPartyPlugins]
    */
  private fun check3rdPartyPluginsPrivacyConsent(parentActivity: Activity?, idMap: Map<PluginId, IdeaPluginDescriptorImpl>) {
    val activity = parentActivity?.startChild("3rd-party plugins consent")

    val aliens = ArrayList<IdeaPluginDescriptorImpl>()
    for (id in get3rdPartyPluginIds()) {
      val pluginDescriptor = idMap.get(id) ?: continue
      aliens.add(pluginDescriptor)
    }
    if (!aliens.isEmpty()) {
      check3rdPartyPluginsPrivacyConsent(aliens)
    }

    activity?.end()
  }

  private fun check3rdPartyPluginsPrivacyConsent(aliens: List<IdeaPluginDescriptorImpl>) {
    fun disableThirdPartyPlugins() {
      for (descriptor in aliens) {
        descriptor.isEnabled = false
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
        descriptor.isEnabled = false
      }
      //write the list of third-party plugins back to ensure that the privacy note will be shown next time
      write3rdPartyPlugins(aliens)
    }
    else if (AppMode.isRemoteDevHost()) {
      logger.warn("""
        |New third-party plugins were installed, they will be disabled because asking for consent to use third-party plugins during startup isn't supported in remote development mode:
        | ${aliens.joinToString(separator = "\n ") { it.getName() }} 
        |Use '--give-consent-to-use-third-party-plugins' option in 'installPlugins' option to approve installed third-party plugins automatically.
        |""".trimMargin())
      disableThirdPartyPlugins()
    }
    else if (!ask3rdPartyPluginsPrivacyConsent(aliens)) {
      logger.info("3rd-party plugin privacy note declined; disabling plugins")
      disableThirdPartyPlugins()
      thirdPartyPluginsNoteAccepted = false
    }
    else {
      thirdPartyPluginsNoteAccepted = true
    }
  }
  
  @Internal
  @JvmStatic
  fun giveConsentToSpecificThirdPartyPlugins(acceptedPlugins: Set<PluginId>) {
    val notAcceptedThirdPartyPluginIds = get3rdPartyPluginIds() - acceptedPlugins
    if (notAcceptedThirdPartyPluginIds.isNotEmpty()) {
      writeThirdPartyPluginIds(notAcceptedThirdPartyPluginIds.asSequence())
    }
  }

  @Internal
  fun isThirdPartyPluginsNoteAccepted(): Boolean? {
    val result = thirdPartyPluginsNoteAccepted
    thirdPartyPluginsNoteAccepted = null
    return result
  }

  @Internal
  @Synchronized
  fun write3rdPartyPlugins(descriptors: Collection<IdeaPluginDescriptor>) {
    writeThirdPartyPluginIds(descriptors.asSequence().map { it.getPluginId() })
  }

  private fun writeThirdPartyPluginIds(pluginIds: Sequence<PluginId>) {
    val path = PathManager.getConfigDir().resolve(THIRD_PARTY_PLUGINS_FILE)
    try {
      writePluginIdsToFile(path = path,
                           pluginIds = pluginIds,
                           openOptions = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))
    }
    catch (e: IOException) {
      logger.error(path.toString(), e)
    }
  }

  @ReviseWhenPortedToJDK(value = "10", description = "Set.of")
  private fun get3rdPartyPluginIds(): Set<PluginId> {
    val path = PathManager.getConfigDir().resolve(THIRD_PARTY_PLUGINS_FILE)
    try {
      val ids = readPluginIdsFromFile(path)
      if (!ids.isEmpty()) {
        Files.delete(path)
      }
      return ids
    }
    catch (e: IOException) {
      logger.error(path.toString(), e)
      return emptySet()
    }
  }

  @Internal
  @Throws(IOException::class)
  fun writePluginIdsToFile(path: Path, pluginIds: Set<PluginId>, openOptions: Array<OpenOption>? = null) {
    writePluginIdsToFile(path = path, pluginIds = pluginIds.asSequence(), openOptions = openOptions)
  }

  @Internal
  fun tryWritePluginIdsToFile(path: Path, pluginIds: Set<PluginId>, logger: Logger, openOptions: Array<OpenOption>? = null): Boolean {
    try {
      writePluginIdsToFile(path = path, pluginIds = pluginIds, openOptions = openOptions)
      return true
    }
    catch (e: IOException) {
      logger.warn("Unable to write plugin id list to: $path", e)
      return false
    }
  }

  @ReviseWhenPortedToJDK(value = "10", description = "toUnmodifiableList")
  @Internal
  @Throws(IOException::class)
  fun writePluginIdsToFile(path: Path, pluginIds: Sequence<PluginId>, openOptions: Array<OpenOption>? = null) {
    writePluginIdsToFile(path = path, pluginIds = pluginIds.map { it.idString }.toList(), openOptions = openOptions)
  }

  @VisibleForTesting
  @Synchronized
  @Throws(IOException::class)
  fun writePluginIdsToFile(path: Path, pluginIds: Collection<String>, openOptions: Array<OpenOption>? = null) {
    NioFiles.createDirectories(path.parent)
    Files.write(path, TreeSet(pluginIds), *(openOptions ?: emptyArray()))
  }

  @ReviseWhenPortedToJDK(value = "10", description = "toUnmodifiableSet")
  @VisibleForTesting
  @JvmStatic
  fun toPluginIds(pluginIdStrings: Collection<String>): Set<PluginId> {
    return pluginIdStrings
      .asSequence()
      .map { it.trim() }
      .filterNot { it.isEmpty() }
      .map { PluginId.getId(it) }
      .toSet()
  }

  private fun ask3rdPartyPluginsPrivacyConsent(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    val title = CoreBundle.message("third.party.plugins.privacy.note.title")
    val pluginList = descriptors.joinToString(separator = "<br>") { "&nbsp;&nbsp;&nbsp;${getPluginNameAndVendor(it)}" }
    val text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList, ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    val buttons = arrayOf(CoreBundle.message("third.party.plugins.privacy.note.accept"),
                          CoreBundle.message("third.party.plugins.privacy.note.disable"))
    val choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                                              IconManager.getInstance().getPlatformIcon(PlatformIcons.WarningDialog), buttons, buttons[0])
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
      val duplicates = duplicateMap.get(id)
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
    duplicateMap.put(id, list)
    return duplicateMap
  }

  @JvmStatic
  fun getPluginNameAndVendor(descriptor: IdeaPluginDescriptor): @Nls String {
    val vendor = descriptor.getVendor() ?: descriptor.organization
    if (vendor.isNullOrEmpty()) {
      return CoreBundle.message("plugin.name.and.unknown.vendor", descriptor.getName())
    }
    else {
      return CoreBundle.message("plugin.name.and.vendor", descriptor.getName(), vendor)
    }
  }

  internal suspend fun initializeAndSetPlugins(context: DescriptorListLoadingContext, loadingResult: PluginLoadingResult): PluginSet {
    val tracerShim = CoroutineTracerShim.coroutineTracer
    return tracerShim.span("plugin initialization") {
      val initResult = initializePlugins(context = context,
                                         loadingResult = loadingResult,
                                         coreLoader = PluginManagerCore::class.java.classLoader,
                                         checkEssentialPlugins = !isUnitTestMode,
                                         parentActivity = tracerShim.getTraceActivity())
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
    val pluginSet = nullablePluginSet ?: return null
    return pluginSet.findEnabledPlugin(id) ?: pluginSet.findInstalledPlugin(id)
  }

  @Internal
  fun findPluginByModuleDependency(id: PluginId): IdeaPluginDescriptorImpl? {
    return getPluginSet().allPlugins.firstOrNull { it.pluginAliases.contains(id) }
  }

  @JvmStatic
  fun isPluginInstalled(id: PluginId): Boolean {
    val pluginSet = nullablePluginSet ?: return false
    return pluginSet.isPluginEnabled(id) || pluginSet.isPluginInstalled(id)
  }

  @Internal
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

  @Internal
  fun processAllNonOptionalDependencyIds(rootDescriptor: IdeaPluginDescriptorImpl,
                                         pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                         consumer: (PluginId) -> FileVisitResult) {
    processAllNonOptionalDependencies(rootDescriptor = rootDescriptor,
                                      depProcessed = HashSet(),
                                      pluginIdMap = pluginIdMap,
                                      consumer = { pluginId, _ -> consumer(pluginId) })
  }

  /**
   * **Note: ** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   * Returns `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @Internal
  fun processAllNonOptionalDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                        pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                        consumer: (IdeaPluginDescriptorImpl) -> FileVisitResult): Boolean {
    return processAllNonOptionalDependencies(
      rootDescriptor = rootDescriptor,
      depProcessed = HashSet(),
      pluginIdMap = pluginIdMap,
      consumer = { _, descriptor -> if (descriptor == null) FileVisitResult.CONTINUE else consumer(descriptor) },
    )
  }

  private fun processAllNonOptionalDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                                depProcessed: MutableSet<in IdeaPluginDescriptorImpl>,
                                                pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                                consumer: (PluginId, IdeaPluginDescriptorImpl?) -> FileVisitResult): Boolean {
    for (dependencyId in getNonOptionalDependenciesIds(rootDescriptor)) {
      val descriptor = pluginIdMap.get(dependencyId)
      val pluginId = descriptor?.getPluginId() ?: dependencyId
      when (consumer(pluginId, descriptor)) {
        FileVisitResult.TERMINATE -> return false
        FileVisitResult.CONTINUE -> {
          if (descriptor != null && depProcessed.add(descriptor)) {
            processAllNonOptionalDependencies(rootDescriptor = descriptor,
                                              depProcessed = depProcessed,
                                              pluginIdMap = pluginIdMap,
                                              consumer = consumer)
          }
        }
        FileVisitResult.SKIP_SUBTREE -> {}
        FileVisitResult.SKIP_SIBLINGS -> throw UnsupportedOperationException("FileVisitResult.SKIP_SIBLINGS is not supported")
      }
    }
    return true
  }

  @Internal
  fun getNonOptionalDependenciesIds(descriptor: IdeaPluginDescriptorImpl): Set<PluginId> {
    val dependencies = LinkedHashSet<PluginId>()
    for (dependency in descriptor.pluginDependencies) {
      if (!dependency.isOptional) {
        dependencies.add(dependency.pluginId)
      }
    }
    for (plugin in descriptor.dependencies.plugins) {
      dependencies.add(plugin.id)
    }
    return dependencies
  }

  @Internal
  @Synchronized
  @JvmStatic
  fun isUpdatedBundledPlugin(plugin: PluginDescriptor): Boolean = shadowedBundledPlugins.contains(plugin.getPluginId())

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use {@link #disablePlugin(PluginId)} ")
  @JvmStatic
  fun disablePlugin(id: String): Boolean = disablePlugin(PluginId.getId(id))

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #enablePlugin(PluginId)} ")
  @JvmStatic
  fun enablePlugin(id: String): Boolean = enablePlugin(PluginId.getId(id))

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link DisabledPluginsState#addDisablePluginListener} directly")
  @JvmStatic
  fun addDisablePluginListener(listener: Runnable) {
    DisabledPluginsState.addDisablePluginListener(listener)
  }
  //</editor-fold>
}

@Internal
class EssentialPluginMissingException internal constructor(@JvmField val pluginIds: List<String>)
  : RuntimeException("Missing essential plugins: ${pluginIds.joinToString(", ")}")

private fun message(key: @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String?, vararg params: Any?): @Nls Supplier<String> =
  Supplier { CoreBundle.message(key!!, *params) }

@Synchronized
@Internal
fun tryReadPluginIdsFromFile(path: Path, log: Logger): Set<PluginId> {
  try {
    return readPluginIdsFromFile(path)
  }
  catch (e: IOException) {
    log.warn("Unable to read plugin id list from: $path", e)
    return emptySet()
  }
}

@Synchronized
private fun readPluginIdsFromFile(path: Path): Set<PluginId> {
  try {
    Files.lines(path).use { lines ->
      return lines.asSequence()
        .map(String::trim)
        .filter { line -> !line.isEmpty() }
        .map { idString -> PluginId.getId(idString) }
        .toSet()
    }
  }
  catch (_: NoSuchFileException) {
    return emptySet()
  }
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
  actions.add(Supplier<HtmlChunk> { HtmlChunk.link(PluginManagerCore.DISABLE, disableMessage) })
  if (!pluginNamesToEnable.isEmpty()) {
    val pluginNameToEnable = pluginNamesToEnable.singleOrNull()
    val enableMessage = if (pluginNameToEnable != null) {
      CoreBundle.message("link.text.enable.plugin", pluginNameToEnable)
    }
    else {
      CoreBundle.message("link.text.enable.all.necessary.plugins")
    }
    actions.add(Supplier<HtmlChunk> { HtmlChunk.link(PluginManagerCore.ENABLE, enableMessage) })
  }
  actions.add(Supplier<HtmlChunk> { HtmlChunk.link(PluginManagerCore.EDIT, CoreBundle.message("link.text.open.plugin.manager")) })
  return actions
}

private fun findClassInPluginThatUsesCoreClassloader(className: @NonNls String, pluginSet: PluginSet): IdeaPluginDescriptorImpl? {
  var root: String? = null
  for (descriptor in pluginSet.enabledPlugins) {
    if (!descriptor.isUseIdeaClassLoader) {
      continue
    }

    if (root == null) {
      root = PathManager.getResourceRoot(descriptor.getClassLoader(), className.replace('.', '/') + ".class")
      if (root == null) {
        return null
      }
    }
    val path = descriptor.getPluginPath()
    if (root.startsWith(FileUtilRt.toSystemIndependentName(path.toString()))) {
      return descriptor
    }
  }
  return null
}

@Internal
@Experimental
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