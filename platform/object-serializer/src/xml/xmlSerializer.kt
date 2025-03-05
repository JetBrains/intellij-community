// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:OptIn(SettingsInternalApi::class)

package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.KotlinAwareBeanBinding
import com.intellij.util.io.URLUtil
import com.intellij.util.xmlb.*
import com.intellij.util.xmlb.XmlSerializerImpl.createClassBinding
import kotlinx.serialization.Serializable
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.lang.ref.SoftReference
import java.lang.reflect.Type
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val skipDefaultsSerializationFilter = ThreadLocal<SoftReference<SkipDefaultsSerializationFilter>>()

@Suppress("TestOnlyProblems")
private fun doGetDefaultSerializationFilter(): SkipDefaultsSerializationFilter {
  var result = skipDefaultsSerializationFilter.get()?.get()
  if (result == null) {
    result = object : SkipDefaultsSerializationFilter() {
      override fun accepts(accessor: Accessor, bean: Any): Boolean {
        return when (bean) {
          is BaseState -> bean.accepts(accessor, bean)
          else -> super.accepts(accessor, bean)
        }
      }
    }
    skipDefaultsSerializationFilter.set(SoftReference(result))
  }
  return result
}

@Suppress("unused")
private class JdomSerializerImpl : JdomSerializer {
  override fun getDefaultSerializationFilter() = doGetDefaultSerializationFilter()

  override fun <T : Any> serialize(bean: T, filter: SerializationFilter?, createElementIfEmpty: Boolean): Element? {
    try {
      val binding = serializer.getRootBinding(bean.javaClass)
      if (binding is BeanBinding) {
        return binding.serialize(bean = bean, createElementIfEmpty = createElementIfEmpty, filter = filter)
      }
      else {
        return (binding as RootBinding).serialize(bean = bean, filter = filter) as Element
      }
    }
    catch (e: SerializationException) {
      throw e
    }
    catch (e: Exception) {
      throw XmlSerializationException("Can't serialize instance of ${bean.javaClass}", e)
    }
  }

  override fun serializeObjectInto(obj: Any, target: Element, filter: SerializationFilter?) {
    if (obj is Element) {
      val iterator = obj.children.iterator()
      for (child in iterator) {
        iterator.remove()
        target.addContent(child)
      }

      val attributeIterator = obj.attributes.iterator()
      for (attribute in attributeIterator) {
        attributeIterator.remove()
        target.setAttribute(attribute)
      }
      return
    }

    val beanBinding = serializer.getRootBinding(obj.javaClass) as KotlinAwareBeanBinding
    beanBinding.serializeProperties(bean = obj, preCreatedElement = target, filter = filter ?: doGetDefaultSerializationFilter())
  }

  override fun <T, E : Any> deserialize(element: E, clazz: Class<T>, adapter: DomAdapter<E>): T {
    if (clazz === Element::class.java && adapter === JdomAdapter) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }

    try {
      @Suppress("UNCHECKED_CAST")
      return serializer.getRootBinding(clazz, clazz).deserialize(context = null, element = element, adapter = adapter) as T
    }
    catch (e: SerializationException) {
      throw e
    }
    catch (e: Exception) {
      throw XmlSerializationException("Cannot deserialize class ${clazz.name}", e)
    }
  }

  override fun clearSerializationCaches() {
    clearBindingCache()
  }

  override fun <T> getBeanBinding(aClass: Class<T>): BeanBinding {
    return serializer.getRootBinding(aClass, aClass) as BeanBinding
  }

  override fun deserializeInto(obj: Any, element: Element) {
    try {
      (serializer.getRootBinding(obj.javaClass) as BeanBinding).deserializeInto(obj, element)
    }
    catch (e: SerializationException) {
      throw e
    }
    catch (e: Exception) {
      throw XmlSerializationException(e)
    }
  }

  override fun <T> deserialize(url: URL, aClass: Class<T>): T {
    try {
      return deserialize(JDOMUtil.load(URLUtil.openStream(url)), aClass, JdomAdapter)
    }
    catch (e: IOException) {
      throw XmlSerializationException(e)
    }
    catch (e: JDOMException) {
      throw XmlSerializationException(e)
    }
  }
}

@Internal
fun deserializeBaseStateWithCustomNameFilter(state: BaseState, excludedPropertyNames: Collection<String>): Element? {
  val binding = serializer.getRootBinding(state.javaClass) as KotlinAwareBeanBinding
  return binding.serializeBaseStateInto(
    bean = state,
    _element = null,
    filter = doGetDefaultSerializationFilter(),
    excludedPropertyNames = excludedPropertyNames,
  )
}

private val serializer = object : Serializer {
  private val cache = HashMap<Type, Binding>()
  private val cacheLock = ReentrantReadWriteLock()

  override fun getBinding(aClass: Class<*>, type: Type): Binding? {
    return if (ClassUtil.isPrimitive(aClass)) null else getRootBinding(aClass, type)
  }

  private fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type, map: MutableMap<Type, Binding>, serializer: Serializer): Binding {
    var binding = createClassBinding(/* aClass = */ aClass, /* accessor = */ null, /* originalType = */ type, serializer)
    if (binding == null) {
      if (aClass.isAnnotationPresent(Serializable::class.java)) {
        binding = KotlinxSerializationBinding(aClass)
      }
      else {
        binding = KotlinAwareBeanBinding(aClass)
      }
    }
    map.put(cacheKey, binding)
    try {
      binding.init(type, serializer)
    }
    catch (e: Throwable) {
      map.remove(type)
      throw e
    }
    return binding
  }

  fun clearBindingCache() {
    cacheLock.write {
      cache.clear()
    }
  }

  override fun getRootBinding(aClass: Class<*>, originalType: Type): Binding {
    // create cache only under write lock
    return cacheLock.read {
      cache.get(originalType)
    } ?: cacheLock.write {
      cache.get(originalType)?.let {
        return it
      }

      createRootBinding(aClass = aClass, type = originalType, cacheKey = originalType, map = cache, serializer = this)
    }
  }
}

@Suppress("FunctionName")
@Internal
fun __platformSerializer(): Serializer = serializer

/**
 * Used by MPS. Do not use if not approved.
 */
@Internal
fun clearBindingCache() {
  serializer.clearBindingCache()
}