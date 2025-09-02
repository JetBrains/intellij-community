// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaHighlightUtil {
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
      return isSerializable(containingClass, "java.io.Externalizable") &&
             method.getParameterList().isEmpty() &&
             method.hasModifierProperty(PsiModifier.PUBLIC);
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    @NonNls String name = method.getName();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if ("readObjectNoData".equals(name)) {
      PsiType returnType = method.getReturnType();
      return parameters.length == 0 && TypeConversionUtil.isVoidType(returnType) && isSerializable(containingClass);
    }
    if ("readObject".equals(name)) {
      PsiType returnType = method.getReturnType();
      return parameters.length == 1
             && parameters[0].getType().equalsToText("java.io.ObjectInputStream")
             && TypeConversionUtil.isVoidType(returnType) && method.hasModifierProperty(PsiModifier.PRIVATE)
             && isSerializable(containingClass);
    }
    if ("readResolve".equals(name)) {
      PsiType returnType = method.getReturnType();
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
             && (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
    }
    if ("writeReplace".equals(name)) {
      PsiType returnType = method.getReturnType();
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
             && (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
    }
    if ("writeObject".equals(name)) {
      PsiType returnType = method.getReturnType();
      return parameters.length == 1
             && TypeConversionUtil.isVoidType(returnType)
             && parameters[0].getType().equalsToText("java.io.ObjectOutputStream")
             && method.hasModifierProperty(PsiModifier.PRIVATE)
             && isSerializable(containingClass);
    }
    return false;
  }

  public static @NotNull String formatType(@Nullable PsiType type) {
    return type == null ? JavaKeywords.NULL : PsiTypesUtil.removeExternalAnnotations(type).getInternalCanonicalText();
  }

  private static @Nullable PsiType getArrayInitializerType(@NotNull PsiArrayInitializerExpression element) {
    PsiType typeCheckResult = sameType(element.getInitializers());
    return typeCheckResult != null ? typeCheckResult.createArrayType() : null;
  }

  public static @Nullable PsiType sameType(PsiExpression @NotNull [] expressions) {
    PsiType type = null;
    for (PsiExpression expression : expressions) {
      PsiType currentType;
      if (expression instanceof PsiArrayInitializerExpression initializerExpression) {
        currentType = getArrayInitializerType(initializerExpression);
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

  public static @NotNull @NlsSafe String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                      PsiFormatUtilBase.SHOW_TYPE);
  }

  public static boolean isSuperOrThisCall(@NotNull PsiStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) return false;
    PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiMethodCallExpression callExpression)) return false;
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    if (testForSuper) {
      if ("super".equals(methodExpression.getText())) return true;
    }
    return testForThis && "this".equals(methodExpression.getText());
  }

  /**
   * return all constructors which are referred from this constructor by
   *  this (...) at the beginning of the constructor body
   * @return referring constructor
   * @deprecated use {@link JavaPsiConstructorUtil#getChainedConstructors(PsiMethod)} instead
   */
  @Deprecated
  public static @NotNull List<PsiMethod> getChainedConstructors(@NotNull PsiMethod constructor) {
    return JavaPsiConstructorUtil.getChainedConstructors(constructor);
  }
  
  public static @Nullable @Nls String checkPsiTypeUseInContext(@NotNull PsiType type, @NotNull PsiElement context) {
    if (type instanceof PsiPrimitiveType) return null;
    if (type instanceof PsiArrayType arrayType) return checkPsiTypeUseInContext(arrayType.getComponentType(), context);
    if (PsiUtil.resolveClassInType(type) != null) return null;
    if (type instanceof PsiClassType classType) return checkClassType(classType, context);
    return invalidJavaTypeMessage();
  }

  public static @NotNull @Nls String invalidJavaTypeMessage() {
    return JavaAnalysisBundle.message("error.message.invalid.java.type");
  }

  private static @NotNull @Nls String checkClassType(@NotNull PsiClassType type, @NotNull PsiElement context) {
    String className = PsiNameHelper.getQualifiedClassName(type.getCanonicalText(false), true);
    if (classExists(context, className)) {
      return getClassInaccessibleMessage(context, className);
    }
    return invalidJavaTypeMessage();
  }

  private static boolean classExists(@NotNull PsiElement context, @NotNull String className) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(className, GlobalSearchScope.allScope(context.getProject())) != null;
  }

  private static @NotNull @Nls String getClassInaccessibleMessage(@NotNull PsiElement context, @NotNull String className) {
    Module module = ModuleUtilCore.findModuleForPsiElement(context);
    if (module == null) {
      return JavaAnalysisBundle.message("message.class.inaccessible", className);
    }
    return JavaAnalysisBundle.message("message.class.inaccessible.from.module", className, module.getName());
  }
}
