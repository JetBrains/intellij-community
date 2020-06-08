// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class RecaptureTypeMapper extends PsiTypeMapper {
  public static final Key<PsiElement> SELF_REFERENCE = Key.create("SELF_REFERENCE");
  private final Set<PsiClassType> myVisited = ContainerUtil.newIdentityTroveSet();

  @Override
  public PsiType visitType(@NotNull PsiType type) {
    return type;
  }

  @Override
  public PsiType visitClassType(@NotNull PsiClassType classType) {
    if (!myVisited.add(classType)) return classType;
    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (psiClass == null) return classType;
    return new PsiImmediateClassType(psiClass, recapture(substitutor));
  }

  public PsiSubstitutor recapture(PsiSubstitutor substitutor) {
    PsiSubstitutor result = PsiSubstitutor.EMPTY;
    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      PsiType value = entry.getValue();
      result = result.put(entry.getKey(), value == null ? null : mapType(value));
    }
    return result;
  }

  @Override
  public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
    PsiElement context = capturedWildcardType.getContext();
    @Nullable PsiElement original = context.getCopyableUserData(SELF_REFERENCE);
    if (original != null) {
      context = original;
    }
    PsiCapturedWildcardType mapped =
      PsiCapturedWildcardType.create(capturedWildcardType.getWildcard(), context, capturedWildcardType.getTypeParameter());

    mapped.setUpperBound(capturedWildcardType.getUpperBound(false).accept(this));

    return mapped;
  }

  public static void encode(PsiElement expression) {
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiExpression) {
          element.putCopyableUserData(SELF_REFERENCE, element);
        }
        super.visitElement(element);
      }
    });
  }

  public static void clean(PsiElement expression) {
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiExpression) {
          element.putCopyableUserData(SELF_REFERENCE, null);
        }
        super.visitElement(element);
      }
    });
  }
}
