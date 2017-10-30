/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;

public abstract class PerformFixesModalTask implements SequentialTask {
  @NotNull
  protected final Project myProject;
  private final CommonProblemDescriptor[] myDescriptors;
  private final PsiDocumentManager myDocumentManager;
  private int myCount = 0;

  public PerformFixesModalTask(@NotNull Project project,
                               @NotNull CommonProblemDescriptor[] descriptors) {
    myProject = project;
    myDescriptors = descriptors;
    myDocumentManager = PsiDocumentManager.getInstance(myProject);
  }

  @Override
  public void prepare() {
  }

  @Override
  public boolean isDone() {
    return myCount > myDescriptors.length - 1;
  }

  @Override
  public boolean iteration() {
    return true;
  }

  public void doRun(ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    while (!isDone()) {
      if (indicator.isCanceled()) {
        break;
      }
      iteration(indicator);
    }
  }

  public boolean iteration(ProgressIndicator indicator) {
    final CommonProblemDescriptor descriptor = myDescriptors[myCount++];
    if (indicator != null) {
      indicator.setFraction((double)myCount / myDescriptors.length);
      String presentableText = "usages";
      if (descriptor instanceof ProblemDescriptor) {
        final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
        if (psiElement != null) {
          presentableText = SymbolPresentationUtil.getSymbolPresentableText(psiElement);
        }
      }
      indicator.setText("Processing " + presentableText);
    }

    final boolean[] runInReadAction = {false};
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null) {
      for (QuickFix fix : fixes) {
        if (!fix.startInWriteAction()) {
          runInReadAction[0] = true;
        } else {
          runInReadAction[0] = false;
          break;
        }
      }
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      myDocumentManager.commitAllDocuments();
      if (!runInReadAction[0]) {
        applyFix(myProject, descriptor);
      }
    });
    if (runInReadAction[0]) {
      applyFix(myProject, descriptor);
    }
    return isDone();
  }

  @Override
  public void stop() {}
  
  protected abstract void applyFix(Project project, CommonProblemDescriptor descriptor);
}
