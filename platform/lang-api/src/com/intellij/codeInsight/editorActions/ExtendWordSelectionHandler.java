package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.List;

public interface ExtendWordSelectionHandler {
  ExtensionPointName<ExtendWordSelectionHandler> EP_NAME = ExtensionPointName.create("com.intellij.extendWordSelectionHandler");
  
  boolean canSelect(PsiElement e);

  List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor);
}