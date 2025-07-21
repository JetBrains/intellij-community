// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

public abstract class ElementProcessingHintPass(
  private val rootElement: PsiElement,
  editor: Editor,
  private val modificationStampHolder: ModificationStampHolder
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true) {
  private val hints = Int2ObjectOpenHashMap<SmartList<String>>()

  override fun doCollectInformation(progress: ProgressIndicator) {
    hints.clear()

    val virtualFile = rootElement.containingFile?.originalFile?.virtualFile ?: return

    if (isAvailable(virtualFile)) {
      val traverser = SyntaxTraverser.psiTraverser(rootElement)
      traverser.forEach { collectElementHints(it
      ) { offset, hint ->
        var hintList = hints.get(offset)
        if (hintList == null) {
          hintList = SmartList()
          hints.put(offset, hintList)
        }
        hintList.add(hint)
      }
      }
    }
  }

  override fun doApplyInformationToEditor() {
    EditorScrollingPositionKeeper.perform(myEditor, false) {
      applyHintsToEditor()
    }

    if (rootElement === myFile) {
      modificationStampHolder.putCurrentModificationStamp(myEditor, myFile)
    }
  }

  /**
   * Returns true if this pass should be applied for current [virtualFile]
   */
  public abstract fun isAvailable(virtualFile: VirtualFile): Boolean

  /**
   * For current [element] collect hints information if it is possible
   */
  public abstract fun collectElementHints(element: PsiElement, collector: (offset: Int, hint : String) -> Unit)

  /**
   * Returns key marking inlay as created by this pass
   */
  public abstract fun getHintKey(): Key<Boolean>

  /**
   * Creates inlay renderer for hints created by this pass
   */
  public abstract fun createRenderer(text: String): HintRenderer

  private fun applyHintsToEditor() {
    val inlayModel = myEditor.inlayModel

    val toRemove = inlayModel.getInlineElementsInRange(rootElement.textRange.startOffset + 1, rootElement.textRange.endOffset - 1)
      .filter {
        if (getHintKey().isIn(it)) {
          val hintsList = hints.get(it.offset)
          hintsList == null || !hintsList.removeAll { hintText: String -> hintText == (it.renderer as HintRenderer).text }
        }
        else false
      }

    myEditor.inlayModel.execute(toRemove.size + hints.values.flatMap { it as SmartList<*> }.count() > 1000) {
      toRemove.forEach { Disposer.dispose(it) }
      for (hint in hints) {
        val offset = hint.key
        for (it in hint.value) {
          inlayModel.addInlineElement(offset, createRenderer(it))?.putUserData(getHintKey(), true)
        }
      }
    }
  }
}

public class ModificationStampHolder(private val key: Key<Long>) {
  public fun putCurrentModificationStamp(editor: Editor, file: PsiFile) {
    editor.putUserData(key, ParameterHintsPassFactory.getCurrentModificationStamp(file))
  }

  private fun forceHintsUpdateOnNextPass(editor: Editor) {
    editor.putUserData(key, null)
  }

  public fun forceHintsUpdateOnNextPass() {
    EditorFactory.getInstance().allEditors.forEach { forceHintsUpdateOnNextPass(it) }
  }

  public fun isNotChanged(editor: Editor, file: PsiFile): Boolean {
    return key.get(editor, 0) == ParameterHintsPassFactory.getCurrentModificationStamp(file)
  }
}