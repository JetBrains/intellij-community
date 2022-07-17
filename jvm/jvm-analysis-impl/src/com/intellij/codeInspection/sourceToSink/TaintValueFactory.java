// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.codeInspection.restriction.RestrictionInfoFactory;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UastContextKt;

import java.util.Arrays;

class TaintValueFactory implements RestrictionInfoFactory<TaintValue> {

  static final TaintValueFactory INSTANCE = new TaintValueFactory();

  @Override
  public @NotNull TaintValue fromAnnotationOwner(@Nullable PsiAnnotationOwner annotationOwner) {
    if (annotationOwner == null) return TaintValue.UNKNOWN;
    for (PsiAnnotation annotation : annotationOwner.getAnnotations()) {
      TaintValue value = fromAnnotation(annotation);
      if (value != null) return value;
    }
    if (!(annotationOwner instanceof PsiModifierListOwner)) return TaintValue.UNKNOWN;
    return of((PsiModifierListOwner)annotationOwner);
  }

  @Override
  public @NotNull TaintValue fromModifierListOwner(@NotNull PsiModifierListOwner modifierListOwner) {
    AnnotationContext annotationContext = AnnotationContext.fromModifierListOwner(modifierListOwner);
    return of(annotationContext);
  }

  @NotNull TaintValue of(@NotNull AnnotationContext context) {
    PsiType type = context.getType();
    TaintValue info = INSTANCE.fromAnnotationOwner(type);
    if (info != TaintValue.UNKNOWN) return info;
    PsiModifierListOwner owner = context.getOwner();
    if (owner == null) return TaintValue.UNKNOWN;
    info = fromAnnotationOwner(owner.getModifierList());
    if (info != TaintValue.UNKNOWN) return info;
    info = fromExternalAnnotations(owner);
    if (info != TaintValue.UNKNOWN) return info;
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)owner;
      info = of(parameter);
      if (info != TaintValue.UNKNOWN) return info;
      if (parameter.isVarArgs() && type instanceof PsiEllipsisType) {
        info = INSTANCE.fromAnnotationOwner(((PsiEllipsisType)type).getComponentType());
      }
    }
    else if (owner instanceof PsiVariable) {
      ULocalVariable uLocal = UastContextKt.toUElement(owner, ULocalVariable.class);
      if (uLocal != null) {
        PsiElement psi = uLocal.getJavaPsi();
        if (psi instanceof PsiAnnotationOwner) {
          info = INSTANCE.fromAnnotationOwner((PsiAnnotationOwner)psi);
        }
      }
    }
    if (info.getKind() != RestrictionInfo.RestrictionInfoKind.KNOWN) {
      info = context.secondaryItems().map(item -> fromAnnotationOwner(item.getModifierList()))
        .filter(inf -> inf != TaintValue.UNKNOWN).findFirst().orElse(info);
    }
    if (info == TaintValue.UNKNOWN) {
      PsiMember member =
        ObjectUtils.tryCast(owner instanceof PsiParameter ? ((PsiParameter)owner).getDeclarationScope() : owner, PsiMember.class);
      if (member != null) {
        info = of(member);
      }
    }
    return info;
  }

  private static @NotNull TaintValue fromExternalAnnotations(@NotNull PsiModifierListOwner owner) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(owner.getProject());
    PsiAnnotation[] annotations = annotationsManager.findExternalAnnotations(owner);
    if (annotations == null) return TaintValue.UNKNOWN;
    return Arrays.stream(annotations)
      .map(a -> fromAnnotation(a)).filter(a -> a != null)
      .findFirst().orElse(TaintValue.UNKNOWN);
  }

  static @NotNull TaintValue of(@NotNull PsiModifierListOwner annotationOwner) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(annotationOwner, TaintValue.NAMES, false);
    if (annotation == null) return TaintValue.UNKNOWN;
    TaintValue value = fromAnnotation(annotation);
    return value == null ? TaintValue.UNKNOWN : value;
  }

  private static @NotNull TaintValue of(@NotNull PsiMember member) {
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      TaintValue classInfo = INSTANCE.fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != TaintValue.UNKNOWN) {
        return classInfo;
      }
      containingClass = containingClass.getContainingClass();
    }
    return TaintValue.UNKNOWN;
  }

  private static @Nullable TaintValue fromAnnotation(@NotNull PsiAnnotation annotation) {
    if (annotation.hasQualifiedName(TaintValue.TAINTED.getAnnotationName())) {
      return TaintValue.TAINTED;
    }
    if (annotation.hasQualifiedName(
      TaintValue.UNTAINTED.getAnnotationName())) {
      return TaintValue.UNTAINTED;
    }
    return null;
  }
}
