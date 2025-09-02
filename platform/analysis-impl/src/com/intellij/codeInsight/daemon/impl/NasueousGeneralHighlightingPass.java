// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * GHP, which throws as soon as it found an error in the file
 * used by Wolf to find any diagnostic in the file, and stop
 */
@ApiStatus.Internal
final class NasueousGeneralHighlightingPass extends GeneralHighlightingPass {
  private final @NotNull AtomicReference<? super HighlightInfo> myError;

  NasueousGeneralHighlightingPass(@NotNull PsiFile psiFile,
                                  @NotNull Document document,
                                  @NotNull ProperTextRange visibleRange,
                                  @NotNull AtomicReference<? super HighlightInfo> error) {
    super(psiFile, document, 0, document.getTextLength(), false, visibleRange, null, true, true, true,
          HighlightInfoUpdater.EMPTY);
    myError = error;
  }

  @Override
  protected @NotNull HighlightInfoHolder createInfoHolder(@NotNull PsiFile psiFile) {
    return new HighlightInfoHolder(psiFile) {
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
          myError.set(info);
          throw new ProcessCanceledException();
        }
        return super.add(info);
      }
    };
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    try {
      super.collectInformationWithProgress(progress);
    }
    catch (Exception ignored) {
      // could throw PCE now
    }
  }
}
