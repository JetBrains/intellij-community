// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope

internal data class SearchWordRequest internal constructor(
  internal val word: String,
  internal val searchScope: SearchScope,
  internal val caseSensitive: Boolean,
  internal val searchContext: Short,
  internal val containerName: String?
) {

  fun shouldProcessInjectedPsi(): Boolean = searchScope !is LocalSearchScope || !searchScope.isIgnoreInjectedPsi
}
