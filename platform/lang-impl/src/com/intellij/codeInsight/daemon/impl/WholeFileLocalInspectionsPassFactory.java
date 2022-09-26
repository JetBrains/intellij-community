// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class WholeFileLocalInspectionsPassFactory implements MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    return createPass(file, TextRange.EMPTY_RANGE, document);
  }

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    // can run in the same time with LIP, but should start after it, since I believe whole-file inspections would run longer
    registrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.LOCAL_INSPECTIONS}, true, Pass.WHOLE_FILE_LOCAL_INSPECTIONS);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    WholeFileLocalInspectionPassTracker tracker = WholeFileLocalInspectionPassTracker.getInstance(file.getProject());
    if (!tracker.isChanged(file)) {
      return null;
    }

    if (!ProblemHighlightFilter.shouldHighlightFile(file)) {
      return null;
     }
    if (tracker.canSkipFile(file)) {
      return null;
    }
    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
    return createPass(file, visibleRange, editor.getDocument());
  }

  @NotNull
  private LocalInspectionsPass createPass(@NotNull PsiFile file, @NotNull TextRange visibleRange, @NotNull Document document) {
    return new LocalInspectionsPass(file, document, 0, file.getTextLength(), visibleRange, true,
                                    new DefaultHighlightInfoProcessor(), false) {
      @Override
      protected boolean isAcceptableLocalTool(@NotNull LocalInspectionToolWrapper wrapper) {
        return wrapper.runForWholeFile();
      }

      @NotNull
      @Override
      List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
        List<LocalInspectionToolWrapper> result = super.getInspectionTools(profile);
        if (result.isEmpty()) {
          WholeFileLocalInspectionPassTracker tracker = WholeFileLocalInspectionPassTracker.getInstance(file.getProject());
          tracker.lookThereAreNoWholeFileToolsConfiguredForThisFileSoWeCanProbablySkipItAltogether(file);
        }
        return result;
      }

      @Override
      protected String getPresentableName() {
        return DaemonBundle.message("pass.whole.inspections");
      }

      @Override
      protected void applyInformationWithProgress() {
        super.applyInformationWithProgress();
        WholeFileLocalInspectionPassTracker.getInstance(file.getProject()).informationApplied(file);
      }
    };
  }
}
