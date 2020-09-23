// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.symbol

import com.intellij.model.Symbol
import com.intellij.refactoring.rename.api.RenameTarget

/**
 * To provide a [RenameTarget] by a Symbol, either:
 * - implement [SymbolRenameTargetFactory] and register as `com.intellij.rename.symbolRenameTargetFactory` extension
 * - implement [RenameableSymbol] in a Symbol to provide rename target for the symbol
 * - implement [RenameTarget] in a Symbol
 */
interface RenameableSymbol : Symbol {

  val renameTarget: RenameTarget
}
