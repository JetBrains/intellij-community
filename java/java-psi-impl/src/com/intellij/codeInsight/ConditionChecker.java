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
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;


/**
 * Used by Constant Condition Inspection to identify methods which perform some type of Validation on the parameters passed into them.
 * For example given the following method
 * <pre>
 * {@code
 *  class Foo {
 *     static boolean validateNotNull(Object o) {
 *       if (o == null) return false;
 *       else return true;
 *     }
 *   }
 * }
 *
 * The corresponding ConditionCheck would be <p/>
 * myConditionCheckType=Type.IS_NOT_NULL_METHOD
 * myClassName=Foo
 * myMethodName=validateNotNull
 * myPsiParameter=o
 *
 * The following block of code would produce a Inspection Warning that o is always true
 *
 *  <pre>
 * {@code
 *   if (Value.isNotNull(o)) {
 *     if(o != null) {}
 *   }
 * }
 * </pre>
 *
 * @author <a href="mailto:johnnyclark@gmail.com">Johnny Clark</a>
 *         Creation Date: 8/14/12
 */
public class ConditionChecker implements Serializable {
  @NotNull private final Type myConditionCheckType;

  public enum Type {
    IS_NULL_METHOD("IsNull Method"),
    IS_NOT_NULL_METHOD("IsNotNull Method"),
    ASSERT_IS_NULL_METHOD("Assert IsNull Method"),
    ASSERT_IS_NOT_NULL_METHOD("Assert IsNotNull Method"),
    ASSERT_TRUE_METHOD("Assert True Method"),
    ASSERT_FALSE_METHOD("Assert False Method");
    private final String myStringRepresentation;

    Type(String stringRepresentation) {
      myStringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return myStringRepresentation;
    }
  }

  @NotNull private final String myClassName;
  @NotNull private final String myMethodName;
  @NotNull private final List<String> myParameterClassList;
  private final int myCheckedParameterIndex;
  private final String myFullName;

  private ConditionChecker(@NotNull String className,
                           @NotNull String methodName,
                           @NotNull List<String> parameterClassList,
                           int checkedParameterIndex,
                           @NotNull Type type,
                           @NotNull String fullName) {
    checkState(!className.isEmpty(), "Class Name is blank");
    checkState(!methodName.isEmpty(), "Method Name is blank");
    checkState(!parameterClassList.isEmpty(), "Parameter Class List is empty");
    checkState(checkedParameterIndex >= 0, "CheckedParameterIndex must be greater than or equal to zero");
    checkState(parameterClassList.size() >= checkedParameterIndex, "CheckedParameterIndex is greater than Parameter Class List's size");
    checkState(!fullName.isEmpty(), "Method Name is blank");

    myConditionCheckType = type;
    myClassName = className;
    myMethodName = methodName;
    myParameterClassList = parameterClassList;
    myCheckedParameterIndex = checkedParameterIndex;
    myFullName = fullName;
  }

  private static void checkState(boolean condition, String errorMsg) {
    if (!condition) throw new IllegalArgumentException(errorMsg);
  }

  public static String getFullyQualifiedName(PsiParameter psiParameter) {
    PsiTypeElement typeElement = psiParameter.getTypeElement();
    if (typeElement == null) throw new RuntimeException("Parameter has null typeElement " + psiParameter.getName());

    PsiType psiType = typeElement.getType();

    return psiType.getCanonicalText();
  }

  public boolean matchesPsiMethod(PsiMethod psiMethod) {
    if (!myMethodName.equals(psiMethod.getName())) return false;

    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return false;

    String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName == null) return false;

    if (!myClassName.equals(qualifiedName)) return false;

    PsiParameterList psiParameterList = psiMethod.getParameterList();
    if (myParameterClassList.size() != psiParameterList.getParameters().length) return false;

    for (int i = 0; i < psiParameterList.getParameters().length; i++) {
      PsiParameter psiParameter = psiParameterList.getParameters()[i];
      PsiTypeElement psiTypeElement = psiParameter.getTypeElement();
      if (psiTypeElement == null) return false;

      PsiType psiType = psiTypeElement.getType();
      String parameterCanonicalText = psiType.getCanonicalText();
      String myParameterCanonicalText = myParameterClassList.get(i);
      if (!myParameterCanonicalText.equals(parameterCanonicalText)) return false;
    }

    return true;
  }

  public boolean matchesPsiMethod(PsiMethod psiMethod, int paramIndex) {
    if (matchesPsiMethod(psiMethod) && paramIndex == myCheckedParameterIndex) return true;

    return false;
  }

  public boolean overlaps(ConditionChecker otherChecker) {
    if (myClassName.equals(otherChecker.myClassName) &&
        myMethodName.equals(otherChecker.myMethodName) &&
        myParameterClassList.equals(otherChecker.myParameterClassList) &&
        myCheckedParameterIndex == otherChecker.myCheckedParameterIndex) {
      return true;
    }

    return false;
  }

  @NotNull
  public Type getConditionCheckType() {
    return myConditionCheckType;
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  public String getMethodName() {
    return myMethodName;
  }

  public int getCheckedParameterIndex() {
    return myCheckedParameterIndex;
  }

  public String getFullName() {
    return myFullName;
  }

  /**
   * In addition to normal duties, this controls the manner in which the ConditionCheck appears in the ConditionCheckDialog.MethodsPanel
   */
  @Override
  public String toString() {
    return myFullName;
  }

  private static class Builder {

    static String initFullName(String className,
                               String methodName,
                               List<String> parameterClasses,
                               List<String> parameterNames,
                               int checkedParameterIndex) {
      String s = className + "." + methodName + "(";
      int index = 0;
      for (String parameterClass : parameterClasses) {
        String parameterClassAndName = parameterClass + " " + parameterNames.get(index);
        if (index == checkedParameterIndex) parameterClassAndName = "*" + parameterClassAndName + "*";

        s += parameterClassAndName + ", ";
        index++;
      }
      s = s.substring(0, s.length() - 2);
      s += ")";
      return s;
    }
  }

  static class FromConfigBuilder extends Builder {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ConditionCheck.FromConfigBuilder");
    @NotNull private final String serializedRepresentation;
    @NotNull private final Type type;

    FromConfigBuilder(@NotNull String serializedRepresentation, @NotNull Type type) {
      this.serializedRepresentation = serializedRepresentation;
      this.type = type;
    }

    private String parseClassAndMethodName() {
      if (!serializedRepresentation.contains("(")) {
        throw new IllegalArgumentException("Name should contain a opening parenthesis.  " + serializedRepresentation);
      }
      else if (!serializedRepresentation.contains(")")) {
        throw new IllegalArgumentException("Name should contain a closing parenthesis.  " + serializedRepresentation);
      }
      else if (serializedRepresentation.indexOf("(", serializedRepresentation.indexOf("(") + 1) > -1) {
        throw new IllegalArgumentException("Name should only contain one opening parenthesis.  " + serializedRepresentation);
      }
      else if (serializedRepresentation.indexOf(")", serializedRepresentation.indexOf(")") + 1) > -1) {
        throw new IllegalArgumentException("Name should only contain one closing parenthesis.  " + serializedRepresentation);
      }
      else if (serializedRepresentation.indexOf(")") < serializedRepresentation.indexOf("(")) {
        throw new IllegalArgumentException("Opening parenthesis should precede closing parenthesis.  " + serializedRepresentation);
      }

      String classAndMethodName = serializedRepresentation.substring(0, serializedRepresentation.indexOf("("));
      if (!classAndMethodName.contains(".")) {
        throw new IllegalArgumentException(
          "Name should contain a dot between the class name and method name (before the opening parenthesis).  " +
          serializedRepresentation);
      }
      return classAndMethodName;
    }

    @Nullable
    public ConditionChecker build() {
      try {
        String classAndMethodName = parseClassAndMethodName();

        String className = classAndMethodName.substring(0, classAndMethodName.lastIndexOf("."));
        String methodName = classAndMethodName.substring(classAndMethodName.lastIndexOf(".") + 1);

        String allParametersSubString =
          serializedRepresentation.substring(serializedRepresentation.indexOf("(") + 1, serializedRepresentation.lastIndexOf(")")).trim();
        if (allParametersSubString.isEmpty()) {
          throw new IllegalArgumentException(
            "Name should contain 1+ parameter (between opening and closing parenthesis).  " + serializedRepresentation);
        }
        if (allParametersSubString.contains("*") && allParametersSubString.indexOf("*") == allParametersSubString.lastIndexOf("*")) {
          throw new IllegalArgumentException("Selected Parameter should be surrounded by asterisks.  " + serializedRepresentation);
        }

        List<String> parameterClasses = new ArrayList<String>();
        List<String> parameterNames = new ArrayList<String>();
        int checkParameterIndex = -1;
        int index = 0;
        for (String parameterClassAndName : allParametersSubString.split(",")) {
          parameterClassAndName = parameterClassAndName.trim();
          if (parameterClassAndName.startsWith("*") && parameterClassAndName.endsWith("*")) {
            checkParameterIndex = index;
            parameterClassAndName = parameterClassAndName.substring(1, parameterClassAndName.length() - 1);
          }

          String[] parameterClassAndNameSplit = parameterClassAndName.split(" ");
          String parameterClass = parameterClassAndNameSplit[0];
          String parameterName = parameterClassAndNameSplit[1];
          parameterClasses.add(parameterClass);
          parameterNames.add(parameterName);
          index++;
        }
        String fullName = initFullName(className, methodName, parameterClasses, parameterNames, checkParameterIndex);
        return new ConditionChecker(className, methodName, parameterClasses, checkParameterIndex, type, fullName);
      }
      catch (Exception e) {
        LOG.error("An Exception occurred while attempting to build ConditionCheck for Serialized String '" +
                  serializedRepresentation +
                  "' and Type '" +
                  type +
                  "'", e);
        return null;
      }
    }
  }

  public static class FromPsiBuilder extends Builder {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ConditionCheck.FromPsiBuilder");
    @NotNull private final PsiMethod psiMethod;
    @NotNull private final PsiParameter psiParameter;
    @NotNull private final Type type;

    public FromPsiBuilder(@NotNull PsiMethod psiMethod, @NotNull PsiParameter psiParameter, @NotNull Type type) {
      this.psiMethod = psiMethod;
      this.psiParameter = psiParameter;
      this.type = type;
    }

    private static void validatePsiMethodHasContainingClass(PsiMethod psiMethod) {
      PsiElement psiElement = psiMethod.getContainingClass();
      if (!(psiElement instanceof PsiClass)) {
        throw new IllegalArgumentException("PsiMethod " + psiMethod + " can not have a null containing class.");
      }
    }

    private static void validatePsiMethodReturnTypeForNonAsserts(PsiMethod psiMethod, Type type) {
      PsiType returnType = psiMethod.getReturnType();
      if (isAssert(type)) return;

      if (returnType == null) throw new IllegalArgumentException("PsiMethod " + psiMethod + " has a null return type PsiType.");

      if (returnType != PsiType.BOOLEAN && !returnType.getCanonicalText().equals(Boolean.class.toString())) {
        throw new IllegalArgumentException("PsiMethod " + psiMethod + " must have a null return type PsiType of boolean or Boolean.");
      }
    }

    private static void validatePsiParameterExistsInPsiMethod(PsiMethod psiMethod, PsiParameter psiParameter) {
      boolean parameterFound = false;
      PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (psiParameter.equals(parameter)) {
          parameterFound = true;
          break;
        }
      }

      if (!parameterFound) {
        throw new IllegalArgumentException("PsiMethod " + psiMethod + " must have parameter " + getFullyQualifiedName(psiParameter));
      }
    }

    private static boolean isAssert(Type type) {
      return type == Type.ASSERT_IS_NULL_METHOD ||
             type == Type.ASSERT_IS_NOT_NULL_METHOD ||
             type == Type.ASSERT_TRUE_METHOD ||
             type == Type.ASSERT_FALSE_METHOD;
    }

    private static String initClassNameFromPsiMethod(PsiMethod psiMethod) {
      PsiElement psiElement = psiMethod.getContainingClass();
      PsiClass psiClass = (PsiClass)psiElement;
      if (psiClass == null) throw new IllegalStateException("PsiClass is null");

      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName == null || qualifiedName.isEmpty()) throw new IllegalStateException("Qualified Name is Blank");
      return qualifiedName;
    }

    private static String initMethodNameFromPsiMethod(PsiMethod psiMethod) {
      return psiMethod.getName();
    }

    private static List<String> initParameterClassListFromPsiMethod(PsiMethod psiMethod) {
      List<String> parameterClasses = new ArrayList<String>();
      PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      for (PsiParameter param : parameters) {
        PsiTypeElement typeElement = param.getTypeElement();
        if (typeElement == null) throw new RuntimeException("Parameter has null typeElement " + param.getName());

        PsiType psiType = typeElement.getType();

        parameterClasses.add(psiType.getCanonicalText());
      }
      return parameterClasses;
    }

    private static List<String> initParameterNameListFromPsiMethod(PsiMethod psiMethod) {
      List<String> parameterNames = new ArrayList<String>();
      PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      for (PsiParameter param : parameters) {
        parameterNames.add(param.getName());
      }
      return parameterNames;
    }

    private static int initCheckedParameterIndex(PsiMethod psiMethod, PsiParameter psiParameterToFind) {
      PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter param = parameters[i];
        if (param.equals(psiParameterToFind)) return i;
      }
      throw new IllegalStateException();
    }

    private void validateConstructorArgs(PsiMethod psiMethod, PsiParameter psiParameter) {
      validatePsiMethodHasContainingClass(psiMethod);
      validatePsiMethodReturnTypeForNonAsserts(psiMethod, type);
      validatePsiParameterExistsInPsiMethod(psiMethod, psiParameter);
    }

    @Nullable
    public ConditionChecker build() {
      try {
        validateConstructorArgs(psiMethod, psiParameter);

        String className = initClassNameFromPsiMethod(psiMethod);
        String methodName = initMethodNameFromPsiMethod(psiMethod);
        List<String> parameterClassList = initParameterClassListFromPsiMethod(psiMethod);
        List<String> parameterNameList = initParameterNameListFromPsiMethod(psiMethod);
        int checkedParameterIndex = initCheckedParameterIndex(psiMethod, psiParameter);
        String fullName = initFullName(className, methodName, parameterClassList, parameterNameList, checkedParameterIndex);
        return new ConditionChecker(className, methodName, parameterClassList, checkedParameterIndex, type, fullName);
      }
      catch (Exception e) {
        LOG.error("An Exception occurred while attempting to build ConditionCheck for PsiMethod '" + psiMethod +
                  "' PsiParameter='" + psiParameter + "' " +
                  "' and Type '" +
                  type +
                  "'", e);
        return null;
      }
    }
  }
}
