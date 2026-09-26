// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search

/**
 * An extension allowing plugins to provide the data at runtime for the setting search to work on.
 */
abstract class SearchableOptionContributor {
  open suspend fun contribute(processor: SearchableOptionProcessor) {
    processOptions(processor)
  }

  open fun processOptions(processor: SearchableOptionProcessor) {
    throw AbstractMethodError()
  }
}
