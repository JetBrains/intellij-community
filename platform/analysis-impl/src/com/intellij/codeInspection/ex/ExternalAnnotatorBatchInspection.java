/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ExternalAnnotatorInspectionVisitor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public interface ExternalAnnotatorBatchInspection extends PairedUnfairLocalInspectionTool {
  @NotNull
  String getShortName();

  @NotNull
  @Override
  default String getInspectionForBatchShortName() {
    return getShortName();
  }

  /**
   * To be invoked during batch run
   */
  @NotNull
  default ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                        @NotNull GlobalInspectionContext context,
                                        @NotNull InspectionManager manager) {
    final String shortName = getShortName();
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    for (Language language : relevantLanguages) {
      PsiFile psiRoot = ReadAction.compute(() -> viewProvider.getPsi(language));
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, psiRoot);

      for (ExternalAnnotator annotator : externalAnnotators) {
        if (shortName.equals(annotator.getPairedBatchInspectionShortName())) {
          return ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator(file, manager, false, annotator);
        }
      }
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }
}
