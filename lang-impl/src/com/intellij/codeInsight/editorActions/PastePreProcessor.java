package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface PastePreProcessor {
  ExtensionPointName<PastePreProcessor> EP_NAME = ExtensionPointName.create("com.intellij.pastePreProcessor");

  String preprocess(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText);
}
