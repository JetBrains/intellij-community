// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.reference.SoftReference
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.KotlinAwareBeanBinding
import com.intellij.serialization.xml.KotlinxSerializationBinding
import com.intellij.util.io.URLUtil
import com.intellij.util.xmlb.*
import kotlinx.serialization.Serializable
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val skipDefaultsSerializationFilter = ThreadLocal<SoftReference<SkipDefaultsSerializationFilter>>()

private fun doGetDefaultSerializationFilter(): SkipDefaultsSerializationFilter {
  var result = SoftReference.dereference(skipDefaultsSerializationFilter.get())
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

  override fun <T : Any> serialize(obj: T, filter: SerializationFilter?, createElementIfEmpty: Boolean): Element? {
    try {
      val binding = serializer.getRootBinding(obj.javaClass)
      return if (binding is BeanBinding) {
        // top level expects not null (null indicates error, empty element will be omitted)
        binding.serialize(obj, createElementIfEmpty, filter)
      }
      else {
        binding.serialize(obj, null, filter) as Element
      }
    }
    catch (e: SerializationException) {
      throw e
    }
    catch (e: Exception) {
      throw XmlSerializationException("Can't serialize instance of ${obj.javaClass}", e)
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
    beanBinding.serializeInto(obj, target, filter ?: getDefaultSerializationFilter())
  }

  override fun <T> deserialize(element: Element, clazz: Class<T>): T {
    if (clazz == Element::class.java) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }

    @Suppress("UNCHECKED_CAST")
    try {
      return (serializer.getRootBinding(clazz, clazz) as NotNullDeserializeBinding).deserialize(null, element) as T
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
      return deserialize(JDOMUtil.load(URLUtil.openStream(url)), aClass)
    }
    catch (e: IOException) {
      throw XmlSerializationException(e)
    }
    catch (e: JDOMException) {
      throw XmlSerializationException(e)
    }
  }
}

fun deserializeBaseStateWithCustomNameFilter(state: BaseState, excludedPropertyNames: Collection<String>): Element? {
  val binding = serializer.getRootBinding(state.javaClass) as KotlinAwareBeanBinding
  return binding.serializeBaseStateInto(state, null, doGetDefaultSerializationFilter(), excludedPropertyNames)
}

private val serializer = MyXmlSerializer()

private abstract class OldBindingProducer<ROOT_BINDING> {
  private val cache: MutableMap<Type, ROOT_BINDING> = HashMap()
  private val cacheLock = ReentrantReadWriteLock()

  @get:TestOnly
  val bindingCount: Int
    get() = cacheLock.read { cache.size }

  fun getRootBinding(aClass: Class<*>, originalType: Type = aClass): ROOT_BINDING {
    val cacheKey = createCacheKey(aClass, originalType)
    return cacheLock.read {
      // create cache only under write lock
      cache.get(cacheKey)
    } ?: cacheLock.write {
      cache.get(cacheKey)?.let {
        return it
      }

      createRootBinding(aClass, originalType, cacheKey, cache)
    }
  }

  protected open fun createCacheKey(aClass: Class<*>, originalType: Type) = originalType

  protected abstract fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type, map: MutableMap<Type, ROOT_BINDING>): ROOT_BINDING

  fun clearBindingCache() {
    cacheLock.write {
      cache.clear()
    }
  }
}

private class MyXmlSerializer : XmlSerializerImpl.XmlSerializerBase() {
  val bindingProducer = object : OldBindingProducer<Binding>() {
    override fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type, map: MutableMap<Type, Binding>): Binding {
      var binding = createClassBinding(aClass, null, type)
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
        binding.init(type, this@MyXmlSerializer)
      }
      catch (e: Throwable) {
        map.remove(type)
        throw e
      }
      return binding
    }
  }

  override fun getRootBinding(aClass: Class<*>, originalType: Type): Binding {
    return bindingProducer.getRootBinding(aClass, originalType)
  }
}

/**
 * used by MPS. Do not use if not approved.
 */
fun clearBindingCache() {
  serializer.bindingProducer.clearBindingCache()
}