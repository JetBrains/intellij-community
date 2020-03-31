// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;

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
    if (value == null) return JavaPsiBundle.message("javadoc.param.tag.parameter.name.expected");
    final ASTNode firstChildNode = value.getNode().getFirstChildNode();
    if (firstChildNode != null &&
        firstChildNode.getElementType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
      if (value.getNode().findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN) == null) {
        return JavaPsiBundle.message("javadoc.param.tag.type.parameter.name.expected");
      }

      if (value.getNode().findChildByType(JavaDocTokenType.DOC_TAG_VALUE_GT) == null) {
        return JavaPsiBundle.message("javadoc.param.tag.type.parameter.gt.expected");
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