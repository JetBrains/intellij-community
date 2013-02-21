/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.codeInsight.ConditionChecker.Type.*;

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
 * The corresponding MethodConditionCheck would be <p/>
 * myType=Type.IS_NOT_NULL_METHOD
 * myPsiClass=Foo
 * myPsiMethod=validateNotNull
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
public class MethodConditionCheck implements ConditionChecker, Comparable<MethodConditionCheck> {
  private final @NotNull Type myType;
  private final @NotNull PsiClass myPsiClass;
  private final @NotNull PsiMethod myPsiMethod;
  private final @NotNull PsiParameter myPsiParameter;
  private final String fullName;
  private final String shortName;

  public MethodConditionCheck(@NotNull PsiMethod psiMethod, @NotNull PsiParameter psiParameter, @NotNull Type type) {
    myPsiMethod = psiMethod;
    myPsiParameter = psiParameter;
    if (type != IS_NULL_METHOD && type != IS_NOT_NULL_METHOD &&
        type != ASSERT_IS_NULL_METHOD && type != ASSERT_IS_NOT_NULL_METHOD &&
        type != ASSERT_TRUE_METHOD && type != ASSERT_FALSE_METHOD)
      throw new IllegalArgumentException("Type is invalid " + type);

    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null)
      throw new IllegalArgumentException("PsiMethod has null Containing Class");

    myPsiClass = containingClass;
    myType = type;

    validatePsiMethod();
    String className = initClassNameFromPsiMethod();
    String methodName = initMethodNameFromPsiMethod();
    List<String> parameters = initParameterNamesFromPsiMethod(myPsiParameter);
    fullName = initFullName(className, methodName, parameters);
    shortName = initShortName(methodName, parameters);
  }

  @Override
  public boolean matches(PsiMethod psiMethod) {
    if (myPsiMethod.equals(psiMethod)) {
      return true;
    }

    // The equals method in PsiMethod compares to see if they are the same object, but sometimes they are not the same object but do represent the same method
    if (!myPsiMethod.getName().equals(psiMethod.getName())) return false;

    PsiClass myContainingClass = myPsiMethod.getContainingClass();
    PsiClass containingClass = myPsiMethod.getContainingClass();
    if (myContainingClass == null && containingClass != null) return false;
    if (myContainingClass != null && containingClass == null) return false;
    if (myContainingClass != null) {   // Both must be non-null
      String myQualifiedName = myContainingClass.getQualifiedName();
      String qualifiedName = containingClass.getQualifiedName();
      if (myQualifiedName == null && qualifiedName != null) return false;
      if (myQualifiedName != null && qualifiedName == null) return false;
      if (myQualifiedName != null && !myQualifiedName.equals(qualifiedName)) return false;
    }

    PsiParameterList myPsiParameterList = myPsiMethod.getParameterList();
    PsiParameterList psiParameterList = psiMethod.getParameterList();
    if (myPsiParameterList.getParameters().length != psiParameterList.getParameters().length) return false;
    for (int i = 0; i < myPsiParameterList.getParameters().length; i++) {
      PsiParameter myPsiParameter = myPsiParameterList.getParameters()[i];
      PsiParameter psiParameter = psiParameterList.getParameters()[i];
      if (myPsiParameter == null && psiParameter != null) return false;
      if (myPsiParameter != null && psiParameter == null) return false;
      if (myPsiParameter != null) { // Both must be non-null
        PsiTypeElement myPsiTypeElement = myPsiParameter.getTypeElement();
        PsiTypeElement psiTypeElement = psiParameter.getTypeElement();
        if (myPsiTypeElement == null && psiTypeElement != null) return false;
        if (myPsiTypeElement != null && psiTypeElement == null) return false;
        if (myPsiTypeElement != null && myPsiTypeElement.getType() == psiTypeElement.getType()) return false;
      }
    }

    return true;
  }

  @Override
  public boolean matches(PsiMethod psiMethod, int paramIndex) {
    if (matches(psiMethod)) {
      PsiParameter[] parameters = myPsiMethod.getParameterList().getParameters();
      if (parameters.length <= paramIndex)
        return false;

      PsiParameter parameter = parameters[paramIndex];
      if (parameter.equals(myPsiParameter))
        return true;
      else
        return false;
    }

    return false;
  }

  @Override
  public boolean overlaps(ConditionChecker checker) {
    MethodConditionCheck otherChecker = (MethodConditionCheck) checker;
    if (myPsiClass.equals(otherChecker.myPsiClass) && myPsiMethod.equals(otherChecker.myPsiMethod) && myPsiParameter.equals(otherChecker.myPsiParameter))
      return true;

    return false;
  }

  @Override
  public Type getType() {
    return myType;
  }

  private void validatePsiMethod() {
    PsiElement psiElement = myPsiMethod.getContainingClass();
    if (!(psiElement instanceof PsiClass))
      throw new IllegalArgumentException("PsiMethod " + myPsiMethod + " can not have a null containing class.");

    PsiType returnType = myPsiMethod.getReturnType();
    if (!isAssert()) {
      if (returnType == null)
        throw new IllegalArgumentException("PsiMethod " + myPsiMethod + " has a null return type PsiType.");

      if (returnType != PsiType.BOOLEAN && !returnType.getCanonicalText().equals(Boolean.class.toString())) {
        throw new IllegalArgumentException("PsiMethod " + myPsiMethod + " must have a null return type PsiType of boolean or Boolean.");
      }
    }

    boolean parameterFound = false;
    for (int i = 0; i < myPsiMethod.getParameterList().getParameters().length; i++) {
      if (myPsiParameter.equals(myPsiMethod.getParameterList().getParameters()[i])) {
        parameterFound = true;
        break;
      }
    }

    if (!parameterFound) {
      throw new IllegalArgumentException("PsiMethod " + myPsiMethod + " must have parameter " + getFullyQualifiedName(myPsiParameter));
    }
  }

  private boolean isAssert() {
    return myType == ASSERT_IS_NULL_METHOD || myType == ASSERT_IS_NOT_NULL_METHOD || myType == ASSERT_TRUE_METHOD || myType == ASSERT_FALSE_METHOD;
  }

  private String initClassNameFromPsiMethod() {
    PsiElement psiElement = myPsiMethod.getContainingClass();
    PsiClass psiClass = (PsiClass) psiElement;
    return psiClass.getQualifiedName();
  }

  private String initMethodNameFromPsiMethod() {
    return myPsiMethod.getName();
  }

  private List<String> initParameterNamesFromPsiMethod(PsiParameter selectedParameter) {
    List<String> parameters = new ArrayList<String>();
    for (int i = 0; i < myPsiMethod.getParameterList().getParameters().length; i++) {
      PsiParameter param = myPsiMethod.getParameterList().getParameters()[i];
      String parameter = getFullyQualifiedName(param);
      if (param.equals(selectedParameter))
        parameters.add("*" + parameter + "*");
      else
        parameters.add(parameter);
    }
    return parameters;
  }

  public static String getFullyQualifiedName(PsiParameter psiParameter) {
    PsiTypeElement typeElement = psiParameter.getTypeElement();
    if (typeElement == null)
      throw new RuntimeException("Parameter has null typeElement " + psiParameter.getName());

    PsiType psiType = typeElement.getType();

    return psiType.getCanonicalText() + " " + psiParameter.getName();
  }

  private String initFullName(String className, String methodName, List<String> parameters) {
    String s = className + "." + methodName + "(";
    for (String parameterName : parameters) {
      s += parameterName + ", ";
    }
    s = s.substring(0, s.length() - 2);
    s += ")";
    return s;
  }

  private String initShortName(String methodName, List<String> parameterNames) {
    String shortName = methodName + "(";
    for (String parameterName : parameterNames) {
      if (parameterNames.lastIndexOf(".") > -1)
        shortName += parameterName.substring(parameterName.lastIndexOf(".") + 1) + ", ";
      else
        shortName += parameterName + ", ";
    }
    shortName = shortName.substring(0, shortName.lastIndexOf(", "));
    shortName += ")";
    return shortName;
  }

  @NotNull
  public PsiMethod getPsiMethod() {
    return myPsiMethod;
  }

  public String getShortName() {
    return shortName;
  }

  @NotNull
  public PsiParameter getPsiParameter() {
    return myPsiParameter;
  }


  @Override
  public int compareTo(MethodConditionCheck o) {
    return fullName.compareToIgnoreCase(fullName);
  }

  @Override
  public String toString() {
    return fullName;
  }

  static class Builder {
    private final @NotNull String serializedRepresentation;
    private final @NotNull Project project;
    private final @NotNull Type type;


    Builder(@NotNull String serializedRepresentation, @NotNull Type type, @NotNull Project project) {
      this.serializedRepresentation = serializedRepresentation;
      this.project = project;
      this.type = type;
    }

    private MethodConditionCheck validateFullyQualifiedClassMethodAndParameterNameAndGetPsiMethod(String fullyQualifiedClassMethodAndParameterName) {
      String classNameAndMethodName = parseClassNameAndMethodName(fullyQualifiedClassMethodAndParameterName);

      String className = classNameAndMethodName.substring(0, classNameAndMethodName.lastIndexOf("."));
      String methodName = classNameAndMethodName.substring(classNameAndMethodName.lastIndexOf(".") + 1);

      String allParametersSubString = fullyQualifiedClassMethodAndParameterName.substring(fullyQualifiedClassMethodAndParameterName.indexOf("(") + 1, fullyQualifiedClassMethodAndParameterName.lastIndexOf(")")).trim();
      if (allParametersSubString.isEmpty()) {
        throw new IllegalArgumentException("Name should contain 1+ parameter (between opening and closing parenthesis).  " + fullyQualifiedClassMethodAndParameterName);
      } else if (allParametersSubString.contains("*") && allParametersSubString.indexOf("*") == allParametersSubString.lastIndexOf("*")) {
        throw new IllegalArgumentException("Selected Parameter should be surrounded by asterisks.  " + fullyQualifiedClassMethodAndParameterName);
      }

      String parameterClassAndName = allParametersSubString.substring(allParametersSubString.indexOf("*") + 1, allParametersSubString.lastIndexOf("*")).trim();

      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      if (psiClass == null) {
        throw new IllegalArgumentException("Unable to locate class " + className + " which was parsed from full name " + fullyQualifiedClassMethodAndParameterName);
      }

      List<PsiMethod> psiMethods = findPsiMethodsInPsiClassWithMatchingMethodName(psiClass, methodName);
      if (psiMethods.size() == 0) {
        throw new IllegalArgumentException("Unable to locate method in class " + className + " named " + methodName + ", which was parsed from full name " + fullyQualifiedClassMethodAndParameterName);
      }

      PsiMethod psiMethod = findPsiMethodWithMatchingParameters(psiMethods, allParametersSubString);
      if (psiMethod == null) {
        throw new IllegalArgumentException("Unable to locate method in class " + className + " named " + methodName + " with a parameter named " + parameterClassAndName + " which was parsed from full name " + fullyQualifiedClassMethodAndParameterName + ".  The following methods matched on method name but not parameter name " + psiMethods);
      }

      PsiParameter psiParameter = null;
      for (int i = 0; i < psiMethod.getParameterList().getParameters().length; i++) {
        PsiParameter parameter = psiMethod.getParameterList().getParameters()[i];
        if (parameterClassAndName.equals(getFullyQualifiedName(parameter))) {
          psiParameter = parameter;
          break;
        }
      }

      if (psiParameter == null) {
        throw new IllegalArgumentException("Unable to locate parameter " + parameterClassAndName + " in class " + className + " named " + methodName + ", which was parsed from full name " + fullyQualifiedClassMethodAndParameterName);
      }

      return new MethodConditionCheck(psiMethod, psiParameter, type);
    }

    private String parseClassNameAndMethodName(String fullyQualifiedClassMethodAndParameterName) {
      if (!fullyQualifiedClassMethodAndParameterName.contains("(")) {
        throw new IllegalArgumentException("Name should contain a opening parenthesis.  " + fullyQualifiedClassMethodAndParameterName);
      } else if (!fullyQualifiedClassMethodAndParameterName.contains(")")) {
        throw new IllegalArgumentException("Name should contain a closing parenthesis.  " + fullyQualifiedClassMethodAndParameterName);
      } else if (fullyQualifiedClassMethodAndParameterName.indexOf("(", fullyQualifiedClassMethodAndParameterName.indexOf("(") + 1) > -1) {
        throw new IllegalArgumentException("Name should only contain one opening parenthesis.  " + fullyQualifiedClassMethodAndParameterName);
      } else if (fullyQualifiedClassMethodAndParameterName.indexOf(")", fullyQualifiedClassMethodAndParameterName.indexOf(")") + 1) > -1) {
        throw new IllegalArgumentException("Name should only contain one closing parenthesis.  " + fullyQualifiedClassMethodAndParameterName);
      } else if (fullyQualifiedClassMethodAndParameterName.indexOf(")") < fullyQualifiedClassMethodAndParameterName.indexOf("(")) {
        throw new IllegalArgumentException("Opening parenthesis should precede closing parenthesis.  " + fullyQualifiedClassMethodAndParameterName);
      }

      String classNameAndMethodName = fullyQualifiedClassMethodAndParameterName.substring(0, fullyQualifiedClassMethodAndParameterName.indexOf("("));
      if (!classNameAndMethodName.contains(".")) {
        throw new IllegalArgumentException("Name should contain a dot between the class name and method name (before the opening parenthesis).  " + fullyQualifiedClassMethodAndParameterName);
      }
      return classNameAndMethodName;
    }

    private PsiMethod findPsiMethodWithMatchingParameters(List<PsiMethod> psiMethods, String allParametersSubString) {
      String[] parameterClassAndNameArray = allParametersSubString.split(",");
      List<String> parameterClassToMatch = new ArrayList<String>();
      for (String parameterClassAndName : parameterClassAndNameArray) {
        parameterClassAndName = parameterClassAndName.replace("*", "").trim();
        parameterClassAndName = parameterClassAndName.substring(0, parameterClassAndName.indexOf(" ")).trim();
        parameterClassToMatch.add(parameterClassAndName);
      }

      for (PsiMethod method : psiMethods) {
        List<String> parameterForCurrentMethod = new ArrayList<String>();
        for (int i = 0; i < method.getParameterList().getParameters().length; i++) {
          PsiParameter psiParameter = method.getParameterList().getParameters()[i];
          PsiTypeElement typeElement = psiParameter.getTypeElement();
          if (typeElement == null)
            break;

          PsiType psiType = typeElement.getType();
          parameterForCurrentMethod.add(psiType.getCanonicalText().trim());
        }

        if (parameterForCurrentMethod.equals(parameterClassToMatch))
          return method;
      }

      return null;
    }

    private List<PsiMethod> findPsiMethodsInPsiClassWithMatchingMethodName(PsiClass psiClass, String methodName) {
      List<PsiMethod> psiMethods = new ArrayList<PsiMethod>();
      for (int i = 0; i < psiClass.getMethods().length; i++) {
        PsiMethod possibleMatchPsiMethod = psiClass.getMethods()[i];
        if (methodName.equals(possibleMatchPsiMethod.getName())) {
          psiMethods.add(possibleMatchPsiMethod);
        }
      }
      return psiMethods;
    }

    public MethodConditionCheck build() {
      return validateFullyQualifiedClassMethodAndParameterNameAndGetPsiMethod(serializedRepresentation);
    }
  }
}
