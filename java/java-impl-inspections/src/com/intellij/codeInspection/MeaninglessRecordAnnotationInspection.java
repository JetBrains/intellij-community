// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public final class MeaninglessRecordAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<TargetType> RECORD_TARGETS =
    EnumSet.of(TargetType.RECORD_COMPONENT, TargetType.FIELD, TargetType.METHOD,
               TargetType.PARAMETER, TargetType.TYPE_USE);
  private static final Set<TargetType> ALWAYS_USEFUL_RECORD_TARGETS =
    EnumSet.of(TargetType.RECORD_COMPONENT, TargetType.FIELD, TargetType.TYPE_USE);
  
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORDS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
        PsiClass recordClass = recordComponent.getContainingClass();
        if (recordClass == null) return;
        String name = recordComponent.getName();
        for (PsiAnnotation annotation : recordComponent.getAnnotations()) {
          processAnnotation(recordClass, name, annotation);
        }
      }

      private void processAnnotation(PsiClass recordClass, String name, PsiAnnotation annotation) {
        PsiClass annotationType = annotation.resolveAnnotationType();
        if (annotationType == null) return;
        Set<TargetType> targets = AnnotationTargetUtil.getAnnotationTargets(annotationType);
        if (targets == null || targets.isEmpty()) return;
        targets = EnumSet.copyOf(targets);
        targets.retainAll(RECORD_TARGETS);
        if (targets.isEmpty() || ContainerUtil.exists(ALWAYS_USEFUL_RECORD_TARGETS, targets::contains)) return;
        boolean hasAccessor = false;
        boolean hasCanonicalConstructor = false;
        if (targets.contains(TargetType.METHOD)) {
          for (PsiMethod method : recordClass.findMethodsByName(name, false)) {
            if (method.getParameterList().isEmpty() && method.isPhysical()) {
              hasAccessor = true;
              targets.remove(TargetType.METHOD);
              break;
            }
          }
        }
        if (targets.contains(TargetType.PARAMETER)) {
          PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
          if (constructor != null && JavaPsiRecordUtil.isExplicitCanonicalConstructor(constructor)) {
            hasCanonicalConstructor = true;
            targets.remove(TargetType.PARAMETER);
          }
        }
        if (!targets.isEmpty()) return;
        String message;
        if (hasAccessor && hasCanonicalConstructor) {
          message = JavaBundle.message("inspection.meaningless.record.annotation.message.method.and.parameter");
        }
        else if (hasAccessor) {
          message = JavaBundle.message("inspection.meaningless.record.annotation.message.method");
        }
        else if (hasCanonicalConstructor) {
          message = JavaBundle.message("inspection.meaningless.record.annotation.message.parameter");
        }
        else return;
        holder.problem(annotation, message).fix(new DeleteElementFix(annotation)).register();
      }
    };
  }
}
