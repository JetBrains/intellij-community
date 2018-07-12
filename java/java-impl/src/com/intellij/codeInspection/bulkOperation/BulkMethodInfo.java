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
package com.intellij.codeInspection.bulkOperation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;

public class BulkMethodInfo {
  private final String myClassName;
  private final String mySimpleName;
  private final String myBulkName;

  public BulkMethodInfo(String className, String simpleName, String bulkName) {
    myClassName = className;
    mySimpleName = simpleName;
    myBulkName = bulkName;
  }

  public boolean isMyMethod(PsiReferenceExpression ref) {
    if (!mySimpleName.equals(ref.getReferenceName())) return false;
    PsiElement element = ref.resolve();
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    PsiParameterList parameters = method.getParameterList();
    if (parameters.getParametersCount() != 1) return false;
    PsiParameter parameter = parameters.getParameters()[0];
    PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameter.getType());
    if (parameterClass == null ||
        CommonClassNames.JAVA_LANG_ITERABLE.equals(parameterClass.getQualifiedName()) ||
        CommonClassNames.JAVA_UTIL_COLLECTION.equals(parameterClass.getQualifiedName())) {
      return false;
    }
    PsiClass methodClass = method.getContainingClass();
    return methodClass != null && InheritanceUtil.isInheritor(methodClass, myClassName);
  }

  public boolean isSupportedIterable(PsiExpression qualifier, PsiExpression iterable, boolean useArraysAsList) {
    PsiType qualifierType = qualifier.getType();
    if (!(qualifierType instanceof PsiClassType)) return false;
    PsiType type = iterable.getType();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(iterable.getProject());
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(iterable.getProject());
    String text = iterable.getText();
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      if (!useArraysAsList || componentType instanceof PsiPrimitiveType) return false;
      PsiClass listClass = psiFacade.findClass(CommonClassNames.JAVA_UTIL_LIST, iterable.getResolveScope());
      if (listClass == null) return false;
      if (!listClass.hasTypeParameters()){
        // Raw List class - Java 1.4?
        type = factory.createType(listClass);
      } else if (listClass.getTypeParameters().length == 1) {
        type = factory.createType(listClass, componentType);
      } else {
        return false;
      }
      text = CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(" + text + ")";
    }
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass == null) return false;
    PsiClass commonParent = psiFacade.findClass(CommonClassNames.JAVA_LANG_ITERABLE, aClass.getResolveScope());
    if (commonParent == null) {
      // No Iterable class in Java 1.4
      commonParent = psiFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, aClass.getResolveScope());
    }
    if(!InheritanceUtil.isInheritorOrSelf(aClass, commonParent, true)) return false;
    PsiExpression expression = factory.createExpressionFromText(qualifier.getText() + "." + myBulkName + "(" + text + ")", iterable);
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    PsiMethod bulkMethod = call.resolveMethod();
    if (bulkMethod == null) return false;
    PsiParameterList parameters = bulkMethod.getParameterList();
    if (parameters.getParametersCount() != 1) return false;
    PsiType parameterType = parameters.getParameters()[0].getType();
    parameterType = call.resolveMethodGenerics().getSubstitutor().substitute(parameterType);
    PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
    return parameterClass != null &&
           (CommonClassNames.JAVA_LANG_ITERABLE.equals(parameterClass.getQualifiedName()) ||
            CommonClassNames.JAVA_UTIL_COLLECTION.equals(parameterClass.getQualifiedName())) &&
           parameterType.isAssignableFrom(type);
  }

  public String getClassName() {
    return myClassName;
  }

  public String getSimpleName() {
    return mySimpleName;
  }

  public String getBulkName() {
    return myBulkName;
  }

  public String getReplacementName() {
    return StringUtil.getShortName(myClassName) + "." + myBulkName;
  }

  @Override
  public String toString() {
    return myClassName + "::" + mySimpleName + " => " + myBulkName;
  }
}
