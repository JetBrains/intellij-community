// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.codeInspection.restriction.RestrictionInfoBuilder;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UastContextKt;

class TaintValueBuilder implements RestrictionInfoBuilder<TaintValue> {

  static final TaintValueBuilder INSTANCE = new TaintValueBuilder();

  @Override
  public @NotNull TaintValue fromAnnotationOwner(@Nullable PsiAnnotationOwner annotationOwner) {
    if (annotationOwner == null) return TaintValue.Unknown;
    for (PsiAnnotation annotation : annotationOwner.getAnnotations()) {
      TaintValue value = fromAnnotation(annotation);
      if (value != null) return value;
    }
    if (!(annotationOwner instanceof PsiModifierListOwner)) return TaintValue.Unknown;
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
    if (info != TaintValue.Unknown) return info;
    PsiModifierListOwner owner = context.getOwner();
    if (owner == null) return TaintValue.Unknown;
    info = fromAnnotationOwner(owner.getModifierList());
    if (info != TaintValue.Unknown) return info;
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)owner;
      info = of(parameter);
      if (info != TaintValue.Unknown) return info;
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
    if (info instanceof RestrictionInfo.Unspecified) {
      info = context.secondaryItems().map(item -> fromAnnotationOwner(item.getModifierList()))
        .filter(inf -> inf != TaintValue.Unknown).findFirst().orElse(info);
    }
    if (info == TaintValue.Unknown) {
      PsiMember member =
        ObjectUtils.tryCast(owner instanceof PsiParameter ? ((PsiParameter)owner).getDeclarationScope() : owner, PsiMember.class);
      if (member != null) {
        info = of(member);
      }
    }
    return info;
  }

  @NotNull
  static TaintValue of(PsiModifierListOwner annotationOwner) {
    PsiAnnotation annotation =
      AnnotationUtil.findAnnotationInHierarchy(annotationOwner, TaintValue.NAMES, false);
    if (annotation == null) return TaintValue.Unknown;
    TaintValue value = fromAnnotation(annotation);
    return value == null ? TaintValue.Unknown : value;
  }

  private static TaintValue of(PsiMember member) {
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      TaintValue classInfo = INSTANCE.fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != TaintValue.Unknown) {
        return classInfo;
      }
      containingClass = containingClass.getContainingClass();
    }
    return TaintValue.Unknown;
  }

  private static @Nullable TaintValue fromAnnotation(@NotNull PsiAnnotation annotation) {
    if (annotation.hasQualifiedName(TaintValue.Tainted.getName())) {
      return TaintValue.Tainted;
    }
    if (annotation.hasQualifiedName(
      TaintValue.Untainted.getName())) {
      return TaintValue.Untainted;
    }
    return null;
  }
}
