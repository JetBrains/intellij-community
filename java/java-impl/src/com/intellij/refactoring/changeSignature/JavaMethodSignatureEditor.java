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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class JavaMethodSignatureEditor extends MethodSignatureEditor<PsiMethod> {
  public JavaMethodSignatureEditor(@NotNull PsiMethod method) {
    super(method, PsiMethod.class);
  }

  @Override
  public TextRange getSignatureTextRange() {
    final PsiMethod method = getMethod();
    final TextRange methodTextRange = method.getTextRange();
    final TextRange paramsRange = method.getParameterList().getTextRange();
    return TextRange.create(methodTextRange.getStartOffset(), paramsRange.getEndOffset());
  }

  @Override
  protected void indexParameters(PsiMethod method, @NotNull ParameterIndexer indexer) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      indexer.setIndex(parameters[i], i);
    }
  }

  @Override
  protected String formatMethod() {

    @NonNls StringBuilder buffer = new StringBuilder();
    PsiMethod method = getMethod();
    PsiModifierList modifierList = method.getModifierList();
    String modifiers = modifierList.getText();

    buffer.append(modifiers);
    if (modifiers.length() > 0 &&
        !StringUtil.endsWithChar(modifiers, '\n') &&
        !StringUtil.endsWithChar(modifiers, '\r') &&
        !StringUtil.endsWithChar(modifiers, ' ')) {
      buffer.append(" ");
    }

    if (!method.isConstructor()) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null) {
        buffer.append(returnType.getPresentableText());
      }
      buffer.append(" ");
    }
    buffer.append(method.getName());
    buffer.append("(");

    final String indent = "    ";
    PsiParameter[] items = method.getParameterList().getParameters();
    for (int i = 0; i < items.length; i++) {
      PsiParameter item = items[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append("\n");
      buffer.append(indent);
      buffer.append(item.getTypeElement().getType().getPresentableText());
      buffer.append(" ");
      buffer.append(item.getName());
    }
    if (items.length != 0) {
      buffer.append("\n");
    }
    buffer.append(")");

    PsiClassType[] thrownExceptionsFragments = method.getThrowsList().getReferencedTypes();
    if (thrownExceptionsFragments.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (int i = 0; i < thrownExceptionsFragments.length; i++) {
        String text = thrownExceptionsFragments[i].getPresentableText();
        buffer.append(indent);
        buffer.append(text);
        if (i < thrownExceptionsFragments.length - 1) {
          buffer.append(",");
          buffer.append("\n");
        }
      }
    }
    return buffer.toString();
  }
}
