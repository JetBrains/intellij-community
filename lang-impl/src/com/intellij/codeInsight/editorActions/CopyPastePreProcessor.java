package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface CopyPastePreProcessor {
  ExtensionPointName<CopyPastePreProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePreProcessor");

  /**
   *
   * @param file
   * @param startOffsets
   * @param endOffsets
   * @param text
   * @return null if no preprocession is to be applied
   */
  @Nullable
  String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, String text);
  String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText);
}
