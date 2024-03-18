/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateSubstitutor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public final class JavaTemplateSubstitutor implements TemplateSubstitutor {
  @Override
  public @Nullable TemplateImpl substituteTemplate(@NotNull TemplateSubstitutionContext substitutionContext,
                                                   @NotNull TemplateImpl template) {
    PsiFile file = substitutionContext.getPsiFile();
    if (file.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      PsiElement element = file.findElementAt(substitutionContext.getOffset());
      PsiElement prevLeaf = element == null ? null : PsiTreeUtil.prevCodeLeaf(element);
      if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.ARROW)) {
        boolean inSwitch = prevLeaf.getParent() instanceof PsiSwitchLabeledRuleStatement;
        String text = template.getString();
        try {
          PsiStatement statement = JavaPsiFacade.getElementFactory(substitutionContext.getProject()).createStatementFromText(text, null);
          String resultText;
          if (inSwitch && (statement instanceof PsiExpressionStatement || statement instanceof PsiThrowStatement)) {
            resultText = text;
          }
          else if (statement instanceof PsiExpressionStatement) {
            resultText = ((PsiExpressionStatement)statement).getExpression().getText();
          }
          else {
            resultText = "{" + text + "}";
          }
          TemplateImpl copy = template.copy();
          copy.setString(resultText);
          return copy;
        }
        catch (IncorrectOperationException ignored) {
        }
      }
    }
    return null;
  }
}
