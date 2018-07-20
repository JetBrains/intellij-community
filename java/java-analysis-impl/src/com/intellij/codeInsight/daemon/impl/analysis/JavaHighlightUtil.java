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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaHighlightUtil {
  public static boolean isSerializable(@NotNull PsiClass aClass) {
    return isSerializable(aClass, "java.io.Serializable");
  }

  public static boolean isSerializable(@NotNull PsiClass aClass, @NotNull String serializableClassName) {
    Project project = aClass.getManager().getProject();
    PsiClass serializableClass = JavaPsiFacade.getInstance(project).findClass(serializableClassName, aClass.getResolveScope());
    return serializableClass != null && aClass.isInheritor(serializableClass, true);
  }

  public static boolean isSerializationRelatedMethod(@NotNull PsiMethod method, @Nullable PsiClass containingClass) {
    if (containingClass == null) return false;
    if (method.isConstructor()) {
      if (isSerializable(containingClass, "java.io.Externalizable") && 
          method.getParameterList().isEmpty() &&
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

  public static boolean isSuperOrThisCall(@NotNull PsiStatement statement, boolean testForSuper, boolean testForThis) {
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
  @NotNull
  public static List<PsiMethod> getChainedConstructors(@NotNull PsiMethod constructor) {
    final ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.visitedConstructors != null) info.visitedConstructors.remove(constructor);
    return ObjectUtils.notNull(info.visitedConstructors, Collections.emptyList());
  }

  static void visitConstructorChain(@NotNull PsiMethod entry, @NotNull ConstructorVisitorInfo info) {
    PsiMethod constructor = entry;
    while (true) {
      PsiMethodCallExpression methodCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (!JavaPsiConstructorUtil.isChainedConstructorCall(methodCall)) return;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return;
      if (info.visitedConstructors != null && info.visitedConstructors.contains(method)) {
        info.recursivelyCalledConstructor = method;
        return;
      }
      if (info.visitedConstructors == null) info.visitedConstructors = new ArrayList<>(5);
      info.visitedConstructors.add(method);
      constructor = method;
    }
  }

  @Nullable
  public static String checkPsiTypeUseInContext(@NotNull PsiType type, @NotNull PsiElement context) {
    if (type instanceof PsiPrimitiveType) return null;
    if (type instanceof PsiArrayType) return checkPsiTypeUseInContext(((PsiArrayType) type).getComponentType(), context);
    if (PsiUtil.resolveClassInType(type) != null) return null;
    if (type instanceof PsiClassType) return checkClassType((PsiClassType)type, context);
    return "Invalid Java type";
  }

  @NotNull
  private static String checkClassType(@NotNull PsiClassType type, @NotNull PsiElement context) {
    String className = PsiNameHelper.getQualifiedClassName(type.getCanonicalText(false), true);
    if (classExists(context, className)) {
      return getClassInaccessibleMessage(context, className);
    }
    return "Invalid Java type";
  }

  private static boolean classExists(@NotNull PsiElement context, @NotNull String className) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(className, GlobalSearchScope.allScope(context.getProject())) != null;
  }

  @NotNull
  private static String getClassInaccessibleMessage(@NotNull PsiElement context, @NotNull String className) {
    Module module = ModuleUtilCore.findModuleForPsiElement(context);
    return "Class '" + className + "' is not accessible " + (module == null ? "here" : "from module '" + module.getName() + "'");
  }

  static class ConstructorVisitorInfo {
    List<PsiMethod> visitedConstructors;
    PsiMethod recursivelyCalledConstructor;
  }
}
