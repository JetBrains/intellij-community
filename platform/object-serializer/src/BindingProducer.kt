// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.SystemProperties
import gnu.trove.THashMap
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Type
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BindingProducer : BindingInitializationContext {
  private val cache: MutableMap<Type, RootBinding> = THashMap()
  private val cacheLock = ReentrantReadWriteLock()

  @get:TestOnly
  internal val bindingCount: Int
    get() = cacheLock.read { cache.size }

  override val bindingProducer: BindingProducer
    get() = this

  override val isResolveConstructorOnInit = SystemProperties.`is`("idea.serializer.resolve.ctor.on.init")

  abstract fun getNestedBinding(accessor: MutableAccessor): NestedBinding

  fun getRootBinding(aClass: Class<*>, type: Type = aClass): RootBinding {
    val cacheKey = createCacheKey(aClass, type)
    return cacheLock.read {
      // create cache only under write lock
      cache.get(cacheKey)
    } ?: cacheLock.write {
      cache.get(cacheKey)?.let {
        return it
      }

      createRootBinding(aClass, type, cacheKey)
      val binding = createRootBinding(aClass, type, cacheKey)
      cache.put(cacheKey, binding)
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

  protected open fun createCacheKey(aClass: Class<*>, type: Type) = type

  protected abstract fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type): RootBinding

  @Suppress("unused")
  fun clearBindingCache() {
    cacheLock.write {
      cache.clear()
    }
  }
}