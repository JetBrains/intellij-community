// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
  public static PsiType sameType(PsiExpression @NotNull [] expressions) {
    PsiType type = null;
    for (PsiExpression expression : expressions) {
      PsiType currentType;
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
  public static @NlsSafe String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                      PsiFormatUtilBase.SHOW_TYPE);
  }

  public static boolean isSuperOrThisCall(@NotNull PsiStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof PsiExpressionStatement)) return false;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
    if (testForSuper) {
      if ("super".equals(methodExpression.getText())) return true;
    }
    return testForThis && "this".equals(methodExpression.getText());
  }

  /**
   * return all constructors which are referred from this constructor by
   *  this (...) at the beginning of the constructor body
   * @return referring constructor
   */
  @NotNull
  public static List<PsiMethod> getChainedConstructors(@NotNull PsiMethod constructor) {
    ConstructorVisitorInfo info = new ConstructorVisitorInfo();
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
  public static @Nls String checkPsiTypeUseInContext(@NotNull PsiType type, @NotNull PsiElement context) {
    if (type instanceof PsiPrimitiveType) return null;
    if (type instanceof PsiArrayType) return checkPsiTypeUseInContext(((PsiArrayType) type).getComponentType(), context);
    if (PsiUtil.resolveClassInType(type) != null) return null;
    if (type instanceof PsiClassType) return checkClassType((PsiClassType)type, context);
    return invalidJavaTypeMessage();
  }

  @NotNull
  @Nls
  public static String invalidJavaTypeMessage() {
    return JavaAnalysisBundle.message("error.message.invalid.java.type");
  }

  @NotNull
  private static @Nls String checkClassType(@NotNull PsiClassType type, @NotNull PsiElement context) {
    String className = PsiNameHelper.getQualifiedClassName(type.getCanonicalText(false), true);
    if (classExists(context, className)) {
      return getClassInaccessibleMessage(context, className);
    }
    return invalidJavaTypeMessage();
  }

  private static boolean classExists(@NotNull PsiElement context, @NotNull String className) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(className, GlobalSearchScope.allScope(context.getProject())) != null;
  }

  @NotNull
  @Nls
  private static String getClassInaccessibleMessage(@NotNull PsiElement context, @NotNull String className) {
    Module module = ModuleUtilCore.findModuleForPsiElement(context);
    if (module == null) {
      return JavaAnalysisBundle.message("message.class.inaccessible", className);
    }
    return JavaAnalysisBundle.message("message.class.inaccessible.from.module", className, module.getName());
  }

  /**
   * @return true if file correspond to the shebang script
   */
  public static boolean isJavaHashBangScript(@NotNull PsiFile containingFile) {
    if (!(containingFile instanceof PsiJavaFile)) return false;
    if (containingFile instanceof PsiFileEx && !((PsiFileEx)containingFile).isContentsLoaded()) {
      VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile.isInLocalFileSystem()) {
        try {
          // don't build PSI when not yet loaded -> time for scanning scope from 18 seconds to 8 seconds on IntelliJ project
          return VfsUtilCore.loadText(vFile, 5).startsWith("#!");
        }
        catch (IOException e) {
          return false;
        }
      }
    }
    PsiElement firstChild = containingFile.getFirstChild();
    if (firstChild instanceof PsiImportList && firstChild.getTextLength() == 0) {
      PsiElement sibling = firstChild.getNextSibling();
      if (sibling instanceof PsiClass) {
        firstChild = sibling.getFirstChild();
      }
    }
    return firstChild instanceof PsiComment &&
           ((PsiComment)firstChild).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT &&
           firstChild.getText().startsWith("#!");
  }

  static class ConstructorVisitorInfo {
    List<PsiMethod> visitedConstructors;
    PsiMethod recursivelyCalledConstructor;
  }
}
