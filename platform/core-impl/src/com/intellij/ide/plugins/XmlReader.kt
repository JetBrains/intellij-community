// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("XmlReader")
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.BeanExtensionPoint
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.InterfaceExtensionPoint
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.util.plugins.DataLoader
import com.intellij.platform.util.plugins.PathResolver
import com.intellij.util.messages.ListenerDescriptor
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException

@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

const val APPLICATION_SERVICE = "com.intellij.applicationService"
const val PROJECT_SERVICE = "com.intellij.projectService"
const val MODULE_SERVICE = "com.intellij.moduleService"

private const val ATTRIBUTE_AREA = "area"

internal fun isSuitableForOs(os: String): Boolean {
  if (os.isEmpty()) {
    return true
  }

  return when (os) {
    IdeaPluginDescriptorImpl.OS.mac.name -> SystemInfoRt.isMac
    IdeaPluginDescriptorImpl.OS.linux.name -> SystemInfoRt.isLinux
    IdeaPluginDescriptorImpl.OS.windows.name -> SystemInfoRt.isWindows
    IdeaPluginDescriptorImpl.OS.unix.name -> SystemInfoRt.isUnix
    IdeaPluginDescriptorImpl.OS.freebsd.name -> SystemInfoRt.isFreeBSD
    else -> throw IllegalArgumentException("Unknown OS '$os'")
  }
}

private fun readServiceDescriptor(element: Element): ServiceDescriptor {
  val descriptor = ServiceDescriptor()
  for (attribute in element.attributes) {
    when (attribute.name) {
      "serviceImplementation" -> descriptor.serviceImplementation = getNullifiedValue(attribute)
      "serviceInterface" -> descriptor.serviceInterface = getNullifiedValue(attribute)
      "testServiceImplementation" -> descriptor.testServiceImplementation = getNullifiedValue(attribute)
      "headlessImplementation" -> descriptor.headlessImplementation = getNullifiedValue(attribute)
      "configurationSchemaKey" -> descriptor.configurationSchemaKey = attribute.value
      "overrides" -> descriptor.overrides = java.lang.Boolean.parseBoolean(attribute.value)
      "preload" -> {
        val preload = attribute.value
        if (preload != null) {
          when (preload) {
            "true" -> descriptor.preload = ServiceDescriptor.PreloadMode.TRUE
            "await" -> descriptor.preload = ServiceDescriptor.PreloadMode.AWAIT
            "notHeadless" -> descriptor.preload = ServiceDescriptor.PreloadMode.NOT_HEADLESS
            "notLightEdit" -> descriptor.preload = ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
            else -> LOG.error("Unknown preload mode value: ${JDOMUtil.writeElement(element)}")
          }
        }
      }
    }
  }
  return descriptor
}

internal fun readListeners(list: Element, containerDescriptor: ContainerDescriptor, mainDescriptor: IdeaPluginDescriptorImpl) {
  val content = list.content
  var result = containerDescriptor.listeners
  if (result == null) {
    result = ArrayList(content.size)
    containerDescriptor.listeners = result
  }
  else {
    (result as ArrayList<ListenerDescriptor?>).ensureCapacity(result.size + content.size)
  }

  for (item in content) {
    if (item !is Element) {
      continue
    }

    val os = item.getAttributeValue("os")
    if (os != null && !isSuitableForOs(os)) {
      continue
    }

    val listenerClassName = item.getAttributeValue("class")
    val topicClassName = item.getAttributeValue("topic")
    if (listenerClassName == null || topicClassName == null) {
      LOG.error("Listener descriptor is not correct: ${JDOMUtil.writeElement(item)}")
    }
    else {
      result.add(ListenerDescriptor(listenerClassName, topicClassName,
                                    getBoolean("activeInTestMode", item), getBoolean("activeInHeadlessMode", item), mainDescriptor))
    }
  }
}

internal fun readContent(list: Element, descriptor: IdeaPluginDescriptorImpl) {
  val content = list.content
  val items: MutableList<PluginContentDescriptor.ModuleItem> = ArrayList()
  for (item in content) {
    if (item !is Element) {
      continue
    }

    if (item.name == "module") {
      items.add(PluginContentDescriptor.ModuleItem(name = item.getAttributeValue("name")!!,
                                                   packageName = item.getAttributeValue("package"),
                                                   configFile = item.getAttributeValue("configFile")))
    }
    else {
      throw RuntimeException("Unknown content item type: ${item.name}")
    }
  }
  descriptor.contentDescriptor = PluginContentDescriptor(items)
}

internal fun readNewDependencies(list: Element, descriptor: IdeaPluginDescriptorImpl, context: DescriptorListLoadingContext): Boolean {
  var modules: MutableList<ModuleDependenciesDescriptor.ModuleItem>? = null
  var plugins: MutableList<ModuleDependenciesDescriptor.PluginItem>? = null
  for (item in list.content) {
    if (item !is Element) {
      continue
    }

    when (item.name) {
      "module" -> {
        if (modules == null) {
          modules = mutableListOf()
        }
        modules.add(ModuleDependenciesDescriptor.ModuleItem(item.getAttributeValue("name")!!, item.getAttributeValue("package")))
      }
      "plugin" -> {
        if (plugins == null) {
          plugins = mutableListOf()
        }

        val dependencyId = PluginId.getId(item.getAttributeValue("id")!!)
        if (context.isPluginDisabled(dependencyId)) {
          descriptor.markAsIncomplete(context, dependencyId) {
            CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId)
          }
          return false
        }
        else if (context.result.isBroken(dependencyId)) {
          descriptor.markAsIncomplete(context, null) {
            CoreBundle.message("plugin.loading.error.short.depends.on.broken.plugin", dependencyId)
          }
          return false
        }
        plugins.add(ModuleDependenciesDescriptor.PluginItem(dependencyId))
      }
      else -> throw RuntimeException("Unknown content item type: ${item.name}")
    }
  }
  descriptor.dependencyDescriptor = ModuleDependenciesDescriptor(modules ?: emptyList(), plugins ?: emptyList())
  return true
}

internal fun readIdAndName(descriptor: IdeaPluginDescriptorImpl, element: Element) {
  var idString = if (descriptor.id == null) element.getChildTextTrim("id") else descriptor.id!!.idString
  var name = element.getChildTextTrim("name")
  if (idString == null) {
    idString = name
  }
  else if (name == null) {
    name = idString
  }
  descriptor.name = name
  if (idString != null && !idString.isEmpty() && descriptor.id == null) {
    descriptor.id = PluginId.getId(idString)
  }
}

internal fun readMetaInfo(descriptor: IdeaPluginDescriptorImpl, element: Element) {
  if (!element.hasAttributes()) {
    return
  }

  for (attribute in element.attributes) {
    when (attribute.name) {
      "url" -> descriptor.url = getNullifiedValue(attribute)
      "use-idea-classloader" -> descriptor.isUseIdeaClassLoader = java.lang.Boolean.parseBoolean(attribute.value)
      "allow-bundled-update" -> descriptor.isBundledUpdateAllowed = java.lang.Boolean.parseBoolean(attribute.value)
      "implementation-detail" -> descriptor.implementationDetail = java.lang.Boolean.parseBoolean(attribute.value)
      "require-restart" -> descriptor.isRestartRequired = java.lang.Boolean.parseBoolean(attribute.value)
      "package" -> descriptor.packagePrefix = getNullifiedValue(attribute)
      "version" -> {
        val internalVersionString = getNullifiedValue(attribute)
        if (internalVersionString != null) {
          try {
            internalVersionString.toInt()
          }
          catch (e: NumberFormatException) {
            LOG.error(PluginException("Invalid value in plugin.xml format version: '$internalVersionString'", e, descriptor.id))
          }
        }
      }
    }
  }
}

private fun getNullifiedValue(attribute: Attribute): String? {
  val v = attribute.value
  return if (v == null || v.isEmpty()) null else v
}

@Throws(IOException::class, JDOMException::class)
internal fun readDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                              descriptor: IdeaPluginDescriptorImpl,
                              context: DescriptorListLoadingContext,
                              pathResolver: PathResolver,
                              dependencies: List<PluginDependency>,
                              dataLoader: DataLoader) {
  var visitedFiles: MutableList<String>? = null
  for (dependency in dependencies) {
    if (dependency.isDisabledOrBroken) {
      continue
    }

    // because of https://youtrack.jetbrains.com/issue/IDEA-206274, configFile maybe not only for optional dependencies
    val configFile = dependency.configFile ?: continue
    if (pathResolver.isFlat && context.checkOptionalConfigShortName(configFile, descriptor, rootDescriptor)) {
      continue
    }

    var element: Element?
    var resolveError: Exception? = null
    try {
      element = pathResolver.resolvePath(dataLoader, configFile, context.xmlFactory)
    }
    catch (e: IOException) {
      resolveError = e
      element = null
    }
    catch (e: JDOMException) {
      resolveError = e
      element = null
    }
    if (element == null) {
      val message = "Plugin $rootDescriptor misses optional descriptor $configFile"
      if (context.ignoreMissingSubDescriptor) {
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
    checkCycle(rootDescriptor, configFile, visitedFiles)
    val subDescriptor = IdeaPluginDescriptorImpl(descriptor.path, descriptor.isBundled)
    subDescriptor.id = rootDescriptor.id
    subDescriptor.descriptorPath = dependency.configFile
    visitedFiles.add(configFile)
    if (subDescriptor.readExternal(element, pathResolver, context, rootDescriptor, dataLoader)) {
      dependency.subDescriptor = subDescriptor
    }
    visitedFiles.clear()
  }
}

private fun checkCycle(rootDescriptor: IdeaPluginDescriptorImpl,
                       configFile: String,
                       visitedFiles: List<String>) {
  var i = 0
  val n = visitedFiles.size
  while (i < n) {
    if (configFile == visitedFiles[i]) {
      val cycle = visitedFiles.subList(i, visitedFiles.size)
      throw RuntimeException("Plugin $rootDescriptor optional descriptors form a cycle: ${java.lang.String.join(", ", cycle)}")
    }
    i++
  }
}

private fun getBoolean(name: String, child: Element): Boolean {
  val value = child.getAttributeValue(name)
  return value == null || java.lang.Boolean.parseBoolean(value)
}

internal fun readExtensions(descriptor: IdeaPluginDescriptorImpl,
                            epNameToExtensions: MutableMap<String, MutableList<Element>>?,
                            loadingContext: DescriptorListLoadingContext,
                            child: Element): MutableMap<String, MutableList<Element>>? {
  var result = epNameToExtensions
  val ns = child.getAttributeValue("defaultExtensionNs")
  for (extensionElement in child.children) {
    val os = extensionElement.getAttributeValue("os")
    if (os != null) {
      extensionElement.removeAttribute("os")
      if (!isSuitableForOs(os)) {
        continue
      }
    }
    val qualifiedExtensionPointName = loadingContext.internString(ExtensionsAreaImpl.extractPointName(extensionElement, ns))
    var containerDescriptor: ContainerDescriptor
    when (qualifiedExtensionPointName) {
      APPLICATION_SERVICE -> containerDescriptor = descriptor.appContainerDescriptor
      PROJECT_SERVICE -> containerDescriptor = descriptor.projectContainerDescriptor
      MODULE_SERVICE -> containerDescriptor = descriptor.moduleContainerDescriptor
      else -> {
        if (result == null) {
          result = LinkedHashMap()
        }
        result.computeIfAbsent(qualifiedExtensionPointName) { ArrayList() }.add(extensionElement)
        continue
      }
    }
    containerDescriptor.addService(readServiceDescriptor(extensionElement))
  }
  return result
}

/**
 * EP cannot be added directly to root descriptor, because probably later EP list will be ignored if dependency plugin is not available.
 *
 * And descriptor as data container.
 */
internal fun readExtensionPoints(descriptor: IdeaPluginDescriptorImpl, parentElement: Element) {
  for (child in parentElement.content) {
    if (child !is Element) {
      continue
    }

    val area = child.getAttributeValue(ATTRIBUTE_AREA)
    val containerDescriptor: ContainerDescriptor = if (area == null) {
      descriptor.appContainerDescriptor
    }
    else {
      if ("IDEA_PROJECT" == area) {
        descriptor.projectContainerDescriptor
      }
      else if ("IDEA_MODULE" == area) {
        descriptor.moduleContainerDescriptor
      }
      else {
        LOG.error("Unknown area: $area")
        continue
      }
    }
    val pointName = getExtensionPointName(child, descriptor.pluginId)
    val beanClassName = child.getAttributeValue("beanClass")
    val interfaceClassName = child.getAttributeValue("interface")
    if (beanClassName == null && interfaceClassName == null) {
      throw RuntimeException(
        "Neither 'beanClass' nor 'interface' attribute is specified for extension point '$pointName' in '${descriptor.pluginId}' plugin")
    }
    if (beanClassName != null && interfaceClassName != null) {
      throw RuntimeException(
        "Both 'beanClass' and 'interface' attributes are specified for extension point '$pointName' in '${descriptor.pluginId}' plugin")
    }

    val dynamic = java.lang.Boolean.parseBoolean(child.getAttributeValue("dynamic"))
    val point: ExtensionPointImpl<Any?> = if (interfaceClassName == null) {
      BeanExtensionPoint(pointName, beanClassName!!, descriptor, dynamic)
    }
    else {
      InterfaceExtensionPoint(pointName, interfaceClassName, descriptor, null, dynamic)
    }

    var result = containerDescriptor.extensionPoints
    if (result == null) {
      result = ArrayList()
      containerDescriptor.extensionPoints = result
    }
    result.add(point)
  }
}

private fun getExtensionPointName(extensionPointElement: Element, effectivePluginId: PluginId): String {
  val pointName = extensionPointElement.getAttributeValue("qualifiedName")
  if (pointName == null) {
    val name = extensionPointElement.getAttributeValue("name")
               ?: throw RuntimeException("'name' attribute not specified for extension point in '$effectivePluginId' plugin")
    return "${effectivePluginId.idString}.$name"
  }
  return pointName
}
