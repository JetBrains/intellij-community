// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class MethodSignatureComponent extends EditorTextField {
  public MethodSignatureComponent(String signature, Project project, FileType filetype) {
    super(createNonUndoableDocument(signature), project, filetype, true, false);
    setFont(EditorFontType.getGlobalPlainFont());
    setBackground(EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.CARET_ROW_COLOR));
  }

  private static Document createNonUndoableDocument(String text) {
    Document document = EditorFactory.getInstance().createDocument(text);
    UndoUtil.disableUndoFor(document);
    return document;
  }

  public void setSignature(String signature) {
    setText(signature);
    final EditorEx editor = (EditorEx)getEditor();
    if (editor != null) {
      editor.getScrollingModel().scrollVertically(0);
      editor.getScrollingModel().scrollHorizontally(0);
    }
  }

  @Override
  protected @NotNull EditorEx createEditor() {
    EditorEx editor = super.createEditor();
    final String fileName = getFileName();
    if (fileName != null) {
      editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(getProject(), fileName));
    }
    editor.getSettings().setWhitespacesShown(false);
    editor.setHorizontalScrollbarVisible(true);
    editor.setVerticalScrollbarVisible(true);
    setupBorder(editor);
    return editor;
  }

  protected @Nullable String getFileName() {
    return null;
  }
}
