// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import gnu.trove.THashMap
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Type
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BindingProducer<ROOT_BINDING> {
  private val cache: MutableMap<Type, ROOT_BINDING> = THashMap()
  private val cacheLock = ReentrantReadWriteLock()

  @get:TestOnly
  internal val bindingCount: Int
    get() = cacheLock.read { cache.size }

  abstract fun getNestedBinding(accessor: MutableAccessor): NestedBinding

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