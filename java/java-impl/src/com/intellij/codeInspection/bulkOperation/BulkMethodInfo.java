// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bulkOperation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.psi.CommonClassNames.*;

public final class BulkMethodInfo {
  private final String myClassName;
  private final String mySimpleName;
  private final String myBulkName;
  private final String myBulkParameterType;

  public BulkMethodInfo(String className, String simpleName, String bulkName, String bulkParameterType) {
    myClassName = className;
    mySimpleName = simpleName;
    myBulkName = bulkName;
    myBulkParameterType = bulkParameterType;
  }

  public boolean isMyMethod(PsiReferenceExpression ref) {
    if (!mySimpleName.equals(ref.getReferenceName())) return false;
    PsiElement element = ref.resolve();
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    PsiParameterList parameters = method.getParameterList();
    if (parameters.getParametersCount() != getSimpleParametersCount()) return false;
    if (getSimpleParametersCount() == 1) {
      PsiParameter parameter = Objects.requireNonNull(parameters.getParameter(0));
      PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameter.getType());
      if (parameterClass == null ||
          JAVA_LANG_ITERABLE.equals(parameterClass.getQualifiedName()) ||
          JAVA_UTIL_COLLECTION.equals(parameterClass.getQualifiedName())) {
        return false;
      }
    }
    PsiClass methodClass = method.getContainingClass();
    if (methodClass == null || !InheritanceUtil.isInheritor(methodClass, myClassName)) return false;
    return haveBulkMethod(methodClass);
  }

  private boolean haveBulkMethod(PsiClass aClass) {
    return ContainerUtil.or(aClass.findMethodsByName(myBulkName, true), method -> {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) return false;
      return TypeUtils.variableHasTypeOrSubtype(parameters[0], getBulkParameterType(aClass.getProject()));
    });
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
      PsiClass listClass = psiFacade.findClass(JAVA_UTIL_LIST, iterable.getResolveScope());
      if (listClass == null) return false;
      if (!listClass.hasTypeParameters()) {
        // Raw List class - Java 1.4?
        type = factory.createType(listClass);
      } else if (listClass.getTypeParameters().length == 1) {
        type = factory.createType(listClass, componentType);
      } else {
        return false;
      }
      text = JAVA_UTIL_ARRAYS + ".asList(" + text + ")";
    }
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass == null) return false;
    String bulkParameterType = getBulkParameterType(aClass.getProject());
    PsiClass commonParent = psiFacade.findClass(bulkParameterType, aClass.getResolveScope());
    if (!InheritanceUtil.isInheritorOrSelf(aClass, commonParent, true)) return false;
    PsiExpression expression = factory.createExpressionFromText(qualifier.getText() + "." + myBulkName + "(" + text + ")", iterable);
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    PsiMethod bulkMethod = call.resolveMethod();
    if (bulkMethod == null) return false;
    PsiParameterList parameters = bulkMethod.getParameterList();
    if (parameters.getParametersCount() != 1) return false;
    PsiType parameterType = Objects.requireNonNull(parameters.getParameter(0)).getType();
    parameterType = call.resolveMethodGenerics().getSubstitutor().substitute(parameterType);
    PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
    if (parameterClass == null) return false;
    String qualifiedName = parameterClass.getQualifiedName();
    return bulkParameterType.equals(qualifiedName) && parameterType.isAssignableFrom(type);
  }

  public String getClassName() {
    return myClassName;
  }

  public String getSimpleName() {
    return mySimpleName;
  }

  public int getSimpleParametersCount() {
    return myBulkParameterType.equals(JAVA_UTIL_MAP) ? 2 : 1;
  }

  public String getBulkName() {
    return myBulkName;
  }

  public String getBulkParameterType(@NotNull Project project) {
    if (myBulkParameterType.equals(JAVA_LANG_ITERABLE) &&
        PsiUtil.getLanguageLevel(project).isLessThan(LanguageLevel.JDK_1_5)) {
      return JAVA_UTIL_COLLECTION;
    }
    return myBulkParameterType;
  }

  public String getReplacementName() {
    return StringUtil.getShortName(myClassName) + "." + myBulkName;
  }

  @Override
  public String toString() {
    return myClassName + "::" + mySimpleName + " => " + myBulkName;
  }
}
