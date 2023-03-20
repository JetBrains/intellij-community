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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DeleteRepeatedInterfaceFix implements IntentionAction {
  private final PsiTypeElement myConjunct;
  private final PsiTypeElement[] myConjList;

  public DeleteRepeatedInterfaceFix(PsiTypeElement conjunct) {
    myConjunct = conjunct;
    PsiTypeElement[] elements = PsiTreeUtil.getChildrenOfType(myConjunct.getParent(), PsiTypeElement.class);
    myConjList = elements == null ? PsiTypeElement.EMPTY_ARRAY : elements;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new DeleteRepeatedInterfaceFix(PsiTreeUtil.findSameElementInCopy(myConjunct, target));
  }

  @NotNull
  @Override
  public String getText() {
    return JavaAnalysisBundle.message("delete.repeated.0", myConjunct.getText());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("delete.repeated.interface");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myConjList.length == 0) return false;
    return ContainerUtil.and(myConjList, PsiElement::isValid);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(myConjunct, PsiTypeCastExpression.class);
    if (castExpression != null) {
      final PsiTypeElement castType = castExpression.getCastType();
      if (castType != null) {
        final PsiType type = castType.getType();
        if (type instanceof PsiIntersectionType) {
          final String typeText = StreamEx.of(myConjList).without(myConjunct).map(PsiElement::getText).joining(" & ");
          final PsiTypeCastExpression newCastExpr =
            (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("(" + typeText + ")a", castType);
          CodeStyleManager.getInstance(project).reformat(castType.replace(Objects.requireNonNull(newCastExpr.getCastType())));
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
