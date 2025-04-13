// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.util.indexing.FileBasedIndex

class JavaIdIndexRegistryValueListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (JavaIdIndexer.INDEX_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY == value.key) {
      JavaIdIndexer.indexSourceFilesInLibraries = value.asBoolean()
      FileBasedIndex.getInstance().requestRebuild(IdIndex.NAME)
    }
  }
}