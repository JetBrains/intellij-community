/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class InspectionManager {
  public static InspectionManager getInstance(Project project) {
    return project.getComponent(InspectionManager.class);
  }

  @NotNull public abstract CommonProblemDescriptor createProblemDescriptor(String descriptionTemplate, QuickFix... fixes);
  @NotNull public abstract RefManager getRefManager();
  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   * @param psiElement problem is reported against
   * @param descriptionTemplate problem message. Use <code>#ref</code> for a link to problem piece of code and <code>#loc</code> for location in source code.
   * @param fix should be null if no fix is provided.
   */
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix fix, ProblemHighlightType highlightType);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType, boolean isAfterEndOfLine);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement startElement,
                                                                     PsiElement endElement,
                                                                     String descriptionTemplate,
                                                                     ProblemHighlightType highlightType,
                                                                     LocalQuickFix... fixes
  );

  public abstract boolean isSuppressed(RefEntity entity, String inspectionToolId);
  public abstract boolean isSuppressed(PsiElement element, String inspectionToolId);

  @NotNull public abstract Project getProject();

  public interface DerivedClassesProcessor extends Processor<PsiClass> {
  }

  public interface DerivedMethodsProcessor extends Processor<PsiMethod> {
  }

  public interface UsagesProcessor extends Processor<PsiReference> {
  }

  protected interface Processor<T> {
    boolean process(T element);
  }

  public abstract void enqueueClassUsagesProcessing(RefClass refClass, UsagesProcessor p);

  public abstract void enqueueDerivedClassesProcessing(RefClass refClass, DerivedClassesProcessor p);

  public abstract void enqueueDerivedMethodsProcessing(RefMethod refMethod, DerivedMethodsProcessor p);

  public abstract void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p);

  public abstract void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p);
}
