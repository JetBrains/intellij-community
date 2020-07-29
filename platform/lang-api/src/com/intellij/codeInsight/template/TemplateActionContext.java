// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes context in which live template supposed to be used.
 */
public final class TemplateActionContext {
  @NotNull
  private final PsiFile myFile;
  @Nullable
  private final Editor myEditor;

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myIsSurrounding;

  private TemplateActionContext(@NotNull PsiFile file,
                                @Nullable Editor editor,
                                int startOffset,
                                int endOffset,
                                boolean isSurrounding) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myIsSurrounding = isSurrounding;
    myEditor = editor;
  }

  public @NotNull PsiFile getFile() {
    return myFile;
  }

  /**
   * @return editor if file is currently opened in one. Sometimes context may be used with fake files, without any editors
   */
  @ApiStatus.Internal
  public @Nullable Editor getEditor() {
    return myEditor;
  }

  /**
   * @return true iff {@code surround with} action is performed
   */
  public boolean isSurrounding() {
    return myIsSurrounding;
  }

  /**
   * @return a copy of current context with specific {@code file}
   */
  public @NotNull TemplateActionContext withFile(@NotNull PsiFile file) {
    return new TemplateActionContext(file, myEditor, myStartOffset, myEndOffset, myIsSurrounding);
  }

  /**
   * @return for surround context returns selection start or caret position if there is no selection or it is expanding context
   */
  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * @return for surround context returns selection end or caret position if there is no selection or it is expanding context
   */
  public int getEndOffset() {
    return myEndOffset;
  }

  public static @NotNull TemplateActionContext expanding(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    int editorOffset = editor.getCaretModel().getOffset();
    return create(psiFile, editor, editorOffset, editorOffset, false);
  }

  public static @NotNull TemplateActionContext expanding(@NotNull PsiFile psiFile, int offset) {
    return create(psiFile, null, offset, offset, false);
  }

  public static @NotNull TemplateActionContext surrounding(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    return create(psiFile, editor, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), true);
  }

  public static @NotNull TemplateActionContext create(@NotNull PsiFile psiFile,
                                                      @Nullable Editor editor,
                                                      int startOffset,
                                                      int endOffset,
                                                      boolean isSurrounding) {
    return new TemplateActionContext(psiFile, editor, startOffset, endOffset, isSurrounding);
  }
}
