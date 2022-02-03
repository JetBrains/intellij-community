// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlReader")
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplacePutWithAssignment", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.createNonCoalescingXmlStreamReader
import com.intellij.util.NoOpXmlInterner
import com.intellij.util.XmlInterner
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.readXmlAsModel
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.typed.TypedXMLStreamException
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.events.XMLEvent

private const val defaultXPointerValue = "xpointer(/idea-plugin/*)"

/**
 * Do not use [java.io.BufferedInputStream] - buffer is used internally already.
 */
fun readModuleDescriptor(input: InputStream,
                         readContext: ReadModuleContext,
                         pathResolver: PathResolver,
                         dataLoader: DataLoader,
                         includeBase: String?,
                         readInto: RawPluginDescriptor?,
                         locationSource: String?): RawPluginDescriptor {
  return readModuleDescriptor(reader = createNonCoalescingXmlStreamReader(input, locationSource),
                              readContext = readContext,
                              pathResolver = pathResolver,
                              dataLoader = dataLoader,
                              includeBase = includeBase,
                              readInto = readInto)
}

fun readModuleDescriptor(input: ByteArray,
                         readContext: ReadModuleContext,
                         pathResolver: PathResolver,
                         dataLoader: DataLoader,
                         includeBase: String?,
                         readInto: RawPluginDescriptor?,
                         locationSource: String?): RawPluginDescriptor {
  return readModuleDescriptor(reader = createNonCoalescingXmlStreamReader(input, locationSource),
                              readContext = readContext,
                              pathResolver = pathResolver,
                              dataLoader = dataLoader,
                              includeBase = includeBase,
                              readInto = readInto)
}

internal fun readModuleDescriptor(reader: XMLStreamReader2,
                                  readContext: ReadModuleContext,
                                  pathResolver: PathResolver,
                                  dataLoader: DataLoader,
                                  includeBase: String?,
                                  readInto: RawPluginDescriptor?): RawPluginDescriptor {
  try {
    if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("State ${XMLStreamConstants.START_DOCUMENT} is expected, " +
                               "but current state is ${getEventTypeString(reader.eventType)}", reader.location)
    }

    val descriptor = readInto ?: RawPluginDescriptor()

    @Suppress("ControlFlowWithEmptyBody")
    while (reader.next() != XMLStreamConstants.START_ELEMENT) {
    }

    if (!reader.isStartElement) {
      return descriptor
    }

    readRootAttributes(reader, descriptor)
    reader.consumeChildElements { localName ->
      readRootElementChild(reader = reader,
                           descriptor = descriptor,
                           readContext = readContext,
                           localName = localName,
                           pathResolver = pathResolver,
                           dataLoader = dataLoader,
                           includeBase = includeBase)
      assert(reader.isEndElement)
    }
    return descriptor
  }
  finally {
    reader.closeCompletely()
  }
}

@TestOnly
fun readModuleDescriptorForTest(input: ByteArray): RawPluginDescriptor {
  return readModuleDescriptor(
    input = input,
    readContext = object : ReadModuleContext {
      override val interner = NoOpXmlInterner

      override val isMissingIncludeIgnored
        get() = false
    },
    pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
    dataLoader = object : DataLoader {
      override val pool: ZipFilePool? = null

      override fun load(path: String) = throw UnsupportedOperationException()

      override fun toString() = ""
    },
    includeBase = null,
    readInto = null,
    locationSource = null,
  )
}

private fun readRootAttributes(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "package" -> descriptor.`package` = getNullifiedAttributeValue(reader, i)
      "url" -> descriptor.url = getNullifiedAttributeValue(reader, i)
      "use-idea-classloader" -> descriptor.isUseIdeaClassLoader = reader.getAttributeAsBoolean(i)
      "allow-bundled-update" -> descriptor.isBundledUpdateAllowed = reader.getAttributeAsBoolean(i)
      "implementation-detail" -> descriptor.implementationDetail = reader.getAttributeAsBoolean(i)
      "require-restart" -> descriptor.isRestartRequired = reader.getAttributeAsBoolean(i)
      "version" -> {
        // internalVersionString - why it is not used, but just checked?
        getNullifiedAttributeValue(reader, i)?.let {
          try {
            it.toInt()
          }
          catch (e: NumberFormatException) {
            LOG.error("Cannot parse version: $it'", e)
          }
        }
      }
    }
  }
}

/**
 * Keep in sync with KotlinPluginUtil.KNOWN_KOTLIN_PLUGIN_IDS
 */
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "SSBasedInspection")
private val KNOWN_KOTLIN_PLUGIN_IDS = HashSet(Arrays.asList(
  "org.jetbrains.kotlin",
  "com.intellij.appcode.kmm",
  "org.jetbrains.kotlin.native.appcode"
))

private fun readRootElementChild(reader: XMLStreamReader2,
                                 descriptor: RawPluginDescriptor,
                                 localName: String,
                                 readContext: ReadModuleContext,
                                 pathResolver: PathResolver,
                                 dataLoader: DataLoader,
                                 includeBase: String?) {
  when (localName) {
    "id" -> {
      if (descriptor.id == null) {
        descriptor.id = getNullifiedContent(reader)
      }
      else if (!KNOWN_KOTLIN_PLUGIN_IDS.contains(descriptor.id) && descriptor.id != "com.intellij") {
        // no warn and no redefinition for kotlin - compiler.xml is a known issue
        LOG.warn("id redefinition (${reader.locationInfo.location})")
        descriptor.id = getNullifiedContent(reader)
      }
      else {
        reader.skipElement()
      }
    }
    "name" -> descriptor.name = getNullifiedContent(reader)
    "category" -> descriptor.category = getNullifiedContent(reader)
    "version" -> {
      // kotlin includes compiler.xml that due to some reasons duplicates version
      if (descriptor.version == null || !KNOWN_KOTLIN_PLUGIN_IDS.contains(descriptor.id)) {
        descriptor.version = getNullifiedContent(reader)
      }
      else {
        reader.skipElement()
      }
    }
    "description" -> descriptor.description = getNullifiedContent(reader)
    "change-notes" -> descriptor.changeNotes = getNullifiedContent(reader)
    "resource-bundle" -> descriptor.resourceBundleBaseName = getNullifiedContent(reader)
    "product-descriptor" -> readProduct(reader, descriptor)
    "module" -> {
      findAttributeValue(reader, "value")?.let { moduleName ->
        var modules = descriptor.modules
        if (modules == null) {
          descriptor.modules = Collections.singletonList(PluginId.getId(moduleName))
        }
        else {
          if (modules.size == 1) {
            val singleton = modules
            modules = ArrayList(4)
            modules.addAll(singleton)
            descriptor.modules = modules
          }
          modules.add(PluginId.getId(moduleName))
        }
      }
      reader.skipElement()
    }
    "idea-version" -> {
      for (i in 0 until reader.attributeCount) {
        when (reader.getAttributeLocalName(i)) {
          "since-build" -> descriptor.sinceBuild = getNullifiedAttributeValue(reader, i)
          "until-build" -> descriptor.untilBuild = getNullifiedAttributeValue(reader, i)
        }
      }
      reader.skipElement()
    }
    "vendor" -> {
      for (i in 0 until reader.attributeCount) {
        when (reader.getAttributeLocalName(i)) {
          "email" -> descriptor.vendorEmail = getNullifiedAttributeValue(reader, i)
          "url" -> descriptor.vendorUrl = getNullifiedAttributeValue(reader, i)
        }
      }
      descriptor.vendor = getNullifiedContent(reader)
    }
    "incompatible-with" -> {
      getNullifiedContent(reader)?.let {
        var list = descriptor.incompatibilities
        if (list == null) {
          list = ArrayList()
          descriptor.incompatibilities = list
        }
        list.add(PluginId.getId(it))
      }
    }

    "application-components" -> readComponents(reader, descriptor.appContainerDescriptor)
    "project-components" -> readComponents(reader, descriptor.projectContainerDescriptor)
    "module-components" -> readComponents(reader, descriptor.moduleContainerDescriptor)

    "applicationListeners" -> readListeners(reader, descriptor.appContainerDescriptor)
    "projectListeners" -> readListeners(reader, descriptor.projectContainerDescriptor)

    "extensions" -> readExtensions(reader, descriptor, readContext.interner)
    "extensionPoints" -> readExtensionPoints(reader = reader,
                                             descriptor = descriptor,
                                             readContext = readContext,
                                             pathResolver = pathResolver,
                                             dataLoader = dataLoader,
                                             includeBase = includeBase)

    "content" -> readContent(reader = reader,
                             descriptor = descriptor,
                             readContext = readContext)
    "dependencies" -> readDependencies(reader = reader, descriptor = descriptor, readContext = readContext)

    "depends" -> readOldDepends(reader, descriptor)

    "actions" -> readActions(descriptor, reader, readContext)

    "include" -> readInclude(reader = reader,
                             readInto = descriptor,
                             readContext = readContext,
                             pathResolver = pathResolver,
                             dataLoader = dataLoader,
                             includeBase = includeBase,
                             allowedPointer = defaultXPointerValue)
    "helpset" -> {
      // deprecated and not used element
      reader.skipElement()
    }
    "locale" -> {
      // not used in descriptor
      reader.skipElement()
    }
    else -> {
      LOG.error("Unknown element: $localName")
      reader.skipElement()
    }
  }

  if (!reader.isEndElement) {
    throw XMLStreamException("Unexpected state (" +
                             "expected=END_ELEMENT, " +
                             "actual=${getEventTypeString(reader.eventType)}, " +
                             "lastProcessedElement=$localName" +
                             ")", reader.location)
  }
}

private fun readActions(descriptor: RawPluginDescriptor, reader: XMLStreamReader2, readContext: ReadModuleContext) {
  var actionElements = descriptor.actions
  if (actionElements == null) {
    actionElements = ArrayList()
    descriptor.actions = actionElements
  }

  val resourceBundle = findAttributeValue(reader, "resource-bundle")
  reader.consumeChildElements { elementName ->
    if (checkXInclude(elementName, reader)) {
      return@consumeChildElements
    }

    actionElements.add(RawPluginDescriptor.ActionDescriptor(
      name = elementName,
      element = readXmlAsModel(reader = reader, rootName = elementName, interner = readContext.interner),
      resourceBundle = resourceBundle,
    ))
  }
}

private fun readOldDepends(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  var isOptional = false
  var configFile: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "optional" -> isOptional = reader.getAttributeAsBoolean(i)
      "config-file" -> configFile = reader.getAttributeValue(i)
    }
  }

  val dependencyIdString = getNullifiedContent(reader) ?: return
  var depends = descriptor.depends
  if (depends == null) {
    depends = ArrayList()
    descriptor.depends = depends
  }
  depends.add(PluginDependency(pluginId = PluginId.getId(dependencyIdString), configFile = configFile, isOptional = isOptional))
}

private fun readExtensions(reader: XMLStreamReader2, descriptor: RawPluginDescriptor, interner: XmlInterner) {
  val ns = findAttributeValue(reader, "defaultExtensionNs")
  reader.consumeChildElements { elementName ->
    if (checkXInclude(elementName, reader)) {
      return@consumeChildElements
    }

    var implementation: String? = null
    var os: ExtensionDescriptor.Os? = null
    var qualifiedExtensionPointName: String? = null
    var order = LoadingOrder.ANY
    var orderId: String? = null

    var hasExtraAttributes = false
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        "implementation" -> implementation = reader.getAttributeValue(i)
        "implementationClass" -> {
          // deprecated attribute
          implementation = reader.getAttributeValue(i)
        }
        "os" -> os = readOs(reader.getAttributeValue(i))
        "id" -> orderId = getNullifiedAttributeValue(reader, i)
        "order" -> order = readOrder(reader.getAttributeValue(i))
        "point" -> qualifiedExtensionPointName = getNullifiedAttributeValue(reader, i)
        else -> hasExtraAttributes = true
      }
    }

    if (qualifiedExtensionPointName == null) {
      qualifiedExtensionPointName = interner.name("${ns ?: reader.namespaceURI}.${elementName}")
    }

    val containerDescriptor: ContainerDescriptor
    when (qualifiedExtensionPointName) {
      "com.intellij.applicationService" -> containerDescriptor = descriptor.appContainerDescriptor
      "com.intellij.projectService" -> containerDescriptor = descriptor.projectContainerDescriptor
      "com.intellij.moduleService" -> containerDescriptor = descriptor.moduleContainerDescriptor
      else -> {
        // bean EP can use id / implementation attributes for own bean class
        // - that's why we have to create XmlElement even if all attributes are common
        val element = if (qualifiedExtensionPointName == "com.intellij.postStartupActivity") {
          reader.skipElement()
          null
        }
        else {
          readXmlAsModel(reader = reader, rootName = null, interner = interner).takeIf {
            !it.children.isEmpty() || !it.attributes.keys.isEmpty()
          }
        }

        var map = descriptor.epNameToExtensions
        if (map == null) {
          map = HashMap()
          descriptor.epNameToExtensions = map
        }

        val extensionDescriptor = ExtensionDescriptor(implementation, os, orderId, order, element, hasExtraAttributes)

        val list = map.get(qualifiedExtensionPointName)
        if (list == null) {
          map.put(qualifiedExtensionPointName, Collections.singletonList(extensionDescriptor))
        }
        else if (list.size == 1) {
          val l = ArrayList<ExtensionDescriptor>(4)
          l.add(list.get(0))
          l.add(extensionDescriptor)
          map.put(qualifiedExtensionPointName, l)
        }
        else {
          list.add(extensionDescriptor)
        }

        assert(reader.isEndElement)
        return@consumeChildElements
      }
    }

    containerDescriptor.addService(readServiceDescriptor(reader, os))
    reader.skipElement()
  }
}

private fun readOrder(orderAttr: String?): LoadingOrder {
  return when (orderAttr) {
    null -> LoadingOrder.ANY
    LoadingOrder.FIRST_STR -> LoadingOrder.FIRST
    LoadingOrder.LAST_STR -> LoadingOrder.LAST
    else -> LoadingOrder(orderAttr)
  }
}

private fun checkXInclude(elementName: String, reader: XMLStreamReader2): Boolean {
  if (elementName == "include" && reader.namespaceURI == "http://www.w3.org/2001/XInclude") {
    LOG.error("`include` is supported only on a root level (${reader.location})")
    reader.skipElement()
    return true
  }
  return false
}

@Suppress("DuplicatedCode")
private fun readExtensionPoints(reader: XMLStreamReader2,
                                descriptor: RawPluginDescriptor,
                                readContext: ReadModuleContext,
                                pathResolver: PathResolver,
                                dataLoader: DataLoader,
                                includeBase: String?) {
  reader.consumeChildElements { elementName ->
    if (elementName != "extensionPoint") {
      if (elementName == "include" && reader.namespaceURI == "http://www.w3.org/2001/XInclude") {
        val partial = RawPluginDescriptor()
        readInclude(reader = reader,
                    readInto = partial,
                    readContext = readContext,
                    pathResolver = pathResolver,
                    dataLoader = dataLoader,
                    includeBase = includeBase,
                    allowedPointer = "xpointer(/idea-plugin/extensionPoints/*)")
        LOG.warn("`include` is supported only on a root level (${reader.location})")
        applyPartialContainer(partial, descriptor) { it.appContainerDescriptor }
        applyPartialContainer(partial, descriptor) { it.projectContainerDescriptor }
        applyPartialContainer(partial, descriptor) { it.moduleContainerDescriptor }
      }
      else {
        LOG.error("Unknown element: $elementName (${reader.location})")
        reader.skipElement()
      }
      return@consumeChildElements
    }

    var area: String? = null
    var qualifiedName: String? = null
    var name: String? = null
    var beanClass: String? = null
    var `interface`: String? = null
    var isDynamic = false
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        "area" -> area = getNullifiedAttributeValue(reader, i)

        "qualifiedName" -> qualifiedName = reader.getAttributeValue(i)
        "name" -> name = getNullifiedAttributeValue(reader, i)

        "beanClass" -> beanClass = getNullifiedAttributeValue(reader, i)
        "interface" -> `interface` = getNullifiedAttributeValue(reader, i)

        "dynamic" -> isDynamic = reader.getAttributeAsBoolean(i)
      }
    }

    if (beanClass == null && `interface` == null) {
      throw RuntimeException("Neither beanClass nor interface attribute is specified for extension point at ${reader.location}")
    }
    if (beanClass != null && `interface` != null) {
      throw RuntimeException("Both beanClass and interface attributes are specified for extension point at ${reader.location}")
    }

    reader.skipElement()

    val containerDescriptor = when (area) {
      null -> descriptor.appContainerDescriptor
      "IDEA_PROJECT" -> descriptor.projectContainerDescriptor
      "IDEA_MODULE" -> descriptor.moduleContainerDescriptor
      else -> {
        LOG.error("Unknown area: $area")
        return@consumeChildElements
      }
    }

    var result = containerDescriptor.extensionPoints
    if (result == null) {
      result = ArrayList()
      containerDescriptor.extensionPoints = result
    }

    result.add(ExtensionPointDescriptor(
      name = qualifiedName ?: name ?: throw RuntimeException("`name` attribute not specified for extension point at ${reader.location}"),
      isNameQualified = qualifiedName != null,
      className = `interface` ?: beanClass!!,
      isBean = `interface` == null,
      isDynamic = isDynamic
    ))
  }
}

private inline fun applyPartialContainer(from: RawPluginDescriptor,
                                         to: RawPluginDescriptor,
                                         crossinline extractor: (RawPluginDescriptor) -> ContainerDescriptor) {
  extractor(from).extensionPoints?.let {
    val toContainer = extractor(to)
    if (toContainer.extensionPoints == null) {
      toContainer.extensionPoints = it
    }
    else {
      toContainer.extensionPoints!!.addAll(it)
    }
  }
}

@Suppress("DuplicatedCode")
private fun readServiceDescriptor(reader: XMLStreamReader2, os: ExtensionDescriptor.Os?): ServiceDescriptor {
  var serviceInterface: String? = null
  var serviceImplementation: String? = null
  var testServiceImplementation: String? = null
  var headlessImplementation: String? = null
  var configurationSchemaKey: String? = null
  var overrides = false
  var preload = ServiceDescriptor.PreloadMode.FALSE
  var client: ServiceDescriptor.ClientKind? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "serviceInterface" -> serviceInterface = getNullifiedAttributeValue(reader, i)
      "serviceImplementation" -> serviceImplementation = getNullifiedAttributeValue(reader, i)
      "testServiceImplementation" -> testServiceImplementation = getNullifiedAttributeValue(reader, i)
      "headlessImplementation" -> headlessImplementation = getNullifiedAttributeValue(reader, i)
      "configurationSchemaKey" -> configurationSchemaKey = reader.getAttributeValue(i)
      "overrides" -> overrides = reader.getAttributeAsBoolean(i)
      "preload" -> {
        when (reader.getAttributeValue(i)) {
          "true" -> preload = ServiceDescriptor.PreloadMode.TRUE
          "await" -> preload = ServiceDescriptor.PreloadMode.AWAIT
          "notHeadless" -> preload = ServiceDescriptor.PreloadMode.NOT_HEADLESS
          "notLightEdit" -> preload = ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
          else -> LOG.error("Unknown preload mode value ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
      "client" -> {
        when (reader.getAttributeValue(i)) {
          "all" -> client = ServiceDescriptor.ClientKind.ALL
          "local" -> client = ServiceDescriptor.ClientKind.LOCAL
          "guest" -> client = ServiceDescriptor.ClientKind.GUEST
          else -> LOG.error("Unknown client value: ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
    }
  }
  return ServiceDescriptor(serviceInterface, serviceImplementation, testServiceImplementation, headlessImplementation,
                           overrides, configurationSchemaKey, preload, client, os)
}

private fun readProduct(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "code" -> descriptor.productCode = getNullifiedAttributeValue(reader, i)
      "release-date" -> descriptor.releaseDate = parseReleaseDate(reader.getAttributeValue(i))
      "release-version" -> {
        try {
          descriptor.releaseVersion = reader.getAttributeAsInt(i)
        }
        catch (e: TypedXMLStreamException) {
          descriptor.releaseVersion = 0
        }
      }
      "optional" -> descriptor.isLicenseOptional = reader.getAttributeAsBoolean(i)
    }
  }
  reader.skipElement()
}

private fun readComponents(reader: XMLStreamReader2, containerDescriptor: ContainerDescriptor) {
  val result = containerDescriptor.getComponentListToAdd()
  reader.consumeChildElements("component") {
    var isApplicableForDefaultProject = false
    var interfaceClass: String? = null
    var implementationClass: String? = null
    var headlessImplementationClass: String? = null
    var os: ExtensionDescriptor.Os? = null
    var overrides = false
    var options: MutableMap<String, String?>? = null

    reader.consumeChildElements { elementName ->
      when (elementName) {
        "skipForDefaultProject" -> {
          val value = reader.elementText
          if (!value.isEmpty() && value.equals("false", ignoreCase = true)) {
            isApplicableForDefaultProject = true
          }
        }
        "loadForDefaultProject" -> {
          val value = reader.elementText
          isApplicableForDefaultProject = value.isEmpty() || value.equals("true", ignoreCase = true)
        }
        "interface-class" -> interfaceClass = getNullifiedContent(reader)
        // empty value must be supported
        "implementation-class" -> implementationClass = getNullifiedContent(reader)
        // empty value must be supported
        "headless-implementation-class" -> headlessImplementationClass = reader.elementText
        "option" -> {
          var name: String? = null
          var value: String? = null
          for (i in 0 until reader.attributeCount) {
            when (reader.getAttributeLocalName(i)) {
              "name" -> name = getNullifiedAttributeValue(reader, i)
              "value" -> value = getNullifiedAttributeValue(reader, i)
            }
          }

          reader.skipElement()

          if (name != null && value != null) {
            when {
              name == "os" -> os = readOs(value)
              name == "overrides" -> overrides = value.toBoolean()
              options == null -> {
                options = Collections.singletonMap(name, value)
              }
              else -> {
                if (options!!.size == 1) {
                  options = HashMap(options)
                }
                options!!.put(name, value)
              }
            }
          }
        }
        else -> reader.skipElement()
      }
      assert(reader.isEndElement)
    }
    assert(reader.isEndElement)

    result.add(ComponentConfig(interfaceClass, implementationClass, headlessImplementationClass, isApplicableForDefaultProject,
                               os, overrides, options))
  }
}

private fun readContent(reader: XMLStreamReader2,
                        descriptor: RawPluginDescriptor,
                        readContext: ReadModuleContext) {
  var items = descriptor.contentModules
  if (items == null) {
    items = ArrayList()
    descriptor.contentModules = items
  }

  reader.consumeChildElements { elementName ->
    when (elementName) {
      "module" -> {
        var name: String? = null
        for (i in 0 until reader.attributeCount) {
          when (reader.getAttributeLocalName(i)) {
            "name" -> name = readContext.interner.name(reader.getAttributeValue(i))
          }
        }

        if (name.isNullOrEmpty()) {
          throw RuntimeException("Name is not specified at ${reader.location}")
        }

        var configFile: String? = null
        val index = name.lastIndexOf('/')
        if (index != -1) {
          configFile = "${name.substring(0, index)}.${name.substring(index + 1)}.xml"
        }

        items.add(PluginContentDescriptor.ModuleItem(name = name, configFile = configFile))
      }
      else -> throw RuntimeException("Unknown content item type: $elementName")
    }
    reader.skipElement()
  }
  assert(reader.isEndElement)
}

private fun readDependencies(reader: XMLStreamReader2, descriptor: RawPluginDescriptor, readContext: ReadModuleContext) {
  var modules: MutableList<ModuleDependenciesDescriptor.ModuleReference>? = null
  var plugins: MutableList<ModuleDependenciesDescriptor.PluginReference>? = null
  reader.consumeChildElements { elementName ->
    when (elementName) {
      "module" -> {
        var name: String? = null
        for (i in 0 until reader.attributeCount) {
          when (reader.getAttributeLocalName(i)) {
            "name" -> name = readContext.interner.name(reader.getAttributeValue(i))
          }
        }

        if (modules == null) {
          modules = ArrayList()
        }
        modules!!.add(ModuleDependenciesDescriptor.ModuleReference(name!!))
      }
      "plugin" -> {
        var id: String? = null
        for (i in 0 until reader.attributeCount) {
          when (reader.getAttributeLocalName(i)) {
            "id" -> id = readContext.interner.name(reader.getAttributeValue(i))
          }
        }

        if (plugins == null) {
          plugins = ArrayList()
        }
        plugins!!.add(ModuleDependenciesDescriptor.PluginReference(PluginId.getId(id!!)))
      }
      else -> throw RuntimeException("Unknown content item type: ${elementName}")
    }
    reader.skipElement()
  }
  descriptor.dependencies = ModuleDependenciesDescriptor(modules ?: Collections.emptyList(), plugins ?: Collections.emptyList())
  assert(reader.isEndElement)
}

private fun findAttributeValue(reader: XMLStreamReader2, name: String): String? {
  for (i in 0 until reader.attributeCount) {
    if (reader.getAttributeLocalName(i) == name) {
      return getNullifiedAttributeValue(reader, i)
    }
  }
  return null
}

private fun getNullifiedContent(reader: XMLStreamReader2): String? = reader.elementText.takeIf { !it.isEmpty() }

private fun getNullifiedAttributeValue(reader: XMLStreamReader2, i: Int) = reader.getAttributeValue(i).takeIf { !it.isEmpty() }

interface ReadModuleContext {
  val interner: XmlInterner
  val isMissingIncludeIgnored: Boolean
}

private fun readInclude(reader: XMLStreamReader2,
                        readInto: RawPluginDescriptor,
                        readContext: ReadModuleContext,
                        pathResolver: PathResolver,
                        dataLoader: DataLoader,
                        includeBase: String?,
                        allowedPointer: String) {
  var path: String? = null
  var pointer: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "href" -> path = getNullifiedAttributeValue(reader, i)
      "xpointer" -> pointer = reader.getAttributeValue(i)?.takeIf { !it.isEmpty() && it != allowedPointer }
      else -> throw RuntimeException("Unknown attribute ${reader.getAttributeLocalName(i)} (${reader.location})")
    }
  }

  if (pointer != null) {
    throw RuntimeException("Attribute `xpointer` is not supported anymore (xpointer=$pointer, location=${reader.location})")
  }

  if (path == null) {
    throw RuntimeException("Missing `href` attribute (${reader.location})")
  }

  var isOptional = false
  reader.consumeChildElements("fallback") {
    isOptional = true
    reader.skipElement()
  }

  var readError: IOException? = null
  val read = try {
    pathResolver.loadXIncludeReference(dataLoader = dataLoader,
                                       base = includeBase,
                                       relativePath = path,
                                       readContext = readContext,
                                       readInto = readInto)
  }
  catch (e: IOException) {
    readError = e
    false
  }

  if (read || isOptional) {
    return
  }

  if (readContext.isMissingIncludeIgnored) {
    LOG.info("$path include ignored (dataLoader=$dataLoader)", readError)
    return
  }
  else {
    throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader)", readError)
  }
}

private var dateTimeFormatter: DateTimeFormatter? = null

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

private fun parseReleaseDate(dateString: String): LocalDate? {
  if (dateString.isEmpty() || dateString == "__DATE__") {
    return null
  }

  var formatter = dateTimeFormatter
  if (formatter == null) {
    formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
    dateTimeFormatter = formatter
  }

  try {
    return LocalDate.parse(dateString, formatter)
  }
  catch (e: ParseException) {
    LOG.error("Cannot parse release date", e)
  }
  return null
}

private fun readListeners(reader: XMLStreamReader2, containerDescriptor: ContainerDescriptor) {
  var result = containerDescriptor.listeners
  if (result == null) {
    result = ArrayList()
    containerDescriptor.listeners = result
  }

  reader.consumeChildElements("listener") {
    var os: ExtensionDescriptor.Os? = null
    var listenerClassName: String? = null
    var topicClassName: String? = null
    var activeInTestMode = true
    var activeInHeadlessMode = true
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        "os" -> os = readOs(reader.getAttributeValue(i))
        "class" -> listenerClassName = getNullifiedAttributeValue(reader, i)
        "topic" -> topicClassName = getNullifiedAttributeValue(reader, i)
        "activeInTestMode" -> activeInTestMode = reader.getAttributeAsBoolean(i)
        "activeInHeadlessMode" -> activeInHeadlessMode = reader.getAttributeAsBoolean(i)
      }
    }

    if (listenerClassName == null || topicClassName == null) {
      LOG.error("Listener descriptor is not correct as ${reader.location}")
    }
    else {
      result.add(ListenerDescriptor(os, listenerClassName, topicClassName, activeInTestMode, activeInHeadlessMode))
    }
    reader.skipElement()
  }

  assert(reader.isEndElement)
}

private fun readOs(value: String): ExtensionDescriptor.Os {
  return when (value) {
    "mac" -> ExtensionDescriptor.Os.mac
    "linux" -> ExtensionDescriptor.Os.linux
    "windows" -> ExtensionDescriptor.Os.windows
    "unix" -> ExtensionDescriptor.Os.unix
    "freebsd" -> ExtensionDescriptor.Os.freebsd
    else -> throw IllegalArgumentException("Unknown OS: $value")
  }
}

private inline fun XMLStreamReader.consumeChildElements(crossinline consumer: (name: String) -> Unit) {
  // cursor must be at the start of parent element
  assert(isStartElement)

  var depth = 1
  while (true) {
    @Suppress("DuplicatedCode")
    when (next()) {
      XMLStreamConstants.START_ELEMENT -> {
        depth++
        consumer(localName)
        assert(isEndElement)
        depth--
      }

      XMLStreamConstants.END_ELEMENT -> {
        if (depth != 1) {
          throw IllegalStateException("Expected depth: 1")
        }
        return
      }

      XMLStreamConstants.CDATA,
      XMLStreamConstants.SPACE,
      XMLStreamConstants.CHARACTERS,
      XMLStreamConstants.ENTITY_REFERENCE,
      XMLStreamConstants.COMMENT,
      XMLStreamConstants.PROCESSING_INSTRUCTION -> {
        // ignore
      }
      else -> throw XMLStreamException("Unexpected state: ${getEventTypeString(eventType)}", location)
    }
  }
}

private inline fun XMLStreamReader2.consumeChildElements(name: String, crossinline consumer: () -> Unit) {
  consumeChildElements {
    if (name == it) {
      consumer()
      assert(isEndElement)
    }
    else {
      skipElement()
    }
  }
}

private fun getEventTypeString(eventType: Int): String {
  return when (eventType) {
    XMLEvent.START_ELEMENT -> "START_ELEMENT"
    XMLEvent.END_ELEMENT -> "END_ELEMENT"
    XMLEvent.PROCESSING_INSTRUCTION -> "PROCESSING_INSTRUCTION"
    XMLEvent.CHARACTERS -> "CHARACTERS"
    XMLEvent.COMMENT -> "COMMENT"
    XMLEvent.START_DOCUMENT -> "START_DOCUMENT"
    XMLEvent.END_DOCUMENT -> "END_DOCUMENT"
    XMLEvent.ENTITY_REFERENCE -> "ENTITY_REFERENCE"
    XMLEvent.ATTRIBUTE -> "ATTRIBUTE"
    XMLEvent.DTD -> "DTD"
    XMLEvent.CDATA -> "CDATA"
    XMLEvent.SPACE -> "SPACE"
    else -> "UNKNOWN_EVENT_TYPE, $eventType"
  }
}