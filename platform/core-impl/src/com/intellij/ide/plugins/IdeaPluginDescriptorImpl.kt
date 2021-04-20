// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.core.CoreBundle
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.util.text.Strings
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.text.ParseException
import java.util.*
import java.util.function.Supplier
import java.util.regex.Pattern

@ApiStatus.Internal
class IdeaPluginDescriptorImpl(val path: Path, private val isBundled: Boolean) : IdeaPluginDescriptor {
  constructor(id: PluginId, name: String, descriptorPath: String, path: Path, isBundled: Boolean) : this(path, isBundled) {
    this.id = id
    this.name = name
    this.descriptorPath = descriptorPath
  }

  @Suppress("EnumEntryName")
  enum class OS {
    mac, linux, windows, unix, freebsd
  }

  companion object {
    @JvmField
    val EMPTY_ARRAY = arrayOfNulls<IdeaPluginDescriptorImpl>(0)

    @JvmField
    val EXPLICIT_BIG_NUMBER_PATTERN: Pattern = Pattern.compile("(.*)\\.(9{4,}+|10{4,}+)")

    /**
     * Convert build number like '146.9999' to '146.*' (like plugin repository does) to ensure that plugins which have such values in
     * 'until-build' attribute will be compatible with 146.SNAPSHOT build.
     */
    @JvmStatic
    fun convertExplicitBigNumberInUntilBuildToStar(build: String?): String? {
      if (build == null) {
        return null
      }
      val matcher = EXPLICIT_BIG_NUMBER_PATTERN.matcher(build)
      return if (matcher.matches()) {
        matcher.group(1) + ".*"
      }
      else build
    }
  }

  @JvmField
  internal var name: String? = null
  var id: PluginId? = null
    internal set

  // only for sub descriptors
  @JvmField
  internal var descriptorPath: String? = null

  @Volatile
  private var description: String? = null
  private var productCode: String? = null
  private var releaseDate: Date? = null
  private var releaseVersion = 0
  private var isLicenseOptional = false
  private var resourceBundleBaseName: String? = null
  private var changeNotes: String? = null
  private var version: String? = null
  private var vendor: String? = null
  private var vendorEmail: String? = null
  private var vendorUrl: String? = null
  private var category: String? = null
  @JvmField
  internal var url: String? = null
  @JvmField
  internal var pluginDependencies: MutableList<PluginDependency>? = null
  @JvmField
  var incompatibilities: MutableList<PluginId>? = null

  @Transient
  @JvmField
  internal var jarFiles: List<Path>? = null
  @JvmField
  internal var actionElements: MutableList<Element>? = null

  // extension point name -> list of extension elements
  private var epNameToExtensionElements: MutableMap<String, MutableList<Element>>? = null

  @ApiStatus.Internal
  @JvmField
  val appContainerDescriptor = ContainerDescriptor()
  @ApiStatus.Internal
  @JvmField
  val projectContainerDescriptor = ContainerDescriptor()
  @ApiStatus.Internal
  @JvmField
  val moduleContainerDescriptor = ContainerDescriptor()

  @JvmField
  internal var contentDescriptor = PluginContentDescriptor.EMPTY
  @JvmField
  internal var dependencyDescriptor = ModuleDependenciesDescriptor.EMPTY
  private var modules: MutableList<PluginId>? = null

  @JvmField
  var classLoader: ClassLoader? = null

  private var descriptionChildText: @NlsSafe String? = null

  var isUseIdeaClassLoader = false
    internal set

  var isUseCoreClassLoader = false
    private set

  @JvmField
  var isBundledUpdateAllowed = false

  @JvmField
  internal var implementationDetail = false

  @JvmField
  internal var isRestartRequired = false

  var packagePrefix: String? = null
    internal set

  private var sinceBuild: String? = null
  private var untilBuild: String? = null
  private var isEnabled = true

  var isDeleted = false

  @JvmField
  internal var isIncomplete = false

  override fun getDescriptorPath() = descriptorPath

  override fun getDependencies(): List<IdeaPluginDependency> {
    return Collections.unmodifiableList(pluginDependencies ?: return Collections.emptyList())
  }

  @ApiStatus.Internal
  fun getPluginDependencies(): List<PluginDependency> {
    return if (pluginDependencies == null) Collections.emptyList() else pluginDependencies!!
  }

  override fun getPluginPath() = path

  @Throws(IOException::class, JDOMException::class)
  fun readExternal(element: Element,
                   pathResolver: PathResolver,
                   context: DescriptorListLoadingContext,
                   mainDescriptor: IdeaPluginDescriptorImpl,
                   dataLoader: DataLoader): Boolean {
    // root element always `!isIncludeElement`, and it means that result always is a singleton list
    // (also, plugin xml describes one plugin, this descriptor is not able to represent several plugins)
    if (JDOMUtil.isEmpty(element)) {
      markAsIncomplete(context = context, disabledDependency = null) { CoreBundle.message("plugin.loading.error.descriptor.file.is.empty") }
      return false
    }
    readIdAndName(this, element)

    // some information required for "incomplete" plugins can be in included files
    resolveNonXIncludeElement(context, pathResolver, dataLoader, element, null)
    if (id != null && context.isPluginDisabled(id!!)) {
      markAsIncomplete(context, null, null)
    }
    else {
      if (id == null || name == null) {
        // read again after resolve
        readIdAndName(this, element)
        if (id != null && context.isPluginDisabled(id!!)) {
          markAsIncomplete(context, null, null)
        }
      }
    }
    if (isIncomplete) {
      readEssentialPluginInformation(element, context)
      return false
    }
    readMetaInfo(this, element)
    pluginDependencies = null
    if (doRead(element, context, mainDescriptor, true)) {
      return false
    }
    if (version == null) {
      version = context.defaultVersion
    }
    if (vendor == null) {
      vendor = mainDescriptor.vendor
    }
    if (resourceBundleBaseName == null) {
      resourceBundleBaseName = mainDescriptor.resourceBundleBaseName
    }
    if (pluginDependencies != null) {
      readDependencies(mainDescriptor, this, context, pathResolver, pluginDependencies!!, dataLoader)
    }

    // include module file descriptor if not specified as `depends` (old way - xi:include)
    if (this === mainDescriptor) {
      moduleLoop@ for (module in contentDescriptor.modules) {
        val descriptorFile = module.configFile ?: module.name + ".xml"
        if (pluginDependencies != null) {
          for (dependency in pluginDependencies!!) {
            if (descriptorFile == dependency.configFile) {
              // ok, it is specified in old way as depends tag - skip it
              continue@moduleLoop
            }
          }
        }

        // inject as xi:include does
        val moduleElement = pathResolver.resolvePath(dataLoader, descriptorFile, context.xmlFactory)
                            ?: throw RuntimeException("Plugin $this misses optional descriptor $descriptorFile")
        doRead(moduleElement, context, mainDescriptor, false)
        module.isInjected = true
      }
    }
    return true
  }

  private fun readEssentialPluginInformation(element: Element, context: DescriptorListLoadingContext) {
    readMetaInfo(this, element)
    if (descriptionChildText == null) {
      descriptionChildText = element.getChildTextTrim("description")
    }
    if (category == null) {
      category = element.getChildTextTrim("category")
    }
    if (version == null) {
      version = element.getChildTextTrim("version")
    }
    if (DescriptorListLoadingContext.LOG.isDebugEnabled) {
      DescriptorListLoadingContext.LOG.debug("Skipping reading of $id from $path (reason: disabled)")
    }
    if (pluginDependencies == null) {
      val dependsElements = element.getChildren("depends")
      for (dependsElement in dependsElements) {
        readOldPluginDependency(context, dependsElement)
      }
    }
    val productElement = element.getChild("product-descriptor")
    productElement?.let { readProduct(context, it) }
    if (modules == null) {
      val moduleElements = element.getChildren("module")
      for (moduleElement in moduleElements) {
        readModule(moduleElement)
      }
    }
  }

  @TestOnly
  fun readForTest(element: Element, pluginId: PluginId) {
    id = pluginId
    doRead(element, DescriptorListLoadingContext.createSingleDescriptorContext(Collections.emptySet()), this, true)
  }

  /**
   * Returns `true` if there are compatibility problems with IDE (`depends`, `since-until`),
   * `false` - otherwise
   */
  private fun doRead(element: Element,
                     context: DescriptorListLoadingContext,
                     mainDescriptor: IdeaPluginDescriptorImpl,
                     readDependencies: Boolean): Boolean {
    for (content in element.content) {
      if (content !is Element) {
        continue
      }

      var clearContent = true
      when (content.name) {
        "extensions" -> epNameToExtensionElements = readExtensions(this, epNameToExtensionElements, context, content)
        "extensionPoints" -> readExtensionPoints(this, content)
        "actions" -> {
          if (actionElements == null) {
            actionElements = ArrayList(content.children)
          }
          else {
            actionElements!!.addAll(content.children)
          }
          clearContent = content.getAttributeValue("resource-bundle") == null
        }
        "module" -> readModule(content)
        // because of x-pointer, maybe several application-components tag in document
        "application-components" -> readComponents(content, appContainerDescriptor)
        "project-components" -> readComponents(content, projectContainerDescriptor)
        "module-components" -> readComponents(content, moduleContainerDescriptor)
        "applicationListeners" -> readListeners(content, appContainerDescriptor, mainDescriptor)
        "projectListeners" -> readListeners(content, projectContainerDescriptor, mainDescriptor)
        "content" -> readContent(content, this)
        "dependencies" -> if (readDependencies && !readNewDependencies(content, this, context)) {
          return true
        }
        "depends" -> if (readDependencies && !readOldPluginDependency(context, content)) {
          return true
        }
        "incompatible-with" -> readPluginIncompatibility(content)
        "category" -> category = Strings.nullize(content.textTrim)
        "change-notes" -> changeNotes = Strings.nullize(content.textTrim)
        "version" -> version = Strings.nullize(content.textTrim)
        "description" -> descriptionChildText = Strings.nullize(content.textTrim)
        "resource-bundle" -> {
          val value = Strings.nullize(content.textTrim)
          if (PluginManagerCore.CORE_ID == mainDescriptor.pluginId) {
            DescriptorListLoadingContext.LOG.warn(
              "<resource-bundle>$value</resource-bundle> tag is found in an xml descriptor included into the platform part of the IDE " +
              "but the platform part uses predefined bundles (e.g. ActionsBundle for actions) anyway; " +
              "this tag must be replaced by a corresponding attribute in some inner tags (e.g. by 'resource-bundle' attribute in 'actions' tag)")
          }
          if (resourceBundleBaseName != null && resourceBundleBaseName != value) {
            DescriptorListLoadingContext.LOG.warn(
              "Resource bundle redefinition for plugin '${mainDescriptor.pluginId}'. Old value: $resourceBundleBaseName, new value: $value")
          }
          resourceBundleBaseName = value
        }
        "product-descriptor" -> readProduct(context, content)
        "vendor" -> {
          vendor = Strings.nullize(content.textTrim)
          vendorEmail = Strings.nullize(content.getAttributeValue("email"))
          vendorUrl = Strings.nullize(content.getAttributeValue("url"))
        }
        "idea-version" -> {
          sinceBuild = Strings.nullize(content.getAttributeValue("since-build"))
          untilBuild = Strings.nullize(content.getAttributeValue("until-build"))
          if (!checkCompatibility(context) { readEssentialPluginInformation(element, context) }) {
            return true
          }
        }
      }
      if (clearContent) {
        content.content.clear()
      }
    }
    return false
  }

  private fun readModule(child: Element) {
    val moduleName = child.getAttributeValue("value") ?: return
    if (modules == null) {
      modules = Collections.singletonList(PluginId.getId(moduleName))
    }
    else {
      if (modules!!.size == 1) {
        val singleton = modules!!
        modules = ArrayList(4)
        modules!!.addAll(singleton)
      }
      modules!!.add(PluginId.getId(moduleName))
    }
  }

  private fun readProduct(context: DescriptorListLoadingContext, child: Element) {
    productCode = Strings.nullize(child.getAttributeValue("code"))
    releaseDate = parseReleaseDate(child.getAttributeValue("release-date"), context)
    releaseVersion = StringUtilRt.parseInt(child.getAttributeValue("release-version"), 0)
    isLicenseOptional = java.lang.Boolean.parseBoolean(child.getAttributeValue("optional", "false"))
  }

  private fun readPluginIncompatibility(child: Element) {
    val pluginId = child.textTrim
    if (pluginId.isEmpty()) {
      return
    }
    if (incompatibilities == null) {
      incompatibilities = ArrayList()
    }
    incompatibilities!!.add(PluginId.getId(pluginId))
  }

  private fun readOldPluginDependency(context: DescriptorListLoadingContext, child: Element): Boolean {
    val dependencyIdString = child.textTrim
    if (dependencyIdString.isEmpty()) {
      return true
    }

    val dependencyId = PluginId.getId(dependencyIdString)
    val isOptional = java.lang.Boolean.parseBoolean(child.getAttributeValue("optional"))
    var isDisabledOrBroken = false
    // context.isPluginIncomplete must be not checked here as another version of plugin maybe supplied later from another source
    if (context.isPluginDisabled(dependencyId)) {
      if (!isOptional) {
        markAsIncomplete(context, dependencyId) {
          CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId)
        }
        return false
      }
      isDisabledOrBroken = true
    }
    else if (context.result.isBroken(dependencyId)) {
      if (!isOptional) {
        DescriptorListLoadingContext.LOG.info("Skipping reading of $id from $path " +
                                              "(reason: non-optional dependency $dependencyId is broken)")
        markAsIncomplete(context = context, disabledDependency = null) {
          CoreBundle.message("plugin.loading.error.short.depends.on.broken.plugin", dependencyId)
        }
        return false
      }
      isDisabledOrBroken = true
    }
    val dependency = PluginDependency(dependencyId, child.getAttributeValue("config-file")?.takeIf { it.isNotEmpty() }, isDisabledOrBroken)
    dependency.isOptional = isOptional
    if (pluginDependencies == null) {
      pluginDependencies = ArrayList()
    }
    else {
      // https://youtrack.jetbrains.com/issue/IDEA-206274
      for (item in pluginDependencies!!) {
        if (dependencyId == item.pluginId) {
          if (item.isOptional) {
            if (!isOptional) {
              item.isOptional = false
            }
          }
          else {
            dependency.isOptional = false
            if (item.configFile == null) {
              item.configFile = dependency.configFile
              return true
            }
          }
        }
      }
    }
    pluginDependencies!!.add(dependency)
    return true
  }

  private fun checkCompatibility(context: DescriptorListLoadingContext, beforeCreateErrorCallback: Runnable): Boolean {
    if (isBundled || sinceBuild == null && untilBuild == null) {
      return true
    }

    val error = PluginManagerCore.checkBuildNumberCompatibility(this, context.result.productBuildNumber.get(),
                                                                beforeCreateErrorCallback) ?: return true

    // error will be added by reportIncompatiblePlugin
    markAsIncomplete(context = context, disabledDependency = null, shortMessage = null)
    context.result.reportIncompatiblePlugin(this, error)
    return false
  }

  internal fun markAsIncomplete(context: DescriptorListLoadingContext, disabledDependency: PluginId?, shortMessage: Supplier<String>?) {
    val wasIncomplete = isIncomplete
    isIncomplete = true
    isEnabled = false
    if (id != null && !wasIncomplete) {
      val pluginError = if (shortMessage == null) null else PluginLoadingError.createWithoutNotification(this, shortMessage)
      if (pluginError != null && disabledDependency != null) {
        pluginError.disabledDependency = disabledDependency
      }
      context.result.addIncompletePlugin(this, pluginError)
    }
  }

  private fun parseReleaseDate(dateStr: String?, context: DescriptorListLoadingContext): Date? {
    if (dateStr.isNullOrEmpty()) {
      return null
    }

    try {
      return context.dateParser.parse(dateStr)
    }
    catch (e: ParseException) {
      DescriptorListLoadingContext.LOG.info("Error parse release date from plugin descriptor for plugin $name (id=$id): ${e.message}")
    }
    return null
  }

  @ApiStatus.Internal
  fun registerExtensions(area: ExtensionsAreaImpl, containerDescriptor: ContainerDescriptor, listenerCallbacks: List<Runnable>?) {
    val extensions = containerDescriptor.extensions
    if (extensions != null) {
      area.registerExtensions(extensions, this, listenerCallbacks)
      return
    }
    if (epNameToExtensionElements == null) {
      return
    }

    // app container: in most cases will be only app-level extensions - to reduce map copying, assume that all extensions are app-level and then filter out
    // project container: rest of extensions wil be mostly project level
    // module container: just use rest, area will not register unrelated extension anyway as no registered point
    containerDescriptor.extensions = epNameToExtensionElements
    var other: LinkedHashMap<String, MutableList<Element>>? = null
    val iterator = containerDescriptor.extensions.entries.iterator()
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
    if (containerDescriptor.extensions.isEmpty()) {
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
        DescriptorListLoadingContext.LOG.info("Cannot find plugin $id resource-bundle: $resourceBundleBaseName")
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
      return pluginDependencies.asSequence().filter { it.isOptional }.map { it.id }.toList().toTypedArray()
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

  val unsortedEpNameToExtensionElements: Map<String, List<Element>>
    get() {
      return Collections.unmodifiableMap(epNameToExtensionElements ?: return Collections.emptyMap())
    }

  fun getActionDescriptionElements(): List<Element>? = actionElements

  override fun getVendorEmail() = vendorEmail

  override fun getVendorUrl() = vendorUrl

  override fun getUrl() = url!!

  //fun setUrl(`val`: String?) {
  //  url = `val`
  //}

  override fun getPluginId() = id!!

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

  override fun hashCode() = id?.hashCode() ?: 0

  override fun toString(): String {
    // don't expose user home in error messages
    val pathString = path.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")
    return "PluginDescriptor(name=$name, id=$id, descriptorPath=${descriptorPath ?: "plugin.xml"}, " +
           "path=$pathString, version=$version, package=$packagePrefix)"
  }
}

private fun addExtensionList(map: MutableMap<String, MutableList<Element>>, name: String, list: MutableList<Element>) {
  val mapList = map.computeIfAbsent(name) { list }
  if (mapList !== list) {
    mapList.addAll(list)
  }
}

private fun readBoolValue(value: String): Boolean {
  return value.isEmpty() || value.equals("true", ignoreCase = true)
}

private fun readComponents(parent: Element, containerDescriptor: ContainerDescriptor) {
  val content = parent.content
  val contentSize = content.size
  if (contentSize == 0) {
    return
  }

  val result = containerDescriptor.getComponentListToAdd(contentSize)
  for (child in content) {
    if (child !is Element) {
      continue
    }

    if (child.name != "component") {
      continue
    }

    val componentConfig = ComponentConfig()
    var options: MutableMap<String, String?>? = null
    loop@ for (elementChild in child.children) {
      when (elementChild.name) {
        "skipForDefaultProject" -> {
          if (!readBoolValue(elementChild.textTrim)) {
            componentConfig.isLoadForDefaultProject = true
          }
        }
        "loadForDefaultProject" -> componentConfig.isLoadForDefaultProject = readBoolValue(elementChild.textTrim)
        "interface-class" -> componentConfig.interfaceClass = elementChild.textTrim
        "implementation-class" -> componentConfig.implementationClass = elementChild.textTrim
        "headless-implementation-class" -> componentConfig.headlessImplementationClass = elementChild.textTrim
        "option" -> {
          val name = elementChild.getAttributeValue("name") ?: continue@loop
          val value = elementChild.getAttributeValue("value")
          if (name == "os") {
            if (value != null && !isSuitableForOs(value)) {
              continue@loop
            }
          }
          else if (options == null) {
            options = Collections.singletonMap(name, value)
          }
          else {
            if (options.size == 1) {
              options = HashMap(options)
            }
            options.put(name, value)
          }
        }
      }
    }
    if (options != null) {
      componentConfig.options = options
    }
    result.add(componentConfig)
  }
}