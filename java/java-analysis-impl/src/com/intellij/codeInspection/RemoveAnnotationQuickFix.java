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
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RemoveAnnotationQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.i18n.AnnotateNonNlsQuickfix");
  private final PsiAnnotation myAnnotation;
  private final PsiModifierListOwner myListOwner;

  public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, final PsiModifierListOwner listOwner) {
    myAnnotation = annotation;
    myListOwner = listOwner;
  }

  @Override
  @NotNull
  public String getName() {
    return CodeInsightBundle.message("remove.annotation");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (myAnnotation.isPhysical()) {
      try {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myAnnotation)) return;
        myAnnotation.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    } else if (myListOwner != null) {
      ExternalAnnotationsManager.getInstance(project).deannotate(myListOwner, myAnnotation.getQualifiedName());
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }
}