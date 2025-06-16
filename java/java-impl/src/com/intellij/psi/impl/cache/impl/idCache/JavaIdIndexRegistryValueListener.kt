// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.util.indexing.FileBasedIndex

/**
 * Forces [IdIndex] re-indexing after [JavaIdIndexer.SKIP_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY] registry value is changed.
 *
 * The actual index(ing) behavior will change **only after IDE restart**, because changing the indexing filter on top of
 * already existing index is hard to make sound -- basically, the simplest method to make index consistent in this case
 * is to just rebuild it entirely, which is, in turn, safer to do on IDE restart.
 * This is why [JavaIdIndexer.skipSourceFilesInLibraries] is intentionally made immutable, and the registry key is tagged
 * with 'required restart'
 */
internal class JavaIdIndexRegistryValueListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (JavaIdIndexer.SKIP_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY == value.key) {
      FileBasedIndex.getInstance().requestRebuild(IdIndex.NAME)
    }
  }
}