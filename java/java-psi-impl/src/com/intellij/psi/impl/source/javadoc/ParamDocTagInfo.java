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
package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;

/**
 * @author mike
 */
class ParamDocTagInfo implements JavadocTagInfo {
  @Override
  public String getName() {
    return "param";
  }

  @Override
  public boolean isInline() {
    return false;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    return element instanceof PsiMethod ||
           (element instanceof PsiClass && PsiUtil.isLanguageLevel5OrHigher(element));
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return JavaErrorMessages.message("javadoc.param.tag.parameter.name.expected");
    final ASTNode firstChildNode = value.getNode().getFirstChildNode();
    if (firstChildNode != null &&
        firstChildNode.getElementType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
      if (value.getNode().findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN) == null) {
        return JavaErrorMessages.message("javadoc.param.tag.type.parameter.name.expected");
      }

      if (value.getNode().findChildByType(JavaDocTokenType.DOC_TAG_VALUE_GT) == null) {
        return JavaErrorMessages.message("javadoc.param.tag.type.parameter.gt.expected");
      }
    }
    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    if (value instanceof PsiDocParamRef) return value.getReference();
    return null;
  }
}