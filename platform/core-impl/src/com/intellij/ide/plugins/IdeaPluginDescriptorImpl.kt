// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.util.Java11Shim
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*

private val LOG: Logger
  get() = PluginManagerCore.logger

fun Iterable<IdeaPluginDescriptor>.toPluginIdSet(): Set<PluginId> = mapTo(LinkedHashSet()) { it.pluginId }

internal fun Iterable<PluginId>.toPluginDescriptors(): List<IdeaPluginDescriptorImpl> {
  val pluginIdMap = PluginManagerCore.buildPluginIdMap()
  return mapNotNull { pluginIdMap[it] }
}

internal fun Iterable<PluginId>.joinedPluginIds(operation: String): String =
  joinToString(prefix = "Plugins to $operation: [", postfix = "]") { it.idString }

@ApiStatus.Internal
class IdeaPluginDescriptorImpl(
  raw: RawPluginDescriptor,
  @JvmField val path: Path,
  private val isBundled: Boolean,
  id: PluginId?,
  @JvmField val moduleName: String?,
  @JvmField val moduleLoadingRule: ModuleLoadingRule? = null,
  @JvmField val useCoreClassLoader: Boolean = false,
  @JvmField var isDependentOnCoreClassLoader: Boolean = true,
) : IdeaPluginDescriptor {
  private val id: PluginId = id ?: PluginId.getId(raw.id ?: raw.name ?: throw RuntimeException("Neither id nor name are specified"))
  private val name = raw.name ?: id?.idString ?: raw.id

  @Suppress("EnumEntryName")
  enum class OS {
    mac, linux, windows, unix, freebsd
  }

  // only for sub descriptors
  @JvmField
  internal var descriptorPath: String? = null

  @Volatile
  private var description: String? = null
  private val productCode = raw.productCode
  private var releaseDate: Date? = raw.releaseDate?.let { Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
  private val releaseVersion = raw.releaseVersion
  private val isLicenseOptional = raw.isLicenseOptional

  @NonNls
  private var resourceBundleBaseName: String? = null
  private val changeNotes = raw.changeNotes
  private var version: String? = raw.version
  private var vendor = raw.vendor
  private val vendorEmail = raw.vendorEmail
  private val vendorUrl = raw.vendorUrl
  private var category: String? = raw.category

  @JvmField
  internal val url: String? = raw.url

  @JvmField
  val pluginDependencies: List<PluginDependency>

  @JvmField
  val incompatibilities: List<PluginId> = raw.incompatibilities ?: Java11Shim.INSTANCE.listOf()

  init {
    if (moduleName != null) {
      require(moduleLoadingRule != null) { "'moduleLoadingRule' parameter must be specified when creating a module descriptor, but it is missing for '$moduleName'" }
    }
    // https://youtrack.jetbrains.com/issue/IDEA-206274
    val list = raw.depends
    if (list.isNullOrEmpty()) {
      pluginDependencies = Java11Shim.INSTANCE.listOf()
    }
    else {
      val iterator = list.iterator()
      while (iterator.hasNext()) {
        val item = iterator.next()
        if (!item.isOptional) {
          for (a in list) {
            if (a.isOptional && a.pluginId == item.pluginId) {
              a.isOptional = false
              iterator.remove()
              break
            }
          }
        }
      }
      pluginDependencies = list
    }
  }

  @Transient
  @JvmField
  var jarFiles: List<Path>? = null
  private var _pluginClassLoader: ClassLoader? = null

  @JvmField
  val actions: List<RawPluginDescriptor.ActionDescriptor> = raw.actions ?: Java11Shim.INSTANCE.listOf()

  // extension point name -> list of extension descriptors
  @JvmField
  val epNameToExtensions: Map<String, List<ExtensionDescriptor>> = raw.epNameToExtensions.let { rawMap ->
    if (rawMap == null) {
      Java11Shim.INSTANCE.mapOf()
    }
    else if (rawMap.size < 2 || !rawMap.containsKey(registryEpName)) {
      rawMap
    }
    else {
      /*
       * What's going on: see `com.intellij.ide.plugins.DynamicPluginsTest.registry access of key from same plugin`
       * This is an ad-hoc solution to the problem, it doesn't fix the root cause. This may also break if this map gets copied
       * or transformed into a HashMap somewhere, but it seems it's not the case right now.
       * TODO: one way to make a better fix is to introduce loadingOrder on extension points (as it is made for extensions).
       */
      val result = LinkedHashMap<String, List<ExtensionDescriptor>>(rawMap.size)
      val keys = rawMap.keys.toTypedArray()
      keys.sortWith(extensionPointNameComparator)
      for (key in keys) {
        result.put(key, rawMap[key]!!)
      }
      result
    }
  }

  @JvmField
  val appContainerDescriptor: ContainerDescriptor = raw.appContainerDescriptor

  @JvmField
  val projectContainerDescriptor: ContainerDescriptor = raw.projectContainerDescriptor

  @JvmField
  val moduleContainerDescriptor: ContainerDescriptor = raw.moduleContainerDescriptor

  @JvmField
  val content: PluginContentDescriptor =
    raw.contentModules.takeIf { !it.isNullOrEmpty() }?.let { PluginContentDescriptor(it) }
    ?: PluginContentDescriptor.EMPTY

  @JvmField
  val dependencies: ModuleDependenciesDescriptor = raw.dependencies

  @JvmField
  var pluginAliases: List<PluginId> = raw.pluginAliases ?: Java11Shim.INSTANCE.listOf()

  private val descriptionChildText = raw.description

  @JvmField
  val isUseIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader

  @JvmField
  val isBundledUpdateAllowed: Boolean = raw.isBundledUpdateAllowed

  @JvmField
  internal val implementationDetail: Boolean = raw.implementationDetail

  @JvmField
  internal val isRestartRequired: Boolean = raw.isRestartRequired

  @JvmField
  val packagePrefix: String? = raw.`package`

  private val sinceBuild = raw.sinceBuild
  private val untilBuild = raw.untilBuild
  private var isEnabled = true

  var isDeleted: Boolean = false

  @JvmField
  internal var isIncomplete: PluginLoadingError? = null

  override fun getDescriptorPath(): String? = descriptorPath

  override fun getDependencies(): List<IdeaPluginDependency> = pluginDependencies

  override fun getPluginPath(): Path = path

  internal fun createSub(
    raw: RawPluginDescriptor,
    descriptorPath: String,
    context: DescriptorListLoadingContext,
    module: PluginContentDescriptor.ModuleItem?,
  ): IdeaPluginDescriptorImpl {
    raw.name = name
    val result = IdeaPluginDescriptorImpl(raw, path, isBundled, id, module?.name, module?.loadingRule, useCoreClassLoader, raw.isDependentOnCoreClassLoader)
    context.debugData?.recordDescriptorPath(descriptor = result, rawPluginDescriptor = raw, path = descriptorPath)
    result.descriptorPath = descriptorPath
    result.vendor = vendor
    result.resourceBundleBaseName = resourceBundleBaseName
    if (raw.resourceBundleBaseName != null) {
      result.readResourceBundleBaseName(raw)
    }
    result.version = version ?: context.defaultVersion
    return result
  }

  internal fun initByRawDescriptor(raw: RawPluginDescriptor, context: DescriptorListLoadingContext, pathResolver: PathResolver, dataLoader: DataLoader) {
    if (raw.resourceBundleBaseName != null) {
      if (id == PluginManagerCore.CORE_ID) {
        LOG.warn("<resource-bundle>${raw.resourceBundleBaseName}</resource-bundle> tag is found in an xml descriptor" +
                 " included into the platform part of the IDE but the platform part uses predefined bundles " +
                 "(e.g. ActionsBundle for actions) anyway; this tag must be replaced by a corresponding attribute in some inner tags " +
                 "(e.g. by 'resource-bundle' attribute in 'actions' tag)")
      }
      readResourceBundleBaseName(raw)
    }

    if (version == null) {
      version = context.defaultVersion
    }

    if (id == PluginManagerCore.CORE_ID) {
      pluginAliases = pluginAliases + IdeaPluginOsRequirement.getHostOsModuleIds()
      pluginAliases = pluginAliases + productModeAliasesForCorePlugin()
    }

    if (context.isPluginDisabled(id)) {
      markAsIncomplete(disabledDependency = null, shortMessage = null)
    }
    else {
      checkCompatibility(context)
      if (isIncomplete != null) {
        return
      }

      for (pluginDependency in dependencies.plugins) {
        if (context.isPluginDisabled(pluginDependency.id)) {
          markAsIncomplete(pluginDependency.id, shortMessage = "plugin.loading.error.short.depends.on.disabled.plugin")
        }
      }
    }

    if (isIncomplete == null && moduleName == null) {
      processOldDependencies(descriptor = this, context, pathResolver, pluginDependencies, dataLoader)
    }
  }

  private fun readResourceBundleBaseName(raw: RawPluginDescriptor) {
    if (resourceBundleBaseName != null && resourceBundleBaseName != raw.resourceBundleBaseName) {
      LOG.warn("Resource bundle redefinition for plugin $id. Old value: $resourceBundleBaseName, new value: ${raw.resourceBundleBaseName}")
    }
    resourceBundleBaseName = raw.resourceBundleBaseName
  }

  /**
   * This method returns plugin aliases, which are added to the core module.
   * This is done to support running without the module-based loader (from sources and in dev mode),
   * where all modules are available, but only some of them need to be loaded.
   *
   * Module dependencies must be satisfied, module dependencies determine whether a module will be loaded.
   * If a module declares a special dependency, the dependency might be not satisfied in some produce mode,
   * and the module will not be loaded in that mode.
   * This function determines which dependencies are satisfiable by the current product mode.
   *
   * There are three product modes:
   * 1. Split frontend (JetBrains Client)
   * 2. Monolith, or regular local IDE
   * 3. Split backend (Remote Dev Host) or CWM plugin installed in monolith
   *
   * If a module needs to be loaded in some mode(-s), declare the following dependency(-ies):
   * - in 1 or 2: `com.intellij.platform.experimental.frontend`
   * - in 2 or 3: `com.intellij.platform.experimental.backend`
   * - only in 2: both `com.intellij.platform.experimental.frontend` and `com.intellij.platform.experimental.backend`
   * - in 1 or 2 or 3: no dependency needed
   */
  private fun productModeAliasesForCorePlugin(): List<PluginId> = buildList {
    if (!AppMode.isRemoteDevHost()) {
      // This alias is available in monolith and frontend.
      // Modules, which depend on it, will not be loaded in split backend.
      add(PluginId.getId("com.intellij.platform.experimental.frontend"))
    }
    if (!PlatformUtils.isJetBrainsClient()) {
      // This alias is available in monolith and backend.
      // Modules, which depend on it, will not be loaded in split frontend.
      add(PluginId.getId("com.intellij.platform.experimental.backend"))
    }
  }

  private fun processOldDependencies(descriptor: IdeaPluginDescriptorImpl,
                                     context: DescriptorListLoadingContext,
                                     pathResolver: PathResolver,
                                     dependencies: List<PluginDependency>,
                                     dataLoader: DataLoader) {
    var visitedFiles: MutableList<String>? = null
    for (dependency in dependencies) {
      // context.isPluginIncomplete must be not checked here as another version of plugin maybe supplied later from another source
      if (context.isPluginDisabled(dependency.pluginId)) {
        if (!dependency.isOptional && isIncomplete == null) {
          markAsIncomplete(dependency.pluginId, "plugin.loading.error.short.depends.on.disabled.plugin")
        }
      }

      // because of https://youtrack.jetbrains.com/issue/IDEA-206274, configFile maybe not only for optional dependencies
      val configFile = dependency.configFile ?: continue
      if (pathResolver.isFlat && context.checkOptionalConfigShortName(configFile, descriptor)) {
        continue
      }

      if (isKotlinPlugin(dependency.pluginId) && isIncompatibleWithKotlinPlugin(descriptor)) {
        LOG.warn("Plugin ${descriptor} depends on Kotlin plugin via `${configFile}` " +
                 "but the plugin is not compatible with the Kotlin plugin in the  ${if (isKotlinPluginK1Mode()) "K1" else "K2"} mode. " +
                 "So, the `${configFile}` was not loaded")
        continue
      }

      var resolveError: Exception? = null
      val raw: RawPluginDescriptor? = try {
        pathResolver.resolvePath(context, dataLoader, configFile, readInto = null)
      }
      catch (e: IOException) {
        resolveError = e
        null
      }

      if (raw == null) {
        val message = "Plugin $descriptor misses optional descriptor $configFile"
        if (context.isMissingSubDescriptorIgnored) {
          LOG.info(message)
          if (resolveError != null) {
            LOG.debug(resolveError)
          }
        }
        else {
          throw RuntimeException(message, resolveError)
        }
        continue
      }

      if (visitedFiles == null) {
        visitedFiles = context.visitedFiles
      }

      checkCycle(descriptor, configFile, visitedFiles)

      visitedFiles.add(configFile)
      val subDescriptor = descriptor.createSub(raw, configFile, context, module = null)
      if (subDescriptor.isIncomplete == null) {
        subDescriptor.processOldDependencies(subDescriptor, context, pathResolver, subDescriptor.pluginDependencies, dataLoader)
      }
      dependency.subDescriptor = subDescriptor
      visitedFiles.clear()
    }
  }

  private fun checkCompatibility(context: DescriptorListLoadingContext) {
    fun markAsIncompatible(error: PluginLoadingError) {
      if (isIncomplete != null) {
        return
      }
      isIncomplete = error
      isEnabled = false
    }

    if (isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(this)) {
      // disable plugins which are incompatible with the Kotlin Plugin K1/K2 Modes KTIJ-24797, KTIJ-30474
      val mode = if (isKotlinPluginK1Mode()) CoreBundle.message("plugin.loading.error.k1.mode") else CoreBundle.message("plugin.loading.error.k2.mode")
      markAsIncompatible(PluginLoadingError(
        plugin = this,
        detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.kotlin.incompatible", getName(), mode) },
        shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.kotlin.incompatible", mode) },
        isNotifyUser = false,
      ))
      return
    }

    if (isBundled) {
      return
    }

    if (AppMode.isDisableNonBundledPlugins()) {
      markAsIncompatible(PluginLoadingError(
        plugin = this,
        detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.custom.plugin.loading.disabled", getName()) },
        shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.custom.plugin.loading.disabled") },
        isNotifyUser = false
      ))
      return
    }

    PluginManagerCore.checkBuildNumberCompatibility(this, context.productBuildNumber())?.let {
      markAsIncompatible(it)
      return
    }

    // "Show broken plugins in Settings | Plugins so that users can uninstall them and resolve 'Plugin Error' (IDEA-232675)"
    if (context.isBroken(this)) {
      markAsIncompatible(PluginLoadingError(
        plugin = this,
        detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.marked.as.broken", name, version) },
        shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.marked.as.broken") }
      ))
    }
  }

  private fun markAsIncomplete(disabledDependency: PluginId?, @PropertyKey(resourceBundle = CoreBundle.BUNDLE) shortMessage: String?) {
    if (isIncomplete != null) {
      return
    }

    if (shortMessage == null) {
      isIncomplete = PluginLoadingError(plugin = this, detailedMessageSupplier = null, shortMessageSupplier = PluginLoadingError.DISABLED)
    }
    else {
      isIncomplete = PluginLoadingError(
        plugin = this,
        detailedMessageSupplier = null,
        shortMessageSupplier = { CoreBundle.message(shortMessage, disabledDependency!!) },
        isNotifyUser = false,
        disabledDependency)
    }

    isEnabled = false
  }

  @ApiStatus.Internal
  fun registerExtensions(nameToPoint: Map<String, ExtensionPointImpl<*>>, containerDescriptor: ContainerDescriptor, listenerCallbacks: MutableList<in Runnable>?) {
    if (!containerDescriptor.extensions.isEmpty()) {
      for ((name, descriptors) in containerDescriptor.extensions) {
        nameToPoint[name]?.registerExtensions(descriptors, pluginDescriptor = this, listenerCallbacks)
      }
      return
    }

    val map = epNameToExtensions
    if (map.isEmpty()) {
      return
    }

    // app container: in most cases will be only app-level extensions - to reduce map copying, assume that all extensions are app-level and then filter out
    // project container: rest of extensions wil be mostly project level
    // module container: just use rest, area will not register unrelated extension anyway as no registered point

    if (containerDescriptor === appContainerDescriptor) {
      val registeredCount = doRegisterExtensions(map, nameToPoint, listenerCallbacks)
      containerDescriptor.distinctExtensionPointCount = registeredCount

      if (registeredCount == map.size) {
        projectContainerDescriptor.extensions = Java11Shim.INSTANCE.mapOf()
        moduleContainerDescriptor.extensions = Java11Shim.INSTANCE.mapOf()
      }
    }
    else if (containerDescriptor === projectContainerDescriptor) {
      val registeredCount = doRegisterExtensions(map, nameToPoint, listenerCallbacks)
      containerDescriptor.distinctExtensionPointCount = registeredCount

      if (registeredCount == map.size) {
        containerDescriptor.extensions = map
        moduleContainerDescriptor.extensions = Java11Shim.INSTANCE.mapOf()
      }
      else if (registeredCount == (map.size - appContainerDescriptor.distinctExtensionPointCount)) {
        moduleContainerDescriptor.extensions = Java11Shim.INSTANCE.mapOf()
      }
    }
    else {
      val registeredCount = doRegisterExtensions(map, nameToPoint, listenerCallbacks)
      if (registeredCount == 0) {
        moduleContainerDescriptor.extensions = Java11Shim.INSTANCE.mapOf()
      }
    }
  }

  private fun doRegisterExtensions(map: Map<String, List<ExtensionDescriptor>>, nameToPoint: Map<String, ExtensionPointImpl<*>>, listenerCallbacks: MutableList<in Runnable>?): Int {
    var registeredCount = 0
    for ((descriptors, point) in intersectMaps(map, nameToPoint)) {
      point.registerExtensions(descriptors, pluginDescriptor = this, listenerCallbacks)
      registeredCount++
    }
    return registeredCount
  }

  private fun <K, V1, V2> intersectMaps(first: Map<K, V1>, second: Map<K, V2>): List<Pair<V1, V2>> =
    // Make sure we iterate the smaller map
    if (first.size < second.size) {
      first.mapNotNull { (key, firstValue) ->
        second[key]?.let { secondValue -> firstValue to secondValue }
      }
    }
    else {
      second.mapNotNull { (key, secondValue) ->
        first[key]?.let { firstValue -> firstValue to secondValue }
      }
    }

  @Suppress("HardCodedStringLiteral")
  override fun getDescription(): String? {
    var result = description
    if (result != null) {
      return result
    }

    result = fromPluginBundle("plugin.$id.description", descriptionChildText)

    description = result
    return result
  }

  private fun fromPluginBundle(key: String, @Nls defaultValue: String?): String? {
    if (!isEnabled) return defaultValue // if the plugin is disabled, its classloader is null and the resource bundle cannot be found
    return (resourceBundleBaseName?.let { baseName ->
      try {
        AbstractBundle.messageOrDefault(DynamicBundle.getResourceBundle(classLoader, baseName), key, defaultValue ?: "")
      }
      catch (_: MissingResourceException) {
        LOG.info("Cannot find plugin $id resource-bundle: $baseName")
        null
      }
    }) ?: defaultValue
  }

  override fun getChangeNotes(): String? = changeNotes

  override fun getName(): String = name!!

  override fun getProductCode(): String? = productCode

  override fun getReleaseDate(): Date? = releaseDate

  override fun getReleaseVersion(): Int = releaseVersion

  override fun isLicenseOptional(): Boolean = isLicenseOptional

  override fun getVendor(): String? = vendor

  override fun getVersion(): String? = version

  override fun getResourceBundleBaseName(): String? = resourceBundleBaseName

  override fun getCategory(): String? = category

  override fun getDisplayCategory(): @Nls String? = getCategory()?.let {
    val key = "plugin.category.${category?.replace(' ', '.')}"
    @Suppress("HardCodedStringLiteral")
    CoreBundle.messageOrNull(key) ?: fromPluginBundle(key, getCategory())
  }

  /**
   * This setter was explicitly defined to be able to set a category for a descriptor outside its loading from the XML file.
   * The problem was that most commonly plugin authors do not publish the plugin's category in its .xml file,
   * so to be consistent in plugin representation (e.g., in the Plugins form) we have to set this value outside.
   */
  fun setCategory(category: String?) {
    this.category = category
  }

  override fun getVendorEmail(): String? = vendorEmail

  override fun getVendorUrl(): String? = vendorUrl

  override fun getUrl(): String? = url

  override fun getPluginId(): PluginId = id

  override fun getPluginClassLoader(): ClassLoader? = _pluginClassLoader

  @ApiStatus.Internal
  fun setPluginClassLoader(classLoader: ClassLoader?) {
    _pluginClassLoader = classLoader
  }

  override fun isEnabled(): Boolean = isEnabled

  override fun setEnabled(enabled: Boolean) {
    isEnabled = enabled
  }

  override fun getSinceBuild(): String? = sinceBuild

  override fun getUntilBuild(): String? = untilBuild

  override fun isBundled(): Boolean = isBundled

  override fun allowBundledUpdate(): Boolean = isBundledUpdateAllowed

  override fun isImplementationDetail(): Boolean = implementationDetail

  override fun isRequireRestart(): Boolean = isRestartRequired

  override fun equals(other: Any?): Boolean {
    return this === other || other is IdeaPluginDescriptorImpl && id == other.id && descriptorPath == other.descriptorPath
  }

  override fun hashCode(): Int = 31 * id.hashCode() + (descriptorPath?.hashCode() ?: 0)

  override fun toString(): String =
    "PluginDescriptor(name=$name, id=$id, " +
    (if (moduleName == null) "" else "moduleName=$moduleName, ") +
    "descriptorPath=${descriptorPath ?: "plugin.xml"}, " +
    "path=${pluginPathToUserString(path)}, version=$version, package=$packagePrefix, isBundled=$isBundled)"

  private fun checkCycle(descriptor: IdeaPluginDescriptorImpl, configFile: String, visitedFiles: List<String>) {
    var i = 0
    val n = visitedFiles.size
    while (i < n) {
      if (configFile == visitedFiles[i]) {
        val cycle = visitedFiles.subList(i, visitedFiles.size)
        throw RuntimeException("Plugin $descriptor optional descriptors form a cycle: ${java.lang.String.join(", ", cycle)}")
      }
      i++
    }
  }
}

// don't expose user home in error messages
internal fun pluginPathToUserString(file: Path): String =
  file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")

private const val registryEpName = "com.intellij.registryKey"

private val extensionPointNameComparator = Comparator<String> { o1, o2 ->
  if (o1 == registryEpName) {
    if (o2 == registryEpName) 0
    else -1
  }
  else if (o2 == registryEpName) 1
  else o1.compareTo(o2)
}
