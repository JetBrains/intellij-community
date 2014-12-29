/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaHighlightUtil {
  public static boolean isSerializable(@NotNull PsiClass aClass) {
    return isSerializable(aClass, "java.io.Serializable");
  }

  public static boolean isSerializable(@NotNull PsiClass aClass,
                                       String serializableClassName) {
    Project project = aClass.getManager().getProject();
    PsiClass serializableClass = JavaPsiFacade.getInstance(project).findClass(serializableClassName, aClass.getResolveScope());
    return serializableClass != null && aClass.isInheritor(serializableClass, true);
  }

  public static boolean isSerializationRelatedMethod(PsiMethod method, PsiClass containingClass) {
    if (containingClass == null) return false;
    if (method.isConstructor()) {
      if (isSerializable(containingClass, "java.io.Externalizable") && 
          method.getParameterList().getParametersCount() == 0 &&
          method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return true;
      }
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    @NonNls String name = method.getName();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType returnType = method.getReturnType();
    if ("readObjectNoData".equals(name)) {
      return parameters.length == 0 && TypeConversionUtil.isVoidType(returnType) && isSerializable(containingClass);
    }
    if ("readObject".equals(name)) {
      return parameters.length == 1
             && parameters[0].getType().equalsToText("java.io.ObjectInputStream")
             && TypeConversionUtil.isVoidType(returnType) && method.hasModifierProperty(PsiModifier.PRIVATE)
             && isSerializable(containingClass);
    }
    if ("readResolve".equals(name)) {
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
             && (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
    }
    if ("writeReplace".equals(name)) {
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
             && (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
    }
    if ("writeObject".equals(name)) {
      return parameters.length == 1
             && TypeConversionUtil.isVoidType(returnType)
             && parameters[0].getType().equalsToText("java.io.ObjectOutputStream")
             && method.hasModifierProperty(PsiModifier.PRIVATE)
             && isSerializable(containingClass);
    }
    return false;
  }

  @NotNull
  public static String formatType(@Nullable PsiType type) {
    return type == null ? PsiKeyword.NULL : type.getInternalCanonicalText();
  }

  @Nullable
  private static PsiType getArrayInitializerType(@NotNull PsiArrayInitializerExpression element) {
    PsiType typeCheckResult = sameType(element.getInitializers());
    return typeCheckResult != null ? typeCheckResult.createArrayType() : null;
  }

  @Nullable
  public static PsiType sameType(@NotNull PsiExpression[] expressions) {
    PsiType type = null;
    for (PsiExpression expression : expressions) {
      final PsiType currentType;
      if (expression instanceof PsiArrayInitializerExpression) {
        currentType = getArrayInitializerType((PsiArrayInitializerExpression)expression);
      }
      else {
        currentType = expression.getType();
      }
      if (type == null) {
        type = currentType;
      }
      else if (!type.equals(currentType)) {
        return null;
      }
    }
    return type;
  }

  @NotNull
  public static String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                      PsiFormatUtilBase.SHOW_TYPE);
  }

  public static boolean isSuperOrThisCall(PsiStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof PsiExpressionStatement)) return false;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
    if (testForSuper) {
      if ("super".equals(methodExpression.getText())) return true;
    }
    if (testForThis) {
      if ("this".equals(methodExpression.getText())) return true;
    }

    return false;
  }

  /**
   * return all constructors which are referred from this constructor by
   *  this (...) at the beginning of the constructor body
   * @return referring constructor
   */
  @Nullable public static List<PsiMethod> getChainedConstructors(PsiMethod constructor) {
    final ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.visitedConstructors != null) info.visitedConstructors.remove(constructor);
    return info.visitedConstructors;
  }

  static void visitConstructorChain(PsiMethod constructor, @NotNull ConstructorVisitorInfo info) {
    while (true) {
      if (constructor == null) return;
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) return;
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) return;
      final PsiStatement statement = statements[0];
      final PsiElement element = new PsiMatcherImpl(statement)
          .dot(PsiMatchers.hasClass(PsiExpressionStatement.class))
          .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
          .firstChild(PsiMatchers.hasClass(PsiReferenceExpression.class))
          .firstChild(PsiMatchers.hasClass(PsiKeyword.class))
          .dot(PsiMatchers.hasText(PsiKeyword.THIS))
          .parent(null)
          .parent(null)
          .getElement();
      if (element == null) return;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return;
      if (info.visitedConstructors != null && info.visitedConstructors.contains(method)) {
        info.recursivelyCalledConstructor = method;
        return;
      }
      if (info.visitedConstructors == null) info.visitedConstructors = new ArrayList<PsiMethod>(5);
      info.visitedConstructors.add(method);
      constructor = method;
    }
  }

  static class ConstructorVisitorInfo {
    List<PsiMethod> visitedConstructors;
    PsiMethod recursivelyCalledConstructor;
  }
}
