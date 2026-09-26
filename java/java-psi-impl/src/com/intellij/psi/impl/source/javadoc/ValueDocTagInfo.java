// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;


public class ValueDocTagInfo implements JavadocTagInfo {
  @Override
  public String getName() {
    return "value";
  }

  @Override
  public boolean isInline() {
    return true;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    return true;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    boolean hasReference = (value != null && value.getFirstChild() != null);
    if (hasReference) {
      if (!PsiUtil.isLanguageLevel5OrHigher(value)) {
        return JavaPsiBundle.message("javadoc.value.tag.jdk15.required");
      }
    }

    if (value != null) {
      PsiReference reference = value.getReference();
      if (reference != null) {
        PsiElement target = reference.resolve();
        if (target != null) {
          if (!(target instanceof PsiField)) {
            return JavaPsiBundle.message("javadoc.value.field.required");
          }
          PsiField field = (PsiField) target;
          if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return JavaPsiBundle.message("javadoc.value.static.field.required");
          }
          if (field.getInitializer() == null ||
              JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) == null) {
            return JavaPsiBundle.message("javadoc.value.field.with.initializer.required");
          }
        }
      }
    }

    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}