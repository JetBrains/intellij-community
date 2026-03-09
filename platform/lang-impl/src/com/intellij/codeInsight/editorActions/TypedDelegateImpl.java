// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.TypedEvent;
import com.intellij.codeInsight.completion.TypedEvent.TypedHandlerPhase;
import com.intellij.codeInsight.editorActions.TypedHandler.TypedDelegateFunc;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.TypingEventsLogger;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.RuntimeFlagsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


/**
 * <p> fireNewTypingStarted
 * <p> fireCheckAutoPopup
 * <p> fireBeforeSelectionRemoved
 * <p> fireBeforeCharTyped
 * <p> fireBeforeClosingQuoteInserted
 * <p> fireCharTyped
 * <p> fireBeforeClosingParenInserted
 */
final class TypedDelegateImpl {
  private static final Logger LOG = Logger.getInstance(TypedDelegateImpl.class);

  void resetCompletionPhase(@NotNull Editor editor) {
    editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, null);
  }

  void fireNewTypingStarted(
    @NotNull Editor originalEditor,
    @NotNull DataContext dataContext,
    char charTyped
  ) {
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      delegate.newTypingStarted(charTyped, originalEditor, dataContext);
    }
  }

  boolean fireCheckAutoPopup(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    setCompletionPhase(editor, TypedHandlerPhase.CHECK_AUTO_POPUP, charTyped);
    boolean handled = callDelegates(
      TypedHandlerDelegate::checkAutoPopup,
      charTyped,
      project,
      editor,
      file
    );
    if (!handled) {
      setCompletionPhase(editor, TypedHandlerPhase.AUTO_POPUP, charTyped);
    }
    return handled;
  }

  boolean fireBeforeSelectionRemoved(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    setCompletionPhase(editor, TypedHandlerPhase.BEFORE_SELECTION_REMOVED, charTyped);
    return callDelegates(
      TypedHandlerDelegate::beforeSelectionRemoved,
      charTyped,
      project,
      editor,
      file
    );
  }

  boolean fireBeforeCharTyped(
    @NotNull Project project,
    @NotNull FileType fileType,
    @NotNull PsiFile originalFile,
    @NotNull PsiFile file,
    @NotNull Editor originalEditor,
    @NotNull Editor editor,
    char charTyped
  ) {
    if (editor != originalEditor) {
      TypingEventsLogger.logTypedInInjected(project, originalFile, file);
    }
    setCompletionPhase(editor, TypedHandlerPhase.BEFORE_CHAR_TYPED, charTyped);
    TypedDelegateFunc beforeCharTyped =
      (delegate, c1, p1, e1, f1) -> delegate.beforeCharTyped(c1, p1, e1, f1, fileType);
    return callDelegates(beforeCharTyped, charTyped, project, editor, file);
  }

  boolean fireBeforeClosingQuoteInserted(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char quote,
    @NotNull CharSequence closingQuote
  ) {
    TypedDelegateFunc beforeClosingQuoteInserted =
      (delegate, c1, p1, e1, f1) -> delegate.beforeClosingQuoteInserted(closingQuote, p1, e1, f1);
    return callDelegates(beforeClosingQuoteInserted, quote, project, editor, file);
  }

  boolean fireCharTyped(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    setCompletionPhase(editor, TypedHandlerPhase.CHAR_TYPED, charTyped);
    return callDelegates(
      TypedHandlerDelegate::charTyped,
      charTyped,
      project,
      editor,
      file
    );
  }

  boolean fireBeforeClosingParenInserted(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    return callDelegates(
      TypedHandlerDelegate::beforeClosingParenInserted,
      charTyped,
      project,
      editor,
      file
    );
  }

  /**
   * @return true if any delegate requested a STOP
   */
  private static boolean callDelegates(
    @NotNull TypedDelegateFunc action,
    char charTyped,
    @NotNull Project project,
    @NotNull Editor editor,
    @NotNull PsiFile file
  ) {
    boolean lockFreeTyping = RuntimeFlagsKt.isEditorLockFreeTypingEnabled();
    boolean warned = false;
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      TypedHandlerDelegate.Result result;
      try {
        result = action.call(delegate, charTyped, project, editor, file);
      } catch (RuntimeException t) {
        if (lockFreeTyping) {
          result = TypedHandlerDelegate.Result.DEFAULT;
        } else {
          throw t;
        }
      }
      if (editor instanceof EditorWindow editorWindow && !editorWindow.isValid() && !warned) {
        LOG.warn(
          new IllegalStateException(
            ("%s has invalidated injected editor on typing char '%s'. " +
             "Please don't call commitDocument() there or other destructive methods")
              .formatted(delegate.getClass(), charTyped)
          )
        );
        warned = true;
      }
      if (result == TypedHandlerDelegate.Result.STOP) {
        return true;
      }
      if (result == TypedHandlerDelegate.Result.DEFAULT) {
        break;
      }
    }
    return false;
  }

  private static void setCompletionPhase(
    @NotNull Editor editor,
    @NotNull TypedHandlerPhase phase,
    char charTyped
  ) {
    TypedEvent event = new TypedEvent(charTyped, editor.getCaretModel().getOffset(), phase);
    editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, event);
  }
}
