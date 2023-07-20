// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.core.CoreBundle
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.DisabledPluginsState.Companion.invalidate
import com.intellij.ide.plugins.IdeaPluginPlatform.Companion.fromModuleId
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.IconManager.Companion.getInstance
import com.intellij.ui.PlatformIcons
import com.intellij.util.Java11Shim.Companion.INSTANCE
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.file.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.*
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.swing.JOptionPane

/**
 * See [Plugin Model](https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md) documentation.
 *
 * @implNote Prefer to use only JDK classes. Any post start-up functionality should be placed in [PluginManager] class.
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
  private const val PLATFORM_DEPENDENCY_PREFIX = "com.intellij.platform"
  @JvmField
  val SPECIAL_IDEA_PLUGIN_ID: PluginId = PluginId.getId("IDEA CORE")
  const val DISABLE: @NonNls String = "disable"
  const val ENABLE: @NonNls String = "enable"
  const val EDIT: @NonNls String = "edit"

  @get:TestOnly
  @set:TestOnly
  @Volatile
  var isIgnoreCompatibility: Boolean = java.lang.Boolean.getBoolean("idea.ignore.plugin.compatibility")

  @Suppress("SpellCheckingInspection")
  private val QODANA_PLUGINS_THIRD_PARTY_ACCEPT = java.lang.Boolean.getBoolean("idea.qodana.thirdpartyplugins.accept")
  private const val THIRD_PARTY_PLUGINS_FILE = "alien_plugins.txt"

  @Volatile
  private var thirdPartyPluginsNoteAccepted: Boolean? = null

  @Volatile
  var nullablePluginSet: PluginSet? = null
    private set
  private var pluginLoadingErrors: Map<PluginId, PluginLoadingError?>? = null

  @VisibleForTesting
  @Volatile
  @JvmField
  var isUnitTestMode: Boolean = java.lang.Boolean.getBoolean("idea.is.unit.test")

  @Internal
  private val pluginErrors: MutableList<Supplier<out HtmlChunk>> = ArrayList()
  private var pluginsToDisable: Set<PluginId>? = null
  private var pluginsToEnable: Set<PluginId>? = null

  /**
   * Bundled plugins that were updated.
   * When we update a bundled plugin, it becomes non-bundled, so it is more difficult for analytics to use that data.
   */
  private var shadowedBundledPlugins: Set<PluginId>? = null

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

  @JvmStatic
  val plugins: Array<IdeaPluginDescriptor>
    /**
     * Returns list of all available plugin descriptors (bundled and custom, including disabled ones).
     * Use [.getLoadedPlugins] if you need to get loaded plugins only.
     *
     *
     * Do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by PluginClassLoader.
     */
    get() = getPluginSet().allPlugins.toTypedArray<IdeaPluginDescriptor>()

  @Internal
  fun getPluginSet(): PluginSet = nullablePluginSet!!

  @JvmStatic
  val loadedPlugins: List<IdeaPluginDescriptor>
    /**
     * Returns descriptors of plugins which are successfully loaded into the IDE.
     * The result is sorted in a way that if each plugin comes after the plugins it depends on.
     */
    get() = getPluginSet().enabledPlugins

  @Internal
  fun getAndClearPluginLoadingErrors(): List<HtmlChunk> {
    synchronized(pluginErrors) {
      if (pluginErrors.isEmpty()) {
        return emptyList()
      }
      val errors: MutableList<HtmlChunk> = ArrayList(pluginErrors.size)
      for (t in pluginErrors) {
        errors.add(t.get())
      }
      pluginErrors.clear()
      return errors
    }
  }

  @Internal
  @JvmStatic
  fun arePluginsInitialized(): Boolean {
    return nullablePluginSet != null
  }

  @Internal
  @JvmStatic
  fun setPluginSet(value: PluginSet) {
    nullablePluginSet = value
  }

  @JvmStatic
  fun isDisabled(pluginId: PluginId): Boolean {
    return PluginEnabler.HEADLESS.isDisabled(pluginId)
  }

  @Internal
  @JvmStatic
  fun disablePlugin(id: PluginId): Boolean {
    return PluginEnabler.HEADLESS.disableById(setOf(id))
  }

  @Internal
  @JvmStatic
  fun enablePlugin(id: PluginId): Boolean {
    return PluginEnabler.HEADLESS.enableById(setOf(id))
  }

  @Internal
  @JvmStatic
  fun isModuleDependency(dependentPluginId: PluginId): Boolean {
    val idString = dependentPluginId.idString
    return (idString.startsWith(MODULE_DEPENDENCY_PREFIX)
            || idString.startsWith(PLATFORM_DEPENDENCY_PREFIX) && "com.intellij.platform.images" != idString)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginByClassName(className: String): PluginId? {
    val result = getPluginDescriptorOrPlatformByClassName(className)
    val id = result?.getPluginId()
    return if (id == null || CORE_ID == id) null else id
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link PluginManager#getPluginByClass}.")
  @JvmStatic
  fun getPluginOrPlatformByClassName(className: String): PluginId? {
    val result = getPluginDescriptorOrPlatformByClassName(className)
    return result?.getPluginId()
  }

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
    if (pluginSet == null || isPlatformClass(className) || !className.contains(".")) {
      return null
    }
    var result: IdeaPluginDescriptorImpl? = null
    for (descriptor in pluginSet.getEnabledModules()) {
      val classLoader = descriptor.getPluginClassLoader()
      if (classLoader is UrlClassLoader && classLoader.hasLoadedClass(className)) {
        result = descriptor
        break
      }
    }
    if (result == null) {
      return null
    }

    // return if the found plugin is not "core", or the package is obviously "core"
    return if (CORE_ID != result.getPluginId() ||
               className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
               className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
               className.startsWith("com.android.") ||
               className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      result
    }
    else findClassInPluginThatUsesCoreClassloader(className, pluginSet)

    // otherwise, we need to check plugins with use-idea-classloader="true"
  }

  private fun findClassInPluginThatUsesCoreClassloader(className: @NonNls String,
                                                       pluginSet: PluginSet): IdeaPluginDescriptorImpl? {
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
  fun getPluginDescriptorIfIdeaClassLoaderIsUsed(aClass: Class<*>): PluginDescriptor? {
    val className = aClass.getName()
    val pluginSet = nullablePluginSet
    return if (pluginSet == null || isPlatformClass(className) || !className.contains(".")) {
      null
    }
    else findClassInPluginThatUsesCoreClassloader(className, pluginSet)
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
    if (isVendorJetBrains(vendorString)) {
      return true
    }
    for (vendor in vendorString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
      val vendorItem = vendor.trim { it <= ' ' }
      if (isVendorJetBrains(vendorItem)) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun isVendorJetBrains(vendorItem: String): Boolean {
    return VENDOR_JETBRAINS == vendorItem || VENDOR_JETBRAINS_SRO == vendorItem
  }

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
    shadowedBundledPlugins = null
  }

  @ReviseWhenPortedToJDK(value = "10", description = "Collectors.toUnmodifiableList()")
  private fun preparePluginErrors(globalErrorsSuppliers: List<Supplier<String>>): List<Supplier<HtmlChunk>> {
    if (pluginLoadingErrors!!.isEmpty() && globalErrorsSuppliers.isEmpty()) {
      return ArrayList()
    }
    val globalErrors = globalErrorsSuppliers.map { it.get() }

    // a log includes all messages, not only those which need to be reported to the user
    val loadingErrors = pluginLoadingErrors!!.entries
      .stream()
      .sorted(java.util.Map.Entry.comparingByKey<PluginId, PluginLoadingError?>())
      .map(
        Function<Map.Entry<PluginId, PluginLoadingError?>, PluginLoadingError> { it.value })
      .collect(Collectors.toList<PluginLoadingError>())
    val logMessage = "Problems found loading plugins:\n  " +
                     Stream.concat(
                       globalErrors.stream(),
                       loadingErrors.stream()
                         .map(PluginLoadingError::internalMessage)
                     ).collect(Collectors.joining("\n  "))
    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      if (!isUnitTestMode) {
        logger.warn(logMessage)
      }
      return Stream.concat(
        globalErrors.stream(),
        loadingErrors.stream()
          .filter(PluginLoadingError::isNotifyUser)
          .map(PluginLoadingError::detailedMessage)
      ).map(Function { text: @NlsContexts.DetailedDescription String? -> Supplier { HtmlChunk.text(text!!) } })
        .collect(Collectors.toList())
    }
    else {
      logger.error(logMessage)
      return ArrayList()
    }
  }

  fun getLoadingError(pluginId: PluginId): PluginLoadingError? {
    return pluginLoadingErrors!!.get(pluginId)
  }

  private fun prepareActions(pluginNamesToDisable: Collection<String>,
                             pluginNamesToEnable: Collection<String>): List<Supplier<out HtmlChunk>> {
    if (pluginNamesToDisable.isEmpty()) {
      return emptyList()
    }
    val actions: MutableList<Supplier<out HtmlChunk>> = ArrayList()
    val pluginNameToDisable = ContainerUtil.getOnlyItem(pluginNamesToDisable)
    val disableMessage = if (pluginNameToDisable != null) CoreBundle.message("link.text.disable.plugin", pluginNameToDisable)
    else CoreBundle.message("link.text.disable.not.loaded.plugins")
    actions.add(Supplier<HtmlChunk> { HtmlChunk.link(DISABLE!!, disableMessage) })
    if (!pluginNamesToEnable.isEmpty()) {
      val pluginNameToEnable = ContainerUtil.getOnlyItem(pluginNamesToEnable)
      val enableMessage = if (pluginNameToEnable != null) CoreBundle.message("link.text.enable.plugin", pluginNameToEnable)
      else CoreBundle.message("link.text.enable.all.necessary.plugins")
      actions.add(Supplier<HtmlChunk> { HtmlChunk.link(ENABLE!!, enableMessage) })
    }
    actions.add(Supplier<HtmlChunk> { HtmlChunk.link(EDIT!!, CoreBundle.message("link.text.open.plugin.manager")) })
    return Collections.unmodifiableList(actions)
  }

  @Internal
  @Synchronized
  @JvmStatic
  fun onEnable(enabled: Boolean): Boolean {
    val pluginIds = if (enabled) pluginsToEnable else pluginsToDisable
    pluginsToEnable = null
    pluginsToDisable = null
    val applied = pluginIds != null
    if (applied) {
      val descriptors: MutableList<IdeaPluginDescriptorImpl> = ArrayList()
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

  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope) {
    scheduleDescriptorLoading(coroutineScope, null)
  }

  @Internal
  @Synchronized
  fun scheduleDescriptorLoading(coroutineScope: CoroutineScope,
                                zipFilePoolDeferred: Deferred<ZipFilePool>?): Deferred<PluginSet> {
    var result = initFuture
    if (result == null) {
      result = coroutineScope.scheduleLoading(zipFilePoolDeferred)
      initFuture = result
    }
    return result
  }

  @get:Internal
  val enabledPluginRawList: CompletableFuture<List<IdeaPluginDescriptorImpl>>
    /**
     * Think twice before use and get an approval from the core team. Returns enabled plugins only.
     */
    get() = initFuture!!.asCompletableFuture().thenApply(
      Function { it: PluginSet -> it.enabledPlugins })

  @get:Internal
  val initPluginFuture: Deferred<PluginSet>
    get() {
      val future = initFuture
      if (future == null) {
        throw IllegalStateException("Call scheduleDescriptorLoading() first")
      }
      return future
    }

  @JvmStatic
  val buildNumber: BuildNumber
    get() {
      var result = ourBuildNumber
      if (result == null) {
        result = BuildNumber.fromPluginsCompatibleBuild()
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
            catch (ignore: RuntimeException) {
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
    val selectedCategory = System.getProperty("idea.load.plugins.category")
    var explicitlyEnabled: MutableSet<IdeaPluginDescriptorImpl>? = null
    if (selectedIds != null) {
      val set: MutableSet<PluginId> = HashSet()
      for (it in selectedIds.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
        set.add(PluginId.getId(it))
      }
      set.addAll(ApplicationInfoImpl.getShadowInstance().getEssentialPluginsIds())
      explicitlyEnabled = LinkedHashSet(set.size)
      for (id in set) {
        val descriptor = idMap[id]
        if (descriptor != null) {
          explicitlyEnabled.add(descriptor)
        }
      }
    }
    else if (selectedCategory != null) {
      explicitlyEnabled = LinkedHashSet()
      for (descriptor in descriptors) {
        if (selectedCategory == descriptor.getCategory()) {
          explicitlyEnabled.add(descriptor)
        }
      }
    }
    if (explicitlyEnabled != null) {
      // add all required dependencies
      val nonOptionalDependencies: MutableList<IdeaPluginDescriptorImpl> = ArrayList()
      for (descriptor in explicitlyEnabled) {
        processAllNonOptionalDependencies(rootDescriptor = descriptor, pluginIdMap = idMap, consumer = { dependency ->
          nonOptionalDependencies.add(dependency!!)
          FileVisitResult.CONTINUE
        })
      }
      explicitlyEnabled.addAll(nonOptionalDependencies)
    }
    val coreDescriptor = idMap[CORE_ID]
    val shouldLoadPlugins = System.getProperty("idea.load.plugins", "true").toBoolean()
    for (descriptor in descriptors) {
      if (descriptor === coreDescriptor) {
        continue
      }
      if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor)) {
          descriptor.isEnabled = false
          logger.info("Plugin '" + descriptor.getName() + "' " +
                      if (selectedIds != null) "is not in 'idea.load.plugins.id' system property" else "category doesn't match 'idea.load.plugins.category' system property")
        }
      }
      else if (!shouldLoadPlugins) {
        descriptor.isEnabled = false
        errors[descriptor.getPluginId()] = PluginLoadingError(descriptor,
                                                              message("plugin.loading.error.long.plugin.loading.disabled",
                                                                      descriptor.getName()),
                                                              message("plugin.loading.error.short.plugin.loading.disabled"))
      }
    }
  }

  @JvmStatic
  fun isCompatible(descriptor: IdeaPluginDescriptor): Boolean {
    return isCompatible(descriptor, null)
  }

  fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean {
    return !isIncompatible(descriptor, buildNumber)
  }

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor): Boolean {
    return isIncompatible(descriptor, null)
  }

  @JvmStatic
  fun isIncompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean {
    return checkBuildNumberCompatibility(descriptor, buildNumber ?: PluginManagerCore.buildNumber) != null
  }

  @JvmStatic
  fun getIncompatiblePlatform(descriptor: IdeaPluginDescriptor): Optional<IdeaPluginPlatform?> {
    return descriptor.getDependencies().stream()
      .map(
        Function { d: IdeaPluginDependency -> fromModuleId(d.pluginId) })
      .filter(Predicate { p: IdeaPluginPlatform? -> p != null && !p.isHostPlatform() })
      .findFirst()
  }

  @JvmStatic
  fun checkBuildNumberCompatibility(descriptor: IdeaPluginDescriptor,
                                    ideBuildNumber: BuildNumber): PluginLoadingError? {
    val incompatiblePlatform = getIncompatiblePlatform(descriptor)
    if (incompatiblePlatform.isPresent) {
      val requiredPlatform = incompatiblePlatform.get()
      return PluginLoadingError(descriptor,
                                message("plugin.loading.error.long.incompatible.with.platform", descriptor.getName(),
                                        descriptor.getVersion(), requiredPlatform, SystemInfo.getOsName()),
                                message("plugin.loading.error.short.incompatible.with.platform", requiredPlatform))
    }
    if (isIgnoreCompatibility) {
      return null
    }
    try {
      val sinceBuild = descriptor.getSinceBuild()
      if (sinceBuild != null) {
        val pluginName = descriptor.getName()
        val sinceBuildNumber = BuildNumber.fromString(sinceBuild, pluginName, null)
        if (sinceBuildNumber != null && sinceBuildNumber.compareTo(ideBuildNumber) > 0) {
          return PluginLoadingError(descriptor,
                                    message("plugin.loading.error.long.incompatible.since.build", pluginName,
                                            descriptor.getVersion(), sinceBuild, ideBuildNumber),
                                    message("plugin.loading.error.short.incompatible.since.build", sinceBuild))
        }
      }
      val untilBuild = descriptor.getUntilBuild()
      if (untilBuild != null) {
        val pluginName = descriptor.getName()
        val untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null)
        if (untilBuildNumber != null && untilBuildNumber.compareTo(ideBuildNumber) < 0) {
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
    val required = ApplicationInfoImpl.getShadowInstance().getEssentialPluginsIds()
    var missing: MutableList<String>? = null
    for (id in required) {
      val descriptor = idMap[id]
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
        globalErrors.add(
          Supplier {
            CoreBundle.message("plugin.loading.error.module.declared.by.multiple.plugins", key,
                               value.stream().map(
                                 Function { obj: IdeaPluginDescriptorImpl -> obj.toString() }).collect(
                                 Collectors.joining("\n  ")))
          })
      }
    }
    val idMap = loadingResult.getIdMap()
    if (checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw EssentialPluginMissingException(listOf(
        CORE_ID.toString() + " (platform prefix: " +
        System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")"))
    }
    val activity = parentActivity?.startChild("3rd-party plugins consent")
    val aliens: MutableList<IdeaPluginDescriptorImpl> = ArrayList()
    for (id in get3rdPartyPluginIds()) {
      val pluginDescriptor = idMap[id]
      if (pluginDescriptor != null) {
        aliens.add(pluginDescriptor)
      }
    }
    if (!aliens.isEmpty()) {
      check3rdPartyPluginsPrivacyConsent(aliens)
    }
    activity?.end()
    val pluginSetBuilder = PluginSetBuilder(loadingResult.enabledPluginsById.values)
    disableIncompatiblePlugins(pluginSetBuilder.unsortedPlugins, idMap, pluginErrorsById)
    pluginSetBuilder.checkPluginCycles(globalErrors)
    val pluginsToDisable: MutableMap<PluginId, String> = HashMap()
    val pluginsToEnable: MutableMap<PluginId, String> = HashMap()
    pluginSetBuilder.computeEnabledModuleMap(Predicate<IdeaPluginDescriptorImpl> { descriptor: IdeaPluginDescriptorImpl ->
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
      descriptor.isEnabled = (descriptor.isEnabled()
                              && isLoadable
                              && !context.expiredPlugins.contains(pluginId))
      !descriptor.isEnabled()
    })
    val actions = prepareActions(pluginsToDisable.values,
                                 pluginsToEnable.values)
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
    val pluginSet = pluginSetBuilder.createPluginSet(loadingResult.getIncompleteIdMap().values)
    ClassLoaderConfigurator(pluginSet, coreLoader).configure()
    pluginDescriptorDebugData = context.debugData
    return PluginManagerState(pluginSet,
                              pluginsToDisable.keys,
                              pluginsToEnable.keys)
  }

  private fun check3rdPartyPluginsPrivacyConsent(aliens: List<IdeaPluginDescriptorImpl>) {
    if (GraphicsEnvironment.isHeadless()) {
      if (QODANA_PLUGINS_THIRD_PARTY_ACCEPT) {
        thirdPartyPluginsNoteAccepted = true
        return
      }
      logger.info("3rd-party plugin privacy note not accepted yet; disabling plugins for this headless session")
      aliens.forEach(
        Consumer { descriptor: IdeaPluginDescriptorImpl -> descriptor.isEnabled = false })
    }
    else if (!ask3rdPartyPluginsPrivacyConsent(aliens)) {
      logger.info("3rd-party plugin privacy note declined; disabling plugins")
      aliens.forEach(
        Consumer { descriptor: IdeaPluginDescriptorImpl -> descriptor.isEnabled = false })
      PluginEnabler.HEADLESS.disable(aliens)
      thirdPartyPluginsNoteAccepted = java.lang.Boolean.FALSE
    }
    else {
      thirdPartyPluginsNoteAccepted = java.lang.Boolean.TRUE
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
    val path = PathManager.getConfigDir().resolve(THIRD_PARTY_PLUGINS_FILE)
    try {
      writePluginIdsToFile(path = path,
                           pluginIds = descriptors.asSequence().map { it.getPluginId() },
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

  @ReviseWhenPortedToJDK(value = "10, 11", description = "toUnmodifiableSet, Set.of, String.isBlank")
  @Internal
  @Synchronized
  @Throws(
    IOException::class)
  fun readPluginIdsFromFile(path: Path): Set<PluginId> {
    try {
      Files.lines(path).use { lines ->
        return lines
          .map(Function { obj: String -> obj.trim { it <= ' ' } })
          .filter(Predicate { line: String -> !line.isEmpty() })
          .map(
            Function { idString: String? ->
              PluginId.getId(
                idString!!)
            })
          .collect(Collectors.toSet())
      }
    }
    catch (ignored: NoSuchFileException) {
      return emptySet()
    }
  }

  @Internal
  @Synchronized
  fun tryReadPluginIdsFromFile(path: Path,
                               logger: Logger): Set<PluginId> {
    try {
      return readPluginIdsFromFile(path)
    }
    catch (e: IOException) {
      logger.warn("Unable to read plugin id list from: $path", e)
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
    val result = HashSet<PluginId>()
    for (pluginIdString in pluginIdStrings) {
      val s = pluginIdString.trim()
      if (!s.isEmpty()) {
        result.add(PluginId.getId(s))
      }
    }
    return result
  }

  private fun ask3rdPartyPluginsPrivacyConsent(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    val title = CoreBundle.message("third.party.plugins.privacy.note.title")
    val pluginList = descriptors.stream()
      .map(
        Function { descriptor: IdeaPluginDescriptorImpl -> "&nbsp;&nbsp;&nbsp;" + getPluginNameAndVendor(descriptor) })
      .collect(Collectors.joining("<br>"))
    val text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList)
    val buttons = arrayOf(CoreBundle.message("third.party.plugins.privacy.note.accept"),
                          CoreBundle.message("third.party.plugins.privacy.note.disable"))
    val choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                                              getInstance().getPlatformIcon(PlatformIcons.WarningDialog), buttons, buttons[0])
    return choice == 0
  }

  private fun checkAndPut(descriptor: IdeaPluginDescriptorImpl,
                          id: PluginId,
                          idMap: MutableMap<PluginId, IdeaPluginDescriptorImpl>,
                          duplicateMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>?): MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? {
    var duplicateMap = duplicateMap
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
    val vendor = StringUtil.defaultIfEmpty(descriptor.getVendor(), descriptor.organization)
    return if (StringUtil.isNotEmpty(vendor)) CoreBundle.message("plugin.name.and.vendor", descriptor.getName(), vendor)
    else CoreBundle.message("plugin.name.and.unknown.vendor", descriptor.getName())
  }

  private fun message(key: @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String?, vararg params: Any): @Nls Supplier<String> {
    return Supplier { CoreBundle.message(key!!, *params) }
  }

  @Synchronized
  fun initializeAndSetPlugins(context: DescriptorListLoadingContext,
                              loadingResult: PluginLoadingResult,
                              coreLoader: ClassLoader): PluginSet {
    val activity = StartUpMeasurer.startActivity("plugin initialization")
    val initResult = initializePlugins(context, loadingResult, coreLoader, !isUnitTestMode, activity)
    pluginsToDisable = INSTANCE.copyOf(initResult.pluginIdsToDisable)
    pluginsToEnable = INSTANCE.copyOf(initResult.pluginIdsToEnable)
    shadowedBundledPlugins = INSTANCE.copyOf(loadingResult.shadowedBundledIds)
    activity.end()
    activity.setDescription("plugin count: " + initResult.pluginSet.enabledPlugins.size)
    nullablePluginSet = initResult.pluginSet
    return initResult.pluginSet
  }

  // do not use class reference here
  @Suppress("SSBasedInspection")
  @get:Internal
  @JvmStatic
  val logger: Logger
    get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

  @Contract("null -> null")
  @JvmStatic
  fun getPlugin(id: PluginId?): IdeaPluginDescriptor? {
    return if (id != null) findPlugin(id) else null
  }

  @Internal
  @JvmStatic
  fun findPlugin(id: PluginId): IdeaPluginDescriptorImpl? {
    val pluginSet = getPluginSet()
    val result = pluginSet.findEnabledPlugin(id)
    return result ?: pluginSet.findInstalledPlugin(id)
  }

  @Internal
  @JvmStatic
  fun findPluginByModuleDependency(id: PluginId): IdeaPluginDescriptorImpl? {
    for (descriptor in getPluginSet().allPlugins) {
      if (descriptor.modules.contains(id)) {
        return descriptor
      }
    }
    return null
  }

  @JvmStatic
  fun isPluginInstalled(id: PluginId): Boolean {
    val pluginSet = nullablePluginSet
    return if (pluginSet == null) {
      false
    }
    else pluginSet.isPluginEnabled(id) ||
         pluginSet.isPluginInstalled(id)
  }

  @Internal
  fun buildPluginIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    val idMap: MutableMap<PluginId, IdeaPluginDescriptorImpl> = HashMap(getPluginSet().allPlugins.size)
    var duplicateMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? = null
    for (descriptor in getPluginSet().allPlugins) {
      var newDuplicateMap = checkAndPut(descriptor, descriptor.getPluginId(), idMap, duplicateMap)
      if (newDuplicateMap != null) {
        duplicateMap = newDuplicateMap
        continue
      }
      for (module in descriptor.modules) {
        newDuplicateMap = checkAndPut(descriptor, module, idMap, duplicateMap)
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
   * **Note:** [FileVisitResult.SKIP_SIBLINGS] is not supported.
   *
   *
   * Returns `false` if processing was terminated because of [FileVisitResult.TERMINATE], and `true` otherwise.
   */
  @Internal
  fun processAllNonOptionalDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                        pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                        consumer: (IdeaPluginDescriptorImpl?) -> FileVisitResult): Boolean {
    return processAllNonOptionalDependencies(rootDescriptor = rootDescriptor,
                                             depProcessed = HashSet(),
                                             pluginIdMap = pluginIdMap,
                                             consumer = { _, descriptor -> consumer(descriptor) })
  }

  private fun processAllNonOptionalDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                                depProcessed: MutableSet<in IdeaPluginDescriptorImpl>,
                                                pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                                                consumer: (PluginId, IdeaPluginDescriptorImpl?) -> FileVisitResult): Boolean {
    for (dependencyId in getNonOptionalDependenciesIds(rootDescriptor)) {
      val descriptor = pluginIdMap.get(dependencyId)
      val pluginId = descriptor?.getPluginId() ?: dependencyId
      val result = consumer(pluginId, descriptor)
      when (result) {
        FileVisitResult.TERMINATE -> return false
        FileVisitResult.CONTINUE -> if (descriptor != null && depProcessed.add(descriptor)) {
          processAllNonOptionalDependencies(descriptor, depProcessed, pluginIdMap, consumer)
        }
        FileVisitResult.SKIP_SUBTREE -> {}
        FileVisitResult.SKIP_SIBLINGS -> throw UnsupportedOperationException("FileVisitResult.SKIP_SIBLINGS is not supported")
      }
    }
    return true
  }

  @Internal
  @JvmStatic
  fun getNonOptionalDependenciesIds(descriptor: IdeaPluginDescriptorImpl): Set<PluginId> {
    val dependencies: MutableSet<PluginId> = LinkedHashSet()
    for (dependency in descriptor.pluginDependencies) {
      if (!dependency.isOptional) {
        dependencies.add(dependency.pluginId)
      }
    }
    for (plugin in descriptor.dependencies.plugins) {
      dependencies.add(plugin.id)
    }
    return Collections.unmodifiableSet(dependencies)
  }

  @Internal
  @Synchronized
  @JvmStatic
  fun isUpdatedBundledPlugin(plugin: PluginDescriptor): Boolean {
    return shadowedBundledPlugins != null && shadowedBundledPlugins!!.contains(plugin.getPluginId())
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use {@link #disablePlugin(PluginId)} ")
  @JvmStatic
  fun disablePlugin(id: String): Boolean {
    return disablePlugin(PluginId.getId(id))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #enablePlugin(PluginId)} ")
  @JvmStatic
  fun enablePlugin(id: String): Boolean {
    return enablePlugin(PluginId.getId(id))
  }

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
