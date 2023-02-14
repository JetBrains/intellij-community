// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature.JDK_INTERNAL_JAVAC_PREVIEW_FEATURE;
import static com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature.JDK_INTERNAL_PREVIEW_FEATURE;

/**
 * This is the base visitor that checks if an element belongs to the preview feature API.
 */
public abstract class PreviewFeatureVisitorBase extends JavaElementVisitor {

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    PsiElement resolved = reference.resolve();

    if (!(resolved instanceof PsiModifierListOwner owner)) return;
    checkPreviewFeature(reference, reference, owner);
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    PsiElement resolved = expression.resolve();

    if (!(resolved instanceof PsiModifierListOwner owner)) return;
    checkPreviewFeature(expression, expression, owner);
  }

  @Override
  public void visitModuleStatement(@NotNull PsiStatement statement) {
    if (statement instanceof PsiRequiresStatement requiresStatement) {
      PsiJavaModule module = requiresStatement.resolve();
      if (module == null) return;

      PsiAnnotation annotation = getPreviewFeatureAnnotation(module);
      HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
      if (feature == null) return;

      String description = JavaBundle.message("inspection.preview.feature.0.is.preview.api.message", module.getName());
      registerProblem(requiresStatement.getReferenceElement(), description, feature, annotation);
    }
    else if (statement instanceof PsiProvidesStatement providesStatement) {
      PsiReferenceList list = providesStatement.getImplementationList();
      if (list == null) return;

      for (PsiJavaCodeReferenceElement element : list.getReferenceElements()) {
        PsiElement resolved = element.resolve();
        if (resolved instanceof PsiClass psiClass) {
          PsiAnnotation annotation = getPreviewFeatureAnnotation(psiClass);
          HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
          if (feature == null) continue;
          String description =
            JavaBundle.message("inspection.preview.feature.0.is.preview.api.message", psiClass.getQualifiedName());
          registerProblem(element, description, feature, annotation);
        }
      }
    }
  }

  /**
   * Participating source code means that such code can access preview feature api in the same package without warnings.
   *
   * @param from the callsite a preview feature API is accessed
   * @param to   the preview feature API that is being accessed
   * @return true if the packages of the callsite and the preview feature element are the same, false otherwise
   */
  private static boolean isParticipating(PsiElement from, PsiElement to) {
    return JavaPsiFacade.getInstance(from.getProject()).arePackagesTheSame(from, to);
  }

  /**
   * The method validates that the language level in the project where an element the context refers to is annotated with
   * either {@link HighlightingFeature#JDK_INTERNAL_PREVIEW_FEATURE} or
   * {@link HighlightingFeature#JDK_INTERNAL_JAVAC_PREVIEW_FEATURE} is sufficient.
   *
   * @param context the element that should be highlighted
   * @param reference the callsite of a preview feature element
   * @param owner an element that should be checked if it's a preview feature
   */
  private void checkPreviewFeature(@NotNull PsiElement context, @NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = getPreviewFeatureAnnotation(owner);
    HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
    if (feature == null) return;
    if (isParticipating(reference, owner)) return;

    @NotNull String name;
    if (owner instanceof PsiMember member) {
      PsiClass className = member.getContainingClass();
      String methodName = member.getName();
      if (member instanceof PsiClass psiClass) {
        name = Objects.requireNonNull(psiClass.getQualifiedName());
      }
      else if (member instanceof PsiMethod method && method.isConstructor()) {
        name = className != null && className.getQualifiedName() != null ? className.getQualifiedName() : reference.getQualifiedName();
      }
      else {
        name = className != null && methodName != null ? className.getQualifiedName() + "#" + methodName : reference.getQualifiedName();
      }
    }
    else {
      name = reference.getQualifiedName();
    }

    String description = JavaBundle.message("inspection.preview.feature.0.is.preview.api.message", name);

    registerProblem(context, description, feature, annotation);
  }

  protected abstract void registerProblem(PsiElement element,
                                          @InspectionMessage String description,
                                          HighlightingFeature feature,
                                          PsiAnnotation annotation);

  /**
   * This method check if the element, its enclosing class(-es) or its jigsaw module is annotated with PreviewFeature.
   * It doesn't take into account the element's package as per
   * <a href="https://mail.openjdk.org/pipermail/compiler-dev/2021-February/016306.html">the mailing list discussion</a>.
   *
   * @param element a PSI element to check if it belongs to the preview feature API.
   * @return the PreviewFeature annotation that describes the preview feature api the element belongs to or null otherwise
   */
  private static PsiAnnotation getPreviewFeatureAnnotation(@NotNull PsiModifierListOwner element) {
    if (element instanceof PsiPackage) return null;
    PsiAnnotation annotation = element.getAnnotation(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE);
    if (annotation == null) {
      annotation = element.getAnnotation(JDK_INTERNAL_PREVIEW_FEATURE);
    }
    if (annotation == null && element instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)element).getContainingClass();
      annotation = containingClass == null ? null : getPreviewFeatureAnnotation(containingClass);
    }
    if (annotation == null && !(element instanceof PsiJavaModule)) {
      PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(element);
      annotation = javaModule == null ? null : getPreviewFeatureAnnotation(javaModule);
    }
    return annotation;
  }
}
