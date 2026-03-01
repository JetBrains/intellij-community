// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CanBePrimaryConstructorProperty")

package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.PluginXmlConst
import com.intellij.platform.pluginSystem.parser.impl.RawPluginDescriptor
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.platform.pluginSystem.parser.impl.elements.DependenciesElement
import com.intellij.platform.pluginSystem.parser.impl.elements.DependsElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ExtensionElement
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.Date

private val LOG: Logger
  get() = PluginManagerCore.logger

@Internal
sealed class IdeaPluginDescriptorImpl(
  raw: RawPluginDescriptor,
) : IdeaPluginDescriptor {
  internal val pluginDependencies: List<PluginDependencyImpl> = raw.depends
    .let(::convertDepends)
  val incompatiblePlugins: List<PluginId> = raw.incompatibleWith.map(PluginId::getId)
  open val pluginAliases: List<PluginId> = raw.pluginAliases.map(PluginId::getId)

  abstract val moduleDependencies: ModuleDependencies

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

  /** **DO NOT USE** outside plugin subsystem internal code. It is public now due to an unfinished migration */
  var isMarkedForLoading: Boolean = true
  private var _pluginClassLoader: ClassLoader? = null

  abstract val isIndependentFromCoreClassLoader: Boolean
  abstract val useCoreClassLoader: Boolean
  abstract val useIdeaClassLoader: Boolean

  /**
   * Aka `<depends>` elements from the plugin.xml
   *
   * Note that it's different from [moduleDependencies]
   */
  override fun getDependencies(): List<PluginDependency> = pluginDependencies

  override fun getPluginClassLoader(): ClassLoader? = _pluginClassLoader

  @Internal
  fun setPluginClassLoader(classLoader: ClassLoader?) {
    _pluginClassLoader = classLoader
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean = isMarkedForLoading

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
}

internal fun logUnexpectedElement(descriptor: IdeaPluginDescriptor, elementName: String) {
  LOG.warnInProduction(PluginException(buildString {
    append("Plugin descriptor for ")
    when (descriptor) {
      is ContentModuleDescriptor -> append("content module '${descriptor.moduleId.name}' of plugin '${descriptor.pluginId}'")
      is DependsSubDescriptor -> append("'depends' sub-descriptor '${descriptor.descriptorPath}' of plugin '${descriptor.pluginId}'")
      is PluginMainDescriptor -> append("plugin '${descriptor.pluginId}'")
    }
    append(" has declared element '$elementName' which has no effect there")
    append("\n in ${descriptor}")
  }, descriptor.pluginId))
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
  if (raw.strictUntilBuild != null) reporter(PluginXmlConst.IDEA_VERSION_STRICT_UNTIL_ATTR)

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

/**
 * Either [PluginMainDescriptor] or [ContentModuleDescriptor].
 * Both of them can be referenced either by plugin id or a module name (while [DependsSubDescriptor] can't be referenced).
 */
@Internal
sealed class PluginModuleDescriptor(raw: RawPluginDescriptor) : IdeaPluginDescriptorImpl(raw)

@Internal
class DependsSubDescriptor(
  /** either [PluginMainDescriptor] or [DependsSubDescriptor]*/
  val parent: IdeaPluginDescriptorImpl,
  raw: RawPluginDescriptor,
  private val descriptorPath: String,
) : IdeaPluginDescriptorImpl(raw) {
  init {
    check(parent is PluginMainDescriptor || parent is DependsSubDescriptor)
  }

  override val useCoreClassLoader: Boolean
    get() = parent.useCoreClassLoader
  override val isIndependentFromCoreClassLoader: Boolean = raw.isIndependentFromCoreClassLoader

  override val moduleDependencies: ModuleDependencies = convertDependencies(raw.dependencies, null)

  private val rawResourceBundleBaseName: String? = raw.resourceBundleBaseName

  override fun getDescriptorPath(): String = descriptorPath

  override fun getResourceBundleBaseName(): String? = rawResourceBundleBaseName ?: parent.resourceBundleBaseName

  override fun toString(): String =
    "DependsSubDescriptor(" +
    "descriptorPath=$descriptorPath" +
    (if (packagePrefix == null) "" else ", package=$packagePrefix") +
    ") <- $parent"

  init {
    reportDependsSubDescriptorUnexpectedElements(raw) { logUnexpectedElement(this@DependsSubDescriptor, it) }
  }

  override fun getPluginId(): PluginId = parent.pluginId

  // <editor-fold desc="Deprecated">
  // These are meaningless for sub-descriptors
  @Deprecated("use main descriptor")
  override fun getName(): @NlsSafe String? = parent.name

  @Deprecated("use main descriptor")
  override fun getVersion(): String? = parent.version

  @Deprecated("use main descriptor")
  override fun getSinceBuild(): String? = parent.sinceBuild

  @Deprecated("use main descriptor")
  override fun getUntilBuild(): String? = parent.untilBuild

  @Deprecated("use main descriptor")
  override fun getChangeNotes(): String? = parent.changeNotes

  @Deprecated("use main descriptor")
  override fun getCategory(): @NlsSafe String? = parent.category

  @Deprecated("use main descriptor")
  override fun getDisplayCategory(): @Nls String? = parent.displayCategory

  @Deprecated("use main descriptor")
  override fun getDescription(): @Nls String? = parent.description

  @Deprecated("use main descriptor")
  override fun isBundled(): Boolean = parent.isBundled

  @Deprecated("use main descriptor")
  override fun getPluginPath(): Path = parent.pluginPath
  @Deprecated("use main descriptor") override val useIdeaClassLoader: Boolean get() = parent.useIdeaClassLoader

  @Deprecated("use main descriptor")
  override fun allowBundledUpdate(): Boolean = parent.allowBundledUpdate()

  @Deprecated("use main descriptor")
  override fun isImplementationDetail(): Boolean = parent.isImplementationDetail

  @Deprecated("use main descriptor")
  override fun isRequireRestart(): Boolean = parent.isRequireRestart

  @Deprecated("use main descriptor")
  override fun getVendor(): String? = parent.vendor

  @Deprecated("use main descriptor")
  override fun getVendorEmail(): String? = parent.vendorEmail

  @Deprecated("use main descriptor")
  override fun getVendorUrl(): String? = parent.vendorUrl

  @Deprecated("use main descriptor")
  override fun getUrl(): String? = parent.url

  @Deprecated("use main descriptor")
  override fun getProductCode(): String? = parent.productCode

  @Deprecated("use main descriptor")
  override fun getReleaseDate(): Date? = parent.releaseDate

  @Deprecated("use main descriptor")
  override fun getReleaseVersion(): Int = parent.releaseVersion

  @Deprecated("use main descriptor")
  override fun isLicenseOptional(): Boolean = parent.isLicenseOptional
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


@Internal
class ContentModuleDescriptor(
  @JvmField val parent: PluginMainDescriptor,
  raw: RawPluginDescriptor,
  @JvmField val moduleId: PluginModuleId,
  @JvmField val moduleLoadingRule: ModuleLoadingRule,
  private val descriptorPath: String,
) : PluginModuleDescriptor(raw) {
  val visibility: ModuleVisibility = raw.moduleVisibility.convert()

  override val moduleDependencies: ModuleDependencies = convertDependencies(raw.dependencies, parent)

  override val useCoreClassLoader: Boolean
    get() = parent.useCoreClassLoader
  override val useIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader
  override val isIndependentFromCoreClassLoader: Boolean = raw.isIndependentFromCoreClassLoader

  private val resourceBundleBaseName: String? = raw.resourceBundleBaseName

  /** java helper */
  fun getModuleNameString(): String = moduleId.name

  override fun getDescriptorPath(): String = descriptorPath

  override fun getResourceBundleBaseName(): String? = resourceBundleBaseName

  override fun toString(): String {
    return "ContentModuleDescriptor(id=${this@ContentModuleDescriptor.moduleId.name}" +
           (if (moduleLoadingRule == ModuleLoadingRule.OPTIONAL) "" else ", loadingRule=$moduleLoadingRule") +
           (if (packagePrefix == null) "" else ", package=$packagePrefix") +
           (if (descriptorPath == "${this@ContentModuleDescriptor.moduleId.name}.xml") "" else ", descriptorPath=$descriptorPath") +
           ") <- $parent"
  }

  init {
    reportContentModuleUnexpectedElements(raw) { logUnexpectedElement(this@ContentModuleDescriptor, it) }
  }

  override fun getPluginId(): PluginId = parent.pluginId

  @Deprecated("make sure you don't confuse it with moduleId; use main descriptor", level = DeprecationLevel.ERROR)
  override fun getName(): @NlsSafe String = parent.name // .also { LOG.error("unexpected call") } TODO test failures

  // <editor-fold desc="Deprecated">
  // These are meaningless for sub-descriptors
  @Deprecated("use main descriptor")
  override fun getVersion(): String? = parent.version

  @Deprecated("use main descriptor")
  override fun getSinceBuild(): String? = parent.sinceBuild

  @Deprecated("use main descriptor")
  override fun getUntilBuild(): String? = parent.untilBuild

  @Deprecated("use main descriptor")
  override fun getChangeNotes(): String? = parent.changeNotes

  @Deprecated("use main descriptor")
  override fun getCategory(): @NlsSafe String? = parent.category

  @Deprecated("use main descriptor")
  override fun getDisplayCategory(): @Nls String? = parent.displayCategory

  @Deprecated("use main descriptor")
  override fun getDescription(): @Nls String? = parent.description

  @Deprecated("use main descriptor")
  override fun isBundled(): Boolean = parent.isBundled

  @Deprecated("use main descriptor")
  override fun getPluginPath(): Path = parent.pluginPath

  @Deprecated("use main descriptor")
  override fun allowBundledUpdate(): Boolean = parent.allowBundledUpdate()

  @Deprecated("use main descriptor")
  override fun isImplementationDetail(): Boolean = parent.isImplementationDetail

  @Deprecated("use main descriptor")
  override fun isRequireRestart(): Boolean = parent.isRequireRestart

  @Deprecated("use main descriptor")
  override fun getVendor(): String? = parent.vendor

  @Deprecated("use main descriptor")
  override fun getVendorEmail(): String? = parent.vendorEmail

  @Deprecated("use main descriptor")
  override fun getVendorUrl(): String? = parent.vendorUrl

  @Deprecated("use main descriptor")
  override fun getUrl(): String? = parent.url

  @Deprecated("use main descriptor")
  override fun getProductCode(): String? = parent.productCode

  @Deprecated("use main descriptor")
  override fun getReleaseDate(): Date? = parent.releaseDate

  @Deprecated("use main descriptor")
  override fun getReleaseVersion(): Int = parent.releaseVersion

  @Deprecated("use main descriptor")
  override fun isLicenseOptional(): Boolean = parent.isLicenseOptional
  // </editor-fold>

  companion object {
    @VisibleForTesting
    fun reportContentModuleUnexpectedElements(raw: RawPluginDescriptor, reporter: (elementName: String) -> Unit) {
      reportSubDescriptorUnexpectedElements(raw, reporter)
      if (raw.depends.isNotEmpty()) {
        reporter(PluginXmlConst.DEPENDS_ELEM)
      }
    }
  }
}

@Internal
tailrec fun IdeaPluginDescriptorImpl.getMainDescriptor(): PluginMainDescriptor {
  return when (this) {
    is PluginMainDescriptor -> this
    is ContentModuleDescriptor -> parent
    is DependsSubDescriptor -> parent.getMainDescriptor()
  }
}

@get:Internal
@Deprecated("only PluginMainDescriptor has contentModules")
val IdeaPluginDescriptorImpl.contentModules: List<ContentModuleDescriptor>
  get() = if (this is PluginMainDescriptor) contentModules else emptyList()

@get:Internal
val IdeaPluginDescriptorImpl.isLoaded: Boolean
  get() = pluginClassLoader != null

internal fun convertDependencies(dependencies: List<DependenciesElement>, parent: PluginMainDescriptor?): ModuleDependencies {
  if (dependencies.isEmpty()) {
    return ModuleDependencies.EMPTY
  }

  val moduleDeps = ArrayList<PluginModuleId>()
  val pluginDeps = ArrayList<PluginId>()
  var cachedContentModuleNames: Set<String>? = null
  for (dep in dependencies) {
    when (dep) {
      is DependenciesElement.PluginDependency -> pluginDeps.add(PluginId.getId(dep.pluginId))
      is DependenciesElement.ModuleDependency -> {
        val namespace =
          dep.namespace
          ?: run {
            if (cachedContentModuleNames == null) {
              cachedContentModuleNames = parent?.content?.modules?.mapTo(HashSet()) { it.moduleId.name } ?: emptySet()
            }
            if (dep.moduleName in cachedContentModuleNames) parent!!.namespace ?: parent.implicitNamespaceForPrivateModules else null
          }
          ?: PluginModuleId.JETBRAINS_NAMESPACE
        moduleDeps.add(PluginModuleId(dep.moduleName, namespace))
      }
      else -> LOG.error("Unknown dependency type: $dep")
    }
  }
  return ModuleDependencies(moduleDeps, pluginDeps)
}

private fun convertDepends(depends: List<DependsElement>): MutableList<PluginDependencyImpl> {
  return depends.mapTo(ArrayList(depends.size)) {
    PluginDependencyImpl(PluginId.getId(it.pluginId), it.configFile, it.isOptional)
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
    result[key] = rawMap[key]!!
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
    }
    catch (e: Throwable) {
      LOG.error(e)
      null
    }
  }
}