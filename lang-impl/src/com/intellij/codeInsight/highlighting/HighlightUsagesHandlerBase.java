package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public abstract class HighlightUsagesHandlerBase {
  protected final Editor myEditor;
  protected final PsiFile myFile;

  protected HighlightUsagesHandlerBase(final Editor editor, final PsiFile file) {
    myEditor = editor;
    myFile = file;
  }

  public abstract void highlightUsages();
}
