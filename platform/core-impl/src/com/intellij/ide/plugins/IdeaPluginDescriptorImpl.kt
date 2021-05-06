// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.BeanExtensionPoint
import com.intellij.openapi.extensions.impl.ExtensionDescriptor
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.InterfaceExtensionPoint
import com.intellij.platform.util.plugins.DataLoader
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.ZoneOffset
import java.util.*
import java.util.function.Supplier

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

@ApiStatus.Internal
class IdeaPluginDescriptorImpl(raw: RawPluginDescriptor,
                               val path: Path,
                               private val isBundled: Boolean,
                               id: PluginId?) : IdeaPluginDescriptor {
  val id: PluginId = id ?: PluginId.getId(raw.id ?: raw.name ?: throw RuntimeException("Nor id, neither name are specified"))
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
  private var resourceBundleBaseName: String? = null
  private val changeNotes = raw.changeNotes
  private var version: String? = raw.version
  private var vendor = raw.vendor
  private val vendorEmail = raw.vendorEmail
  private val vendorUrl = raw.vendorUrl
  private var category: String? = raw.category
  @JvmField internal val url = raw.url
  @JvmField internal val pluginDependencies: List<PluginDependency>?
  @JvmField val incompatibilities: List<PluginId>? = raw.incompatibilities

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
    pluginDependencies = list
  }

  @Transient @JvmField internal var jarFiles: List<Path>? = null
  @JvmField var classLoader: ClassLoader? = null

  @JvmField internal val actionElements = raw.actionElements

  // extension point name -> list of extension elements
  private var epNameToExtensionElements = raw.epNameToExtensionElements

  @JvmField val appContainerDescriptor = raw.appContainerDescriptor
  @JvmField val projectContainerDescriptor = raw.projectContainerDescriptor
  @JvmField val moduleContainerDescriptor = raw.moduleContainerDescriptor

  @JvmField internal val contentDescriptor = raw.contentDescriptor
  @JvmField internal val dependencyDescriptor = raw.dependencyDescriptor
  private val modules = raw.modules

  private val descriptionChildText = raw.description

  @JvmField val isUseIdeaClassLoader = raw.isUseIdeaClassLoader

  var isUseCoreClassLoader = false
    private set

  @JvmField val isBundledUpdateAllowed = raw.isBundledUpdateAllowed

  @JvmField internal val implementationDetail = raw.implementationDetail

  @JvmField internal val isRestartRequired = raw.isRestartRequired

  @JvmField val packagePrefix = raw.`package`

  private val sinceBuild = raw.sinceBuild
  private val untilBuild = raw.untilBuild
  private var isEnabled = true

  var isDeleted = false

  @JvmField internal var isIncomplete = false

  override fun getDescriptorPath() = descriptorPath

  override fun getDependencies(): List<IdeaPluginDependency> {
    return Collections.unmodifiableList(pluginDependencies ?: return Collections.emptyList())
  }

  @ApiStatus.Internal
  fun getPluginDependencies(): List<PluginDependency> = pluginDependencies ?: Collections.emptyList()

  override fun getPluginPath() = path

  fun createSub(raw: RawPluginDescriptor, descriptorPath: String): IdeaPluginDescriptorImpl {
    raw.name = name
    @Suppress("TestOnlyProblems")
    val result = IdeaPluginDescriptorImpl(raw, path = path, isBundled = isBundled, id = id)
    result.descriptorPath = descriptorPath
    result.vendor = vendor
    result.version = version
    result.resourceBundleBaseName = resourceBundleBaseName
    return result
  }

  fun readExternal(raw: RawPluginDescriptor,
                   pathResolver: PathResolver,
                   context: DescriptorListLoadingContext,
                   isSub: Boolean,
                   dataLoader: DataLoader): Boolean {
    // include module file descriptor if not specified as `depends` (old way - xi:include)
    // must be first because merged into raw descriptor
    if (!isSub) {
      moduleLoop@ for (module in contentDescriptor.modules) {
        val descriptorFile = module.configFile ?: "${module.name}.xml"
        val oldDepends = raw.depends
        if (oldDepends != null) {
          for (dependency in oldDepends) {
            if (descriptorFile == dependency.configFile) {
              // ok, it is specified in old way as depends tag - skip it
              continue@moduleLoop
            }
          }
        }

        pathResolver.resolvePath(context, dataLoader, descriptorFile, raw)
        ?: throw RuntimeException("Plugin $this misses optional descriptor $descriptorFile")
        module.isInjected = true
      }
    }

    if (raw.resourceBundleBaseName != null) {
      if (id == PluginManagerCore.CORE_ID) {
        LOG.warn(
          "<resource-bundle>${raw.resourceBundleBaseName}</resource-bundle> tag is found in an xml descriptor included into the platform part of the IDE " +
          "but the platform part uses predefined bundles (e.g. ActionsBundle for actions) anyway; " +
          "this tag must be replaced by a corresponding attribute in some inner tags (e.g. by 'resource-bundle' attribute in 'actions' tag)")
      }
      if (resourceBundleBaseName != null && resourceBundleBaseName != raw.resourceBundleBaseName) {
        LOG.warn("Resource bundle redefinition for plugin $id." +
                                              " Old value: $resourceBundleBaseName, new value: ${raw.resourceBundleBaseName}")
      }
      resourceBundleBaseName = raw.resourceBundleBaseName
    }

    if (version == null) {
      version = context.defaultVersion
    }

    if (context.isPluginDisabled(id)) {
      markAsIncomplete(context, null, null)
      if (LOG.isDebugEnabled) {
        LOG.debug("Skipping reading of $id from $path (reason: disabled)")
      }
      return false
    }

    if (isIncomplete || !checkCompatibility(context)) {
      return false
    }

    for (pluginDependency in dependencyDescriptor.plugins) {
      if (context.isPluginDisabled(pluginDependency.id)) {
        markAsIncomplete(context, pluginDependency.id) {
          CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", pluginDependency.id)
        }
        return false
      }
      else if (context.result.isBroken(pluginDependency.id)) {
        markAsIncomplete(context, null) {
          CoreBundle.message("plugin.loading.error.short.depends.on.broken.plugin", pluginDependency.id)
        }
        return false
      }
    }

    createExtensionPoints(appContainerDescriptor, this)
    createExtensionPoints(projectContainerDescriptor, this)
    createExtensionPoints(moduleContainerDescriptor, this)

    pluginDependencies?.let {
      processOldDependencies(descriptor = this,
                             context = context,
                             pathResolver = pathResolver,
                             dependencies = it,
                             dataLoader = dataLoader)
    }

    return true
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
        if (!dependency.isOptional && !isIncomplete) {
          markAsIncomplete(context, dependency.pluginId) {
            CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependency.pluginId)
          }
        }
        dependency.isDisabledOrBroken = true
      }
      else if (context.result.isBroken(dependency.pluginId)) {
        if (!dependency.isOptional && !isIncomplete) {
          LOG.info("Skipping reading of $id from $path " +
                                                "(reason: non-optional dependency ${dependency.pluginId} is broken)")
          markAsIncomplete(context = context, disabledDependency = null) {
            CoreBundle.message("plugin.loading.error.short.depends.on.broken.plugin", dependency.pluginId)
          }
        }
        dependency.isDisabledOrBroken = true
      }

      if (dependency.isDisabledOrBroken) {
        continue
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

      val subDescriptor = descriptor.createSub(raw, configFile)
      visitedFiles.add(configFile)
      if (subDescriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = true, dataLoader = dataLoader)) {
        dependency.subDescriptor = subDescriptor
      }
      visitedFiles.clear()
    }
  }

  private fun checkCompatibility(context: DescriptorListLoadingContext): Boolean {
    if (isBundled || sinceBuild == null && untilBuild == null) {
      return true
    }

    val error = PluginManagerCore.checkBuildNumberCompatibility(this, context.result.productBuildNumber.get()) ?: return true

    // error will be added by reportIncompatiblePlugin
    markAsIncomplete(context = context, disabledDependency = null, shortMessage = null)
    context.result.reportIncompatiblePlugin(this, error)
    return false
  }

  internal fun markAsIncomplete(context: DescriptorListLoadingContext, disabledDependency: PluginId?, shortMessage: Supplier<String>?) {
    val wasIncomplete = isIncomplete
    isIncomplete = true
    isEnabled = false
    if (!wasIncomplete) {
      val pluginError = if (shortMessage == null) null else PluginLoadingError.createWithoutNotification(this, shortMessage)
      if (pluginError != null && disabledDependency != null) {
        pluginError.disabledDependency = disabledDependency
      }
      context.result.addIncompletePlugin(this, pluginError)
    }
  }

  @ApiStatus.Internal
  fun registerExtensions(area: ExtensionsAreaImpl, containerDescriptor: ContainerDescriptor, listenerCallbacks: List<Runnable>?) {
    var extensions = containerDescriptor.extensions
    if (extensions != null) {
      area.registerExtensions(extensions, this, listenerCallbacks)
      return
    }

    extensions = epNameToExtensionElements
    if (extensions == null) {
      return
    }

    // app container: in most cases will be only app-level extensions - to reduce map copying, assume that all extensions are app-level and then filter out
    // project container: rest of extensions wil be mostly project level
    // module container: just use rest, area will not register unrelated extension anyway as no registered point
    containerDescriptor.extensions = extensions
    var other: LinkedHashMap<String, MutableList<ExtensionDescriptor>>? = null
    val iterator = extensions.entries.iterator()
    while (iterator.hasNext()) {
      val (key, value) = iterator.next()
      if (!area.registerExtensions(key, value, this, listenerCallbacks)) {
        iterator.remove()
        if (other == null) {
          other = LinkedHashMap()
        }
        addExtensionList(other, key, value)
      }
    }

    if (extensions.isEmpty()) {
      containerDescriptor.extensions = Collections.emptyMap()
    }

    if (containerDescriptor == projectContainerDescriptor) {
      // assign unsorted to module level to avoid concurrent access during parallel module loading
      moduleContainerDescriptor.extensions = other
      epNameToExtensionElements = null
    }
    else {
      epNameToExtensionElements = other
    }
  }

  override fun getDescription(): String? {
    @Suppress("HardCodedStringLiteral")
    var result = description
    if (result != null) {
      return result
    }

    val bundle: ResourceBundle? = resourceBundleBaseName?.let { resourceBundleBaseName ->
      try {
        DynamicBundle.INSTANCE.getResourceBundle(resourceBundleBaseName, pluginClassLoader)
      }
      catch (e: MissingResourceException) {
        LOG.info("Cannot find plugin $id resource-bundle: $resourceBundleBaseName")
        null
      }
    }
    if (bundle == null) {
      result = descriptionChildText
    }
    else {
      result = AbstractBundle.messageOrDefault(bundle, "plugin.$id.description", descriptionChildText ?: "")
    }
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
    if (pluginDependencies == null || pluginDependencies.isEmpty()) {
      return PluginId.EMPTY_ARRAY
    }
    else {
      return pluginDependencies.asSequence().filter { it.isOptional }.map { it.pluginId }.toList().toTypedArray()
    }
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
      return Collections.unmodifiableMap(epNameToExtensionElements ?: return Collections.emptyMap())
    }

  fun getActionDescriptionElements(): List<RawPluginDescriptor.ActionDescriptor>? = actionElements

  override fun getVendorEmail() = vendorEmail

  override fun getVendorUrl() = vendorUrl

  override fun getUrl() = url!!

  //fun setUrl(`val`: String?) {
  //  url = `val`
  //}

  override fun getPluginId() = id

  override fun getPluginClassLoader(): ClassLoader = classLoader ?: javaClass.classLoader

  fun setUseCoreClassLoader() {
    isUseCoreClassLoader = true
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

  override fun isRequireRestart() = isRestartRequired

  fun getModules(): List<PluginId> = modules ?: Collections.emptyList()

  override fun equals(other: Any?) = this === other || id == if (other is IdeaPluginDescriptorImpl) other.id else null

  override fun hashCode() = id.hashCode()

  override fun toString(): String {
    return "PluginDescriptor(name=$name, id=$id, descriptorPath=${descriptorPath ?: "plugin.xml"}, " +
           "path=${pluginPathToUserString(path)}, version=$version, package=$packagePrefix)"
  }
}

// don't expose user home in error messages
internal fun pluginPathToUserString(file: Path): String {
  return file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")
}

private fun addExtensionList(map: MutableMap<String, MutableList<ExtensionDescriptor>>, name: String, list: MutableList<ExtensionDescriptor>) {
  val mapList = map.computeIfAbsent(name) { list }
  if (mapList !== list) {
    mapList.addAll(list)
  }
}

private fun createExtensionPoints(containerDescriptor: ContainerDescriptor, pluginDescriptor: IdeaPluginDescriptorImpl) {
  containerDescriptor.extensionPoints = (containerDescriptor.extensionPointDescriptors ?: return)
    .mapTo(ArrayList()) {
      val name = it.qualifiedName ?: "${pluginDescriptor.id}.${it.name!!}"
      if (it.`interface` == null) {
        @Suppress("RemoveExplicitTypeArguments")
        BeanExtensionPoint<Any>(name, it.beanClass!!, pluginDescriptor, it.dynamic)
      }
      else {
        @Suppress("RemoveExplicitTypeArguments")
        InterfaceExtensionPoint<Any>(name, it.`interface`, pluginDescriptor, null, it.dynamic)
      }
    }
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