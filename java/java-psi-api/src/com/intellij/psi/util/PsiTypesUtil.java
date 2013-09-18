/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PsiTypesUtil {
  @NonNls private static final Map<String, String> ourUnboxedTypes = new THashMap<String, String>();
  @NonNls private static final Map<String, String> ourBoxedTypes = new THashMap<String, String>();

  static {
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_LONG, "long");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");

    ourBoxedTypes.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    ourBoxedTypes.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    ourBoxedTypes.put("short", CommonClassNames.JAVA_LANG_SHORT);
    ourBoxedTypes.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    ourBoxedTypes.put("long", CommonClassNames.JAVA_LANG_LONG);
    ourBoxedTypes.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    ourBoxedTypes.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    ourBoxedTypes.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @NonNls private static final String GET_CLASS_METHOD = "getClass";

  private PsiTypesUtil() { }

  public static String getDefaultValueOfType(PsiType type) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(componentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    else if (type instanceof PsiPrimitiveType) {
      if (PsiType.BOOLEAN.equals(type)) {
        return PsiKeyword.FALSE;
      }
      else {
        return "0";
      }
    }
    else {
      return PsiKeyword.NULL;
    }
  }

  /**
   * Returns the unboxed type name or parameter.
   * @param type boxed java type name
   * @return unboxed type name if available; same value otherwise
   */
  @Nullable
  public static String unboxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourUnboxedTypes.get(type);
    return s == null? type : s;
  }

  /**
   * Returns the boxed type name or parameter.
   * @param type primitive java type name
   * @return boxed type name if available; same value otherwise
   */
  @Nullable
  public static String boxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  @Nullable
  public static PsiClass getPsiClass(final PsiType psiType) {
    return psiType instanceof PsiClassType? ((PsiClassType)psiType).resolve() : null;
  }

  public static PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  @Nullable
  public static PsiClassType getLowestUpperBoundClassType(@NotNull final PsiDisjunctionType type) {
    final PsiType lub = type.getLeastUpperBound();
    if (lub instanceof PsiClassType) {
      return (PsiClassType)lub;
    }
    else if (lub instanceof PsiIntersectionType) {
      for (PsiType subType : ((PsiIntersectionType)lub).getConjuncts()) {
        if (subType instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType)subType).resolve();
          if (aClass != null && !aClass.isInterface()) {
            return (PsiClassType)subType;
          }
        }
      }
    }
    return null;
  }

  public static PsiType patchMethodGetClassReturnType(@NotNull PsiExpression call,
                                                      @NotNull PsiReferenceExpression methodExpression,
                                                      @NotNull PsiMethod method,
                                                      @Nullable Condition<IElementType> condition,
                                                      @NotNull LanguageLevel languageLevel) {
    //JLS3 15.8.2
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) &&
        GET_CLASS_METHOD.equals(method.getName()) &&
        CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      }
      else if (condition != null) {
        ASTNode parent = call.getNode().getTreeParent();
        while (parent != null && condition.value(parent.getElementType())) {
          parent = parent.getTreeParent();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getInstance(project).getElementFactory().createType((PsiClass)parent.getPsi());
        }
      }
      if (qualifierType != null) {
        PsiClass javaLangClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_CLASS, call.getResolveScope());
        if (javaLangClass != null && javaLangClass.getTypeParameters().length == 1) {
          Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
          map.put(javaLangClass.getTypeParameters()[0], PsiWildcardType.createExtends(call.getManager(), qualifierType));
          PsiSubstitutor substitutor = JavaPsiFacade.getInstance(project).getElementFactory().createSubstitutor(map);
          final PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory()
            .createType(javaLangClass, substitutor, languageLevel);
          final PsiElement parent = call.getParent();
          return parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiMethodCallExpression || parent instanceof PsiExpressionList
                 ? PsiUtil.captureToplevelWildcards(classType, methodExpression) : classType;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getExpectedTypeByParent(PsiExpression methodCall) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    if (parent instanceof PsiVariable) {
      if (PsiUtil.checkSameExpression(methodCall, ((PsiVariable)parent).getInitializer())) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (PsiUtil.checkSameExpression(methodCall, ((PsiAssignmentExpression)parent).getRExpression())) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        return LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getFunctionalInterfaceType());
      }
      else {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
        if (method != null) {
          return method.getReturnType();
        }
      }
    }
    else if (PsiUtil.isCondition(methodCall, parent)) {
      return PsiType.BOOLEAN.getBoxedType(parent);
    }
    return null;
  }
}
