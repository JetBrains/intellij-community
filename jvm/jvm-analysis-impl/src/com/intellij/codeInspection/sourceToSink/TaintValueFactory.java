// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.codeInspection.restriction.RestrictionInfoFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UastContextKt;

import java.util.*;

class TaintValueFactory implements RestrictionInfoFactory<TaintValue> {

  public static final String JAVAX_ANNOTATION_UNTAINTED = "javax.annotation.Untainted";
  @NotNull
  private final Set<String> myTaintedAnnotations;
  @NotNull
  private final Set<String> myUnTaintedAnnotations;

  @Nullable
  private final String firstAnnotation;
  @NotNull
  private final UntaintedContext myContext;

  TaintValueFactory(@NotNull UntaintedContext context) {
    this.myTaintedAnnotations = new HashSet<>(context.taintedAnnotations);
    this.myUnTaintedAnnotations = new HashSet<>(context.unTaintedAnnotations);
    this.firstAnnotation = context.firstAnnotation();
    this.myContext = context;
  }

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
    TaintValue info = fromAnnotationOwner(type);
    if (info != TaintValue.UNKNOWN) return info;
    PsiModifierListOwner owner = context.getOwner();
    if (owner == null) return TaintValue.UNKNOWN;
    info = fromAnnotationOwner(owner.getModifierList());
    if (info != TaintValue.UNKNOWN) return info;
    info = fromExternalAnnotations(owner);
    if (info != TaintValue.UNKNOWN) return info;
    if (owner instanceof PsiParameter parameter) {
      info = of(parameter);
      if (info != TaintValue.UNKNOWN) return info;
      if (parameter.isVarArgs() && type instanceof PsiEllipsisType) {
        info = fromAnnotationOwner(((PsiEllipsisType)type).getComponentType());
      }
    }
    else if (owner instanceof PsiVariable) {
      ULocalVariable uLocal = UastContextKt.toUElement(owner, ULocalVariable.class);
      if (uLocal != null) {
        PsiElement psi = uLocal.getJavaPsi();
        if (psi instanceof PsiAnnotationOwner) {
          info = fromAnnotationOwner((PsiAnnotationOwner)psi);
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

  private @NotNull TaintValue fromExternalAnnotations(@NotNull PsiModifierListOwner owner) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(owner.getProject());
    PsiAnnotation[] annotations = annotationsManager.findExternalAnnotations(owner);
    if (annotations == null) return TaintValue.UNKNOWN;
    return Arrays.stream(annotations)
      .map(a -> fromAnnotation(a)).filter(a -> a != null)
      .findFirst().orElse(TaintValue.UNKNOWN);
  }

  private @NotNull TaintValue of(@NotNull PsiModifierListOwner annotationOwner) {
    HashSet<String> allNames = new HashSet<>();
    allNames.addAll(myUnTaintedAnnotations);
    allNames.addAll(myTaintedAnnotations);
    PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(annotationOwner, allNames, false);
    if (annotation == null) return TaintValue.UNKNOWN;
    TaintValue value = fromAnnotation(annotation);
    return value == null ? TaintValue.UNKNOWN : value;
  }

  private @NotNull TaintValue of(@NotNull PsiMember member) {
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      TaintValue classInfo = fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != TaintValue.UNKNOWN) {
        return classInfo;
      }
      containingClass = containingClass.getContainingClass();
    }
    return TaintValue.UNKNOWN;
  }

  private @Nullable TaintValue fromAnnotation(@NotNull PsiAnnotation annotation) {
    String annotationQualifiedName = annotation.getQualifiedName();

    TaintValue fromJsr = processJsr(annotationQualifiedName, annotation);
    if (fromJsr != null) return fromJsr;

    if (myTaintedAnnotations.contains(annotationQualifiedName)) {
      return TaintValue.TAINTED;
    }
    if (myUnTaintedAnnotations.contains(annotationQualifiedName)) {
      return TaintValue.UNTAINTED;
    }
    return null;
  }

  @Nullable
  private TaintValue processJsr(@Nullable String qualifiedName, @NotNull PsiAnnotation annotation) {
    if (qualifiedName == null) {
      return null;
    }
    if (!qualifiedName.equals(JAVAX_ANNOTATION_UNTAINTED)) {
      return null;
    }
    if (!myUnTaintedAnnotations.contains(JAVAX_ANNOTATION_UNTAINTED)) {
      return null;
    }
    PsiAnnotationMemberValue when = annotation.findAttributeValue("when");
    if (when == null) {
      return null;
    }
    return when.textMatches("ALWAYS") ? TaintValue.UNTAINTED : null;
  }

  public @Nullable TaintValue fromAnnotation(@Nullable PsiElement target) {
    PsiType type = target == null ? null : PsiUtil.getTypeByPsiElement(target);
    if (type == null) return null;
    if (target instanceof PsiClass) return null;
    if (target instanceof PsiModifierListOwner owner) {
      TaintValue taintValue = fromModifierListOwner(owner);
      if (taintValue == TaintValue.UNKNOWN) taintValue = of(owner);
      if (taintValue != TaintValue.UNKNOWN) return taintValue;
    }
    return fromAnnotationOwner(type);
  }

  @Nullable
  public String getAnnotation() {
    return firstAnnotation;
  }

  @NotNull
  public Set<PsiAnnotation.TargetType> getAnnotationTarget(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (firstAnnotation == null) {
      return Set.of();
    }
    PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(firstAnnotation, scope);
    if (annotationClass == null) {
      return Set.of();
    }
    Set<PsiAnnotation.TargetType> targets = AnnotationTargetUtil.getAnnotationTargets(annotationClass);
    return targets == null ? Set.of() : targets;
  }

  record UntaintedContext(@NotNull List<String> taintedAnnotations,
                          @NotNull List<String> unTaintedAnnotations,
                          @Nullable String firstAnnotation,
                          @NotNull List<String> methodClass, @NotNull List<String> methodPatterns,
                          @NotNull List<String> fieldClass, @NotNull List<String> fieldPatterns) {

    public UntaintedContext copy() {
      return new UntaintedContext(new ArrayList<>(taintedAnnotations), new ArrayList<>(unTaintedAnnotations),
                                  firstAnnotation,
                                  new ArrayList<>(methodClass), new ArrayList<>(methodPatterns),
                                  new ArrayList<>(fieldClass), new ArrayList<>(fieldPatterns));
    }
  }

  @NotNull
  UntaintedContext getContext() {
    return myContext;
  }
}
