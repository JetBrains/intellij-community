/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
class SeeDocTagInfo implements JavadocTagInfo {
  private final String myName;
  private final boolean myInline;
  @NonNls private static final String LINKPLAIN_TAG = "linkplain";

  public SeeDocTagInfo(@NonNls String name, boolean isInline) {
    myName = name;
    myInline = isInline;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    if (place instanceof PsiDocToken) {
      PsiDocToken token = (PsiDocToken) place;
      if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        return getPossibleMethodsAndFields(context, place, prefix);
      } else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
        if (token.getPrevSibling() == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
        final String methodName = token.getPrevSibling().getText();

        PsiElement targetContext = getTargetContext(context, place);

        List<PsiMethod> result = new ArrayList<PsiMethod>();
        final PsiMethod[] methods = PsiDocMethodOrFieldRef.getAllMethods(targetContext, place);
        for (final PsiMethod method : methods) {
          if (method.getName().equals(methodName)) {
            result.add(method);
          }
        }
        return ArrayUtil.toObjectArray(result);
      } else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN && place.getParent() instanceof PsiDocMethodOrFieldRef) {
        return getPossibleMethodsAndFields(context, place, prefix);
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] getPossibleMethodsAndFields(PsiElement context, PsiElement place, String prefix) {
    List<PsiModifierListOwner> result = new ArrayList<PsiModifierListOwner>();

    PsiElement targetContext = getTargetContext(context, place);

    final PsiMethod[] methods = PsiDocMethodOrFieldRef.getAllMethods(targetContext, place);
    for (PsiMethod method : methods) {
      result.add(method);
    }

    final PsiVariable[] variables = PsiDocMethodOrFieldRef.getAllVariables(targetContext, place);
    for (PsiVariable variable : variables) {
      result.add(variable);
    }

    return ArrayUtil.toObjectArray(result);
  }

  private PsiElement getTargetContext(PsiElement context, PsiElement place) {
    PsiElement targetContext = context;

    if (place.getParent() instanceof PsiDocMethodOrFieldRef) {
      PsiDocMethodOrFieldRef methodRef = (PsiDocMethodOrFieldRef) place.getParent();

      final IElementType firstChildType = methodRef.getFirstChildNode().getElementType();
      if (firstChildType == JavaElementType.JAVA_CODE_REFERENCE || firstChildType == JavaElementType.REFERENCE_EXPRESSION) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) SourceTreeToPsiMap.treeElementToPsi(methodRef.getFirstChildNode());
        final PsiElement element = referenceElement.resolve();
        if (element instanceof PsiClass) {
          targetContext = element.getFirstChild();
        }
      }
    }
    return targetContext;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (myInline && myName.equals(LINKPLAIN_TAG) && element != null)
      return PsiUtil.getLanguageLevel(element).compareTo(LanguageLevel.JDK_1_4) >= 0;

    return true;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  @Override
  public boolean isInline() {
    return myInline;
  }
}
