// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.actions.CopyReferenceAction.COPY_REFERENCE_TARGETS
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.containers.nullize

internal val COPY_REFERENCE_TARGET_PROVIDER_EP: ExtensionPointName<CopyReferenceTargetProvider> =
  ExtensionPointName("com.intellij.copyReference.targetProvider")

internal val SYMBOL_COPY_REFERENCE_TARGET_PROVIDER_EP: ExtensionPointName<SymbolCopyReferenceTargetProvider> =
  ExtensionPointName("com.intellij.copyReference.symbolTargetProvider")

internal class CopyReferenceTargetsDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(COPY_REFERENCE_TARGETS) { data ->
      val project = data[CommonDataKeys.PROJECT] ?: return@lazyValue null

      val editorTargets = data[CommonDataKeys.EDITOR]?.let { editorCopyReferenceTargets(project, it, it.caretModel.allCarets) }
      if (!editorTargets.isNullOrEmpty()) {
        return@lazyValue editorTargets
      }

      val symbolTargets = data[CommonDataKeys.SYMBOLS]?.let { symbolCopyReferenceTargets(project, it) }
      if (!symbolTargets.isNullOrEmpty()) {
        return@lazyValue symbolTargets
      }

      return@lazyValue null
    }
  }
}

private fun symbolCopyReferenceTargets(project: Project, symbols: List<Symbol>): List<CopyReferenceTarget> =
  symbols.flatMapTo(LinkedHashSet()) { symbolCopyReferenceTargets(project, it) }.toList()

private fun symbolCopyReferenceTargets(project: Project, symbol: Symbol): List<CopyReferenceTarget> {
  val targets = SYMBOL_COPY_REFERENCE_TARGET_PROVIDER_EP.computeSafeIfAny { it.copyReferenceTargets(project, symbol).nullize() }
  if (!targets.isNullOrEmpty()) {
    return targets
  }

  if (symbol is CopyReferenceSymbol) {
    return listOf(symbol.getCopyReferenceTarget(project))
  }

  return emptyList()
}

private fun editorCopyReferenceTargets(project: Project, editor: Editor, carets: Collection<Caret>): List<CopyReferenceTarget> {
  val lookup = LookupManager.getActiveLookup(editor)
  if (lookup != null) {
    val lookupElement = lookup.currentItem ?: return emptyList()
    return lookupElementCopyReferenceTargets(project, lookupElement)
  }

  return carets.flatMapTo(LinkedHashSet()) { editorCopyReferenceTargets(project, it) }.toList()
}

private fun editorCopyReferenceTargets(project: Project, caret: Caret): List<CopyReferenceTarget> {
  val file = PsiUtilBase.getPsiFileInEditor(caret, project) ?: return emptyList()

  return COPY_REFERENCE_TARGET_PROVIDER_EP.computeSafeIfAny { it.copyReferenceTargets(file, caret.offset).nullize() } ?: emptyList()
}

private fun lookupElementCopyReferenceTargets(project: Project, lookupElement: LookupElement): List<CopyReferenceTarget> {
  val obj = (lookupElement.`object` as? Pointer<*>)?.dereference()
  if (obj is Symbol) {
    return symbolCopyReferenceTargets(project, obj)
  }

  return emptyList()
}
