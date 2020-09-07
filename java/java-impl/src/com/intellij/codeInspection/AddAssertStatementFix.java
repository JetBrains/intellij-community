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
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private final String myText;

  public AddAssertStatementFix(@NotNull String text) {
    myText = text;
  }

  @Override
  @NotNull
  public String getName() {
    return JavaBundle.message("inspection.assert.quickfix", myText);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression element = PsiTreeUtil.getNonStrictParentOfType(descriptor.getPsiElement(), PsiExpression.class);
    if (element == null) return;
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(element);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    element = result.getExpression();
    PsiElement anchorElement = result.getAnchor();
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorElement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    @NonNls String text = "assert " + myText + ";";
    PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText(text, element);

    anchorElement.getParent().addBefore(assertStatement, anchorElement);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.quickfix.assert.family");
  }
}
