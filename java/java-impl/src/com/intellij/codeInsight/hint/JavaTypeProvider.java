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
package com.intellij.codeInsight.hint;

import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author gregsh
 */
public class JavaTypeProvider extends ExpressionTypeProvider<PsiExpression> {
  @NotNull
  @Override
  public String getInformationHint(@NotNull PsiExpression element) {
    PsiType type = element.getType();
    String text = type == null ? "<unknown>" : type.getCanonicalText();
    return StringUtil.escapeXml(text);
  }

  @NotNull
  @Override
  public String getErrorHint() {
    return "No expression found";
  }

  @NotNull
  @Override
  public List<PsiExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return SyntaxTraverser.psiApi().parents(elementAt).filter(PsiExpression.class).toList();
  }
}
