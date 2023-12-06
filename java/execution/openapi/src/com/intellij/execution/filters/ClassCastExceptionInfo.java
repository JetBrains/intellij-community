// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassCastExceptionInfo extends ExceptionInfo {
  private static final Pattern CCE_MESSAGE = Pattern.compile("(?:class )?(\\S+) cannot be cast to (?:class )?(\\S+)(?: \\(.+\\))?");
  private final @Nullable String myTargetClass;
  private final @Nullable String myActualClass;

  ClassCastExceptionInfo(int offset, @NotNull String exceptionMessage) {
    super(offset, "java.lang.ClassCastException", exceptionMessage);
    Matcher matcher = CCE_MESSAGE.matcher(exceptionMessage);
    if (matcher.matches()) {
      myTargetClass = matcher.group(2);
      myActualClass = matcher.group(1);
    } else {
      myTargetClass = null;
      myActualClass = null;
    }
  }

  public @Nullable String getActualClass() {
    return myActualClass;
  }

  @Override
  ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement current) {
    if (myTargetClass == null) return null;
    PsiElement e = PsiTreeUtil.nextVisibleLeaf(current);
    if (e instanceof PsiJavaToken && e.textMatches("(") && e.getParent() instanceof PsiTypeCastExpression) {
      PsiTypeElement typeElement = ((PsiTypeCastExpression)e.getParent()).getCastType();
      if (typeElement != null && castClassMatches(typeElement.getType(), myTargetClass)) {
        return onTheSameLineFor(current, typeElement, true);
      }
    }
    e = PsiTreeUtil.prevVisibleLeaf(current);
    if (e instanceof PsiIdentifier && e.getParent() instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      PsiType type;
      if (target instanceof PsiMethod method) {
        type = method.getReturnType();
      }
      else if (target instanceof PsiVariable variable) {
        type = variable.getType();
      }
      else {
        return null;
      }
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (!(psiClass instanceof PsiTypeParameter)) return null;
      // Implicit cast added by compiler
      if (castClassMatches(ref.getType(), myTargetClass)) {
        return onTheSameLineFor(current, e, false);
      }
    }
    return null;
  }

  private static boolean castClassMatches(PsiType type, String className) {
    if (type instanceof PsiPrimitiveType) {
      return className.equals(((PsiPrimitiveType)type).getBoxedTypeName());
    }
    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (castClassMatches(conjunct, className)) return true;
      }
      return false;
    }
    if (type instanceof PsiArrayType) {
      if (className.startsWith("[") && className.length() > 1) {
        PsiType componentType = ((PsiArrayType)type).getComponentType();
        char descriptorChar = className.charAt(1);
        PsiPrimitiveType expected = PsiPrimitiveType.fromJvmTypeDescriptor(descriptorChar);
        if (expected != null) {
          return componentType.equals(expected);
        }
        if (descriptorChar == '[') {
          return castClassMatches(componentType, className.substring(1));
        }
        if (descriptorChar == 'L' && className.charAt(className.length() - 1) == ';') {
          return castClassMatches(componentType, className.substring(2, className.length() - 1));
        }
        return false;
      }
    }
    if (type instanceof PsiClassType) {
      return classTypeMatches(className, (PsiClassType)type, new HashSet<>());
    }
    return true;
  }

  private static boolean classTypeMatches(String className, PsiClassType classType, Set<PsiClass> visited) {
    PsiClass psiClass = PsiUtil.resolveClassInType(classType);
    if (psiClass instanceof PsiTypeParameter) {
      if (!visited.add(psiClass)) {
        return true;
      }
      for (PsiClassType bound : ((PsiTypeParameter)psiClass).getExtendsList().getReferencedTypes()) {
        if (classTypeMatches(className, bound, visited)) return true;
      }
      return className.equals(CommonClassNames.JAVA_LANG_OBJECT); // e.g. cast to Object[] array
    }
    String name = classType.getClassName();
    if (name == null) return true;
    if (!name.equals(StringUtil.substringAfterLast(className, ".")) &&
        !name.equals(StringUtil.substringAfterLast(className, "$"))) {
      return false;
    }
    if (psiClass != null) {
      if (className.equals(psiClass.getQualifiedName())) return true;
      String packageName = StringUtil.getPackageName(className);
      PsiFile psiFile = psiClass.getContainingFile();
      return psiFile instanceof PsiClassOwner && packageName.equals(((PsiClassOwner)psiFile).getPackageName());
    }
    return true;
  }
}
