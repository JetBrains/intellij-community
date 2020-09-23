// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.symbol

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.search.SearchService
import com.intellij.usages.Usage

/**
 * To delegate [Usage] search to [reference search][SearchService.searchPsiSymbolReferences], either:
 * - implement [SymbolSearchTarget] in a [SearchTarget]
 * - implement [SearchTarget] in a [Symbol]
 */
interface SymbolSearchTarget : SearchTarget {

  override fun createPointer(): Pointer<out SymbolSearchTarget>

  val symbol: Symbol
}
