// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.extensions.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.ThreeState
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Modifier
import java.util.*
import java.util.function.Consumer

@Internal
class ExtensionsAreaImpl(private val componentManager: ComponentManager) : ExtensionsArea {
  companion object {
    private val LOG = Logger.getInstance(ExtensionsAreaImpl::class.java)

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
                                  dynamic = descriptor.isDynamic)
        }
        result.putIfAbsent(name, point)?.let { old ->
          val oldPluginDescriptor = old.getPluginDescriptor()
          throw componentManager.createError(
            "Duplicate registration for EP $name first in $oldPluginDescriptor, second in $pluginDescriptor", pluginDescriptor.pluginId
          )
        }
      }
    }
  }

  @Volatile
  @Internal
  @JvmField
  var extensionPoints = emptyMap<String, ExtensionPointImpl<*>>()

  private val epTraces = if (DEBUG_REGISTRATION) HashMap<String, Throwable>() else null

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
    point.unregisterExtensions(componentManager, pluginDescriptor, priorityListenerCallbacks, listenerCallbacks)
    return true
  }

  fun resetExtensionPoints(descriptors: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    for (descriptor in descriptors) {
      extensionPoints.get(descriptor.getQualifiedName(pluginDescriptor))?.reset()
    }
  }

  fun clearUserCache() {
    extensionPoints.values.forEach(ExtensionPointImpl<*>::clearUserCache)
  }

  /**
   * You must call [.resetExtensionPoints] before otherwise event `ExtensionEvent.REMOVED` will be not fired.
   */
  fun unregisterExtensionPoints(descriptors: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    if (descriptors.isEmpty()) {
      return
    }

    val map = HashMap(extensionPoints)
    for (descriptor in descriptors) {
      map.remove(descriptor.getQualifiedName(pluginDescriptor))
    }
    // Map.copyOf is not available in extension module
    extensionPoints = Collections.unmodifiableMap(map)
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
                           dynamic = false)
    Disposer.register(parentDisposable) { unregisterExtensionPoint(extensionPointName) }
  }

  @TestOnly
  override fun registerExtensionPoint(extensionPointName: String,
                                      extensionPointBeanClass: String,
                                      kind: ExtensionPoint.Kind,
                                      dynamic: Boolean) {
    val pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("fakeIdForTests"))
    doRegisterExtensionPoint<Any>(name = extensionPointName,
                                  extensionClass = extensionPointBeanClass,
                                  pluginDescriptor = pluginDescriptor,
                                  isInterface = kind == ExtensionPoint.Kind.INTERFACE,
                                  dynamic = dynamic)
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
    val newMap = HashMap<String, ExtensionPointImpl<*>>(extensionPoints.size + 1)
    newMap.putAll(extensionPoints)
    newMap.put(name, point)
    extensionPoints = Collections.unmodifiableMap(newMap)
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
    if (!hasExtensionPoint(pointName)) {
      return
    }

    val id1 = getExtensionPoint<Any>(pointName).getPluginDescriptor().pluginId
    val id2 = pluginDescriptor.pluginId
    val message = "Duplicate registration for EP '$pointName': first in $id1, second in $id2"
    if (DEBUG_REGISTRATION) {
      LOG.error(message, epTraces!!.get(pointName))
    }
    throw componentManager.createError(message, pluginDescriptor.pluginId)
  }

  // _only_ for CoreApplicationEnvironment
  fun registerExtensionPoints(points: List<ExtensionPointDescriptor>, pluginDescriptor: PluginDescriptor) {
    val map = HashMap(extensionPoints)
    createExtensionPoints(points, componentManager, map, pluginDescriptor)
    extensionPoints = Collections.unmodifiableMap(map)
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
  fun processExtensionPoints(consumer: Consumer<ExtensionPointImpl<*>>) {
    extensionPoints.values.forEach(consumer)
  }

  @Internal
  fun <T : Any> findExtensionByClass(aClass: Class<T>): T? {
    // TeamCity plugin wants DefaultDebugExecutor in constructor
    if (aClass.name == "com.intellij.execution.executors.DefaultDebugExecutor") {
      return getExtensionPointIfRegistered<Any>("com.intellij.executor")?.findExtension(aClass, false, ThreeState.YES)
    }

    for (point in extensionPoints.values) {
      if (point !is InterfaceExtensionPoint<*>) {
        continue
      }

      try {
        if (!point.getExtensionClass().isAssignableFrom(aClass)) {
          continue
        }

        @Suppress("UNCHECKED_CAST")
        return (point as ExtensionPointImpl<T>).findExtension(aClass, false, ThreeState.YES) ?: continue
      }
      catch (e: Throwable) {
        LOG.warn("error during findExtensionPointByClass", e)
      }
    }
    return null
  }

  @TestOnly
  override fun unregisterExtensionPoint(extensionPointName: String) {
    val extensionPoint = getExtensionPointIfRegistered<Any>(extensionPointName) ?: return
    extensionPoint.reset()
    val map = HashMap(extensionPoints)
    map.remove(extensionPointName)
    extensionPoints = Collections.unmodifiableMap(map)
  }

  override fun hasExtensionPoint(extensionPointName: String): Boolean = extensionPoints.containsKey(extensionPointName)

  override fun hasExtensionPoint(extensionPointName: ExtensionPointName<*>): Boolean = hasExtensionPoint(extensionPointName.name)

  override fun toString(): String = componentManager.toString()
}