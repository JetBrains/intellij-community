// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

/**
 * An extension to collect and apply additional transferable data when performing copy-paste in editors.<p/>
 */
public abstract class CopyPastePostProcessor<T extends TextBlockTransferableData> {
  public static final ExtensionPointName<CopyPastePostProcessor<? extends TextBlockTransferableData>> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePostProcessor");

  /**
   * This method will be run in the dispatch thread with alternative resolve enabled
   */
  @NotNull
  public abstract List<T> collectTransferableData(final PsiFile file,
                                                  final Editor editor,
                                                  final int[] startOffsets,
                                                  final int[] endOffsets);

  @NotNull
  public List<T> extractTransferableData(final Transferable content) {
    return Collections.emptyList();
  }

  public void processTransferableData(final Project project, final Editor editor, final RangeMarker bounds, int caretOffset,
                                      Ref<? super Boolean> indented, final List<? extends T> values) {
  }

  //For performance optimization implementations can return false in case when they dont have access to any other documents(psi file)
  // except current one
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return true;
  }
}
