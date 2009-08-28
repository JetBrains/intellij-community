package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public interface CopyPastePostProcessor {
  ExtensionPointName<CopyPastePostProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePostProcessor");

  TextBlockTransferableData collectTransferableData(final PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets);

  @Nullable
  TextBlockTransferableData extractTransferableData(final Transferable content);

  void processTransferableData(final Project project, final Editor editor, final RangeMarker bounds, final TextBlockTransferableData value);
}
