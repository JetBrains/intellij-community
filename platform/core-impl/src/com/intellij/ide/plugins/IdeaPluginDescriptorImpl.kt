// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CanBePrimaryConstructorProperty")
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.diagnostic.PluginException
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginXmlConst
import com.intellij.platform.plugins.parser.impl.RawPluginDescriptor
import com.intellij.platform.plugins.parser.impl.elements.*
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*

private val LOG: Logger
  get() = PluginManagerCore.logger

@ApiStatus.Internal
sealed class IdeaPluginDescriptorImpl(
  raw: RawPluginDescriptor,
) : IdeaPluginDescriptor {
  internal val pluginDependencies: List<PluginDependencyImpl> = raw.depends
    .let(::convertDepends)
  val incompatiblePlugins: List<PluginId> = raw.incompatibleWith.map(PluginId::getId)
  open val pluginAliases: List<PluginId> = raw.pluginAliases.map(PluginId::getId)

  val moduleDependencies: ModuleDependencies = raw.dependencies.let(::convertDependencies)
  val packagePrefix: String? = raw.`package`

  val appContainerDescriptor: ContainerDescriptor = raw.appElementsContainer.convert()
  val projectContainerDescriptor: ContainerDescriptor = raw.projectElementsContainer.convert()
  val moduleContainerDescriptor: ContainerDescriptor = raw.moduleElementsContainer.convert()

  val extensions: Map<String, List<ExtensionDescriptor>> = raw.extensions
    .let(::convertExtensions)
    .let(::sortExtensions)

  val actions: List<ActionElement> = raw.actions

  var isDeleted: Boolean = false
  @Transient
  var jarFiles: List<Path>? = null

  var isMarkedForLoading: Boolean = true
  private var _pluginClassLoader: ClassLoader? = null

  abstract val isIndependentFromCoreClassLoader: Boolean
  abstract val useCoreClassLoader: Boolean
  abstract val useIdeaClassLoader: Boolean

  /**
   * aka `<depends>` elements from the plugin.xml
   *
   * Note that it's different from [moduleDependencies]
   */
  override fun getDependencies(): List<PluginDependency> = pluginDependencies

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
    return this === other || other is IdeaPluginDescriptorImpl && pluginId == other.pluginId && descriptorPath == other.descriptorPath
  }

  override fun hashCode(): Int = 31 * pluginId.hashCode() + (descriptorPath?.hashCode() ?: 0)

  internal fun createDependsSubDescriptor(
    subBuilder: PluginDescriptorBuilder,
    descriptorPath: String,
  ): DependsSubDescriptor = DependsSubDescriptor(
    parent = this,
    raw = subBuilder.build(),
    descriptorPath = descriptorPath
  )

  @ApiStatus.Internal
  fun registerExtensions(nameToPoint: Map<String, ExtensionPointImpl<*>>, listenerCallbacks: MutableList<in Runnable>?) {
    for ((descriptors, point) in intersectMaps(extensions, nameToPoint)) {
      point.registerExtensions(descriptors, pluginDescriptor = this, listenerCallbacks)
    }
  }

  @ApiStatus.Internal
  companion object {
    private fun convertDepends(depends: List<DependsElement>): MutableList<PluginDependencyImpl> =
      depends.mapTo(ArrayList(depends.size)) {
        PluginDependencyImpl(PluginId.getId(it.pluginId), it.configFile, it.isOptional)
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
      val moduleDeps = ArrayList<PluginModuleId>()
      val pluginDeps = ArrayList<PluginId>()
      for (dep in dependencies) {
        when (dep) {
          is DependenciesElement.PluginDependency -> pluginDeps.add(PluginId.getId(dep.pluginId))
          is DependenciesElement.ModuleDependency -> moduleDeps.add(PluginModuleId(dep.moduleName))
          else -> LOG.error("Unknown dependency type: $dep")
        }
      }
      return ModuleDependencies(moduleDeps, pluginDeps)
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

    internal fun IdeaPluginDescriptor.logUnexpectedElement(elementName: String) {
      LOG.warnInProduction(PluginException(buildString {
        append("Plugin descriptor for ")
        when (this@logUnexpectedElement) {
          is ContentModuleDescriptor -> append("content module '${moduleId}' of plugin '${pluginId}'")
          is DependsSubDescriptor -> append("'depends' sub-descriptor '${descriptorPath}' of plugin '${pluginId}'")
          is PluginMainDescriptor -> error("not intended")
        }
        append(" has declared element '$elementName' which has no effect there")
        append("\n in ${this@logUnexpectedElement}")
      }, pluginId))
    }

    internal fun reportSubDescriptorUnexpectedElements(raw: RawPluginDescriptor, reporter: (elementName: String) -> Unit) {
      if (raw.id != null) reporter(PluginXmlConst.ID_ELEM)
      if (raw.name != null) reporter(PluginXmlConst.NAME_ELEM)

      if (raw.isBundledUpdateAllowed) reporter(PluginXmlConst.PLUGIN_ALLOW_BUNDLED_UPDATE_ATTR)
      if (raw.isRestartRequired) reporter(PluginXmlConst.PLUGIN_REQUIRE_RESTART_ATTR)
      if (raw.isImplementationDetail) reporter(PluginXmlConst.PLUGIN_IMPLEMENTATION_DETAIL_ATTR)

      if (raw.version != null) reporter(PluginXmlConst.VERSION_ELEM)
      if (raw.sinceBuild != null) reporter(PluginXmlConst.IDEA_VERSION_SINCE_ATTR)
      if (raw.untilBuild != null) reporter(PluginXmlConst.IDEA_VERSION_UNTIL_ATTR)

      if (raw.vendor != null) reporter(PluginXmlConst.VENDOR_ELEM)
      if (raw.vendorUrl != null) reporter(PluginXmlConst.VENDOR_URL_ATTR)
      if (raw.vendorEmail != null) reporter(PluginXmlConst.VENDOR_EMAIL_ATTR)
      if (raw.url != null) reporter(PluginXmlConst.PLUGIN_URL_ATTR)

      if (raw.productCode != null) reporter(PluginXmlConst.PRODUCT_DESCRIPTOR_CODE_ATTR)
      if (raw.isLicenseOptional) reporter(PluginXmlConst.PRODUCT_DESCRIPTOR_OPTIONAL_ATTR)
      if (raw.releaseDate != null) reporter(PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_DATE_ATTR)
      if (raw.releaseVersion != 0) reporter(PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_VERSION_ATTR)

      if (raw.changeNotes != null) reporter(PluginXmlConst.CHANGE_NOTES_ELEM)
      if (raw.category != null) reporter(PluginXmlConst.CATEGORY_ELEM)
      if (raw.description != null) reporter(PluginXmlConst.DESCRIPTION_ELEM)

      if (raw.contentModules.isNotEmpty()) reporter(PluginXmlConst.CONTENT_ELEM)
      if (raw.incompatibleWith.isNotEmpty()) reporter(PluginXmlConst.INCOMPATIBLE_WITH_ELEM)
    }
  }
}

/**
 * Either [PluginMainDescriptor] or [ContentModuleDescriptor].
 * Both of them can be referenced either by plugin id or a module name (while [DependsSubDescriptor] can't be referenced).
 */
@ApiStatus.Internal
sealed class PluginModuleDescriptor(raw: RawPluginDescriptor) : IdeaPluginDescriptorImpl(raw)

/**
 * Main plugin descriptor, instantiated from "plugin.xml" (or from platform XMLs for Core).
 */
@ApiStatus.Internal
class PluginMainDescriptor(
  raw: RawPluginDescriptor,
  pluginPath: Path,
  isBundled: Boolean,
  useCoreClassLoader: Boolean = false
): PluginModuleDescriptor(raw) {
  private val id: PluginId = PluginId.getId(raw.id ?: raw.name ?: throw RuntimeException("Neither id nor name are specified"))
  private val name: String = raw.name ?: id.idString

  private val version: String? = raw.version
  private val sinceBuild: String? = raw.sinceBuild
  @Suppress("DEPRECATION")
  private val untilBuild: String? = UntilBuildDeprecation.nullizeIfTargetsMinimalApiOrLater(raw.untilBuild, raw.name ?: raw.id)

  @Volatile
  private var loadedDescriptionText: @Nls String? = null
  private val rawDescription: @NlsSafe String? = raw.description
  private val category: @NlsSafe String? = raw.category
  private val changeNotes: String? = raw.changeNotes

  private val vendor: String? = raw.vendor
  private val vendorEmail: String? = raw.vendorEmail
  private val vendorUrl: String? = raw.vendorUrl
  private val url: String? = raw.url

  private val productCode: String? = raw.productCode
  private val releaseDate: Date? = raw.releaseDate?.let { Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
  private val releaseVersion: Int = raw.releaseVersion
  private val isLicenseOptional: Boolean = raw.isLicenseOptional

  private val isBundled: Boolean = isBundled
  private val isBundledUpdateAllowed: Boolean = raw.isBundledUpdateAllowed
  private val isRestartRequired: Boolean = raw.isRestartRequired
  private val isImplementationDetail: Boolean = raw.isImplementationDetail

  private val pluginPath: Path = pluginPath

  override val useCoreClassLoader: Boolean = useCoreClassLoader
  override val isIndependentFromCoreClassLoader: Boolean get() = false
  override val useIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader

  private val resourceBundleBaseName: String? = raw.resourceBundleBaseName

  override val pluginAliases: List<PluginId> = super.pluginAliases.let(::addCorePluginAliases)

  /**
   * this is an implementation detail required during descriptor loading, use [contentModules] instead
   */
  @VisibleForTesting
  val content: PluginContentDescriptor =
    raw.contentModules.takeIf { it.isNotEmpty() }?.let { PluginContentDescriptor(convertContentModules(it)) }
    ?: PluginContentDescriptor.EMPTY

  val contentModules: List<ContentModuleDescriptor>
    get() = content.modules.map { it.descriptor }

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

  override fun getVendor(): String? = vendor
  override fun getVendorEmail(): String? = vendorEmail
  override fun getVendorUrl(): String? = vendorUrl
  override fun getUrl(): String? = url

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
    PluginCardOverrides.getDescriptionOverride(pluginId)?.let {
      loadedDescriptionText = it
      return it
    }
    result = fromPluginBundle("plugin.$pluginId.description", rawDescription)
    loadedDescriptionText = result
    return result
  }

  override fun getPluginPath(): Path = pluginPath
  override fun getDescriptorPath(): Nothing? = null
  override fun isBundled(): Boolean = isBundled

  override fun allowBundledUpdate(): Boolean = isBundledUpdateAllowed
  override fun isImplementationDetail(): Boolean = isImplementationDetail
  override fun isRequireRestart(): Boolean = isRestartRequired

  override fun getResourceBundleBaseName(): String? = resourceBundleBaseName

  override fun toString(): String =
    "PluginMainDescriptor(name=$name, id=$pluginId, version=$version, " +
    (if (packagePrefix == null) "" else "package=$packagePrefix, ") +
    "isBundled=$isBundled, " +
    "path=${PluginUtils.pluginPathToUserString(pluginPath)})"


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
      LOG.info("Cannot find plugin $pluginId resource-bundle: $baseName")
      null
    }) ?: defaultValue
  }

  private fun addCorePluginAliases(pluginAliases: List<PluginId>): List<PluginId> {
    if (pluginId != PluginManagerCore.CORE_ID) {
      return pluginAliases
    }
    return pluginAliases + IdeaPluginOsRequirement.getHostOsModuleIds() + productModeAliasesForCorePlugin()
  }

  internal fun createContentModule(
    subBuilder: PluginDescriptorBuilder,
    descriptorPath: String,
    module: PluginContentDescriptor.ModuleItem,
  ): ContentModuleDescriptor = ContentModuleDescriptor(
    parent = this,
    raw = subBuilder.build(),
    moduleId = module.moduleId,
    moduleLoadingRule = module.loadingRule,
    descriptorPath = descriptorPath
  )

  fun initialize(context: PluginInitializationContext): PluginNonLoadReason? {
    content.modules.forEach { it.requireDescriptor() }
    if (content.modules.size > 1) {
      val duplicates = HashSet<PluginModuleId>()
      for (item in content.modules) {
        if (!duplicates.add(item.moduleId)) {
          return onInitError(PluginHasDuplicateContentModuleDeclaration(this, item.moduleId))
        }
      }
    }
    if (context.isPluginDisabled(pluginId)) {
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
      if (context.isPluginDisabled(pluginDependency)) {
        return onInitError(PluginDependencyIsDisabled(this, pluginDependency, false))
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
    if (isPluginBroken(pluginId, version)) {
      return onInitError(PluginIsMarkedBroken(this))
    }
    return null
  }

  private fun onInitError(error: PluginNonLoadReason): PluginNonLoadReason {
    isMarkedForLoading = false
    return error
  }

  init {
    if (pluginId == PluginManagerCore.CORE_ID && resourceBundleBaseName != null) {
      LOG.warn("<resource-bundle>$resourceBundleBaseName</resource-bundle> tag is found in an xml descriptor" +
               " included into the platform part of the IDE but the platform part uses predefined bundles " +
               "(e.g. ActionsBundle for actions) anyway; this tag must be replaced by a corresponding attribute in some inner tags " +
               "(e.g. by 'resource-bundle' attribute in 'actions' tag)")
    }
  }

  @ApiStatus.Internal
  companion object {
    private fun convertContentModules(contentElements: List<ContentModuleElement>): List<PluginContentDescriptor.ModuleItem> {
      return contentElements.map { elem ->
        val index = elem.name.lastIndexOf('/')
        val configFile: String? = if (index != -1) {
          "${elem.name.substring(0, index)}.${elem.name.substring(index + 1)}.xml"
        } else null
        PluginContentDescriptor.ModuleItem(
          moduleId = PluginModuleId(elem.name),
          configFile = configFile,
          descriptorContent = elem.embeddedDescriptorContent,
          loadingRule = elem.loadingRule.convert())
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
class DependsSubDescriptor(
  /** either [PluginMainDescriptor] or [DependsSubDescriptor]*/
  val parent: IdeaPluginDescriptorImpl,
  raw: RawPluginDescriptor,
  private val descriptorPath: String
): IdeaPluginDescriptorImpl(raw) {
  init {
    check(parent is PluginMainDescriptor || parent is DependsSubDescriptor)
  }

  override val useCoreClassLoader: Boolean
    get() = parent.useCoreClassLoader
  override val isIndependentFromCoreClassLoader: Boolean = raw.isIndependentFromCoreClassLoader

  private val rawResourceBundleBaseName: String? = raw.resourceBundleBaseName

  override fun getDescriptorPath(): String = descriptorPath

  override fun getResourceBundleBaseName(): String? = rawResourceBundleBaseName ?: parent.resourceBundleBaseName

  override fun toString(): String =
    "DependsSubDescriptor(" +
    "descriptorPath=$descriptorPath" +
    (if (packagePrefix == null) "" else ", package=$packagePrefix") +
    ") <- $parent"

  init {
    reportDependsSubDescriptorUnexpectedElements(raw) { logUnexpectedElement(it) }
  }

  override fun getPluginId(): PluginId = parent.pluginId
  // <editor-fold desc="Deprecated">
  // These are meaningless for sub-descriptors
  @Deprecated("use main descriptor") override fun getName(): @NlsSafe String? = parent.name
  @Deprecated("use main descriptor") override fun getVersion(): String? = parent.version
  @Deprecated("use main descriptor") override fun getSinceBuild(): String? = parent.sinceBuild
  @Deprecated("use main descriptor") override fun getUntilBuild(): String? = parent.untilBuild
  @Deprecated("use main descriptor") override fun getChangeNotes(): String? = parent.changeNotes
  @Deprecated("use main descriptor") override fun getCategory(): @NlsSafe String? = parent.category
  @Deprecated("use main descriptor") override fun getDisplayCategory(): @Nls String? = parent.displayCategory
  @Deprecated("use main descriptor") override fun getDescription(): @Nls String? = parent.description
  @Deprecated("use main descriptor") override fun isBundled(): Boolean = parent.isBundled
  @Deprecated("use main descriptor") override fun getPluginPath(): Path = parent.pluginPath
  @Deprecated("use main descriptor") override val useIdeaClassLoader: Boolean get() = parent.useIdeaClassLoader
  @Deprecated("use main descriptor") override fun allowBundledUpdate(): Boolean = parent.allowBundledUpdate()
  @Deprecated("use main descriptor") override fun isImplementationDetail(): Boolean = parent.isImplementationDetail
  @Deprecated("use main descriptor") override fun isRequireRestart(): Boolean = parent.isRequireRestart
  @Deprecated("use main descriptor") override fun getVendor(): String? = parent.vendor
  @Deprecated("use main descriptor") override fun getVendorEmail(): String? = parent.vendorEmail
  @Deprecated("use main descriptor") override fun getVendorUrl(): String? = parent.vendorUrl
  @Deprecated("use main descriptor") override fun getUrl(): String? = parent.url
  @Deprecated("use main descriptor") override fun getProductCode(): String? = parent.productCode
  @Deprecated("use main descriptor") override fun getReleaseDate(): Date? = parent.releaseDate
  @Deprecated("use main descriptor") override fun getReleaseVersion(): Int = parent.releaseVersion
  @Deprecated("use main descriptor") override fun isLicenseOptional(): Boolean = parent.isLicenseOptional
  // </editor-fold>

  companion object {
    @VisibleForTesting
    fun reportDependsSubDescriptorUnexpectedElements(raw: RawPluginDescriptor, reporter: (elementName: String) -> Unit) {
      reportSubDescriptorUnexpectedElements(raw, reporter)
      if (raw.isUseIdeaClassLoader) reporter(PluginXmlConst.PLUGIN_USE_IDEA_CLASSLOADER_ATTR)
      if (raw.dependencies.any { it is DependenciesElement.ModuleDependency }) reporter("<dependencies><module>")
      if (raw.dependencies.any { it is DependenciesElement.PluginDependency }) reporter("<dependencies><plugin>")
    }
  }
}


@ApiStatus.Internal
class ContentModuleDescriptor(
  val parent: PluginMainDescriptor,
  raw: RawPluginDescriptor,
  moduleId: PluginModuleId,
  moduleLoadingRule: ModuleLoadingRule,
  private val descriptorPath: String
): PluginModuleDescriptor(raw) {
  val moduleId: PluginModuleId = moduleId
  val moduleLoadingRule: ModuleLoadingRule = moduleLoadingRule

  override val useCoreClassLoader: Boolean
    get() = parent.useCoreClassLoader
  override val useIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader
  override val isIndependentFromCoreClassLoader: Boolean = raw.isIndependentFromCoreClassLoader

  private val resourceBundleBaseName: String? = raw.resourceBundleBaseName

  /** java helper */
  fun getModuleIdString(): String = moduleId.id

  override fun getDescriptorPath(): String = descriptorPath

  override fun getResourceBundleBaseName(): String? = resourceBundleBaseName

  override fun toString(): String =
    "ContentModuleDescriptor(id=${this@ContentModuleDescriptor.moduleId}" +
    (if (moduleLoadingRule == ModuleLoadingRule.OPTIONAL) "" else ", loadingRule=$moduleLoadingRule") +
    (if (packagePrefix == null) "" else ", package=$packagePrefix") +
    (if (descriptorPath == "${this@ContentModuleDescriptor.moduleId}.xml") "" else ", descriptorPath=$descriptorPath") +
    ") <- $parent"

  init {
    reportContentModuleUnexpectedElements(raw) { logUnexpectedElement(it) }
  }

  override fun getPluginId(): PluginId = parent.pluginId
  @Deprecated("make sure you don't confuse it with moduleId; use main descriptor", level = DeprecationLevel.ERROR)
  override fun getName(): @NlsSafe String = parent.name // .also { LOG.error("unexpected call") } TODO test failures
  // <editor-fold desc="Deprecated">
  // These are meaningless for sub-descriptors
  @Deprecated("use main descriptor") override fun getVersion(): String? = parent.version
  @Deprecated("use main descriptor") override fun getSinceBuild(): String? = parent.sinceBuild
  @Deprecated("use main descriptor") override fun getUntilBuild(): String? = parent.untilBuild
  @Deprecated("use main descriptor") override fun getChangeNotes(): String? = parent.changeNotes
  @Deprecated("use main descriptor") override fun getCategory(): @NlsSafe String? = parent.category
  @Deprecated("use main descriptor") override fun getDisplayCategory(): @Nls String? = parent.displayCategory
  @Deprecated("use main descriptor") override fun getDescription(): @Nls String? = parent.description
  @Deprecated("use main descriptor") override fun isBundled(): Boolean = parent.isBundled
  @Deprecated("use main descriptor") override fun getPluginPath(): Path = parent.pluginPath
  @Deprecated("use main descriptor") override fun allowBundledUpdate(): Boolean = parent.allowBundledUpdate()
  @Deprecated("use main descriptor") override fun isImplementationDetail(): Boolean = parent.isImplementationDetail
  @Deprecated("use main descriptor") override fun isRequireRestart(): Boolean = parent.isRequireRestart
  @Deprecated("use main descriptor") override fun getVendor(): String? = parent.vendor
  @Deprecated("use main descriptor") override fun getVendorEmail(): String? = parent.vendorEmail
  @Deprecated("use main descriptor") override fun getVendorUrl(): String? = parent.vendorUrl
  @Deprecated("use main descriptor") override fun getUrl(): String? = parent.url
  @Deprecated("use main descriptor") override fun getProductCode(): String? = parent.productCode
  @Deprecated("use main descriptor") override fun getReleaseDate(): Date? = parent.releaseDate
  @Deprecated("use main descriptor") override fun getReleaseVersion(): Int = parent.releaseVersion
  @Deprecated("use main descriptor") override fun isLicenseOptional(): Boolean = parent.isLicenseOptional
  // </editor-fold>

  companion object {
    @VisibleForTesting
    fun reportContentModuleUnexpectedElements(raw: RawPluginDescriptor, reporter: (elementName: String) -> Unit) {
      reportSubDescriptorUnexpectedElements(raw, reporter)
      if (raw.depends.isNotEmpty()) reporter(PluginXmlConst.DEPENDS_ELEM)
    }
  }
}

@ApiStatus.Internal
tailrec fun IdeaPluginDescriptorImpl.getMainDescriptor(): PluginMainDescriptor = when (this) {
  is PluginMainDescriptor -> this
  is ContentModuleDescriptor -> parent
  is DependsSubDescriptor -> parent.getMainDescriptor()
}

@get:ApiStatus.Internal
@Deprecated("only PluginMainDescriptor has contentModules")
val IdeaPluginDescriptorImpl.contentModules: List<ContentModuleDescriptor>
  get() = if (this is PluginMainDescriptor) contentModules else emptyList()

@ApiStatus.Internal
@TestOnly
fun PluginMainDescriptor.createContentModuleInTest(
  subBuilder: PluginDescriptorBuilder,
  descriptorPath: String,
  module: PluginContentDescriptor.ModuleItem,
): ContentModuleDescriptor = createContentModule(subBuilder, descriptorPath, module)