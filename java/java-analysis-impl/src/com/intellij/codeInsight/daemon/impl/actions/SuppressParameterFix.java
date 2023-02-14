/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressParameterFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  private String myAlternativeID;

  public SuppressParameterFix(@NotNull HighlightDisplayKey key) {
    this(key.getID());
    myAlternativeID = HighlightDisplayKey.getAlternativeID(key);
  }

  public SuppressParameterFix(String ID) {
    super(ID, false);
  }

  @Override
  @NotNull
  public String getText() {
    return JavaAnalysisBundle.message("suppress.for.parameter");
  }

  @Nullable
  @Override
  public PsiElement getContainer(PsiElement context) {
    PsiParameter psiParameter = PsiTreeUtil.getParentOfType(context, PsiParameter.class, false);
    return psiParameter != null && psiParameter.getTypeElement() != null && JavaSuppressionUtil.canHave15Suppressions(psiParameter) ? psiParameter : null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected boolean replaceSuppressionComments(@NotNull PsiElement container) {
    return false;
  }

  @Override
  protected void createSuppression(@NotNull Project project, @NotNull PsiElement element, @NotNull PsiElement cont)
    throws IncorrectOperationException {
    PsiModifierListOwner container = (PsiModifierListOwner)cont;
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      final String id = SuppressFix.getID(container, myAlternativeID);
      JavaSuppressionUtil.addSuppressAnnotation(project, container, container, id != null ? id : myID);
    }
  }
}
