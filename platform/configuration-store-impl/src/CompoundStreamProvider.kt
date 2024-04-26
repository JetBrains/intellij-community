// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

internal class CompoundStreamProvider : StreamProvider {
  private val providers = CopyOnWriteArrayList<StreamProvider>()

  // true by default
  override val saveStorageDataOnReload: Boolean
    get() = !providers.any { !it.saveStorageDataOnReload }

  override val enabled: Boolean
    get() = providers.any { it.enabled }

  override val isExclusive: Boolean
    get() = providers.any { it.isExclusive }

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return providers.any { it.isApplicable(fileSpec = fileSpec, roamingType = roamingType) }
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    return providers.any { it.read(fileSpec = fileSpec, roamingType = roamingType, consumer = consumer) }
  }

  override fun processChildren(
    path: String,
    roamingType: RoamingType,
    filter: Function1<String, Boolean>,
    processor: Function3<String, InputStream, Boolean, Boolean>,
  ): Boolean {
    return providers.any {
      it.processChildren(path = path, roamingType = roamingType, filter = filter, processor = processor)
    }
  }

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    for (provider in providers) {
      if (provider.isApplicable(fileSpec, roamingType)) {
        provider.write(fileSpec = fileSpec, content = content, roamingType = roamingType)
      }
    }
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = providers.any { it.delete(fileSpec, roamingType) }

  override fun deleteIfObsolete(fileSpec: String, roamingType: RoamingType) {
    for (provider in providers) {
      provider.deleteIfObsolete(fileSpec, roamingType)
    }
  }

  fun addStreamProvider(provider: StreamProvider, first: Boolean) {
    if (first) {
      providers.add(0, provider)
    }
    else {
      providers.add(provider)
    }
  }

  fun removeStreamProvider(aClass: Class<out StreamProvider>) {
    providers.removeAll(aClass::isInstance)
  }

  override fun getInstanceOf(aClass: Class<out StreamProvider>): StreamProvider = providers.first { aClass.isInstance(it) }
}