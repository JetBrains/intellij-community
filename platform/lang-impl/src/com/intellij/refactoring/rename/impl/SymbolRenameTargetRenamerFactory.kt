// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.Renamer
import com.intellij.refactoring.rename.RenamerFactory
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory

/**
 * [RenamerFactory] which follows the chain: [DataContext] -> [Symbol] -> [RenameTarget] -> [Renamer].
 */
class SymbolRenameTargetRenamerFactory : RenamerFactory {

  override fun createRenamers(dataContext: DataContext): Collection<Renamer> {
    val project: Project = dataContext.getData(CommonDataKeys.PROJECT)
                           ?: return emptyList()
    val symbols: Collection<Symbol> = targetSymbols(dataContext)
    if (symbols.isEmpty()) {
      return emptyList()
    }
    val allRenameTargets: Collection<RenameTarget> = symbols.mapNotNull { symbol: Symbol ->
      renameTarget(project, symbol)
    }
    val distinctRenameTargets: Collection<RenameTarget> = allRenameTargets.toSet()
    val editor: Editor? = dataContext.getData(CommonDataKeys.EDITOR)
    return distinctRenameTargets.map { target: RenameTarget ->
      RenameTargetRenamer(project, editor, target)
    }
  }

  private fun renameTarget(project: Project, symbol: Symbol): RenameTarget? {
    for (factory: SymbolRenameTargetFactory in SymbolRenameTargetFactory.EP_NAME.extensions) {
      return factory.renameTarget(project, symbol) ?: continue
    }
    if (symbol is RenameableSymbol) {
      return symbol.renameTarget
    }
    if (symbol is RenameTarget) {
      return symbol
    }
    return null
  }
}
