// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.requireNonNullElse;

/**
 * Utilities to support Java preview feature highlighting
 */
public final class JavaPreviewFeatureUtil {
  /**
   * Internal JDK annotation for preview feature APIs
   */
  public static final @NonNls String JDK_INTERNAL_PREVIEW_FEATURE = "jdk.internal.PreviewFeature";
  public static final @NonNls String JDK_INTERNAL_JAVAC_PREVIEW_FEATURE = "jdk.internal.javac.PreviewFeature";

  /**
   * @param feature preview feature
   * @param ref reference to highlight ({@link PsiJavaCodeReferenceElement} or {@link PsiJavaModuleReferenceElement})
   * @param target target object (module, class, method, field)
   * @param annotation preview annotation
   */
  public record PreviewFeatureUsage(
    @NotNull JavaFeature feature,
    @NotNull PsiElement ref,
    @NotNull PsiModifierListOwner target,
    @NotNull PsiAnnotation annotation
  ) {
    /**
     * @return user-readable name of the reference or target object to be used in the error message
     */
    public @NlsSafe String targetName() {
      if (target instanceof PsiJavaModule module) {
        return module.getName();
      }
      String name = null;
      if (target instanceof PsiMember member) {
        PsiClass containingClass = member.getContainingClass();
        String methodName = member.getName();
        if (member instanceof PsiClass psiClass) {
          name = psiClass.getQualifiedName();
        }
        else if (member instanceof PsiMethod method && method.isConstructor()) {
          if (containingClass != null) {
            name = containingClass.getQualifiedName();
          }
        }
        else {
          if (containingClass != null && methodName != null) {
            name = containingClass.getQualifiedName() + "#" + methodName;
          }
        }
      }
      if (name == null) {
        if (ref instanceof PsiJavaCodeReferenceElement refElement) {
          name = refElement.getQualifiedName();
        }
      }
      return requireNonNullElse(name, ref.getText());
    }

    /**
     * @return true if 
     */
    public boolean isReflective() {
      return Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation(), "reflective"));
    }
  }

  /**
   * @param element element to check (reference or a statement from a module-info file)
   * @return PreviewFeatureUsage object if the element refers to a preview API feature; null otherwise
   */
  public static @Nullable PreviewFeatureUsage getPreviewFeatureUsage(@NotNull PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement refElement) {
      PsiElement resolved = refElement.resolve();
      if (resolved instanceof PsiModifierListOwner owner) {
        return getPreviewFeatureUsage(refElement, owner);
      }
    }
    else if (element instanceof PsiRequiresStatement requiresStatement) {
      PsiJavaModule module = requiresStatement.resolve();
      if (module == null) return null;

      PsiAnnotation annotation = getPreviewFeatureAnnotationInternal(module);
      JavaFeature feature = fromPreviewFeatureAnnotation(annotation);
      if (feature == null) return null;
      PsiJavaModuleReferenceElement moduleRef = requiresStatement.getReferenceElement();
      if (moduleRef != null) {
        return new PreviewFeatureUsage(feature, moduleRef, module, annotation);
      }
    }
    else if (element instanceof PsiProvidesStatement providesStatement) {
      PsiReferenceList list = providesStatement.getImplementationList();
      if (list == null) return null;

      for (PsiJavaCodeReferenceElement ref : list.getReferenceElements()) {
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiClass psiClass) {
          PsiAnnotation annotation = getPreviewFeatureAnnotationInternal(psiClass);
          JavaFeature feature = fromPreviewFeatureAnnotation(annotation);
          if (feature == null) continue;
          return new PreviewFeatureUsage(feature, ref, psiClass, annotation);
        }
      }
    }
    return null;
  }

  /**
   * @see #getPreviewFeatureUsage(PsiElement)
   */
  public static @Nullable PreviewFeatureUsage getPreviewFeatureUsage(@NotNull PsiJavaCodeReferenceElement refElement,
                                                                     @NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = getPreviewFeatureAnnotationInternal(owner);
    JavaFeature feature = fromPreviewFeatureAnnotation(annotation);
    if (feature == null) return null;
    if (isParticipating(refElement, owner)) return null;
    return new PreviewFeatureUsage(feature, refElement, owner, annotation);
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
   * This method check if the element, its enclosing class(-es) or its jigsaw module is annotated with PreviewFeature.
   * It doesn't take into account the element's package as per
   * <a href="https://mail.openjdk.org/pipermail/compiler-dev/2021-February/016306.html">the mailing list discussion</a>.
   *
   * @param element a PSI element to check if it belongs to the preview feature API.
   * @return the PreviewFeature annotation that describes the preview feature api the element belongs to or null otherwise
   */
  private static PsiAnnotation getPreviewFeatureAnnotationInternal(@NotNull PsiModifierListOwner element) {
    if (element instanceof PsiPackage) return null;
    PsiAnnotation annotation = element.getAnnotation(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE);
    if (annotation == null) {
      annotation = element.getAnnotation(JDK_INTERNAL_PREVIEW_FEATURE);
    }
    if (annotation == null && element instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)element).getContainingClass();
      annotation = containingClass == null ? null : getPreviewFeatureAnnotationInternal(containingClass);
    }
    if (annotation == null && !(element instanceof PsiJavaModule)) {
      PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByElement(element);
      annotation = javaModule == null ? null : getPreviewFeatureAnnotationInternal(javaModule);
    }
    return annotation;
  }

  /**
   * @param annotation annotation to get the language feature from
   * @return language feature referenced by a given annotation; null if the annotation is not preview feature annotation.
   * @see #JDK_INTERNAL_PREVIEW_FEATURE
   * @see #JDK_INTERNAL_JAVAC_PREVIEW_FEATURE
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable JavaFeature fromPreviewFeatureAnnotation(@Nullable PsiAnnotation annotation) {
    if (annotation == null) return null;
    if (!annotation.hasQualifiedName(JDK_INTERNAL_PREVIEW_FEATURE) &&
        !annotation.hasQualifiedName(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE)) {
      return null;
    }

    PsiNameValuePair feature = AnnotationUtil.findDeclaredAttribute(annotation, "feature");
    if (feature == null) return null;

    PsiReferenceExpression referenceExpression = tryCast(feature.getDetachedValue(), PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    var referenceName = referenceExpression.getReferenceName();
    return referenceName == null ? null : JavaFeature.convertFromPreviewFeatureName(referenceName);
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiAnnotation getPreviewFeatureAnnotation(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return null;

    PsiAnnotation annotation = getAnnotation(owner);
    if (annotation != null) return annotation;

    if (owner instanceof PsiMember member && !owner.hasModifier(JvmModifier.STATIC)) {
      PsiAnnotation result = getPreviewFeatureAnnotation(member.getContainingClass());
      if (result != null) return result;
    }

    PsiPackage psiPackage = JavaResolveUtil.getContainingPackage(owner);
    if (psiPackage == null) return null;

    PsiAnnotation packageAnnotation = getAnnotation(psiPackage);
    if (packageAnnotation != null) return packageAnnotation;

    PsiJavaModule module = JavaPsiModuleUtil.findDescriptorByElement(owner);
    if (module == null) return null;

    return getAnnotation(module);
  }

  private static PsiAnnotation getAnnotation(@NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = owner.getAnnotation(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE);
    if (annotation != null) return annotation;

    return owner.getAnnotation(JDK_INTERNAL_PREVIEW_FEATURE);
  }
}
