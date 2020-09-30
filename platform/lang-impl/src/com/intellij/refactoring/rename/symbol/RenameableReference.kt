// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.symbol

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.refactoring.rename.api.RenameUsage

/**
 * If the [RenameUsage] search is [delegated][SymbolRenameTarget] to the symbol reference search,
 * then to provide a [RenameUsage] by a [PsiSymbolReference], either:
 * - implement [ReferenceRenameUsageFactory] and register as `com.intellij.rename.referenceRenameUsageFactory` extension
 * - implement [RenameableReference] in a [PsiSymbolReference] to provide rename target for the reference
 * - implement [RenameUsage] in a [PsiSymbolReference]
 */
interface RenameableReference : PsiSymbolReference {

  val renameUsage: RenameUsage
}
