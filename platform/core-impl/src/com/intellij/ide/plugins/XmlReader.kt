// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlReader")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.ActionDescriptor.*
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.LoadingOrder.Companion.readOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.xml.dom.XmlInterner
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import com.intellij.util.xml.dom.readXmlAsModel
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.typed.TypedXMLStreamException
import org.jetbrains.annotations.ApiStatus
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

/**
 * Do not use [java.io.BufferedInputStream] - buffer is used internally already.
 */
@ApiStatus.Internal
fun readModuleDescriptor(
  input: InputStream,
  readContext: ReadModuleContext,
  pathResolver: PathResolver?,
  dataLoader: DataLoader,
  includeBase: String?,
  readInto: RawPluginDescriptor?,
  locationSource: String?,
): RawPluginDescriptor {
  return readModuleDescriptor(
    reader = createNonCoalescingXmlStreamReader(input = input, locationSource = locationSource),
    readContext = readContext,
    dataLoader = dataLoader,
    pathResolver = pathResolver,
    includeBase = includeBase,
    readInto = readInto,
  )
}

@ApiStatus.Internal
fun readModuleDescriptor(
  input: ByteArray,
  readContext: ReadModuleContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
  includeBase: String?,
  readInto: RawPluginDescriptor?,
  locationSource: String?,
): RawPluginDescriptor {
  return readModuleDescriptor(
    reader = createNonCoalescingXmlStreamReader(input = input, locationSource = locationSource),
    readContext = readContext,
    dataLoader = dataLoader,
    pathResolver = pathResolver,
    includeBase = includeBase,
    readInto = readInto,
  )
}

internal fun readModuleDescriptor(
  reader: XMLStreamReader2,
  readContext: ReadModuleContext,
  dataLoader: DataLoader,
  pathResolver: PathResolver? = null,
  includeBase: String? = null,
  readInto: RawPluginDescriptor? = null,
): RawPluginDescriptor {
  try {
    if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("State ${XMLStreamConstants.START_DOCUMENT} is expected, but current state is ${getEventTypeString(reader.eventType)}", reader.location)
    }

    val descriptor = readInto ?: RawPluginDescriptor()

    @Suppress("ControlFlowWithEmptyBody")
    while (reader.next() != XMLStreamConstants.START_ELEMENT) ;
    if (!reader.isStartElement) {
      return descriptor
    }

    readRootAttributes(reader, descriptor)

    reader.consumeChildElements { localName ->
      readRootElementChild(
        reader = reader,
        descriptor = descriptor,
        localName = localName,
        readContext = readContext,
        pathResolver = pathResolver,
        dataLoader = dataLoader,
        includeBase = includeBase,
      )
      assert(reader.isEndElement)
    }

    return descriptor
  }
  finally {
    reader.closeCompletely()
  }
}

@Throws(XMLStreamException::class)
internal fun readBasicDescriptorData(input: InputStream): RawPluginDescriptor? {
  val reader = createNonCoalescingXmlStreamReader(input = input, locationSource = null)
  try {
    if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("Expected: ${XMLStreamConstants.START_DOCUMENT}, got: ${getEventTypeString(reader.eventType)}", reader.location)
    }

    @Suppress("ControlFlowWithEmptyBody")
    while (reader.next() != XMLStreamConstants.START_ELEMENT) ;
    if (!reader.isStartElement) {
      return null
    }

    val descriptor = RawPluginDescriptor()

    reader.consumeChildElements { localName ->
      when (localName) {
        PluginXmlConst.ID_ELEM -> descriptor.id = getNullifiedContent(reader)
        PluginXmlConst.NAME_ELEM -> descriptor.name = getNullifiedContent(reader)
        PluginXmlConst.VERSION_ELEM -> descriptor.version = getNullifiedContent(reader)
        PluginXmlConst.DESCRIPTION_ELEM -> descriptor.description = getNullifiedContent(reader)
        PluginXmlConst.IDEA_VERSION_ELEM -> readIdeaVersion(reader, descriptor)
        PluginXmlConst.PRODUCT_DESCRIPTOR_ELEM -> readProduct(reader, descriptor)
        else -> reader.skipElement()
      }
      assert(reader.isEndElement)
    }

    return descriptor
  }
  finally {
    reader.close()
  }
}

private fun readRootAttributes(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.PLUGIN_PACKAGE_ATTR -> descriptor.`package` = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.PLUGIN_URL_ATTR -> descriptor.url = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.PLUGIN_USE_IDEA_CLASSLOADER_ATTR -> descriptor.isUseIdeaClassLoader = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_ALLOW_BUNDLED_UPDATE_ATTR -> descriptor.isBundledUpdateAllowed = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_IMPLEMENTATION_DETAIL_ATTR -> descriptor.implementationDetail = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_REQUIRE_RESTART_ATTR -> descriptor.isRestartRequired = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_DEPENDENT_ON_CORE_ATTR -> descriptor.isDependentOnCoreClassLoader = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_IS_SEPARATE_JAR_ATTR -> descriptor.isSeparateJar = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_VERSION_ATTR -> {
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
private val KNOWN_KOTLIN_PLUGIN_IDS = Java11Shim.INSTANCE.copyOf(listOf(
  "org.jetbrains.kotlin",
  "com.intellij.appcode.kmm",
  "org.jetbrains.kotlin.native.appcode"
))

fun isKotlinPlugin(pluginId: PluginId): Boolean =
  pluginId.idString in KNOWN_KOTLIN_PLUGIN_IDS

private val K2_ALLOWED_PLUGIN_IDS = Java11Shim.INSTANCE.copyOf(KNOWN_KOTLIN_PLUGIN_IDS + listOf(
  "org.jetbrains.android",
  "androidx.compose.plugins.idea",
  "com.intellij.kmm",
  "com.jetbrains.kotlin.ocswift",
))

private fun readRootElementChild(
  reader: XMLStreamReader2,
  descriptor: RawPluginDescriptor,
  localName: String,
  readContext: ReadModuleContext,
  pathResolver: PathResolver?,
  dataLoader: DataLoader,
  includeBase: String?,
) {
  when (localName) {
    PluginXmlConst.ID_ELEM -> {
      when {
        descriptor.id == null -> {
          descriptor.id = getNullifiedContent(reader)
        }
        !KNOWN_KOTLIN_PLUGIN_IDS.contains(descriptor.id) && descriptor.id != "com.intellij" -> {
          // no warning and no redefinition for kotlin - compiler.xml is a known issue
          LOG.warn("id redefinition (${reader.locationInfo.location})")
          descriptor.id = getNullifiedContent(reader)
        }
        else -> {
          reader.skipElement()
        }
      }
    }
    PluginXmlConst.NAME_ELEM -> descriptor.name = getNullifiedContent(reader)
    PluginXmlConst.CATEGORY_ELEM -> descriptor.category = getNullifiedContent(reader)
    PluginXmlConst.VERSION_ELEM -> {
      // kotlin includes compiler.xml that due to some reasons duplicates a version
      if (descriptor.version == null || !KNOWN_KOTLIN_PLUGIN_IDS.contains(descriptor.id)) {
        descriptor.version = getNullifiedContent(reader)
      }
      else {
        reader.skipElement()
      }
    }
    PluginXmlConst.DESCRIPTION_ELEM -> descriptor.description = getNullifiedContent(reader)
    PluginXmlConst.CHANGE_NOTES_ELEM -> descriptor.changeNotes = getNullifiedContent(reader)
    PluginXmlConst.RESOURCE_BUNDLE_ELEM -> descriptor.resourceBundleBaseName = getNullifiedContent(reader)
    PluginXmlConst.PRODUCT_DESCRIPTOR_ELEM -> readProduct(reader, descriptor)
    PluginXmlConst.MODULE_ELEM -> {
      findAttributeValue(reader, PluginXmlConst.MODULE_VALUE_ATTR)?.let { moduleName ->
        if (descriptor.pluginAliases == null) {
          descriptor.pluginAliases = ArrayList()
        }
        descriptor.pluginAliases!!.add(PluginId.getId(moduleName))
      }
      reader.skipElement()
    }
    PluginXmlConst.IDEA_VERSION_ELEM -> readIdeaVersion(reader, descriptor)
    PluginXmlConst.VENDOR_ELEM -> {
      for (i in 0 until reader.attributeCount) {
        when (reader.getAttributeLocalName(i)) {
          PluginXmlConst.VENDOR_EMAIL_ATTR -> descriptor.vendorEmail = getNullifiedAttributeValue(reader, i)
          PluginXmlConst.VENDOR_URL_ATTR -> descriptor.vendorUrl = getNullifiedAttributeValue(reader, i)
        }
      }
      descriptor.vendor = getNullifiedContent(reader)
    }
    PluginXmlConst.INCOMPATIBLE_WITH_ELEM -> {
      getNullifiedContent(reader)?.let {
        if (descriptor.incompatibilities == null) {
          descriptor.incompatibilities = ArrayList()
        }
        descriptor.incompatibilities!!.add(PluginId.getId(it))
      }
    }

    PluginXmlConst.APPLICATION_COMPONENTS_ELEM -> readComponents(reader, descriptor.appContainerDescriptor)
    PluginXmlConst.PROJECT_COMPONENTS_ELEM -> readComponents(reader, descriptor.projectContainerDescriptor)
    PluginXmlConst.MODULE_COMPONENTS_ELEM -> readComponents(reader, descriptor.moduleContainerDescriptor)

    PluginXmlConst.APPLICATION_LISTENERS_ELEM -> readListeners(reader, descriptor.appContainerDescriptor)
    PluginXmlConst.PROJECT_LISTENERS_ELEM -> readListeners(reader, descriptor.projectContainerDescriptor)

    PluginXmlConst.EXTENSIONS_ELEM -> readExtensions(reader, descriptor, readContext.interner)
    PluginXmlConst.EXTENSION_POINTS_ELEM -> readExtensionPoints(
      reader = reader,
      descriptor = descriptor,
      readContext = readContext,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = includeBase,
    )

    PluginXmlConst.CONTENT_ELEM -> readContent(reader, descriptor, readContext)
    PluginXmlConst.DEPENDENCIES_ELEM-> readDependencies(reader, descriptor, readContext.interner)

    PluginXmlConst.DEPENDS_ELEM -> readOldDepends(reader, descriptor)

    PluginXmlConst.ACTIONS_ELEM -> readActions(descriptor, reader, readContext)

    PluginXmlConst.INCLUDE_ELEM -> readInclude(
      reader = reader,
      readInto = descriptor,
      readContext = readContext,
      pathResolver = pathResolver ?: throw XMLStreamException("include is not supported because no pathResolver", reader.location),
      dataLoader = dataLoader,
      includeBase = includeBase,
      allowedPointer = PluginXmlConst.DEFAULT_XPOINTER_VALUE,
    )
    PluginXmlConst.HELPSET_ELEM -> {
      // deprecated and not used element
      reader.skipElement()
    }
    PluginXmlConst.LOCALE_ELEM -> {
      // not used in descriptor
      reader.skipElement()
    }
    else -> {
      LOG.error("Unknown element: $localName")
      reader.skipElement()
    }
  }

  if (!reader.isEndElement) {
    throw XMLStreamException("Unexpected state (expected=END_ELEMENT, actual=${getEventTypeString(reader.eventType)}, lastProcessedElement=$localName)", reader.location)
  }
}

private fun readIdeaVersion(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.IDEA_VERSION_SINCE_ATTR -> descriptor.sinceBuild = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.IDEA_VERSION_UNTIL_ATTR -> descriptor.untilBuild = getNullifiedAttributeValue(reader, i)
    }
  }
  reader.skipElement()
}

private val actionNameToEnum = run {
  val entries = ActionDescriptorName.entries
  entries.associateByTo(HashMap<String, ActionDescriptorName>(entries.size), ActionDescriptorName::name)
}

private fun readActions(descriptor: RawPluginDescriptor, reader: XMLStreamReader2, readContext: ReadModuleContext) {
  var actionElements = descriptor.actions
  if (actionElements == null) {
    actionElements = ArrayList()
    descriptor.actions = actionElements
  }

  val resourceBundle = findAttributeValue(reader, PluginXmlConst.ACTIONS_RESOURCE_BUNDLE_ATTR)
  reader.consumeChildElements { elementName ->
    if (checkXInclude(elementName, reader)) {
      return@consumeChildElements
    }

    val name = actionNameToEnum[elementName]
    if (name == null) {
      LOG.error("Unexpected name of element: $elementName at ${reader.location}")
      reader.skipElement()
      return@consumeChildElements
    }

    val element = readXmlAsModel(reader, elementName, readContext.interner)

    val attributes = element.attributes
    when (name) {
      ActionDescriptorName.action -> {
        val className = attributes["class"]
        if (className.isNullOrEmpty()) {
          LOG.error("action element should have specified \"class\" attribute at ${reader.location}")
          reader.skipElement()
          return@consumeChildElements
        }
        actionElements.add(ActionDescriptorAction(className, isInternal = attributes["internal"].toBoolean(), element, resourceBundle))
      }
      ActionDescriptorName.group -> {
        var className = attributes["class"]
        if (className.isNullOrEmpty()) {
          className = if (attributes["compact"] == "true") "com.intellij.openapi.actionSystem.DefaultCompactActionGroup" else null
        }
        val id = attributes["id"]
        if (id != null && id.isEmpty()) {
          LOG.error("ID of the group cannot be an empty string at ${reader.location}")
          reader.skipElement()
          return@consumeChildElements
        }
        actionElements.add(ActionDescriptorGroup(className, id, element, resourceBundle))
      }
      else -> {
        actionElements.add(ActionDescriptorMisc(name, element, resourceBundle))
      }
    }
  }
}

private fun readOldDepends(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  var isOptional = false
  var configFile: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.DEPENDS_OPTIONAL_ATTR -> isOptional = reader.getAttributeAsBoolean(i)
      PluginXmlConst.DEPENDS_CONFIG_FILE_ATTR -> configFile = reader.getAttributeValue(i)
    }
  }

  val dependencyIdString = getNullifiedContent(reader) ?: return
  var depends = descriptor.depends
  if (depends == null) {
    depends = ArrayList()
    descriptor.depends = depends
  }

  depends.add(PluginDependency(PluginId.getId(dependencyIdString), configFile, isOptional))
}

private fun readExtensions(reader: XMLStreamReader2, descriptor: RawPluginDescriptor, interner: XmlInterner) {
  val ns = findAttributeValue(reader, PluginXmlConst.EXTENSIONS_DEFAULT_EXTENSION_NS_ATTR)
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
        PluginXmlConst.EXTENSION_IMPLEMENTATION_ATTR -> implementation = reader.getAttributeValue(i)
        PluginXmlConst.EXTENSION_IMPLEMENTATION_CLASS_ATTR -> implementation = reader.getAttributeValue(i)  // deprecated attribute
        PluginXmlConst.EXTENSION_OS_ATTR -> os = readOs(reader.getAttributeValue(i))
        PluginXmlConst.EXTENSION_ID_ATTR -> orderId = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_ORDER_ATTR -> order = readOrder(reader.getAttributeValue(i))
        PluginXmlConst.EXTENSION_POINT_ATTR -> qualifiedExtensionPointName = getNullifiedAttributeValue(reader, i)
        else -> hasExtraAttributes = true
      }
    }

    if (qualifiedExtensionPointName == null) {
      qualifiedExtensionPointName = interner.name("${ns ?: reader.namespaceURI}.${elementName}")
    }

    val containerDescriptor: ContainerDescriptor
    when (qualifiedExtensionPointName) {
      PluginXmlConst.FQN_APPLICATION_SERVICE -> containerDescriptor = descriptor.appContainerDescriptor
      PluginXmlConst.FQN_PROJECT_SERVICE -> containerDescriptor = descriptor.projectContainerDescriptor
      PluginXmlConst.FQN_MODULE_SERVICE -> containerDescriptor = descriptor.moduleContainerDescriptor
      else -> {
        // bean EP can use id / implementation attributes for own bean class
        // - that's why we have to create XmlElement even if all attributes are common
        val element = if (qualifiedExtensionPointName == PluginXmlConst.FQN_POST_STARTUP_ACTIVITY) {
          reader.skipElement()
          null
        }
        else {
          readXmlAsModel(reader, rootName = null, interner).takeIf {
            !it.children.isEmpty() || !it.attributes.keys.isEmpty()
          }
        }

        val extensionDescriptor = ExtensionDescriptor(implementation, os, orderId, order, element, hasExtraAttributes)

        var epNameToExtensions = descriptor.epNameToExtensions
        if (epNameToExtensions == null) {
          epNameToExtensions = HashMap()
          descriptor.epNameToExtensions = epNameToExtensions
        }

        epNameToExtensions.computeIfAbsent(qualifiedExtensionPointName) { ArrayList() }.add(extensionDescriptor)

        assert(reader.isEndElement)
        return@consumeChildElements
      }
    }

    containerDescriptor.addService(readServiceDescriptor(reader, os))
    reader.skipElement()
  }
}

private fun checkXInclude(elementName: String, reader: XMLStreamReader2): Boolean {
  if (elementName == PluginXmlConst.INCLUDE_ELEM && reader.namespaceURI == PluginXmlConst.XINCLUDE_NAMESPACE_URI) {
    LOG.error("`include` is supported only on a root level (${reader.location})")
    reader.skipElement()
    return true
  }
  return false
}

@Suppress("DuplicatedCode")
private fun readExtensionPoints(
  reader: XMLStreamReader2,
  descriptor: RawPluginDescriptor,
  readContext: ReadModuleContext,
  pathResolver: PathResolver?,
  dataLoader: DataLoader,
  includeBase: String?,
) {
  reader.consumeChildElements { elementName ->
    if (elementName != PluginXmlConst.EXTENSION_POINT_ELEM) {
      if (elementName == PluginXmlConst.INCLUDE_ELEM && reader.namespaceURI == PluginXmlConst.XINCLUDE_NAMESPACE_URI) {
        val partial = RawPluginDescriptor()
        readInclude(
          reader,
          partial,
          readContext,
          pathResolver = pathResolver ?: throw XMLStreamException("include is not supported because no pathResolver", reader.location),
          dataLoader,
          includeBase,
          allowedPointer = PluginXmlConst.EXTENSION_POINTS_XINCLUDE_VALUE
        )
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
    var hasAttributes = false
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        PluginXmlConst.EXTENSION_POINT_AREA_ATTR -> area = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_POINT_QUALIFIED_NAME_ATTR -> qualifiedName = reader.getAttributeValue(i)
        PluginXmlConst.EXTENSION_POINT_NAME_ATTR -> name = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_POINT_BEAN_CLASS_ATTR -> beanClass = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_POINT_INTERFACE_ATTR -> `interface` = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_POINT_DYNAMIC_ATTR -> isDynamic = reader.getAttributeAsBoolean(i)
        PluginXmlConst.EXTENSION_POINT_HAS_ATTRIBUTES_ATTR -> hasAttributes = reader.getAttributeAsBoolean(i)
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
      PluginXmlConst.EXTENSION_POINT_AREA_IDEA_PROJECT_VALUE -> descriptor.projectContainerDescriptor
      PluginXmlConst.EXTENSION_POINT_AREA_IDEA_MODULE_VALUE -> descriptor.moduleContainerDescriptor
      else -> {
        LOG.error("Unknown area: $area")
        return@consumeChildElements
      }
    }

    if (containerDescriptor.extensionPoints == null) {
      containerDescriptor.extensionPoints = ArrayList()
    }
    containerDescriptor.extensionPoints!!.add(ExtensionPointDescriptor(
      name = qualifiedName ?: name ?: throw RuntimeException("`name` attribute not specified for extension point at ${reader.location}"),
      isNameQualified = qualifiedName != null,
      className = `interface` ?: beanClass!!,
      isBean = `interface` == null,
      hasAttributes,
      isDynamic,
    ))
  }
}

private inline fun applyPartialContainer(from: RawPluginDescriptor, to: RawPluginDescriptor, crossinline extractor: (RawPluginDescriptor) -> ContainerDescriptor) {
  extractor(from).extensionPoints.takeIf { !it.isNullOrEmpty() }?.let {
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
  var client: ClientKind? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.SERVICE_EP_SERVICE_INTERFACE_ATTR -> serviceInterface = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.SERVICE_EP_SERVICE_IMPLEMENTATION_ATTR-> serviceImplementation = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.SERVICE_EP_TEST_SERVICE_IMPLEMENTATION_ATTR -> testServiceImplementation = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.SERVICE_EP_HEADLESS_IMPLEMENTATION_ATTR -> headlessImplementation = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.SERVICE_EP_CONFIGURATION_SCHEMA_KEY_ATTR -> configurationSchemaKey = reader.getAttributeValue(i)
      PluginXmlConst.SERVICE_EP_OVERRIDES_ATTR -> overrides = reader.getAttributeAsBoolean(i)
      PluginXmlConst.SERVICE_EP_PRELOAD_ATTR -> {
        when (reader.getAttributeValue(i)) {
          PluginXmlConst.SERVICE_EP_PRELOAD_TRUE_VALUE -> preload = ServiceDescriptor.PreloadMode.TRUE
          PluginXmlConst.SERVICE_EP_PRELOAD_AWAIT_VALUE -> preload = ServiceDescriptor.PreloadMode.AWAIT
          PluginXmlConst.SERVICE_EP_PRELOAD_NOT_HEADLESS_VALUE -> preload = ServiceDescriptor.PreloadMode.NOT_HEADLESS
          PluginXmlConst.SERVICE_EP_PRELOAD_NOT_LIGHT_EDIT_VALUE -> preload = ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
          else -> LOG.error("Unknown preload mode value ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
      PluginXmlConst.SERVICE_EP_CLIENT_ATTR -> {
        @Suppress("DEPRECATION")
        when (reader.getAttributeValue(i)) {
          PluginXmlConst.SERVICE_EP_CLIENT_LOCAL_VALUE -> client = ClientKind.LOCAL
          PluginXmlConst.SERVICE_EP_CLIENT_GUEST_VALUE -> client = ClientKind.GUEST
          PluginXmlConst.SERVICE_EP_CLIENT_CONTROLLER_VALUE -> client = ClientKind.CONTROLLER
          PluginXmlConst.SERVICE_EP_CLIENT_OWNER_VALUE -> client = ClientKind.OWNER
          PluginXmlConst.SERVICE_EP_CLIENT_REMOTE_VALUE -> client = ClientKind.REMOTE
          PluginXmlConst.SERVICE_EP_CLIENT_FRONTEND_VALUE -> client = ClientKind.FRONTEND
          PluginXmlConst.SERVICE_EP_CLIENT_ALL_VALUE -> client = ClientKind.ALL
          else -> LOG.error("Unknown client value: ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
    }
  }
  return ServiceDescriptor(serviceInterface, serviceImplementation, testServiceImplementation, headlessImplementation, overrides, configurationSchemaKey, preload, client, os)
}

private fun readProduct(reader: XMLStreamReader2, descriptor: RawPluginDescriptor) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.PRODUCT_DESCRIPTOR_CODE_ATTR -> descriptor.productCode = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_DATE_ATTR -> descriptor.releaseDate = parseReleaseDate(reader.getAttributeValue(i))
      PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_VERSION_ATTR -> {
        try {
          descriptor.releaseVersion = reader.getAttributeAsInt(i)
        }
        catch (_: TypedXMLStreamException) {
          descriptor.releaseVersion = 0
        }
      }
      PluginXmlConst.PRODUCT_DESCRIPTOR_OPTIONAL_ATTR -> descriptor.isLicenseOptional = reader.getAttributeAsBoolean(i)
    }
  }
  reader.skipElement()
}

private fun readComponents(reader: XMLStreamReader2, containerDescriptor: ContainerDescriptor) {
  reader.consumeChildElements(PluginXmlConst.COMPONENT_ELEM) {
    var isApplicableForDefaultProject = false
    var interfaceClass: String? = null
    var implementationClass: String? = null
    var headlessImplementationClass: String? = null
    var os: ExtensionDescriptor.Os? = null
    var overrides = false
    var options: MutableMap<String, String?>? = null

    reader.consumeChildElements { elementName ->
      when (elementName) {
        PluginXmlConst.COMPONENT_SKIP_FOR_DEFAULT_PROJECT_ELEM -> {
          val value = reader.elementText
          if (!value.isEmpty() && value.equals("false", ignoreCase = true)) {
            isApplicableForDefaultProject = true
          }
        }
        PluginXmlConst.COMPONENT_LOAD_FOR_DEFAULT_PROJECT_ELEM -> {
          val value = reader.elementText
          isApplicableForDefaultProject = value.isEmpty() || value.equals("true", ignoreCase = true)
        }
        PluginXmlConst.COMPONENT_INTERFACE_CLASS_ELEM -> interfaceClass = getNullifiedContent(reader)
        // empty value must be supported
        PluginXmlConst.COMPONENT_IMPLEMENTATION_CLASS_ELEM -> implementationClass = getNullifiedContent(reader)
        // empty value must be supported
        PluginXmlConst.COMPONENT_HEADLESS_IMPLEMENTATION_CLASS_ELEM -> headlessImplementationClass = reader.elementText
        PluginXmlConst.COMPONENT_OPTION_ELEM -> {
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
                options.put(name, value)
              }
            }
          }
        }
        else -> reader.skipElement()
      }
      assert(reader.isEndElement)
    }
    assert(reader.isEndElement)

    if (containerDescriptor.components == null) {
      containerDescriptor.components = ArrayList()
    }
    containerDescriptor.components!!.add(ComponentConfig(interfaceClass, implementationClass, headlessImplementationClass, isApplicableForDefaultProject, os, overrides, options))
  }
}

private fun readContent(reader: XMLStreamReader2, descriptor: RawPluginDescriptor, readContext: ReadModuleContext) {
  reader.consumeChildElements { elementName ->
    if (elementName != "module") {
      reader.skipElement()
      throw RuntimeException("Unknown content item type: $elementName")
    }

    var name: String? = null
    var loadingRule = ModuleLoadingRule.OPTIONAL
    var os: ExtensionDescriptor.Os? = null
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        "name" -> name = readContext.interner.name(reader.getAttributeValue(i))
        "loading" -> {
          val loading = reader.getAttributeValue(i)
          loadingRule = when (loading) {
            "optional" -> ModuleLoadingRule.OPTIONAL
            "required" -> ModuleLoadingRule.REQUIRED
            "embedded" -> ModuleLoadingRule.EMBEDDED
            "on-demand" -> ModuleLoadingRule.ON_DEMAND
            else -> error("Unexpected value '$loading' of 'loading' attribute at ${reader.location}")
          }
        }
        "os" -> os = readOs(reader.getAttributeValue(i))
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

    if (descriptor.contentModules == null) {
      descriptor.contentModules = ArrayList()
    }

    val isEndElement = reader.next() == XMLStreamConstants.END_ELEMENT
    if (isEndElement) {
      if (os == null || os.isSuitableForOs()) {
        descriptor.contentModules!!.add(PluginContentDescriptor.ModuleItem(name = name, configFile = configFile, descriptorContent = null, loadingRule = loadingRule))
      }
    }
    else {
      if (os == null || os.isSuitableForOs()) {
        val fromIndex = reader.textStart
        val toIndex = fromIndex + reader.textLength
        val length = toIndex - fromIndex
        val descriptorContent = if (length == 0) null else reader.textCharacters.copyOfRange(fromIndex, toIndex)
        descriptor.contentModules!!.add(PluginContentDescriptor.ModuleItem(name = name, configFile = configFile, descriptorContent = descriptorContent, loadingRule = loadingRule))
      }

      var nesting = 1
      while (true) {
        val type = reader.next()
        if (type == XMLStreamConstants.START_ELEMENT) {
          nesting++
        }
        else if (type == XMLStreamConstants.END_ELEMENT) {
          if (--nesting == 0) {
            break
          }
        }
      }
    }
  }
  assert(reader.isEndElement)
}

private fun readDependencies(reader: XMLStreamReader2, descriptor: RawPluginDescriptor, interner: XmlInterner) {
  val modules = ArrayList<ModuleDependenciesDescriptor.ModuleReference>()
  val plugins = ArrayList<ModuleDependenciesDescriptor.PluginReference>()

  reader.consumeChildElements { elementName ->
    when (elementName) {
      "module" -> {
        var name: String? = null
        for (i in 0 until reader.attributeCount) {
          if (reader.getAttributeLocalName(i) == "name") {
            name = interner.name(reader.getAttributeValue(i))
            break
          }
        }
        modules.add(ModuleDependenciesDescriptor.ModuleReference(name!!))
      }
      "plugin" -> {
        var id: String? = null
        for (i in 0 until reader.attributeCount) {
          if (reader.getAttributeLocalName(i) == "id") {
            id = interner.name(reader.getAttributeValue(i))
            break
          }
        }

        plugins.add(ModuleDependenciesDescriptor.PluginReference(PluginId.getId(id!!)))
      }
      else -> throw RuntimeException("Unknown content item type: $elementName")
    }
    reader.skipElement()
  }

  val oldDependencies = descriptor.dependencies
  val newModules = if (oldDependencies.modules.isEmpty()) modules else oldDependencies.modules + modules
  val newPlugins = if (oldDependencies.plugins.isEmpty()) plugins else oldDependencies.plugins + plugins
  descriptor.dependencies = ModuleDependenciesDescriptor(newModules, newPlugins) 
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

private fun getNullifiedContent(reader: XMLStreamReader2): String? = reader.elementText.trim().takeIf { !it.isEmpty() }

private fun getNullifiedAttributeValue(reader: XMLStreamReader2, i: Int) = reader.getAttributeValue(i).trim().takeIf { !it.isEmpty() }

@ApiStatus.Internal
interface ReadModuleContext {
  val interner: XmlInterner

  val isMissingIncludeIgnored: Boolean
    get() = false
}

private fun readInclude(
  reader: XMLStreamReader2,
  readInto: RawPluginDescriptor,
  readContext: ReadModuleContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
  includeBase: String?,
  allowedPointer: String,
) {
  var path: String? = null
  var pointer: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      "href" -> path = getNullifiedAttributeValue(reader, i)
      "xpointer" -> pointer = reader.getAttributeValue(i)?.takeIf { !it.isEmpty() && it != allowedPointer }
      "includeIf" -> {
        checkConditionalIncludeIsSupported("includeIf", readInto)
        val value = reader.getAttributeValue(i)?.let { System.getProperty(it) }
        if (value != "true") {
          reader.skipElement()
          return
        }
      }
      "includeUnless" -> {
        checkConditionalIncludeIsSupported("includeUnless", readInto)
        val value = reader.getAttributeValue(i)?.let { System.getProperty(it) }
        if (value == "true") {
          reader.skipElement()
          return
        }
      }
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
    pathResolver.loadXIncludeReference(dataLoader = dataLoader, base = includeBase, relativePath = path, readContext = readContext, readInto = readInto)
  }
  catch (e: IOException) {
    readError = e
    false
  }
  if (read) {
    (readContext as? DescriptorListLoadingContext)?.debugData?.recordIncludedPath(
      rawPluginDescriptor = readInto,
      path = PluginXmlPathResolver.toLoadPath(relativePath = path, base = includeBase),
    )
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

private fun checkConditionalIncludeIsSupported(attribute: String, pluginDescriptor: RawPluginDescriptor) {
  if (pluginDescriptor.id !in K2_ALLOWED_PLUGIN_IDS) {
    throw IllegalArgumentException("$attribute of 'include' is not supported")
  }
}

private var dateTimeFormatter: DateTimeFormatter? = null

private val LOG: Logger
  get() = PluginManagerCore.logger

private fun parseReleaseDate(dateString: String): LocalDate? {
  if (dateString.isEmpty() || dateString == "__DATE__") {
    return null
  }

  var formatter = dateTimeFormatter
  if (formatter == null) {
    formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)!!
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
  // the cursor must be at the start of the parent element
  assert(isStartElement)

  var depth = 1
  while (true) {
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
