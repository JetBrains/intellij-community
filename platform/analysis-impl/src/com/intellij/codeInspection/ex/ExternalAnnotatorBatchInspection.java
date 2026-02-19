// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ExternalAnnotatorInspectionVisitor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ExternalAnnotatorBatchInspection extends PairedUnfairLocalInspectionTool {
  @NotNull
  String getShortName();

  @Override
  default @NotNull String getInspectionForBatchShortName() {
    return getShortName();
  }

  /**
   * To be invoked during batch run
   */
  default ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                  @NotNull GlobalInspectionContext context,
                                                  @NotNull InspectionManager manager) {
    String shortName = getShortName();
    FileViewProvider viewProvider = file.getViewProvider();

    for (PsiFile psiRoot : ReadAction.compute(() -> viewProvider.getAllFiles())) {
      List<ExternalAnnotator<?,?>> externalAnnotators = ReadAction.compute(() -> ExternalLanguageAnnotators.allForFile(psiRoot.getLanguage(), psiRoot));
      for (ExternalAnnotator<?,?> annotator : externalAnnotators) {
        if (shortName.equals(annotator.getPairedBatchInspectionShortName())) {
          return ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator(file, manager, false, annotator);
        }
      }
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }
}
