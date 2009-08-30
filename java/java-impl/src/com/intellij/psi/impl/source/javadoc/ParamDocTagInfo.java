package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mike
 */
class ParamDocTagInfo implements JavadocTagInfo {
  public String getName() {
    return "param";
  }

  public boolean isValidInContext(PsiElement element) {
    return element instanceof PsiMethod ||
           (element instanceof PsiClass && PsiUtil.isLanguageLevel5OrHigher(element));
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    if (context instanceof PsiTypeParameterListOwner) {
      List<PsiNamedElement> result = new ArrayList<PsiNamedElement>(Arrays.asList(((PsiTypeParameterListOwner)context).getTypeParameters()));

      if ((PsiTypeParameterListOwner)context instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)context;
        result.addAll(Arrays.asList(method.getParameterList().getParameters()));
      }

      return result.toArray(new PsiNamedElement[result.size()]);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return JavaErrorMessages.message("javadoc.param.tag.paramter.name.expected");
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

  public PsiReference getReference(PsiDocTagValue value) {
    if (value instanceof PsiDocParamRef) return value.getReference();
    return null;
  }


  public boolean isInline() {
    return false;
  }
}
