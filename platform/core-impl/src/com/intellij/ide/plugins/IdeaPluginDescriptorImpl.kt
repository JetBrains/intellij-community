// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty", "OVERRIDE_DEPRECATION")
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import org.jetbrains.annotations.*
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*

private val LOG: Logger
  get() = PluginManagerCore.getLogger()


fun Collection<String>.toPluginIds(): Set<PluginId> = PluginManagerCore.toPluginIds(this)

fun Iterable<IdeaPluginDescriptor>.toPluginIdSet(): Set<PluginId> = mapTo(LinkedHashSet()) { it.pluginId }

fun Iterable<PluginId>.toPluginDescriptors(): List<IdeaPluginDescriptorImpl> {
  val pluginIdMap = PluginManagerCore.buildPluginIdMap()
  return mapNotNull { pluginIdMap[it] }
}

internal fun Iterable<PluginId>.joinedPluginIds(operation: String): String {
  return joinToString(
    prefix = "Plugins to $operation: [",
    postfix = "]",
  ) { it.idString }
}

@ApiStatus.Internal
class IdeaPluginDescriptorImpl(raw: RawPluginDescriptor,
                               @JvmField val path: Path,
                               private val isBundled: Boolean,
                               id: PluginId?,
                               @JvmField val moduleName: String?,
                               @JvmField val useCoreClassLoader: Boolean = false) : IdeaPluginDescriptor {
  private val id: PluginId = id ?: PluginId.getId(raw.id ?: raw.name ?: throw RuntimeException("Neither id nor name are specified"))
  private val name = raw.name ?: id?.idString ?: raw.id

  @Suppress("EnumEntryName")
  enum class OS {
    mac, linux, windows, unix, freebsd
  }

  // only for sub descriptors
  @JvmField internal var descriptorPath: String? = null
  @Volatile private var description: String? = null
  private val productCode = raw.productCode
  private var releaseDate: Date? = raw.releaseDate?.let { Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
  private val releaseVersion = raw.releaseVersion
  private val isLicenseOptional = raw.isLicenseOptional
  @NonNls private var resourceBundleBaseName: String? = null
  private val changeNotes = raw.changeNotes
  private var version: String? = raw.version
  private var vendor = raw.vendor
  private val vendorEmail = raw.vendorEmail
  private val vendorUrl = raw.vendorUrl
  private var category: String? = raw.category
  @JvmField internal val url = raw.url
  @JvmField val pluginDependencies: List<PluginDependency>
  @JvmField val incompatibilities: List<PluginId> = raw.incompatibilities ?: Collections.emptyList()

  init {
    // https://youtrack.jetbrains.com/issue/IDEA-206274
    val list = raw.depends
    if (list != null) {
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
    }
    pluginDependencies = list ?: Collections.emptyList()
  }

  companion object {

    private var _isOnDemandEnabled: Boolean? = null

    @VisibleForTesting
    const val ON_DEMAND_ENABLED_KEY: String = "ide.plugins.allow.on.demand"

    @JvmStatic
    var isOnDemandEnabled: Boolean
      @ApiStatus.Experimental get() {
        var result = _isOnDemandEnabled

        if (result == null) {
          synchronized(Companion::class.java) {
            if (_isOnDemandEnabled == null) {
              result = !AppMode.isHeadless()
                       && EarlyAccessRegistryManager.getBoolean(ON_DEMAND_ENABLED_KEY)
              _isOnDemandEnabled = result
            }
          }
        }

        return result!!
      }
      @TestOnly set(value) {
        synchronized(Companion::class.java) {
          _isOnDemandEnabled = value
        }
      }
  }

  @Transient @JvmField var jarFiles: List<Path>? = null
  private var _pluginClassLoader: ClassLoader? = null

  @JvmField val actions: List<RawPluginDescriptor.ActionDescriptor> = raw.actions ?: Collections.emptyList()

  // extension point name -> list of extension descriptors
  val epNameToExtensions: Map<String, MutableList<ExtensionDescriptor>>? = raw.epNameToExtensions

  @JvmField val appContainerDescriptor = raw.appContainerDescriptor
  @JvmField val projectContainerDescriptor = raw.projectContainerDescriptor
  @JvmField val moduleContainerDescriptor = raw.moduleContainerDescriptor

  @JvmField val content: PluginContentDescriptor = raw.contentModules?.let { PluginContentDescriptor(it) } ?: PluginContentDescriptor.EMPTY
  @JvmField val dependencies = raw.dependencies
  @JvmField var modules: List<PluginId> = raw.modules ?: Collections.emptyList()

  private val descriptionChildText = raw.description

  @JvmField val isUseIdeaClassLoader = raw.isUseIdeaClassLoader
  @JvmField val isBundledUpdateAllowed = raw.isBundledUpdateAllowed
  @JvmField internal val implementationDetail = raw.implementationDetail
  @ApiStatus.Experimental @JvmField internal val onDemand = isOnDemandEnabled && raw.onDemand
  @JvmField internal val isRestartRequired = raw.isRestartRequired
  @JvmField val packagePrefix = raw.`package`

  private val sinceBuild = raw.sinceBuild
  private val untilBuild = raw.untilBuild
  private var isEnabled = true

  var isDeleted = false

  @JvmField internal var isIncomplete: PluginLoadingError? = null

  override fun getDescriptorPath() = descriptorPath

  override fun getDependencies(): List<IdeaPluginDependency> {
    return if (pluginDependencies.isEmpty()) Collections.emptyList() else Collections.unmodifiableList(pluginDependencies)
  }

  override fun getPluginPath() = path

  private fun createSub(raw: RawPluginDescriptor,
                        descriptorPath: String,
                        pathResolver: PathResolver,
                        context: DescriptorListLoadingContext,
                        dataLoader: DataLoader,
                        moduleName: String?): IdeaPluginDescriptorImpl {
    raw.name = name
    val result = IdeaPluginDescriptorImpl(raw, path = path, isBundled = isBundled, id = id, moduleName = moduleName,
                                          useCoreClassLoader = useCoreClassLoader)
    result.descriptorPath = descriptorPath
    result.vendor = vendor
    result.version = version
    result.resourceBundleBaseName = resourceBundleBaseName

    result.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = true, dataLoader = dataLoader)
    return result
  }

  fun readExternal(raw: RawPluginDescriptor,
                   pathResolver: PathResolver,
                   context: DescriptorListLoadingContext,
                   isSub: Boolean,
                   dataLoader: DataLoader) {
    // include module file descriptor if not specified as `depends` (old way - xi:include)
    // must be first because merged into raw descriptor
    if (!isSub) {
      for (module in content.modules) {
        val subDescriptorFile = module.configFile ?: "${module.name}.xml"
        val subDescriptor = createSub(raw = pathResolver.resolveModuleFile(readContext = context,
                                                                           dataLoader = dataLoader,
                                                                           path = subDescriptorFile,
                                                                           readInto = null),
                                      descriptorPath = subDescriptorFile,
                                      pathResolver = pathResolver,
                                      context = context,
                                      dataLoader = dataLoader,
                                      moduleName = module.name)
        module.descriptor = subDescriptor
      }
    }

    if (raw.resourceBundleBaseName != null) {
      if (id == PluginManagerCore.CORE_ID && !isSub) {
        LOG.warn("<resource-bundle>${raw.resourceBundleBaseName}</resource-bundle> tag is found in an xml descriptor" +
                 " included into the platform part of the IDE but the platform part uses predefined bundles " +
                 "(e.g. ActionsBundle for actions) anyway; this tag must be replaced by a corresponding attribute in some inner tags " +
                 "(e.g. by 'resource-bundle' attribute in 'actions' tag)")
      }
      if (resourceBundleBaseName != null && resourceBundleBaseName != raw.resourceBundleBaseName) {
        LOG.warn("Resource bundle redefinition for plugin $id. " +
                 "Old value: $resourceBundleBaseName, new value: ${raw.resourceBundleBaseName}")
      }
      resourceBundleBaseName = raw.resourceBundleBaseName
    }

    if (version == null) {
      version = context.defaultVersion
    }

    if (!isSub) {
      if (id == PluginManagerCore.CORE_ID) {
        modules = modules + IdeaPluginPlatform.getHostPlatformModuleIds()
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
    }

    if (isIncomplete == null && moduleName == null) {
      processOldDependencies(descriptor = this,
                             context = context,
                             pathResolver = pathResolver,
                             dependencies = pluginDependencies,
                             dataLoader = dataLoader)
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

      var resolveError: Exception? = null
      val raw: RawPluginDescriptor? = try {
        pathResolver.resolvePath(readContext = context, dataLoader = dataLoader, relativePath = configFile, readInto = null)
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
      val subDescriptor = descriptor.createSub(raw = raw,
                                               descriptorPath = configFile,
                                               pathResolver = pathResolver,
                                               context = context,
                                               dataLoader = dataLoader,
                                               moduleName = null)
      dependency.subDescriptor = subDescriptor
      visitedFiles.clear()
    }
  }

  private fun checkCompatibility(context: DescriptorListLoadingContext) {
    if (isBundled) {
      return
    }

    fun markAsIncompatible(error: PluginLoadingError) {
      if (isIncomplete != null) {
        return
      }

      isIncomplete = error
      isEnabled = false
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

    // "Show broken plugins in Settings | Plugins so that users can uninstall them and resolve "Plugin Error" (IDEA-232675)"
    if (context.isBroken(this)) {
      markAsIncompatible(PluginLoadingError(
        plugin = this,
        detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.marked.as.broken", name, version) },
        shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.marked.as.broken") }
      ))
    }
  }

  private fun markAsIncomplete(disabledDependency: PluginId?,
                               @PropertyKey(resourceBundle = CoreBundle.BUNDLE) shortMessage: String?,
                               pluginId: PluginId? = disabledDependency) {
    if (isIncomplete != null) {
      return
    }

    if (shortMessage == null) {
      isIncomplete = PluginLoadingError(plugin = this, detailedMessageSupplier = null, shortMessageSupplier = PluginLoadingError.DISABLED)
    }
    else {
      isIncomplete = PluginLoadingError(plugin = this,
                                        detailedMessageSupplier = null,
                                        shortMessageSupplier = { CoreBundle.message(shortMessage, pluginId!!) },
                                        isNotifyUser = false,
                                        disabledDependency = disabledDependency)
    }

    isEnabled = false
  }

  @ApiStatus.Internal
  fun registerExtensions(nameToPoint: Map<String, ExtensionPointImpl<*>>,
                         containerDescriptor: ContainerDescriptor,
                         listenerCallbacks: MutableList<in Runnable>?) {
    containerDescriptor.extensions?.let {
      if (!it.isEmpty()) {
        @Suppress("JavaMapForEach")
        it.forEach { name, list ->
          nameToPoint.get(name)?.registerExtensions(list, this, listenerCallbacks)
        }
      }
      return
    }

    val unsortedMap = epNameToExtensions ?: return

    // app container: in most cases will be only app-level extensions - to reduce map copying, assume that all extensions are app-level and then filter out
    // project container: rest of extensions wil be mostly project level
    // module container: just use rest, area will not register unrelated extension anyway as no registered point

    if (containerDescriptor == appContainerDescriptor) {
      val registeredCount = doRegisterExtensions(unsortedMap, nameToPoint, listenerCallbacks)
      containerDescriptor.distinctExtensionPointCount = registeredCount

      if (registeredCount == unsortedMap.size) {
        projectContainerDescriptor.extensions = Collections.emptyMap()
        moduleContainerDescriptor.extensions = Collections.emptyMap()
      }
    }
    else if (containerDescriptor == projectContainerDescriptor) {
      val registeredCount = doRegisterExtensions(unsortedMap, nameToPoint, listenerCallbacks)
      containerDescriptor.distinctExtensionPointCount = registeredCount

      if (registeredCount == unsortedMap.size) {
        containerDescriptor.extensions = unsortedMap
        moduleContainerDescriptor.extensions = Collections.emptyMap()
      }
      else if (registeredCount == (unsortedMap.size - appContainerDescriptor.distinctExtensionPointCount)) {
        moduleContainerDescriptor.extensions = Collections.emptyMap()
      }
    }
    else {
      val registeredCount = doRegisterExtensions(unsortedMap, nameToPoint, listenerCallbacks)
      if (registeredCount == 0) {
        moduleContainerDescriptor.extensions = Collections.emptyMap()
      }
    }
  }

  private fun doRegisterExtensions(unsortedMap: Map<String, MutableList<ExtensionDescriptor>>,
                                   nameToPoint: Map<String, ExtensionPointImpl<*>>,
                                   listenerCallbacks: MutableList<in Runnable>?): Int {
    var registeredCount = 0
    for (entry in unsortedMap) {
      val point = nameToPoint.get(entry.key) ?: continue
      point.registerExtensions(entry.value, this, listenerCallbacks)
      registeredCount++
    }
    return registeredCount
  }

  @Suppress("HardCodedStringLiteral")
  override fun getDescription(): String? {
    var result = description
    if (result != null) {
      return result
    }

    result = (resourceBundleBaseName?.let { baseName ->
      try {
        AbstractBundle.messageOrDefault(
          DynamicBundle.getResourceBundle(classLoader, baseName),
          "plugin.$id.description",
          descriptionChildText ?: "",
        )
      }
      catch (_: MissingResourceException) {
        LOG.info("Cannot find plugin $id resource-bundle: $baseName")
        null
      }
    }) ?: descriptionChildText

    description = result
    return result
  }

  override fun getChangeNotes() = changeNotes

  override fun getName(): String = name!!

  override fun getProductCode() = productCode

  override fun getReleaseDate() = releaseDate

  override fun getReleaseVersion() = releaseVersion

  override fun isLicenseOptional() = isLicenseOptional

  override fun getOptionalDependentPluginIds(): Array<PluginId> {
    val pluginDependencies = pluginDependencies
    return if (pluginDependencies.isEmpty())
      PluginId.EMPTY_ARRAY
    else
      pluginDependencies.asSequence()
        .filter { it.isOptional }
        .map { it.pluginId }
        .toList()
        .toTypedArray()
  }

  override fun getVendor() = vendor

  override fun getVersion() = version

  override fun getResourceBundleBaseName() = resourceBundleBaseName

  override fun getCategory() = category

  /*
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  fun setCategory(category: String?) {
    this.category = category
  }

  val unsortedEpNameToExtensionElements: Map<String, List<ExtensionDescriptor>>
    get() {
      return Collections.unmodifiableMap(epNameToExtensions ?: return Collections.emptyMap())
    }

  override fun getVendorEmail() = vendorEmail

  override fun getVendorUrl() = vendorUrl

  override fun getUrl() = url

  override fun getPluginId() = id

  override fun getPluginClassLoader(): ClassLoader? = _pluginClassLoader

  @ApiStatus.Internal
  fun setPluginClassLoader(classLoader: ClassLoader?) {
    _pluginClassLoader = classLoader
  }

  override fun isEnabled() = isEnabled

  override fun setEnabled(enabled: Boolean) {
    isEnabled = enabled
  }

  override fun getSinceBuild() = sinceBuild

  override fun getUntilBuild() = untilBuild

  override fun isBundled() = isBundled

  override fun allowBundledUpdate() = isBundledUpdateAllowed

  override fun isImplementationDetail() = implementationDetail

  override fun isOnDemand() = onDemand

  override fun isRequireRestart() = isRestartRequired

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is IdeaPluginDescriptorImpl) {
      return false
    }
    return id == other.id && descriptorPath == other.descriptorPath
  }

  override fun hashCode(): Int {
    return 31 * id.hashCode() + (descriptorPath?.hashCode() ?: 0)
  }

  override fun toString(): String {
    return "PluginDescriptor(" +
           "name=$name, " +
           "id=$id, " +
           (if (moduleName == null) "" else "moduleName=$moduleName, ") +
           "descriptorPath=${descriptorPath ?: "plugin.xml"}, " +
           "path=${pluginPathToUserString(path)}, " +
           "version=$version, " +
           "package=$packagePrefix, " +
           "isBundled=$isBundled" +
           ")"
  }
}

// don't expose user home in error messages
internal fun pluginPathToUserString(file: Path): String {
  return file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")
}

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