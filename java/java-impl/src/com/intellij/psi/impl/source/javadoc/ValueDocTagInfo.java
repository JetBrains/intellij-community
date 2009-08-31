package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 10.08.2005
 * Time: 18:25:51
 * To change this template use File | Settings | File Templates.
 */
public class ValueDocTagInfo implements JavadocTagInfo {
  public String getName() {
    return "value";
  }

  public boolean isInline() {
    return true;
  }

  public boolean isValidInContext(PsiElement element) {
    return true;
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    return null;
  }

  public String checkTagValue(PsiDocTagValue value) {
    boolean hasReference = (value != null && value.getFirstChild() != null);
    if (hasReference) {
      if (!PsiUtil.getLanguageLevel(value).hasEnumKeywordAndAutoboxing()) {
        return JavaErrorMessages.message("javadoc.value.tag.jdk15.required");
      }
    }

    if (value != null) {
      PsiReference reference = value.getReference();
      if (reference != null) {
        PsiElement target = reference.resolve();
        if (target != null) {
          if (!(target instanceof PsiField)) {
            return JavaErrorMessages.message("javadoc.value.field.required");
          }
          PsiField field = (PsiField) target;
          if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return JavaErrorMessages.message("javadoc.value.static.field.required");
          }
          if (field.getInitializer() == null ||
              JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) == null) {
            return JavaErrorMessages.message("javadoc.value.field.with.initializer.required");
          }
        }
      }
    }

    return null;
  }

  public PsiReference getReference(PsiDocTagValue value) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
