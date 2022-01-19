// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature.JDK_INTERNAL_JAVAC_PREVIEW_FEATURE;
import static com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature.JDK_INTERNAL_PREVIEW_FEATURE;

/**
 * This is the base visitor that checks if an element belongs to the preview feature API.
 */
public abstract class PreviewFeatureVisitorBase extends JavaElementVisitor {

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    PsiElement resolved = reference.resolve();

    if (!(resolved instanceof PsiModifierListOwner)) return;

    PsiModifierListOwner owner = (PsiModifierListOwner)resolved;

    checkPreviewFeature(reference, reference, owner);
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    PsiElement resolved = expression.resolve();

    if (!(resolved instanceof PsiModifierListOwner)) return;

    PsiModifierListOwner owner = (PsiModifierListOwner)resolved;

    checkPreviewFeature(expression, expression, owner);
  }

  @Override
  public void visitModuleStatement(PsiStatement statement) {
    if (statement instanceof PsiRequiresStatement) {
      PsiRequiresStatement requiresStatement = (PsiRequiresStatement)statement;
      PsiJavaModule module = requiresStatement.resolve();
      if (module == null) return;

      PsiAnnotation annotation = getPreviewFeatureAnnotation(module);
      HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
      if (feature == null) return;

      String description = JavaBundle.message("inspection.preview.feature.0.is.preview.api.message", module.getName());
      registerProblem(requiresStatement.getReferenceElement(), description, feature, annotation);
    }
    else if (statement instanceof PsiProvidesStatement) {
      PsiProvidesStatement providesStatement = (PsiProvidesStatement)statement;
      PsiReferenceList list = providesStatement.getImplementationList();
      if (list == null) return;

      for (PsiJavaCodeReferenceElement element : list.getReferenceElements()) {
        PsiElement resolved = element.resolve();
        if (resolved instanceof PsiClass) {
          PsiClass psiClass = (PsiClass)resolved;
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
  private void checkPreviewFeature(PsiElement context, PsiJavaCodeReferenceElement reference, PsiModifierListOwner owner) {
    PsiAnnotation annotation = getPreviewFeatureAnnotation(owner);
    HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
    if (feature == null) return;
    if (isParticipating(reference, owner)) return;

    @NotNull String name;
    if (owner instanceof PsiMember) {
      PsiMember member = (PsiMember)owner;
      PsiClass className = member.getContainingClass();
      String methodName = member.getName();
      if (member instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)member;
        name = Objects.requireNonNull(psiClass.getQualifiedName());
      }
      else if (member instanceof PsiMethod && ((PsiMethod)member).isConstructor()) {
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

  private static @Nullable PsiAnnotation getPreviewFeatureAnnotation(@Nullable PsiModifierListOwner owner) {
    return getPreviewFeatureAnnotationOptional(owner).orElse(null);
  }

  /**
   * This method check if the element, its enclosing class(-es) or its jigsaw module is annotated with PreviewFeature.
   * It doesn't take into account the element's package as per
   * <a href="https://mail.openjdk.java.net/pipermail/compiler-dev/2021-February/016306.html">the mailing list discussion</a>.
   *
   * @param element a PSI element to check if it belongs to the preview feature API.
   * @return the PreviewFeature annotation inside of {@link Optional} that describes the preview feature api the element belongs to, {@link Optional#empty()} otherwise
   */
  private static @NotNull Optional<PsiAnnotation> getPreviewFeatureAnnotationOptional(@Nullable PsiModifierListOwner element) {
    if (element == null) return Optional.empty();
    if (element instanceof PsiPackage) return Optional.empty();

    Supplier<PsiClass> containingClass = () -> element instanceof PsiMember ? ((PsiMember)element).getContainingClass() : null;
    Supplier<PsiJavaModule> javaModule = () -> element instanceof PsiJavaModule ? null : JavaModuleGraphUtil.findDescriptorByElement(element);

    return Optional.ofNullable(element.getAnnotation(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE))
      .or(() -> Optional.ofNullable(element.getAnnotation(JDK_INTERNAL_PREVIEW_FEATURE)))
      .or(() -> getPreviewFeatureAnnotationOptional(containingClass.get()))
      .or(() -> getPreviewFeatureAnnotationOptional(javaModule.get()))
      ;
  }
}
