// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Handler, extending IDE behaviour on typing in editor.
 * <p>
 * Note that {@code PsiFile} passed to handler's methods isn't guaranteed to be in sync with the document at the time of invocation, for performance reasons.
 * Heavy/expensive methods should not be called there because they would lead to freezes on typing.
 * For example, {@link com.intellij.psi.PsiDocumentManager#commitDocument(Document)} should be avoided, if possible.
 */
public abstract class TypedHandlerDelegate {
  public static final ExtensionPointName<TypedHandlerDelegate> EP_NAME = new ExtensionPointName<>("com.intellij.typedHandler");

  /**
   * If the specified character triggers auto-popup, schedules the auto-popup appearance. This method is called even
   * in overwrite mode, when the rest of typed handler delegate methods are not called. It is invoked only for the primary caret.
   */
  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before selected text is deleted.
   * This method is supposed to be overridden by handlers having custom behaviour with respect to selection.
   * This method is called for each caret individually.
   */
  public @NotNull Result beforeSelectionRemoved(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before starting processing the user input. It can be used to determine individual character input because other methods
   *   may be called multiple times (for each caret individually).
   * This method is called once regardless how many carets exist in the editor.
   */
  public void newTypingStarted(char c, @NotNull Editor editor, @NotNull DataContext context) {
  }

  /**
   * Called before the specified character typed by the user is inserted in the editor.
   * This method is called for each caret individually.
   */
  public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    return Result.CONTINUE;
  }

  /**
   * Called after the specified character typed by the user has been inserted in the editor.
   * This method is called for each caret individually.
   */
  public @NotNull Result charTyped(char c, @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before IDE automatically inserts the specified closing bracket in the editor.
   * This method is called for each caret individually.
   * <p>
   * If {@link Result#STOP} is returned, no closing paren will be inserted.
   */
  public @NotNull Result beforeClosingParenInserted(char c, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before IDE automatically inserts the specified closing quote in the editor.
   * This method is called for each caret individually.
   * <p>
   * If {@link Result#STOP} is returned, no closing quote will be inserted.
   */
  public @NotNull Result beforeClosingQuoteInserted(@NotNull CharSequence quote, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  public boolean isImmediatePaintingEnabled(@NotNull Editor editor, char c, @NotNull DataContext context) {
    return true;
  }

  public enum Result {
    STOP,
    CONTINUE,
    DEFAULT
  }
}
