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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class ExtractChainedMapAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if (variable == null) {
      return false;
    }
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return false;
    PsiDeclarationStatement declaration = tryCast(variable.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getDeclaredElements().length != 1) return false;
    PsiCodeBlock block = tryCast(declaration.getParent(), PsiCodeBlock.class);
    if (block == null || ArrayUtil.getFirstElement(block.getStatements()) != declaration) return false;
    PsiLambdaExpression lambda = tryCast(block.getParent(), PsiLambdaExpression.class);
    ChainCallExtractor extractor = ChainCallExtractor.findExtractor(lambda, initializer, variable.getType());
    if (extractor == null) return false;
    PsiParameter parameter = lambda.getParameterList().getParameters()[0];
    if (ReferencesSearch.search(parameter).allMatch(ref -> PsiTreeUtil.isAncestor(initializer, ref.getElement(), false))) {
      setText(JavaBundle.message("intention.extract.map.step.text", variable.getName(),
                                        extractor.getMethodName(parameter, initializer, variable.getType())));
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    ChainCallExtractor.extractMappingStep(project, variable);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.extract.map.step.family");
  }
}
