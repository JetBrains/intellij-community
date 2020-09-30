// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.symbol

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.search.SearchService
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsage

/**
 * To delegate [RenameUsage] search to [reference search][SearchService.searchPsiSymbolReferences], either:
 * - implement [SymbolRenameTarget] in a [RenameTarget]
 * - implement [RenameTarget] in a [Symbol]
 *
 * To provide a [RenameUsage] by a discovered [PsiSymbolReference], either:
 * - implement [ReferenceRenameUsageFactory] and register as `com.intellij.rename.referenceRenameUsageFactory` extension
 * - implement [RenameableReference] in a [PsiSymbolReference] to provide rename target for the reference
 * - implement [RenameUsage] in a [PsiSymbolReference]
 */
interface SymbolRenameTarget : RenameTarget {

  override fun createPointer(): Pointer<out SymbolRenameTarget>

  val symbol: Symbol
}
