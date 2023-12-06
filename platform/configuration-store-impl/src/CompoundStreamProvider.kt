// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

@Internal
class CompoundStreamProvider : StreamProvider {
  private val providers = ContainerUtil.createConcurrentList<StreamProvider>()
  private val providerListModificationCount = AtomicInteger()

  override val saveStorageDataOnReload: Boolean // true by default
    get() = !providers.any { !it.saveStorageDataOnReload }

  override val enabled: Boolean
    get() = providers.any { it.enabled }

  override val isExclusive: Boolean
    get() = providers.any { it.isExclusive }

  val isExclusivelyEnabled: Boolean
    get() = enabled && isExclusive

  override fun isApplicable(fileSpec: String, roamingType: RoamingType) = providers.any { it.isApplicable(fileSpec, roamingType) }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit) = providers.any { it.read(fileSpec, roamingType, consumer) }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: Function1<String, Boolean>,
                               processor: Function3<String, InputStream, Boolean, Boolean>): Boolean {
    return providers.any { it.processChildren(path, roamingType, filter, processor) }
  }

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    providers.forEach {
      if (it.isApplicable(fileSpec, roamingType)) {
        it.write(fileSpec, content, roamingType)
      }
    }
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) = providers.any { it.delete(fileSpec, roamingType) }

  override fun deleteIfObsolete(fileSpec: String, roamingType: RoamingType) {
    providers.forEach { 
      it.deleteIfObsolete(fileSpec, roamingType)
    }
  }

  fun addStreamProvider(provider: StreamProvider, first: Boolean) {
    if (first) {
      providers.add(0, provider)
    }
    else {
      providers.add(provider)
    }
    providerListModificationCount.getAndIncrement()
  }

  fun removeStreamProvider(aClass: Class<out StreamProvider>) {
    providers.removeAll(aClass::isInstance)
    providerListModificationCount.getAndIncrement()
  }

  fun getInstanceOf(aClass: Class<out StreamProvider>): StreamProvider = providers.first { aClass.isInstance(it) }
}