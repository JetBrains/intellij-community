// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.SystemProperties
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal abstract class BindingProducer : BindingInitializationContext {
  private val cache: MutableMap<Type, Binding> = Object2ObjectOpenCustomHashMap(object : Hash.Strategy<Type> {
    override fun equals(o1: Type?, o2: Type?): Boolean {
      if (o1 is ParameterizedType && o2 is ParameterizedType) {
        return o1 === o2 || (Arrays.equals(o1.actualTypeArguments, o2.actualTypeArguments) && o1.rawType == o2.rawType)
      }
      return o1 == o2
    }

    override fun hashCode(o: Type?): Int {
      // ours ParameterizedTypeImpl hash code differs from java impl
      return when (o) {
        is ParameterizedType -> 31 * o.rawType.hashCode() + Arrays.hashCode(o.actualTypeArguments)
        null -> 0
        else -> o.hashCode()
      }
    }
  })

  private val cacheLock = ReentrantReadWriteLock()
  @get:TestOnly
  internal val bindingCount: Int
    get() = cacheLock.read { cache.size }

  override val bindingProducer: BindingProducer
    get() = this

  override val isResolveConstructorOnInit = SystemProperties.`is`("idea.serializer.resolve.ctor.on.init")

  abstract fun getNestedBinding(accessor: MutableAccessor): Binding

  fun getRootBinding(aClass: Class<*>) = getRootBinding(aClass, aClass)

  fun getRootBinding(aClass: Class<*>?, type: Type): Binding {
    fun getByTypeOrByClass(): Binding? {
      var result = cache.get(type)
      if (result == null && aClass !== type && aClass != null) {
        result = cache.get(aClass)
      }
      return result
    }

    cacheLock.read {
      getByTypeOrByClass()?.let {
        return it
      }
    }

    cacheLock.write {
      getByTypeOrByClass()?.let {
        return it
      }

      val binding = createRootBinding(aClass, type)
      cache.put(binding.createCacheKey(aClass, type), binding)
      try {
        binding.init(type, this)
      }
      catch (e: Throwable) {
        cache.remove(type)
        throw e
      }
      return binding
    }
  }

  protected abstract fun createRootBinding(aClass: Class<*>?, type: Type): Binding

  @Suppress("unused")
  fun clearBindingCache() {
    cacheLock.write {
      cache.clear()
    }
  }
}