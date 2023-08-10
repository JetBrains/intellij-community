// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.symbol

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Symbol

/**
 * To provide a [SearchTarget] by a Symbol, either:
 * - implement [SymbolSearchTargetFactory] and register as `com.intellij.lang.symbolSearchTarget` extension
 * - implement [SearchTargetSymbol] in a Symbol to provide search target for the symbol
 * - implement [SearchTarget] in a Symbol
 */
interface SearchTargetSymbol : Symbol {

  val searchTarget: SearchTarget
}
