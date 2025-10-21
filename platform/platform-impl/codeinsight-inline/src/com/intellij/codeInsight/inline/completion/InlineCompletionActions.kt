// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.editor.InlineCompletionEditorType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import org.jetbrains.annotations.ApiStatus

class InsertInlineCompletionAction : EditorAction(InsertInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class InsertInlineCompletionHandler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      InlineCompletion.getHandlerOrNull(editor)?.insert()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return InlineCompletionContext.getOrNull(editor)?.startOffset() == caret.offset
             && InlineCompletionEditorType.get(editor) != InlineCompletionEditorType.TERMINAL
    }
  }
}

@ApiStatus.Internal
abstract class SwitchInlineCompletionVariantAction protected constructor(
  direction: Direction
) : EditorAction(Handler(direction)), HintManagerImpl.ActionToIgnore {

  class Next : SwitchInlineCompletionVariantAction(Direction.Next)

  class Prev : SwitchInlineCompletionVariantAction(Direction.Prev)

  private class Handler(private val direction: Direction) : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      val session = InlineCompletionSession.getOrNull(editor) ?: return
      when (direction) {
        Direction.Next -> session.useNextVariant()
        Direction.Prev -> session.usePrevVariant()
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      val context = InlineCompletionContext.getOrNull(editor)
      return context != null && !context.isDisposed && context.startOffset() == caret.offset
    }
  }

  protected enum class Direction {
    Next,
    Prev
  }
}

@ApiStatus.Internal
abstract class CancellationKeyInlineCompletionHandler(val originalHandler: EditorActionHandler,
                                                      val finishType: FinishType) : EditorActionHandler() {
  public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val context = InlineCompletionContext.getOrNull(editor) ?: run {
      if (originalHandler.isEnabled(editor, caret, dataContext)) {
        originalHandler.execute(editor, caret, dataContext)
      }
      return
    }
    InlineCompletion.getHandlerOrNull(editor)?.hide(context, finishType)

    if (originalHandler.isEnabled(editor, caret, dataContext)) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    if (InlineCompletionContext.getOrNull(editor) != null) {
      return true
    }

    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}

@ApiStatus.Internal
class EscapeInlineCompletionHandler(originalHandler: EditorActionHandler) :
  CancellationKeyInlineCompletionHandler(originalHandler, FinishType.ESCAPE_PRESSED)

@ApiStatus.Internal
class BackspaceDeleteInlineCompletionHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {

  private fun invokeOriginalHandler(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (originalHandler.isEnabled(editor, caret, dataContext)) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return originalHandler.isEnabled(editor, caret, dataContext)
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val handler = InlineCompletion.getHandlerOrNull(editor)
    if (handler == null) {
      invokeOriginalHandler(editor, caret, dataContext)
      return
    }

    InlineCompletionSession.getOrNull(editor)?.let { session ->
      handler.hide(session.context, FinishType.BACKSPACE_PRESSED)
    }

    if (editor.caretModel.caretCount != 1) {
      invokeOriginalHandler(editor, caret, dataContext)
      return
    }

    val initialModificationStamp = editor.document.modificationStamp
    invokeOriginalHandler(editor, caret, dataContext)
    if (editor.document.modificationStamp == initialModificationStamp) {
      return
    }

    handler.invokeEvent(InlineCompletionEvent.Backspace(editor))
  }
}

@ApiStatus.Internal
class CallInlineCompletionAction : EditorAction(CallInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {

  class CallInlineCompletionHandler : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val curCaret = caret ?: editor.caretModel.currentCaret

      val listener = InlineCompletion.getHandlerOrNull(editor) ?: return
      listener.invoke(InlineCompletionEvent.DirectCall(editor, curCaret, dataContext))
    }
  }
}

@ApiStatus.Experimental
@ApiStatus.Internal
class InsertInlineCompletionWordAction : EditorAction(Handler()), HintManagerImpl.ActionToIgnore {
  private class Handler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      // TODO call insert if possible
      if (InlineCompletionSession.getOrNull(editor) != null) {
        val event = InlineCompletionEvent.InsertNextWord(editor)
        InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(event)
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return InlineCompletionContext.getOrNull(editor)?.startOffset() == caret.offset
    }
  }
}

@ApiStatus.Experimental
@ApiStatus.Internal
class InsertInlineCompletionLineAction : EditorAction(Handler()), HintManagerImpl.ActionToIgnore {
  private class Handler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
      val session = InlineCompletionSession.getOrNull(editor) ?: return
      if (!session.context.textToInsert().any { it == '\n' }) {
        handler.insert()
      }
      else {
        handler.invokeEvent(InlineCompletionEvent.InsertNextLine(editor))
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return InlineCompletionContext.getOrNull(editor)?.startOffset() == caret.offset
    }
  }
}
