package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface CopyPastePreProcessor {
  ExtensionPointName<CopyPastePreProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePreProcessor");

  String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, String text);
  String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText);
}
