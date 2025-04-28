// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl.Type
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.RawPluginDescriptor
import com.intellij.platform.plugins.parser.impl.elements.*
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*

private val LOG: Logger
  get() = PluginManagerCore.logger

@ApiStatus.Internal
class IdeaPluginDescriptorImpl private constructor(
  raw: RawPluginDescriptor,
  pluginPath: Path,
  isBundled: Boolean,
  moduleName: String?,
  moduleLoadingRule: ModuleLoadingRule? = null,
  useCoreClassLoader: Boolean = false,
  isIndependentFromCoreClassLoader: Boolean = false,
  descriptorPath: String? = null
) : IdeaPluginDescriptorEx {

  constructor(
    raw: RawPluginDescriptor,
    pluginPath: Path,
    isBundled: Boolean,
    useCoreClassLoader: Boolean = false
  ): this(
    raw = raw,
    pluginPath = pluginPath,
    isBundled = isBundled,
    moduleName = null,
    moduleLoadingRule = null,
    useCoreClassLoader = useCoreClassLoader,
    isIndependentFromCoreClassLoader = false,
    descriptorPath = null)

  /** see [type], [checkTypeInvariants] */
  @ApiStatus.Internal
  enum class Type {
    /**
     * Main plugin descriptor, instantiated from "plugin.xml" (or from platform XMLs for Core).
     *
     * `descriptorPath`, `moduleName`, `moduleLoadingRule` properties are `null`.
     */
    PluginMainDescriptor,

    /**
     * Descriptor instantiated as a sub-descriptor of some [PluginMainDescriptor] using a content module info. See [createSub].
     *
     * `descriptorPath`, `moduleName`, `moduleLoadingRule` properties are _not_ `null`.
     */
    ContentModuleDescriptor,

    /**
     * Descriptor instantiated as a sub-descriptor of some [PluginMainDescriptor] _or_ another [DependsSubDescriptor] in [loadPluginDependencyDescriptors]. See [createSub].
     *
     * `descriptorPath` is _not_ null, `moduleName` and `moduleLoadingRule` properties are `null`.
     */
    DependsSubDescriptor
  }

  init {
    if (moduleName != null) {
      require(moduleLoadingRule != null) { "'moduleLoadingRule' parameter must be specified when creating a module descriptor, but it is missing for '$moduleName'" }
    }
  }

  private val id: PluginId = PluginId.getId(raw.id ?: raw.name ?: throw RuntimeException("Neither id nor name are specified"))
  private val name: String = raw.name ?: id.idString

  override val moduleName: String? = moduleName
  override val moduleLoadingRule: ModuleLoadingRule? = moduleLoadingRule

  private val version: String? = raw.version
  private val sinceBuild: String? = raw.sinceBuild
  @Suppress("DEPRECATION")
  private val untilBuild: String? = UntilBuildDeprecation.nullizeIfTargets243OrLater(raw.untilBuild, raw.name ?: raw.id)

  private val productCode: String? = raw.productCode
  private val releaseDate: Date? = raw.releaseDate?.let { Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
  private val releaseVersion: Int = raw.releaseVersion
  private val isLicenseOptional: Boolean = raw.isLicenseOptional

  private val rawDescription: @NlsSafe String? = raw.description
  private val category: @NlsSafe String? = raw.category
  private val changeNotes: String? = raw.changeNotes

  private val vendor: String? = raw.vendor
  private val vendorEmail: String? = raw.vendorEmail
  private val vendorUrl: String? = raw.vendorUrl
  private val url: String? = raw.url

  private val pluginDependencies: List<PluginDependencyImpl> = raw.depends
    .let(::fixDepends)
    .let(::convertDepends)
  override val incompatiblePlugins: List<PluginId> = raw.incompatibleWith.map(PluginId::getId)
  override val pluginAliases: List<PluginId> = raw.pluginAliases.map(PluginId::getId)
    .let(::addCorePluginAliases)

  /**
   * this is an implementation detail required during descriptor loading, use [contentModules] instead
   */
  val content: PluginContentDescriptor =
    raw.contentModules.takeIf { it.isNotEmpty() }?.let { PluginContentDescriptor(convertContentModules(it)) }
    ?: PluginContentDescriptor.EMPTY

  override val contentModules: List<ContentModule>
    get() = content.modules
  override val moduleDependencies: ModuleDependencies = raw.dependencies.let(::convertDependencies)
  override val packagePrefix: String? = raw.`package`

  override val appContainerDescriptor: ContainerDescriptor = raw.appElementsContainer.convert()
  override val projectContainerDescriptor: ContainerDescriptor = raw.projectElementsContainer.convert()
  override val moduleContainerDescriptor: ContainerDescriptor = raw.moduleElementsContainer.convert()

  override val extensions: Map<String, List<ExtensionDescriptor>> = raw.extensions
    .let(::convertExtensions)
    .let(::sortExtensions)

  private val resourceBundleBaseName: String? = raw.resourceBundleBaseName
    .also { warnIfResourceBundleIsDefinedForCorePlugin(it) }
  override val actions: List<ActionElement> = raw.actions

  private val isRestartRequired: Boolean = raw.isRestartRequired
  private val isImplementationDetail: Boolean = raw.isImplementationDetail
  private val isBundledUpdateAllowed: Boolean = raw.isBundledUpdateAllowed
  override val isUseIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader

  private val isBundled: Boolean = isBundled
  override val isIndependentFromCoreClassLoader: Boolean = isIndependentFromCoreClassLoader
  override val useCoreClassLoader: Boolean = useCoreClassLoader

  private val pluginPath: Path = pluginPath
  private val descriptorPath: String? = descriptorPath

  var isDeleted: Boolean = false
  @Transient
  var jarFiles: List<Path>? = null

  override var isMarkedForLoading: Boolean = true
  private var _pluginClassLoader: ClassLoader? = null
  @Volatile
  private var loadedDescriptionText: @Nls String? = null

  val type: Type get() {
    return when {
      moduleName != null -> Type.ContentModuleDescriptor
      descriptorPath != null -> Type.DependsSubDescriptor
      else -> Type.PluginMainDescriptor
    }
  }

  init {
    checkTypeInvariants()
  }

  override fun getPluginId(): PluginId = id

  override fun getName(): String {
    PluginCardOverrides.getNameOverride(id)?.let {
      return it
    }
    return name
  }

  override fun getVersion(): String? = version
  override fun getSinceBuild(): String? = sinceBuild
  override fun getUntilBuild(): String? = untilBuild

  override fun getProductCode(): String? = productCode
  override fun getReleaseDate(): Date? = releaseDate
  override fun getReleaseVersion(): Int = releaseVersion
  override fun isLicenseOptional(): Boolean = isLicenseOptional

  override fun getChangeNotes(): String? = changeNotes
  override fun getCategory(): @NlsSafe String? = category

  override fun getDisplayCategory(): @Nls String? {
    if (category == null) {
      return null
    }
    val key = "plugin.category.${category.replace(' ', '.')}"
    return CoreBundle.messageOrNull(key) ?: fromPluginBundle(key, category)
  }

  override fun getDescription(): @Nls String? {
    var result = loadedDescriptionText
    if (result != null) {
      return result
    }
    PluginCardOverrides.getDescriptionOverride(id)?.let {
      loadedDescriptionText = it
      return it
    }
    result = fromPluginBundle("plugin.$id.description", rawDescription)
    loadedDescriptionText = result
    return result
  }

  override fun getVendor(): String? = vendor
  override fun getVendorEmail(): String? = vendorEmail
  override fun getVendorUrl(): String? = vendorUrl
  override fun getUrl(): String? = url

  override fun isBundled(): Boolean = isBundled
  override fun allowBundledUpdate(): Boolean = isBundledUpdateAllowed
  override fun isImplementationDetail(): Boolean = isImplementationDetail
  override fun isRequireRestart(): Boolean = isRestartRequired

  /**
   * aka `<depends>` elements from the plugin.xml
   *
   * Note that it's different from [moduleDependencies]
   */
  override fun getDependencies(): List<PluginDependency> = pluginDependencies

  override fun getResourceBundleBaseName(): String? = resourceBundleBaseName

  override fun getPluginPath(): Path = pluginPath
  override fun getDescriptorPath(): String? = descriptorPath

  override fun getPluginClassLoader(): ClassLoader? = _pluginClassLoader

  @ApiStatus.Internal
  fun setPluginClassLoader(classLoader: ClassLoader?) {
    _pluginClassLoader = classLoader
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean = isMarkedForLoading

  @Deprecated("Deprecated in Java")
  override fun setEnabled(enabled: Boolean) {
    isMarkedForLoading = enabled
  }

  override fun equals(other: Any?): Boolean {
    return this === other || other is IdeaPluginDescriptorImpl && id == other.id && descriptorPath == other.descriptorPath
  }

  override fun hashCode(): Int = 31 * id.hashCode() + (descriptorPath?.hashCode() ?: 0)

  override fun toString(): String =
    "$type(name=$name, id=$id, version=$version, " +
    (if (moduleName == null) "" else "moduleName=$moduleName, ") +
    (if (packagePrefix == null) "" else "package=$packagePrefix, ") +
    "isBundled=$isBundled, " +
    "descriptorPath=${descriptorPath ?: "plugin.xml"}, " +
    "path=${PluginUtils.pluginPathToUserString(pluginPath)})"

  internal fun createSub(
    subBuilder: PluginDescriptorBuilder,
    descriptorPath: String,
    module: PluginContentDescriptor.ModuleItem?,
  ): IdeaPluginDescriptorImpl {
    subBuilder.id = id.idString
    subBuilder.name = name
    subBuilder.vendor = vendor
    if (subBuilder.version != null && subBuilder.version != version) {
      LOG.warn("Sub descriptor version redefinition for plugin $id. Original value: ${subBuilder.version}, inherited value: ${version}")
    }
    subBuilder.version = version
    if (module == null) { // resource bundle is inherited for v1 sub-descriptors only
      if (subBuilder.resourceBundleBaseName == null) {
        subBuilder.resourceBundleBaseName = resourceBundleBaseName
      } else {
        if (subBuilder.resourceBundleBaseName != resourceBundleBaseName && resourceBundleBaseName != null) {
          LOG.warn("Resource bundle redefinition for plugin $id. Parent value: $resourceBundleBaseName, new value: ${subBuilder.resourceBundleBaseName}")
        }
      }
    }
    val raw = subBuilder.build()
    val result = IdeaPluginDescriptorImpl(
      raw = raw,
      pluginPath = pluginPath,
      isBundled = isBundled,
      moduleName = module?.name,
      moduleLoadingRule = module?.loadingRule,
      useCoreClassLoader = useCoreClassLoader,
      isIndependentFromCoreClassLoader = raw.isIndependentFromCoreClassLoader,
      descriptorPath = descriptorPath)
    return result
  }

  internal fun loadPluginDependencyDescriptors(loadingContext: PluginDescriptorLoadingContext, pathResolver: PathResolver, dataLoader: DataLoader): Unit =
    loadPluginDependencyDescriptors(loadingContext, pathResolver, dataLoader, ArrayList(3))

  private fun loadPluginDependencyDescriptors(context: PluginDescriptorLoadingContext,
                                              pathResolver: PathResolver,
                                              dataLoader: DataLoader,
                                              visitedFiles: MutableList<String>) {
    for (dependency in pluginDependencies) {
      // because of https://youtrack.jetbrains.com/issue/IDEA-206274, configFile maybe not only for optional dependencies
      val configFile = dependency.configFile ?: continue
      if (pathResolver.isFlat && context.checkOptionalConfigShortName(configFile, this)) {
        continue
      }

      if (isKotlinPlugin(dependency.pluginId) && isIncompatibleWithKotlinPlugin(this)) {
        LOG.warn("Plugin ${this} depends on Kotlin plugin via `${configFile}` " +
                 "but the plugin is not compatible with the Kotlin plugin in the  ${if (isKotlinPluginK1Mode()) "K1" else "K2"} mode. " +
                 "So, the `${configFile}` was not loaded")
        continue
      }

      var resolveError: Exception? = null
      val raw: PluginDescriptorBuilder? = try {
        pathResolver.resolvePath(context, dataLoader, configFile)
      }
      catch (e: IOException) {
        resolveError = e
        null
      }

      if (raw == null) {
        val message = "Plugin $this misses optional descriptor $configFile"
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

      checkCycle(configFile, visitedFiles)
      visitedFiles.add(configFile)
      try {
        val subDescriptor = createSub(raw, configFile, module = null)
        subDescriptor.loadPluginDependencyDescriptors(context, pathResolver, dataLoader, visitedFiles)
        dependency.setSubDescriptor(subDescriptor)
      } finally {
        visitedFiles.removeLast()
      }
    }
  }

  fun initialize(context: PluginInitializationContext): PluginNonLoadReason? {
    assert(type == Type.PluginMainDescriptor)
    content.modules.forEach { it.requireDescriptor() }
    if (context.isPluginDisabled(id)) {
      return onInitError(PluginIsMarkedDisabled(this))
    }
    checkCompatibility(context::productBuildNumber, context::isPluginBroken)?.let {
      return it
    }
    for (dependency in pluginDependencies) { // FIXME: likely we actually have to recursively traverse these after they are resolved
      if (context.isPluginDisabled(dependency.pluginId) && !dependency.isOptional) {
        return onInitError(PluginDependencyIsDisabled(this, dependency.pluginId, false))
      }
    }
    for (pluginDependency in moduleDependencies.plugins) {
      if (context.isPluginDisabled(pluginDependency.id)) {
        return onInitError(PluginDependencyIsDisabled(this, pluginDependency.id, false))
      }
    }
    return null
  }

  private fun checkCompatibility(getBuildNumber: () -> BuildNumber, isPluginBroken: (PluginId, version: String?) -> Boolean): PluginNonLoadReason? {
    if (isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(this)) {
      // disable plugins which are incompatible with the Kotlin Plugin K1/K2 Modes KTIJ-24797, KTIJ-30474
      val mode = if (isKotlinPluginK1Mode()) CoreBundle.message("plugin.loading.error.k1.mode") else CoreBundle.message("plugin.loading.error.k2.mode")
      return onInitError(PluginIsIncompatibleWithKotlinMode(this, mode))
    }

    if (isBundled) {
      return null
    }

    if (AppMode.isDisableNonBundledPlugins()) {
      return onInitError(NonBundledPluginsAreExplicitlyDisabled(this))
    }

    PluginManagerCore.checkBuildNumberCompatibility(this, getBuildNumber())?.let {
      return onInitError(it)
    }

    // "Show broken plugins in Settings | Plugins so that users can uninstall them and resolve 'Plugin Error' (IDEA-232675)"
    if (isPluginBroken(id, version)) {
      return onInitError(PluginIsMarkedBroken(this))
    }
    return null
  }

  private fun onInitError(error: PluginNonLoadReason): PluginNonLoadReason {
    isMarkedForLoading = false
    return error
  }

  @ApiStatus.Internal
  fun registerExtensions(nameToPoint: Map<String, ExtensionPointImpl<*>>, listenerCallbacks: MutableList<in Runnable>?) {
    for ((descriptors, point) in intersectMaps(extensions, nameToPoint)) {
      point.registerExtensions(descriptors, pluginDescriptor = this, listenerCallbacks)
    }
  }

  private fun fromPluginBundle(key: String, @Nls defaultValue: String?): @Nls String? {
    if (!isEnabled) { // if the plugin is disabled, its classloader is null and the resource bundle cannot be found
      return defaultValue
    }
    val baseName = resourceBundleBaseName
    if (baseName == null) {
      return defaultValue
    }
    return (try {
      AbstractBundle.messageOrDefault(DynamicBundle.getResourceBundle(classLoader, baseName), key, defaultValue ?: "")
    }
    catch (_: MissingResourceException) {
      LOG.info("Cannot find plugin $id resource-bundle: $baseName")
      null
    }) ?: defaultValue
  }

  private fun checkCycle(configFile: String, visitedFiles: List<String>) {
    var i = 0
    val n = visitedFiles.size
    while (i < n) {
      if (configFile == visitedFiles[i]) {
        val cycle = visitedFiles.subList(i, visitedFiles.size)
        throw RuntimeException("Plugin $this optional descriptors form a cycle: ${java.lang.String.join(", ", cycle)}")
      }
      i++
    }
  }

  private fun addCorePluginAliases(pluginAliases: List<PluginId>): List<PluginId> {
    if (id != PluginManagerCore.CORE_ID) {
      return pluginAliases
    }
    return pluginAliases + IdeaPluginOsRequirement.getHostOsModuleIds() + productModeAliasesForCorePlugin()
  }

  private fun warnIfResourceBundleIsDefinedForCorePlugin(resourceBundle: String?) {
    if (resourceBundle != null && id == PluginManagerCore.CORE_ID && moduleName == null) {
      LOG.warn("<resource-bundle>$resourceBundle</resource-bundle> tag is found in an xml descriptor" +
               " included into the platform part of the IDE but the platform part uses predefined bundles " +
               "(e.g. ActionsBundle for actions) anyway; this tag must be replaced by a corresponding attribute in some inner tags " +
               "(e.g. by 'resource-bundle' attribute in 'actions' tag)")
    }
  }

  private fun checkTypeInvariants() {
    when (type) {
      Type.PluginMainDescriptor -> {
        assert(moduleName == null && moduleLoadingRule == null && descriptorPath == null) { this }
      }
      Type.ContentModuleDescriptor -> {
        assert(moduleName != null && moduleLoadingRule != null && descriptorPath != null) { this }
        if (pluginDependencies.isNotEmpty()) {
          LOG.error("Unexpected `depends` dependencies in a content module: ${pluginDependencies.joinToString()}\n in $this")
        }
        if (content.modules.isNotEmpty()) {
          LOG.error("Unexpected `content` elements in a content module: ${content.modules.joinToString()}\n in $this")
        }
      }
      Type.DependsSubDescriptor -> {
        assert(moduleName == null && moduleLoadingRule == null && descriptorPath != null) { this }
        if (content.modules.isNotEmpty()) {
          LOG.error("Unexpected `content` elements in a `depends` sub-descriptor: ${content.modules.joinToString()}\n in $this")
        }
        if (moduleDependencies.modules.isNotEmpty()) {
          LOG.error("Unexpected `module` dependencies in a `depends` sub-descriptor: ${moduleDependencies.modules.joinToString()}\n in $this")
        }
        if (moduleDependencies.plugins.isNotEmpty()) {
          LOG.error("Unexpected `plugin` dependencies in a `depends` sub-descriptor: ${moduleDependencies.plugins.joinToString()}\n in $this")
        }
      }
    }
  }

  @ApiStatus.Internal
  companion object {
    private fun convertDepends(depends: List<DependsElement>): MutableList<PluginDependencyImpl> =
      depends.mapTo(ArrayList(depends.size)) {
        PluginDependencyImpl(PluginId.getId(it.pluginId), it.configFile, it.isOptional)
      }

    /** https://youtrack.jetbrains.com/issue/IDEA-206274 */
    private fun fixDepends(depends: List<DependsElement>): List<DependsElement> {
      val unchanged = 0.toByte()
      val removed = 1.toByte()
      val nonOptional = 2.toByte()
      var elemState: ByteArray? = null
      fun getState(index: Int) = elemState?.get(index) ?: unchanged
      fun setState(index: Int, value: Byte) {
        if (elemState == null) {
          elemState = ByteArray(depends.size) { unchanged }
        }
        elemState[index] = value
      }
      fun isOptional(index: Int) = depends[index].isOptional && getState(index) == unchanged
      for ((index, item) in depends.withIndex()) {
        if (isOptional(index)) continue
        for ((candidateIndex, candidate) in depends.withIndex()) {
          if (isOptional(candidateIndex) && candidate.pluginId == item.pluginId) {
            setState(candidateIndex, nonOptional)
            setState(index, removed)
            break
          }
        }
      }
      if (elemState == null) {
        return depends
      }
      return depends.mapIndexedNotNull { index, _ ->
        when (getState(index)) {
          unchanged -> depends[index]
          removed -> null
          nonOptional -> DependsElement(depends[index].pluginId, false, depends[index].configFile)
          else -> throw IllegalStateException("Unknown state ${getState(index)}")
        }
      }
    }

    // FIXME sorting is likely unnecessary.
    //  Instead, users of ExtensionPoint change listeners must not touch other services/extension points.
    //  Only clear up some caches and/or launch coroutines.
    //  `com.intellij.ide.plugins.DynamicPluginsTest.registry access of key from same plugin` has a reproducer
    private fun sortExtensions(rawMap: Map<String, List<ExtensionDescriptor>>): Map<String, List<ExtensionDescriptor>> {
      if (rawMap.size < 2 || !rawMap.containsKey(REGISTRY_EP_NAME)) {
        return rawMap
      }
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
      return result
    }

    private const val REGISTRY_EP_NAME = "com.intellij.registryKey"

    private val extensionPointNameComparator = Comparator<String> { o1, o2 ->
      if (o1 == REGISTRY_EP_NAME) {
        if (o2 == REGISTRY_EP_NAME) 0
        else -1
      }
      else if (o2 == REGISTRY_EP_NAME) 1
      else o1.compareTo(o2)
    }

    private fun convertExtensions(rawMap: Map<String, List<ExtensionElement>>): Map<String, List<ExtensionDescriptor>> = rawMap.mapValues { (_, extensions) ->
      extensions.mapNotNull {
        try {
          val order = LoadingOrder.readOrder(it.order) // throws AssertionError
          ExtensionDescriptor(
            implementation = it.implementation,
            os = it.os?.convert(),
            orderId = it.orderId,
            order = order,
            element = it.element,
            hasExtraAttributes = it.hasExtraAttributes
          )
        } catch (e: Throwable) {
          LOG.error(e)
          null
        }
      }
    }

    private fun convertDependencies(dependencies: List<DependenciesElement>): ModuleDependencies {
      if (dependencies.isEmpty()) {
        return ModuleDependencies.EMPTY
      }
      val moduleDeps = ArrayList<ModuleDependencies.ModuleReference>()
      val pluginDeps = ArrayList<ModuleDependencies.PluginReference>()
      for (dep in dependencies) {
        when (dep) {
          is DependenciesElement.PluginDependency -> pluginDeps.add(ModuleDependencies.PluginReference(PluginId.getId(dep.pluginId)))
          is DependenciesElement.ModuleDependency -> moduleDeps.add(ModuleDependencies.ModuleReference(dep.moduleName))
          else -> LOG.error("Unknown dependency type: $dep")
        }
      }
      return ModuleDependencies(moduleDeps, pluginDeps)
    }

    private fun convertContentModules(contentElements: List<ContentElement>): List<PluginContentDescriptor.ModuleItem> {
      return contentElements.mapNotNull { elem ->
        when (elem) {
          is ContentElement.Module -> {
            val index = elem.name.lastIndexOf('/')
            val configFile: String? = if (index != -1) {
              "${elem.name.substring(0, index)}.${elem.name.substring(index + 1)}.xml"
            } else null
            PluginContentDescriptor.ModuleItem(elem.name, configFile, elem.embeddedDescriptorContent, elem.loadingRule.convert())
          }
          else -> {
            LOG.error("Unknown content element: $elem")
            null
          }
        }
      }
    }

    private fun <K, V1, V2> intersectMaps(first: Map<K, V1>, second: Map<K, V2>): Sequence<Pair<V1, V2>> {
      // Make sure we iterate the smaller map
      return if (first.size < second.size) {
        first.asSequence().mapNotNull { (key, firstValue) ->
          second[key]?.let { secondValue -> firstValue to secondValue }
        }
      }
      else {
        second.asSequence().mapNotNull { (key, secondValue) ->
          first[key]?.let { firstValue -> firstValue to secondValue }
        }
      }
    }

    /**
     * This method returns plugin aliases, which are added to the core module.
     * This is done to support running without the module-based loader (from sources and in dev mode),
     * where all modules are available, but only some of them need to be loaded.
     *
     * This method is left for compatibility only.
     * Now dependencies on 'intellij.platform.frontend' and 'intellij.platform.backend' should be used instead.
     * These modules are automatically disabled if they aren't relevant to the product mode, see [PluginSetBuilder.getModuleIncompatibleWithCurrentProductMode].
     */
    @VisibleForTesting
    @ApiStatus.Obsolete
    fun productModeAliasesForCorePlugin(): List<PluginId> = buildList {
      if (!AppMode.isRemoteDevHost()) {
        // This alias is available in monolith and frontend.
        // Modules, which depend on it, will not be loaded in a split backend.
        add(PluginId.getId("com.intellij.platform.experimental.frontend"))
      }
      if (!PlatformUtils.isJetBrainsClient()) {
        // This alias is available in monolith and backend.
        // Modules, which depend on it, will not be loaded in a split frontend.
        add(PluginId.getId("com.intellij.platform.experimental.backend"))
      }
      if (!AppMode.isRemoteDevHost() && !PlatformUtils.isJetBrainsClient()) {
        // This alias is available in monolith only.
        // Modules, which depend on it, will not be loaded in split mode.
        add(PluginId.getId("com.intellij.platform.experimental.monolith"))
      }
    }
  }
}

@ApiStatus.Internal
@TestOnly
fun IdeaPluginDescriptorImpl.createSubInTest(
  subBuilder: PluginDescriptorBuilder,
  descriptorPath: String,
  module: PluginContentDescriptor.ModuleItem?,
): IdeaPluginDescriptorImpl = createSub(subBuilder, descriptorPath, module)

@get:ApiStatus.Internal
val IdeaPluginDescriptorImpl.isMainPluginDescriptor: Boolean get() = type == Type.PluginMainDescriptor

@get:ApiStatus.Internal
val IdeaPluginDescriptorImpl.isContentModuleDescriptor: Boolean get() = type == Type.ContentModuleDescriptor

@get:ApiStatus.Internal
val IdeaPluginDescriptorImpl.isDependsSubDescriptor: Boolean get() = type == Type.DependsSubDescriptor