// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginXmlConst
import com.intellij.platform.plugins.parser.impl.RawPluginDescriptor
import com.intellij.platform.plugins.parser.impl.elements.ContentModuleElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleVisibilityValue
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*

private val LOG: Logger
  get() = PluginManagerCore.logger

/**
 * Main plugin descriptor, instantiated from "plugin.xml" (or from platform XMLs for Core).
 */
@Internal
class PluginMainDescriptor(
  raw: RawPluginDescriptor,
  private val pluginPath: Path,
  private val isBundled: Boolean,
  override val useCoreClassLoader: Boolean = false,
) : PluginModuleDescriptor(raw) {
  private val id: PluginId = PluginId.getId(raw.id ?: raw.name ?: throw IllegalArgumentException("Neither id nor name are specified for plugin located at $pluginPath"))
  private val name: String = raw.name ?: id.idString

  private val version: String? = raw.version
  private val sinceBuild: String? = raw.sinceBuild

  @Suppress("DEPRECATION")
  private val untilBuild: String? = raw.strictUntilBuild ?: UntilBuildDeprecation.nullizeIfTargetsMinimalApiOrLater(raw.untilBuild, raw.name ?: raw.id)

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

  private val isBundledUpdateAllowed: Boolean = raw.isBundledUpdateAllowed
  private val isRestartRequired: Boolean = raw.isRestartRequired
  private val isImplementationDetail: Boolean = raw.isImplementationDetail

  override val isIndependentFromCoreClassLoader: Boolean
    get() = false

  override val useIdeaClassLoader: Boolean = raw.isUseIdeaClassLoader

  private val resourceBundleBaseName: String? = raw.resourceBundleBaseName

  override val pluginAliases: List<PluginId> = super.pluginAliases.let(::addCorePluginAliases)

  /**
   * Explicitly set namespace for content modules of the plugin
   */
  val namespace: String? = raw.namespace

  /**
   * Implicit namespace used in [PluginModuleId] instances for dependencies between plugins modules if the explicit [namespace] is not set.
   * Currently, it's not necessary to specify the namespace explicitly if all modules are private and don't depend on internal modules, but it's still convenient to have some
   * namespace for debugging and logging.
   */
  internal val implicitNamespaceForPrivateModules by lazy { $$"$${id.idString}_$implicit" }

  /**
   * this is an implementation detail required during descriptor loading, use [contentModules] instead
   */
  @VisibleForTesting
  val content: PluginContentDescriptor =
    raw.contentModules.takeIf { it.isNotEmpty() }?.let { convertContentModules(it, namespace ?: implicitNamespaceForPrivateModules) }
    ?: PluginContentDescriptor.EMPTY

  val contentModules: List<ContentModuleDescriptor>
    get() = content.modules.map { it.descriptor }

  override val moduleDependencies: ModuleDependencies = convertDependencies(raw.dependencies, this)

  init {
    reportMainDescriptorUnexpectedElements(raw) { logUnexpectedElement(this@PluginMainDescriptor, it) }
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

  override fun toString(): String {
    return "PluginMainDescriptor(name=$name, id=$pluginId, version=$version, " +
           (if (packagePrefix == null) "" else "package=$packagePrefix, ") +
           "isBundled=$isBundled, " +
           "path=${PluginUtils.pluginPathToUserString(pluginPath)})"
  }


  private fun fromPluginBundle(key: String, @Nls defaultValue: String?): @Nls String? {
    val pluginClassLoader = pluginClassLoader
                            ?: return defaultValue
    val baseName = resourceBundleBaseName
    if (baseName == null) {
      return defaultValue
    }
    return (try {
      AbstractBundle.messageOrDefault(DynamicBundle.getResourceBundle(pluginClassLoader, baseName), key, defaultValue ?: "")
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
    return pluginAliases +
           IdeaPluginOsRequirement.getHostOsModuleIds() +
           PluginCpuArchRequirement.getHostCpuArchModuleIds() +
           productModeAliasesForCorePlugin()
  }

  private fun convertContentModules(contentElements: List<ContentModuleElement>, namespace: String): PluginContentDescriptor {
    val modules = contentElements.map { elem ->
      val index = elem.name.lastIndexOf('/')
      val configFile: String? = if (index == -1) {
        null
      }
      else {
        "${elem.name.substring(0, index)}.${elem.name.substring(index + 1)}.xml"
      }
      val moduleId = PluginModuleId(elem.name, namespace)
      PluginContentDescriptor.ModuleItem(
        moduleId = moduleId,
        configFile = configFile,
        descriptorContent = elem.embeddedDescriptorContent,
        loadingRule = elem.loadingRule.convert(),
        requiredIfAvailable = elem.requiredIfAvailable?.let { PluginModuleId(it, PluginModuleId.JETBRAINS_NAMESPACE) },
      )
    }
    if (modules.size > 1) {
      val duplicates = HashSet<PluginModuleId>()
      for (item in modules) {
        require(duplicates.add(item.moduleId)) {
          "Duplicate content module declaration: '${item.moduleId}' in plugin '${id}' located at $pluginPath"
        }
      }
    }
    return PluginContentDescriptor(modules)
  }

  internal fun createContentModule(
    subBuilder: PluginDescriptorBuilder,
    descriptorPath: String,
    module: PluginContentDescriptor.ModuleItem,
  ): ContentModuleDescriptor = ContentModuleDescriptor(
    parent = this,
    raw = subBuilder.build(),
    moduleId = module.moduleId,
    moduleLoadingRule = module.determineLoadingRule( // FIXME this call should happen in init phase, not while parsing
      initContextForLoadingRuleDetermination,
      id
    ),
    descriptorPath = descriptorPath
  )

  init {
    if (pluginId == PluginManagerCore.CORE_ID && resourceBundleBaseName != null) {
      LOG.warn("<resource-bundle>$resourceBundleBaseName</resource-bundle> tag is found in an xml descriptor" +
               " included into the platform part of the IDE but the platform part uses predefined bundles " +
               "(e.g. ActionsBundle for actions) anyway; this tag must be replaced by a corresponding attribute in some inner tags " +
               "(e.g. by 'resource-bundle' attribute in 'actions' tag)")
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
 * These modules are automatically disabled if they aren't relevant to the product mode, see [moduleIncompatibleWithCurrentProductMode].
 */
@VisibleForTesting
@Obsolete
@Internal
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

@VisibleForTesting
@Internal
fun reportMainDescriptorUnexpectedElements(raw: RawPluginDescriptor, reporter: (elementName: String) -> Unit) {
  if (raw.moduleVisibility != ModuleVisibilityValue.PRIVATE) {
    reporter(PluginXmlConst.CONTENT_MODULE_VISIBILITY_ATTR)
  }
}

// FIXME this should not exist
@Volatile
private var initContextForLoadingRuleDetermination: PluginInitializationContext = ProductPluginInitContext()

// FIXME this should not exist
@Internal
@TestOnly
fun <T> withInitContextForLoadingRuleDetermination(initContext: PluginInitializationContext, body: () -> T): T {
  val prev = initContextForLoadingRuleDetermination
  initContextForLoadingRuleDetermination = initContext
  try {
    return body()
  } finally {
    initContextForLoadingRuleDetermination = prev
  }
}

@Internal
@TestOnly
fun PluginMainDescriptor.createContentModuleInTest(
  subBuilder: PluginDescriptorBuilder,
  descriptorPath: String,
  module: PluginContentDescriptor.ModuleItem,
): ContentModuleDescriptor = createContentModule(subBuilder, descriptorPath, module)