// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.extensions.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Modifier

private val LOG: Logger
  get() = logger<ExtensionsAreaImpl>()

private const val DEBUG_REGISTRATION = false

@Internal
fun createExtensionPoints(points: List<ExtensionPointDescriptor>,
                          componentManager: ComponentManager,
                          result: MutableMap<String, ExtensionPointImpl<*>>,
                          pluginDescriptor: PluginDescriptor) {
  for (descriptor in points) {
    val name = descriptor.getQualifiedName(pluginDescriptor)
    val point: ExtensionPointImpl<Any> = if (descriptor.isBean) {
      BeanExtensionPoint(name = name,
                         className = descriptor.className,
                         pluginDescriptor = pluginDescriptor,
                         componentManager = componentManager,
                         dynamic = descriptor.isDynamic)
    }
    else {
      InterfaceExtensionPoint(name = name,
                              className = descriptor.className,
                              pluginDescriptor = pluginDescriptor,
                              componentManager = componentManager,
                              clazz = null,
                              hasAttributes = descriptor.hasAttributes,
                              dynamic = descriptor.isDynamic)
    }
    result.putIfAbsent(point.name, point)?.let { old ->
      val oldPluginDescriptor = old.getPluginDescriptor()
      throw componentManager.createError(
        "Duplicate registration for EP ${point.name} first in $oldPluginDescriptor, second in $pluginDescriptor", pluginDescriptor.pluginId
      )
    }
  }
}

@Internal
class ExtensionsAreaImpl(private val componentManager: ComponentManager) : ExtensionsArea {
  @Volatile
  private var extensionPoints: PersistentMap<String, ExtensionPointImpl<*>> = persistentHashMapOf()

  private val epTraces = if (DEBUG_REGISTRATION) HashMap<String, Throwable>() else null

  override val nameToPointMap: Map<String, ExtensionPointImpl<*>>
    get() = extensionPoints

  private val lock = Any()

  fun reset(nameToPointMap: PersistentMap<String, ExtensionPointImpl<*>>) {
    extensionPoints = nameToPointMap
  }

  @TestOnly
  fun notifyAreaReplaced(newArea: ExtensionsAreaImpl?) {
    val processedEPs = HashSet<String>(extensionPoints.size)
    for (point in extensionPoints.values) {
      point.notifyAreaReplaced(this)
      processedEPs.add(point.name)
    }

    if (newArea == null) {
      return
    }

    for (point in newArea.extensionPoints.values) {
      if (!processedEPs.contains(point.name)) {
        point.notifyAreaReplaced(this)
      }
    }
  }

  @TestOnly
  fun registerExtensionPoints(pluginDescriptor: PluginDescriptor, extensionPointElements: List<Element>) {
    for (element in extensionPointElements) {
      var pointName = element.getAttributeValue("qualifiedName")
      if (pointName == null) {
        val name = element.getAttributeValue("name")
                   ?: throw componentManager.createError("'name' attribute not specified for extension point in '$pluginDescriptor' plugin",
                                                         pluginDescriptor.pluginId)
        pointName = pluginDescriptor.pluginId.idString + '.' + name
      }

      val beanClassName = element.getAttributeValue("beanClass")
      val interfaceClassName = element.getAttributeValue("interface")
      if (beanClassName == null && interfaceClassName == null) {
        throw componentManager.createError(
          "Neither 'beanClass' nor 'interface' attribute is specified for extension point '$pointName' in '${pluginDescriptor}' plugin",
          pluginDescriptor.pluginId
        )
      }
      if (beanClassName != null && interfaceClassName != null) {
        throw componentManager.createError(
          "Both 'beanClass' and 'interface' attributes are specified for extension point '$pointName' in '$pluginDescriptor' plugin",
          pluginDescriptor.pluginId
        )
      }

      val dynamic = java.lang.Boolean.parseBoolean(element.getAttributeValue("dynamic"))
      doRegisterExtensionPoint<Any>(name = pointName,
                                    extensionClass = interfaceClassName ?: beanClassName,
                                    pluginDescriptor = pluginDescriptor,
                                    isInterface = interfaceClassName != null,
                                    dynamic = dynamic)
    }
  }

  fun unregisterExtensions(extensionPointName: String,
                           pluginDescriptor: PluginDescriptor,
                           priorityListenerCallbacks: MutableList<in Runnable>,
                           listenerCallbacks: MutableList<in Runnable>): Boolean {
    val point = extensionPoints.get(extensionPointName) ?: return false
    point.unregisterExtensions(componentManager = componentManager,
                               pluginDescriptor = pluginDescriptor,
                               priorityListenerCallbacks = priorityListenerCallbacks,
                               listenerCallbacks = listenerCallbacks)
    return true
  }

  fun resetExtensionPoints(descriptors: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    for (descriptor in descriptors) {
      extensionPoints.get(descriptor.getQualifiedName(pluginDescriptor))?.reset()
    }
  }

  fun clearUserCache() {
    for (point in extensionPoints.values) {
      point.clearUserCache()
    }
  }

  /**
   * You must call [.resetExtensionPoints] before otherwise event `ExtensionEvent.REMOVED` will be not fired.
   */
  fun unregisterExtensionPoints(descriptors: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    if (descriptors.isEmpty()) {
      return
    }

    synchronized(lock) {
      extensionPoints = extensionPoints.mutate { map ->
        for (descriptor in descriptors) {
          map.remove(descriptor.getQualifiedName(pluginDescriptor))
        }
      }
    }
  }

  @TestOnly
  fun registerExtensionPoint(extensionPoint: BaseExtensionPointName<*>,
                             extensionPointBeanClass: String,
                             kind: ExtensionPoint.Kind,
                             parentDisposable: Disposable) {
    val extensionPointName = extensionPoint.name
    registerExtensionPoint(extensionPointName = extensionPointName,
                           extensionPointBeanClass = extensionPointBeanClass,
                           kind = kind,
                           isDynamic = false)
    Disposer.register(parentDisposable) { unregisterExtensionPoint(extensionPointName) }
  }

  @TestOnly
  override fun registerExtensionPoint(extensionPointName: String,
                                      extensionPointBeanClass: String,
                                      kind: ExtensionPoint.Kind,
                                      isDynamic: Boolean) {
    val pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("fakeIdForTests"))
    doRegisterExtensionPoint<Any>(name = extensionPointName,
                                  extensionClass = extensionPointBeanClass,
                                  pluginDescriptor = pluginDescriptor,
                                  isInterface = kind == ExtensionPoint.Kind.INTERFACE,
                                  dynamic = isDynamic)
  }

  @TestOnly
  fun <T : Any> registerPoint(name: String,
                              extensionClass: Class<T>,
                              pluginDescriptor: PluginDescriptor,
                              isDynamic: Boolean): ExtensionPointImpl<T> {
    return doRegisterExtensionPoint(name = name,
                                    extensionClass = extensionClass.name,
                                    pluginDescriptor = pluginDescriptor,
                                    isInterface = extensionClass.isInterface || extensionClass.modifiers and Modifier.ABSTRACT != 0,
                                    dynamic = isDynamic)
  }

  @TestOnly
  private fun <T : Any> doRegisterExtensionPoint(name: String,
                                                 extensionClass: String,
                                                 pluginDescriptor: PluginDescriptor,
                                                 isInterface: Boolean,
                                                 dynamic: Boolean): ExtensionPointImpl<T> {
    val point: ExtensionPointImpl<T> = if (isInterface) {
      InterfaceExtensionPoint(name = name,
                              className = extensionClass,
                              pluginDescriptor = pluginDescriptor,
                              componentManager = componentManager,
                              clazz = null,
                              hasAttributes = false,
                              dynamic = dynamic)
    }
    else {
      BeanExtensionPoint(name = name,
                         className = extensionClass,
                         pluginDescriptor = pluginDescriptor,
                         componentManager = componentManager,
                         dynamic = dynamic)
    }
    checkThatPointNotDuplicated(name, point.getPluginDescriptor())
    synchronized(lock) {
      extensionPoints = extensionPoints.put(name, point)
    }
    if (DEBUG_REGISTRATION) {
      epTraces!!.put(name, Throwable("Original registration for $name"))
    }
    return point
  }

  /**
   * To register extensions for [com.intellij.openapi.util.KeyedExtensionCollector] for test purposes,
   * where extension instance can be KeyedLazyInstance and not a real bean class,
   * because often it is not possible to use one (for example, [com.intellij.lang.LanguageExtensionPoint]).
   */
  @TestOnly
  fun <T : Any> registerFakeBeanPoint(name: String, pluginDescriptor: PluginDescriptor): ExtensionPointImpl<T> {
    // any object name can be used, because EP must not create any instance
    return doRegisterExtensionPoint(name = name,
                                    extensionClass = Any::class.java.name,
                                    pluginDescriptor = pluginDescriptor,
                                    isInterface = false,
                                    dynamic = false)
  }

  private fun checkThatPointNotDuplicated(pointName: String, pluginDescriptor: PluginDescriptor) {
    val id1 = (extensionPoints.get(pointName) ?: return).getPluginDescriptor().pluginId
    val id2 = pluginDescriptor.pluginId
    val message = "Duplicate registration for EP '$pointName': first in $id1, second in $id2"
    if (DEBUG_REGISTRATION) {
      LOG.error(message, epTraces!!.get(pointName))
    }
    throw componentManager.createError(message, pluginDescriptor.pluginId)
  }

  // _only_ for CoreApplicationEnvironment
  fun registerExtensionPoints(points: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    synchronized(lock) {
      extensionPoints = extensionPoints.mutate {
        createExtensionPoints(points = points, componentManager = componentManager, result = it, pluginDescriptor = pluginDescriptor)
      }
    }
  }

  override fun <T : Any> getExtensionPoint(extensionPointName: String): ExtensionPointImpl<T> {
    return getExtensionPointIfRegistered(extensionPointName)
           ?: throw IllegalArgumentException("Missing extension point: $extensionPointName in container $componentManager")
  }

  override fun <T : Any> getExtensionPointIfRegistered(extensionPointName: String): ExtensionPointImpl<T>? {
    @Suppress("UNCHECKED_CAST")
    return extensionPoints.get(extensionPointName) as ExtensionPointImpl<T>?
  }

  override fun <T : Any> getExtensionPoint(extensionPointName: ExtensionPointName<T>): ExtensionPoint<T> {
    return getExtensionPoint(extensionPointName.name)
  }

  @TestOnly
  override fun processExtensionPoints(consumer: (ExtensionPointImpl<*>) -> Unit) {
    extensionPoints.values.forEach(consumer)
  }

  @TestOnly
  override fun unregisterExtensionPoint(extensionPointName: String) {
    val extensionPoint = getExtensionPointIfRegistered<Any>(extensionPointName) ?: return
    extensionPoint.reset()
    synchronized(lock) {
    extensionPoints = extensionPoints.remove(extensionPointName)
      }
  }

  override fun hasExtensionPoint(extensionPointName: String): Boolean = extensionPoints.containsKey(extensionPointName)

  override fun hasExtensionPoint(extensionPointName: ExtensionPointName<*>): Boolean = hasExtensionPoint(extensionPointName.name)

  override fun toString(): String = componentManager.toString()
}

