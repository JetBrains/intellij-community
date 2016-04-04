/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.presentation.java;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class ClassPresentationUtil {
  private ClassPresentationUtil() {
  }

  public static String getNameForClass(@NotNull PsiClass aClass, boolean qualified) {
    if (aClass instanceof PsiAnonymousClass) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        PsiEnumConstant enumConstant = ((PsiEnumConstantInitializer)aClass).getEnumConstant();
        String name = enumConstant.getName();
        return PsiBundle.message("enum.constant.context", name, getContextName(enumConstant, qualified));
      }
      return PsiBundle.message("anonymous.class.context.display", getContextName(aClass, qualified));
    }
    if (qualified){
      String qName = aClass.getQualifiedName();
      if (qName != null) return qName;
    }

    String className = aClass.getName();
    String contextName = getContextName(aClass, qualified);
    return contextName != null ? PsiBundle.message("class.context.display", className, contextName) : className;
  }

  private static String getNameForElement(@NotNull PsiElement element, boolean qualified) {
    if (element instanceof PsiClass){
      return getNameForClass((PsiClass)element, qualified);
    }
    else if (element instanceof PsiMethod){
      PsiMethod method = (PsiMethod)element;
      String methodName = method.getName();
      return PsiBundle.message("method.context.display", methodName, getContextName(method, qualified));
    }
    else if (element instanceof PsiClassOwner){
      return null;
    }
    else if (element instanceof PsiFile){
      return ((PsiFile)element).getName();
    }
    else if (element instanceof PsiField) {
      return ((PsiField)element).getName() + " in " + getContextName(element, qualified);
    }
    else{
      return null;
    }
  }

  public static String getContextName(@NotNull PsiElement element, boolean qualified) {
    PsiElement parent = PsiTreeUtil.getStubOrPsiParentOfType(element, PsiMember.class);
    if (parent == null) parent = element.getContainingFile();
    while(true){
      if (parent == null) return null;
      String name = getNameForElement(parent, qualified);
      if (name != null) return name;
      if (parent instanceof PsiFile) return null;
      parent = PsiTreeUtil.getStubOrPsiParent(parent);
    }
  }

  public static String getFunctionalExpressionPresentation(PsiFunctionalExpression functionalExpression, boolean qualified) {
    return "Functional expression in " + getContextName(functionalExpression, qualified);
  }
}
