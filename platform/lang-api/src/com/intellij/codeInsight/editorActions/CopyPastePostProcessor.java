// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
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
  @RequiresEdt
  public abstract @NotNull List<T> collectTransferableData(@NotNull PsiFile file,
                                                  @NotNull Editor editor,
                                                  int @NotNull [] startOffsets,
                                                  int @NotNull [] endOffsets);

  public @NotNull List<T> extractTransferableData(@NotNull Transferable content) {
    return Collections.emptyList();
  }

  public void processTransferableData(@NotNull Project project, @NotNull Editor editor, @NotNull RangeMarker bounds, int caretOffset,
                                      @NotNull Ref<? super Boolean> indented, @NotNull List<? extends T> values) {
  }

  //For performance optimization implementations can return false in case when they dont have access to any other documents(psi file)
  // except current one
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return true;
  }
}
