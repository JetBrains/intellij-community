// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * All possible Java error kinds
 */
public final class JavaErrorKinds {
  private JavaErrorKinds() {}
  
  public static final JavaErrorKind<PsiElement, @NotNull JavaFeature> UNSUPPORTED_FEATURE =
    new Parameterized<>("insufficient.language.level") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, @NotNull JavaFeature feature) {
        String name = feature.getFeatureName();
        String version = JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(element)).getDescription();
        return HtmlChunk.raw(JavaCompilationErrorBundle.message("insufficient.language.level", name, version));
      }
    };

  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_HERE =
    new JavaSimpleErrorKind<>("annotation.not.allowed.here");
  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_VAR =
    new JavaSimpleErrorKind<>("annotation.not.allowed.var");
  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_VOID =
    new JavaSimpleErrorKind<>("annotation.not.allowed.void");
  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_CLASS =
    new JavaSimpleErrorKind<>("annotation.not.allowed.class");
  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_REF =
    new JavaSimpleErrorKind<>("annotation.not.allowed.ref");
  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_STATIC =
    new JavaSimpleErrorKind<>("annotation.not.allowed.static");
  public static final JavaSimpleErrorKind<PsiJavaCodeReferenceElement> ANNOTATION_TYPE_EXPECTED =
    new JavaSimpleErrorKind<>("annotation.type.expected");
  public static final JavaSimpleErrorKind<PsiReferenceExpression> ANNOTATION_REPEATED_TARGET =
    new JavaSimpleErrorKind<>("annotation.repeated.target");
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_NOT_FUNCTIONAL_INTERFACE =
    new Parameterized<>("lambda.not.a.functional.interface") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, PsiClass aClass) {
        return HtmlChunk.raw(JavaCompilationErrorBundle.message("lambda.not.a.functional.interface", aClass.getName()));
      }
    };
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_NO_TARGET_METHOD =
    new Parameterized<>("lambda.no.target.method.found") {};
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_MULTIPLE_TARGET_METHODS =
    new Parameterized<>("lambda.multiple.sam.candidates") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, PsiClass aClass) {
        return HtmlChunk.raw(JavaCompilationErrorBundle.message("lambda.multiple.sam.candidates", aClass.getName()));
      }
    };
  public static final JavaErrorKind<PsiAnnotation, PsiClass> LAMBDA_FUNCTIONAL_INTERFACE_SEALED =
    new Parameterized<>("lambda.sealed.functional.interface") {};
  public static final JavaErrorKind<PsiAnnotation, @NotNull List<PsiAnnotation.@NotNull TargetType>> ANNOTATION_NOT_APPLICABLE =
    new Parameterized<>("annotation.not.applicable") {
      @Override
      public void validate(@NotNull PsiAnnotation annotation, @NotNull List<PsiAnnotation.@NotNull TargetType> types)
        throws IllegalArgumentException {
        if (types.isEmpty()) {
          throw new IllegalArgumentException("types must not be empty");
        }
      }

      @Override
      public @NotNull HtmlChunk description(@NotNull PsiAnnotation annotation, @NotNull List<PsiAnnotation.@NotNull TargetType> types) {
        String target = JavaPsiBundle.message("annotation.target." + types.get(0));
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return HtmlChunk.raw(JavaCompilationErrorBundle.message(
          "annotation.not.applicable", nameRef != null ? nameRef.getText() : annotation.getText(), target));
      }
    };
  public static final JavaErrorKind<PsiAnnotation, @NotNull List<String>> ANNOTATION_MISSING_ATTRIBUTE =
    new Parameterized<>("annotation.missing.attribute") {
      @Override
      public @NotNull PsiElement anchor(@NotNull PsiAnnotation annotation, @NotNull List<String> strings) {
        return Objects.requireNonNull(annotation.getNameReferenceElement());
      }

      @Override
      public @NotNull HtmlChunk description(@NotNull PsiAnnotation annotation, @NotNull List<String> attributeNames) {
        return HtmlChunk.raw(JavaCompilationErrorBundle.message(
          "annotation.missing.attribute", attributeNames.stream().map(attr -> "'" + attr + "'").collect(Collectors.joining(", "))));
      }
    };
}
