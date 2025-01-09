// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle.message;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorFormatUtil.*;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * All possible Java error kinds
 */
public final class JavaErrorKinds {
  private JavaErrorKinds() {}
  
  public static final JavaErrorKind<PsiElement, @NotNull JavaFeature> UNSUPPORTED_FEATURE =
    new JavaParameterizedErrorKind<>("insufficient.language.level") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, @NotNull JavaFeature feature) {
        String name = feature.getFeatureName();
        String version = JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(element)).getDescription();
        return HtmlChunk.raw(message("insufficient.language.level", name, version));
      }
    };

  public static final JavaSimpleErrorKind<PsiAnnotation> ANNOTATION_NOT_ALLOWED_HERE =
    new JavaSimpleErrorKind<>("annotation.not.allowed.here");
  public static final JavaSimpleErrorKind<PsiPackageStatement> ANNOTATION_NOT_ALLOWED_ON_PACKAGE =
    new JavaSimpleErrorKind<PsiPackageStatement>("annotation.not.allowed.on.package")
      .withAnchor(statement -> requireNonNull(statement.getAnnotationList()));
  public static final JavaSimpleErrorKind<PsiReferenceList> ANNOTATION_MEMBER_THROWS_NOT_ALLOWED =
    new JavaSimpleErrorKind<PsiReferenceList>("annotation.member.may.not.have.throws.list")
      .withAnchor(list -> requireNonNull(list.getFirstChild()));
  public static final JavaSimpleErrorKind<PsiReferenceList> ANNOTATION_NOT_ALLOWED_EXTENDS =
    new JavaSimpleErrorKind<PsiReferenceList>("annotation.may.not.have.extends.list")
      .withAnchor(list -> requireNonNull(list.getFirstChild()));
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
  public static final JavaSimpleErrorKind<PsiNameValuePair> ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING =
    new JavaSimpleErrorKind<>("annotation.attribute.annotation.name.is.missing");
  public static final JavaSimpleErrorKind<PsiAnnotationMemberValue> ANNOTATION_ATTRIBUTE_NON_CLASS_LITERAL =
    new JavaSimpleErrorKind<>("annotation.attribute.non.class.literal");
  public static final JavaSimpleErrorKind<PsiExpression> ANNOTATION_ATTRIBUTE_NON_ENUM_CONSTANT =
    new JavaSimpleErrorKind<>("annotation.attribute.non.enum.constant");
  public static final JavaSimpleErrorKind<PsiExpression> ANNOTATION_ATTRIBUTE_NON_CONSTANT =
    new JavaSimpleErrorKind<>("annotation.attribute.non.constant");
  public static final JavaSimpleErrorKind<PsiTypeElement> ANNOTATION_CYCLIC_TYPE =
    new JavaSimpleErrorKind<>("annotation.cyclic.element.type");
  public static final JavaErrorKind<PsiMethod, PsiMethod> ANNOTATION_MEMBER_CLASH =
    new JavaParameterizedErrorKind<>("annotation.member.clash") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiMethod curMethod, PsiMethod clashMethod) {
        PsiClass containingClass = requireNonNull(clashMethod.getContainingClass());
        return HtmlChunk.raw(message("annotation.member.clash", formatMethod(clashMethod), formatClass(containingClass)));
      }

      @Override
      public @NotNull PsiElement anchor(@NotNull PsiMethod curMethod, PsiMethod clashMethod) {
        return requireNonNull(curMethod.getNameIdentifier());
      }
    };
  public static final JavaErrorKind<PsiTypeElement, PsiType> ANNOTATION_METHOD_INVALID_TYPE =
    new JavaParameterizedErrorKind<>("annotation.member.invalid.type") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiTypeElement element, PsiType type) {
        return HtmlChunk.raw(message("annotation.member.invalid.type", 
                                                                type == null ? null : type.getPresentableText()));
      }
    };
  public static final JavaAnnotationValueErrorKind<PsiAnnotationMemberValue> ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE =
    new JavaAnnotationValueErrorKind<>("annotation.attribute.incompatible.type") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiAnnotationMemberValue value, 
                                            JavaAnnotationValueErrorKind.@NotNull AnnotationValueErrorContext context) {
        String text = value instanceof PsiAnnotation annotation ? requireNonNull(annotation.getNameReferenceElement()).getText() :
                      PsiTypesUtil.removeExternalAnnotations(requireNonNull(((PsiExpression)value).getType())).getInternalCanonicalText();
        return HtmlChunk.raw(message("annotation.attribute.incompatible.type", context.typeText(), text));
      }
    };
  public static final JavaAnnotationValueErrorKind<PsiArrayInitializerMemberValue> ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER =
    new JavaAnnotationValueErrorKind<>("annotation.attribute.illegal.array.initializer") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiArrayInitializerMemberValue element, AnnotationValueErrorContext context) {
        return HtmlChunk.raw(message("annotation.attribute.illegal.array.initializer", context.typeText()));
      }
    };
  public static final JavaErrorKind<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_DUPLICATE =
    new JavaParameterizedErrorKind<>("annotation.attribute.duplicate") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiNameValuePair element, String attribute) {
        return HtmlChunk.raw(message("annotation.attribute.duplicate", attribute));
      }
    };
  public static final JavaErrorKind<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD =
    new JavaParameterizedErrorKind<>("annotation.attribute.unknown.method") {
      @Override
      public @NotNull JavaErrorHighlightType highlightType(@NotNull PsiNameValuePair pair, String s) {
        return pair.getName() == null ? JavaErrorHighlightType.ERROR : JavaErrorHighlightType.WRONG_REF;
      }

      @Override
      public @NotNull PsiElement anchor(@NotNull PsiNameValuePair pair, String s) {
        return requireNonNull(pair.getReference()).getElement();
      }

      @Override
      public @NotNull HtmlChunk description(@NotNull PsiNameValuePair pair, String methodName) {
        return HtmlChunk.raw(message("annotation.attribute.unknown.method", methodName));
      }
    };
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_NOT_FUNCTIONAL_INTERFACE =
    new JavaParameterizedErrorKind<>("lambda.not.a.functional.interface") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, PsiClass aClass) {
        return HtmlChunk.raw(message("lambda.not.a.functional.interface", aClass.getName()));
      }
    };
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_NO_TARGET_METHOD =
    new JavaParameterizedErrorKind<>("lambda.no.target.method.found");
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final JavaErrorKind<PsiElement, PsiClass> LAMBDA_MULTIPLE_TARGET_METHODS =
    new JavaParameterizedErrorKind<>("lambda.multiple.sam.candidates") {
      @Override
      public @NotNull HtmlChunk description(@NotNull PsiElement element, PsiClass aClass) {
        return HtmlChunk.raw(message("lambda.multiple.sam.candidates", aClass.getName()));
      }
    };
  public static final JavaErrorKind<PsiAnnotation, PsiClass> LAMBDA_FUNCTIONAL_INTERFACE_SEALED =
    new JavaParameterizedErrorKind<>("lambda.sealed.functional.interface");
  public static final JavaErrorKind<PsiAnnotation, @NotNull List<PsiAnnotation.@NotNull TargetType>> ANNOTATION_NOT_APPLICABLE =
    new JavaParameterizedErrorKind<>("annotation.not.applicable") {
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
        return HtmlChunk.raw(message(
          "annotation.not.applicable", nameRef != null ? nameRef.getText() : annotation.getText(), target));
      }
    };
  public static final JavaErrorKind<PsiAnnotation, @NotNull List<String>> ANNOTATION_MISSING_ATTRIBUTE =
    new JavaParameterizedErrorKind<>("annotation.missing.attribute") {
      @Override
      public @NotNull PsiElement anchor(@NotNull PsiAnnotation annotation, @NotNull List<String> strings) {
        return requireNonNull(annotation.getNameReferenceElement());
      }

      @Override
      public @NotNull HtmlChunk description(@NotNull PsiAnnotation annotation, @NotNull List<String> attributeNames) {
        return HtmlChunk.raw(message(
          "annotation.missing.attribute", attributeNames.stream().map(attr -> "'" + attr + "'").collect(Collectors.joining(", "))));
      }
    };
  public static final JavaSimpleErrorKind<PsiAnnotation> SAFE_VARARGS_ON_RECORD_COMPONENT =
    new JavaSimpleErrorKind<>("safe.varargs.on.record.component");
  public static final JavaErrorKind<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_FIXED_ARITY =
    new JavaParameterizedErrorKind<>("safe.varargs.on.fixed.arity");
  public static final JavaErrorKind<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_NON_FINAL_METHOD =
    new JavaParameterizedErrorKind<>("safe.varargs.on.non.final.method");
  public static final JavaErrorKind<PsiAnnotation, PsiMethod> OVERRIDE_ON_STATIC_METHOD =
    new JavaParameterizedErrorKind<>("override.on.static.method");
  public static final JavaErrorKind<PsiAnnotation, PsiMethod> OVERRIDE_ON_NON_OVERRIDING_METHOD =
    new JavaParameterizedErrorKind<>("override.on.non-overriding.method");

  public static final JavaSimpleErrorKind<PsiMethod> METHOD_DUPLICATE =
    new JavaSimpleErrorKind<>("method.duplicate") {
      @Override
      public @NotNull TextRange range(@NotNull PsiMethod method, Void unused) {
        return getMethodDeclarationTextRange(method);
      }

      @Override
      public @NotNull HtmlChunk description(@NotNull PsiMethod method, Void unused) {
        PsiClass aClass = requireNonNull(method.getContainingClass());
        return HtmlChunk.raw(message("method.duplicate", formatMethod(method), formatClass(aClass)));
      }
    };
  public static final JavaSimpleErrorKind<PsiReceiverParameter> RECEIVER_WRONG_CONTEXT =
    new JavaSimpleErrorKind<PsiReceiverParameter>("receiver.wrong.context").withAnchor(PsiReceiverParameter::getIdentifier);
  public static final JavaSimpleErrorKind<PsiReceiverParameter> RECEIVER_STATIC_CONTEXT =
    new JavaSimpleErrorKind<PsiReceiverParameter>("receiver.static.context").withAnchor(PsiReceiverParameter::getIdentifier);
  public static final JavaSimpleErrorKind<PsiReceiverParameter> RECEIVER_WRONG_POSITION =
    new JavaSimpleErrorKind<>("receiver.wrong.position");
  public static final JavaErrorKind<PsiReceiverParameter, PsiType> RECEIVER_TYPE_MISMATCH =
    new JavaParameterizedErrorKind<>("receiver.type.mismatch") {
      @Override
      public @NotNull PsiElement anchor(@NotNull PsiReceiverParameter parameter, PsiType type) {
        return requireNonNullElse(parameter.getTypeElement(), parameter);
      }
    };
  public static final JavaErrorKind<PsiReceiverParameter, @Nullable String> RECEIVER_NAME_MISMATCH =
    new JavaParameterizedErrorKind<>("receiver.name.mismatch") {
      @Override
      public @NotNull PsiElement anchor(@NotNull PsiReceiverParameter parameter, @Nullable String s) {
        return parameter.getIdentifier();
      }
    };
}
