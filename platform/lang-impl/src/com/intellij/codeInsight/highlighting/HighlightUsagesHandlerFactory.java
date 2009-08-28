package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface HighlightUsagesHandlerFactory {
  ExtensionPointName<HighlightUsagesHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.highlightUsagesHandlerFactory");

  @Nullable
  HighlightUsagesHandlerBase createHighlightUsagesHandler(Editor editor, PsiFile file);
}
