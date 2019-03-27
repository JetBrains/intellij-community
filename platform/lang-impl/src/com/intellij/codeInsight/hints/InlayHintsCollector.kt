// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.DocumentUtil
import com.intellij.util.SmartList
import gnu.trove.TIntObjectHashMap


/**
 * Collects inlays in the given file.
 * The contract is that after collect always follows apply call.
 * Object of this class can be used multiple times.
 * For per element collection see [TraversingInlayHintsCollector] and [FactoryInlayHintsCollector]
 */
interface InlayHintsCollector<T : Any> {
  /**
   * Collect hints into some collection stored in this collector
   * Implementors must handle dumb mode themselves.
   * @param isEnabled provider is enabled
   */
  fun collect(element: PsiElement, editor: Editor, settings: T, isEnabled: Boolean)

  /**
   * Apply all collected hints to editor and clear hints collection.
   * To apply hints it is required to use [model], because it take care of setting corresponding listeners, that will cause repaint of inlay.
   */
  fun apply(element: PsiElement, editor: Editor, model: InlayModelWrapper, settings: T)

  /**
   * Settings key of corresponding [InlayHintsProvider]
   */
  val key: SettingsKey<T>
}

/**
 * Traverses all the file and for each element calls collect
 */
abstract class TraversingInlayHintsCollector<T : Any, R: EditorCustomElementRenderer>(override val key: SettingsKey<T>) : InlayHintsCollector<T> {
  private val hints = TIntObjectHashMap<SmartList<R>>()

  final override fun collect(element: PsiElement, editor: Editor, settings: T, isEnabled: Boolean) {
    val traverser = SyntaxTraverser.psiTraverser(element)
    val sink = object: InlayHintsSink<R> {
      override fun addInlay(offset: Int, relatesToPrecedingText: Boolean, renderer: R) {
        val inlaysAtOffset = hints[offset]
        if (inlaysAtOffset != null) {
          inlaysAtOffset.add(renderer)
        } else {
          hints.put(offset, SmartList(renderer))
        }
      }
    }
    if (isEnabled) {
      traverser.forEach {
        collectHints(it, editor, sink, settings)
      }
    }
  }

  final override fun apply(element: PsiElement, editor: Editor, model: InlayModelWrapper, settings: T) {
    val existingInlays = model.getInlineElementsInRange(element.textRange.startOffset + 1, element.textRange.endOffset - 1)
    val isBulkChange = existingInlays.size + hints.size() > BulkChangeThreshold

    DocumentUtil.executeInBulk(editor.document, isBulkChange) {
      for (inlay in existingInlays) {
        val inlayKey = inlay.getUserData<Any?>(INLAY_KEY) as SettingsKey<*>?
        if (inlayKey != key) continue
        Disposer.dispose(inlay)
      }
      hints.forEachEntry { offset, inlayInfos ->
        inlayInfos.forEach {
          val inlay = model.addInlineElement(offset, it) ?: return@forEach
          inlay.putUserData(INLAY_KEY, key)
        }
        true
      }
    }
  }

  companion object {
    private val INLAY_KEY: Key<SettingsKey<*>> = Key.create<SettingsKey<*>>("INLAY_KEY")
    private const val BulkChangeThreshold = 1000
  }

  abstract fun collectHints(element: PsiElement, editor: Editor, sink: InlayHintsSink<R>, settings: T)
}

/**
 * Convenience class for common case of creating hints using [PresentationFactory]
 */
abstract class FactoryInlayHintsCollector<T : Any>(editor: Editor, key: SettingsKey<T>) : TraversingInlayHintsCollector<T, PresentationRenderer>(key) {
  val factory = PresentationFactory(editor as EditorImpl)

  final override fun collectHints(element: PsiElement, editor: Editor, sink: InlayHintsSink<PresentationRenderer>, settings: T) {
    collectHints(element, sink, settings, factory)
  }

  abstract fun collectHints(element: PsiElement, sink: InlayHintsSink<PresentationRenderer>, settings: T, factory: PresentationFactory)
}