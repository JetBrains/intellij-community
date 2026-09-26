// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.indexing.FileBasedIndex

/**
 * Forces [TrigramIndex] re-indexing after [TrigramIndexFilter.ENABLE_EXTENSION_EXCLUDES_REGISTRY_KEY] registry value is changed.
 *
 * The actual index(ing) behavior will change **only after IDE restart**, because changing the indexing filter on top of
 * already existing index is hard to make sound -- basically, the simplest method to make index consistent in this case
 * is to just rebuild it entirely, which is, in turn, safer to do on IDE restart.
 * This is why [TrigramIndexFilter.ENABLE_EXTENSION_EXCLUDES] is intentionally made not mutable, and the registry key
 * is tagged with 'required restart'
 */
internal class TrigramIndexRegistryValueListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (TrigramIndexFilter.ENABLE_EXTENSION_EXCLUDES_REGISTRY_KEY == value.key) {
      FileBasedIndex.getInstance().requestRebuild(TrigramIndex.INDEX_ID)
    }
  }
}