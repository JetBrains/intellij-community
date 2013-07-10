/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final InspectionToolWrapper myToolWrapper;
  private final Class myQuickfixClass;

  public CleanupInspectionIntention(@NotNull InspectionToolWrapper toolWrapper, Class quickFixClass) {
    myToolWrapper = toolWrapper;
    myQuickfixClass = quickFixClass;
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myToolWrapper.getDisplayName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    final List<ProblemDescriptor> descriptions =
      ProgressManager.getInstance().runProcess(new Computable<List<ProblemDescriptor>>() {
        @Override
        public List<ProblemDescriptor> compute() {
          InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(project);
          return InspectionEngine.runInspectionOnFile(file, myToolWrapper, inspectionManager.createNewGlobalContext(false));
        }
      }, new EmptyProgressIndicator());

    Collections.sort(descriptions, new Comparator<CommonProblemDescriptor>() {
      @Override
      public int compare(final CommonProblemDescriptor o1, final CommonProblemDescriptor o2) {
        final ProblemDescriptorBase d1 = (ProblemDescriptorBase)o1;
        final ProblemDescriptorBase d2 = (ProblemDescriptorBase)o2;
        return d2.getTextRange().getStartOffset() - d1.getTextRange().getStartOffset();
      }
    });
    boolean fixesWereAvailable = false;
    for (ProblemDescriptor descriptor : descriptions) {
      final QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        fixesWereAvailable = true;
        for (QuickFix<CommonProblemDescriptor> fix : fixes) {
          if (fix != null && fix.getClass().isAssignableFrom(myQuickfixClass)) {
            final PsiElement element = descriptor.getPsiElement();
            if (element != null && element.isValid()) {
              fix.applyFix(project, descriptor);
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            }
            break;
          }
        }
      }
    }
    if (!fixesWereAvailable) {
      CommonRefactoringUtil.showErrorHint(project, editor, "No fixes are available in batch mode", CommonBundle.getWarningTitle(), null);
    }
  }




  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myQuickfixClass != null && myQuickfixClass != EmptyIntentionAction.class && !(myToolWrapper instanceof LocalInspectionToolWrapper &&
                                                                                         ((LocalInspectionToolWrapper)myToolWrapper).isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
