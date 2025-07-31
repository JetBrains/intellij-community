// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.core.JavaPreviewFeatureUtil;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind.Parameterized;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind.Simple;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.HtmlChunk.Element;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle.message;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorFormatUtil.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * All possible Java error kinds.
 * <p>
 * The constants generally should be named as the corresponding properties from {@link JavaCompilationErrorBundle}, 
 * uppercased and having dots replaced with underscores.
 * <p>
 * While not all the names follow the convention described below, it's preferred to follow. In general, they
 * should not read like an English sentence. Instead, the first part should denote a group of errors (like statement,
 * or construct the error relates to), optionally followed by a more specific subgroup, 
 * and the rest should describe the situation shortly (no prepositions, etc.). 
 * Avoid abbreviating words (e.g., IMPL instead of IMPLEMENTATION, etc.)
 * <p>
 * An example of a good name: ANNOTATION_ATTRIBUTE_NAME_MISSING. Here, ANNOTATION is a group, ATTRIBUTE is a subgroup,
 * and NAME_MISSING is the description.
 */
public final class JavaErrorKinds {
  private JavaErrorKinds() {}

  public static final Parameterized<PsiElement, @NotNull JavaFeature> UNSUPPORTED_FEATURE =
    parameterized(PsiElement.class, JavaFeature.class, "insufficient.language.level")
      .withDescription((element, feature) -> {
        String name = feature.getFeatureName();
        String version = JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(element)).getDescription();
        return message("insufficient.language.level", name, version);
      })
      .withRange((element, feature) -> {
        if (feature == JavaFeature.TEXT_BLOCK_ESCAPES && element instanceof PsiLiteralExpression literalExpression) {
          return PsiLiteralUtil.findSlashS(literalExpression.getText());
        }
        return null;
      });
  public static final Parameterized<PsiElement, @NotNull TextRange> ILLEGAL_UNICODE_ESCAPE =
    parameterized(PsiElement.class, TextRange.class, "illegal.unicode.escape")
      .withRange((psi, range) -> range);
  public static final Parameterized<PsiElement, @NotNull Character> ILLEGAL_CHARACTER =
    parameterized(PsiElement.class, Character.class, "illegal.character")
      .withDescription((e, c) -> {
        boolean printable = StringUtil.isPrintableUnicode(c) && !Character.isSpaceChar(c);
        @NlsSafe String hex = String.format("U+%04X", (int)c);
        return message("illegal.character", printable ? c + " (" + hex + ")" : hex);
      });
  public static final Simple<PsiErrorElement> SYNTAX_ERROR =
    error(PsiErrorElement.class, "syntax.error")
      .withRange(e -> {
        TextRange range = e.getTextRange();
        if (range.getLength() == 0) {
          PsiFile file = e.getContainingFile();
          int endOffset = range.getEndOffset();
          if (endOffset < file.getTextLength() && file.getFileDocument().getCharsSequence().charAt(endOffset) != '\n') {
            return TextRange.from(0, 1);
          }
        }
        return null;
      })
      .withDescription(e -> message("syntax.error", e.getErrorDescription()));
  public static final Parameterized<PsiElement, JavaPreviewFeatureUtil.PreviewFeatureUsage> PREVIEW_API_USAGE =
    parameterized(PsiElement.class, JavaPreviewFeatureUtil.PreviewFeatureUsage.class, "preview.api.usage")
      .withDescription((e, c) -> message("preview.api.usage", c.targetName()));

  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_HERE = error("annotation.not.allowed.here");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_IN_PERMIT_LIST = error("annotation.not.allowed.in.permit.list");
  public static final Simple<PsiPackageStatement> ANNOTATION_NOT_ALLOWED_ON_PACKAGE =
    error(PsiPackageStatement.class, "annotation.not.allowed.on.package")
      .withAnchor(statement -> requireNonNull(statement.getAnnotationList()));
  public static final Simple<PsiReferenceList> ANNOTATION_MEMBER_THROWS_NOT_ALLOWED =
    error(PsiReferenceList.class, "annotation.member.may.not.have.throws.list").withAnchor(list -> requireNonNull(list.getFirstChild()));
  public static final Simple<PsiParameterList> ANNOTATION_MEMBER_MAY_NOT_HAVE_PARAMETERS =
    error(PsiParameterList.class, "annotation.member.may.not.have.parameters");
  public static final Simple<PsiReferenceList> ANNOTATION_NOT_ALLOWED_EXTENDS =
    error(PsiReferenceList.class, "annotation.may.not.have.extends.list").withAnchor(list -> requireNonNull(list.getFirstChild()));
  public static final Simple<PsiElement> ANNOTATION_LOCAL = error("annotation.cannot.be.local");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_VAR = error("annotation.not.allowed.var");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_VOID = error("annotation.not.allowed.void");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_CLASS = error("annotation.not.allowed.class");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_REF = error("annotation.not.allowed.ref");
  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_STATIC = error("annotation.not.allowed.static");
  public static final Simple<PsiJavaCodeReferenceElement> ANNOTATION_TYPE_EXPECTED = error("annotation.type.expected");
  public static final Simple<PsiReferenceExpression> ANNOTATION_REPEATED_TARGET = error("annotation.repeated.target");
  public static final Simple<PsiNameValuePair> ANNOTATION_ATTRIBUTE_NAME_MISSING =
    error("annotation.attribute.name.missing");
  public static final Simple<PsiAnnotationMemberValue> ANNOTATION_ATTRIBUTE_NON_CLASS_LITERAL =
    error("annotation.attribute.non.class.literal");
  public static final Simple<PsiExpression> ANNOTATION_ATTRIBUTE_NON_ENUM_CONSTANT = error("annotation.attribute.non.enum.constant");
  public static final Simple<PsiExpression> ANNOTATION_ATTRIBUTE_NON_CONSTANT = error("annotation.attribute.non.constant");
  public static final Simple<PsiTypeElement> ANNOTATION_CYCLIC_TYPE = error("annotation.cyclic.element.type");
  public static final Parameterized<PsiMethod, PsiMethod> ANNOTATION_MEMBER_CLASH =
    error(PsiMethod.class, "annotation.member.clash")
      .withAnchor(curMethod -> requireNonNull(curMethod.getNameIdentifier()))
      .<PsiMethod>parameterized()
      .withDescription((curMethod, clashMethod) -> {
        PsiClass containingClass = requireNonNull(clashMethod.getContainingClass());
        return message("annotation.member.clash", formatMethod(clashMethod), formatClass(containingClass));
      });
  public static final Parameterized<PsiTypeElement, PsiType> ANNOTATION_METHOD_INVALID_TYPE =
    parameterized(PsiTypeElement.class, PsiType.class, "annotation.member.invalid.type")
      .withDescription((element, type) ->
                            message("annotation.member.invalid.type", type == null ? null : type.getPresentableText()));
  public static final Parameterized<PsiAnnotationMemberValue, AnnotationValueErrorContext> ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE =
    parameterized(PsiAnnotationMemberValue.class, AnnotationValueErrorContext.class, 
                  "annotation.attribute.incompatible.type").withDescription((value, context) -> {
      String text;
      if (value instanceof PsiAnnotation annotation) {
        text = requireNonNull(annotation.getNameReferenceElement()).getText();
      }
      else {
        PsiType type = ((PsiExpression)value).getType();
        text = type == null ? JavaKeywords.NULL : PsiTypesUtil.removeExternalAnnotations(type).getInternalCanonicalText();
      }
      return message("annotation.attribute.incompatible.type", context.typeText(), text);
    });
  public static final Parameterized<PsiArrayInitializerMemberValue, AnnotationValueErrorContext> ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER =
    parameterized(PsiArrayInitializerMemberValue.class, AnnotationValueErrorContext.class, 
                  "annotation.attribute.illegal.array.initializer").withDescription((element, context) -> {
      return message("annotation.attribute.illegal.array.initializer", context.typeText());
    });
  public static final Parameterized<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_DUPLICATE =
    parameterized(PsiNameValuePair.class, String.class, "annotation.attribute.duplicate")
      .withDescription((pair, attribute) -> message("annotation.attribute.duplicate", attribute));
  public static final Parameterized<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD =
    error(PsiNameValuePair.class, "annotation.attribute.unknown.method")
      .withAnchor(pair -> requireNonNull(pair.getReference()).getElement())
      .withHighlightType(pair -> pair.getName() == null ? JavaErrorHighlightType.ERROR : JavaErrorHighlightType.WRONG_REF)
      .<String>parameterized()
      .withDescription((pair, methodName) -> message("annotation.attribute.unknown.method", methodName));
  public static final Simple<PsiReferenceList> ANNOTATION_PERMITS = error(PsiReferenceList.class, "annotation.permits")
    .withAnchor(PsiReferenceList::getFirstChild);

  // Can be anchored on @FunctionalInterface annotation or at a call site
  public static final Parameterized<PsiElement, PsiType> LAMBDA_NOT_FUNCTIONAL_INTERFACE =
    parameterized(PsiElement.class, PsiType.class, "lambda.not.a.functional.interface")
      .withDescription((element, type) -> message("lambda.not.a.functional.interface", formatType(type)));
  // Can be anchored on @FunctionalInterface annotation or at a call site
  public static final Parameterized<PsiElement, PsiType> LAMBDA_NO_TARGET_METHOD =
    parameterized("lambda.no.target.method.found");
  // Can be anchored on @FunctionalInterface annotation or at a call site
  public static final Parameterized<PsiElement, PsiType> LAMBDA_MULTIPLE_TARGET_METHODS =
    parameterized(PsiElement.class, PsiType.class, "lambda.multiple.sam.candidates")
      .withDescription((psi, type) -> message("lambda.multiple.sam.candidates",
                                              (type instanceof PsiClassType cls ? cls.rawType() : type).getPresentableText()));
  public static final Parameterized<PsiAnnotation, PsiClass> FUNCTIONAL_INTERFACE_SEALED =
    parameterized("lambda.sealed.functional.interface");
  public static final Simple<PsiLambdaExpression> LAMBDA_NOT_EXPECTED = error("lambda.not.expected");
  public static final Simple<PsiParameterList> LAMBDA_PARAMETERS_INCONSISTENT_VAR = error("lambda.parameters.inconsistent.var");
  public static final Simple<PsiLambdaExpression> LAMBDA_SEALED = error("lambda.sealed");
  public static final Simple<PsiFunctionalExpression> LAMBDA_TYPE_INFERENCE_FAILURE = error("lambda.type.inference.failure");
  public static final Simple<PsiFunctionalExpression> LAMBDA_SAM_GENERIC = error("lambda.sam.generic");
  public static final Parameterized<PsiFunctionalExpression, PsiType> LAMBDA_TARGET_NOT_INTERFACE = 
    parameterized("lambda.target.not.interface");
  public static final Parameterized<PsiLambdaExpression, MethodCandidateInfo> LAMBDA_INFERENCE_ERROR =
    parameterized(PsiLambdaExpression.class, MethodCandidateInfo.class, "lambda.inference.error")
      .withDescription((psi, candidate) -> message("lambda.inference.error", candidate.getInferenceErrorMessage()));
  public static final Parameterized<PsiElement, String> LAMBDA_RETURN_TYPE_ERROR =
    parameterized(PsiElement.class, String.class, "lambda.return.type.error")
      .withDescription((psi, message) -> message("lambda.return.type.error", message));
  public static final Parameterized<PsiLambdaExpression, PsiMethod> LAMBDA_WRONG_NUMBER_OF_PARAMETERS =
    parameterized(PsiLambdaExpression.class, PsiMethod.class, "lambda.wrong.number.of.parameters")
      .withAnchor(PsiLambdaExpression::getParameterList)
      .withDescription((lambda, method) -> message("lambda.wrong.number.of.parameters",
                                                   method.getParameterList().getParametersCount(),
                                                   lambda.getParameterList().getParametersCount()));
  public static final Parameterized<PsiParameter, PsiType> LAMBDA_INCOMPATIBLE_PARAMETER_TYPES =
    parameterized(PsiParameter.class, PsiType.class, "lambda.incompatible.parameter.types")
      .withDescription((parameter, expectedType) -> message("lambda.incompatible.parameter.types",
                                                            expectedType.getPresentableText(), parameter.getType().getPresentableText()));
  
    
  public static final Simple<PsiMethodReferenceExpression> METHOD_REFERENCE_SEALED = error("method.reference.sealed");
  public static final Simple<PsiMethodReferenceExpression> METHOD_REFERENCE_NOT_EXPECTED = error("method.reference.not.expected");
  public static final Simple<PsiMethodReferenceExpression> METHOD_REFERENCE_RAW_CONSTRUCTOR = error("method.reference.raw.constructor");
  public static final Simple<PsiElement> METHOD_REFERENCE_QUALIFIER_CLASS_UNRESOLVED = 
    error(PsiElement.class, "method.reference.qualifier.class.unresolved")
      .withDescription(qualifier -> message("method.reference.qualifier.class.unresolved", qualifier.getText()));
  public static final Simple<PsiTypeElement> METHOD_REFERENCE_QUALIFIER_WILDCARD = error("method.reference.qualifier.wildcard");
  public static final Parameterized<PsiMethodReferenceExpression, String> METHOD_REFERENCE_RETURN_TYPE_ERROR =
    parameterized(PsiMethodReferenceExpression.class, String.class, "method.reference.return.type.error")
      .withDescription((psi, message) -> message("method.reference.return.type.error", message));
  public static final Parameterized<PsiMethodReferenceExpression, MethodCandidateInfo> METHOD_REFERENCE_INFERENCE_ERROR =
    parameterized(PsiMethodReferenceExpression.class, MethodCandidateInfo.class, "method.reference.inference.error")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, candidate) -> message("method.reference.inference.error", candidate.getInferenceErrorMessage()));
  public static final Parameterized<PsiMethodReferenceExpression, PsiClass> METHOD_REFERENCE_UNRESOLVED_CONSTRUCTOR =
    parameterized(PsiMethodReferenceExpression.class, PsiClass.class, "method.reference.unresolved.constructor")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, cls) -> message("method.reference.unresolved.constructor", cls.getName()));
  public static final Simple<PsiMethodReferenceExpression> METHOD_REFERENCE_UNRESOLVED_METHOD =
    error(PsiMethodReferenceExpression.class, "method.reference.unresolved.method")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription(ref -> message("method.reference.unresolved.method", ref.getReferenceName()));
  public static final Parameterized<PsiMethodReferenceExpression, PsiMethod> METHOD_REFERENCE_ABSTRACT_METHOD =
    parameterized(PsiMethodReferenceExpression.class, PsiMethod.class, "method.reference.abstract.method")
      .withDescription((psi, method) -> message("method.reference.abstract.method", method.getName()));
  public static final Parameterized<PsiMethodReferenceExpression, PsiMethod> METHOD_REFERENCE_NON_STATIC_METHOD_IN_STATIC_CONTEXT =
    parameterized(PsiMethodReferenceExpression.class, PsiMethod.class, "method.reference.non.static.method.in.static.context");
  public static final Parameterized<PsiMethodReferenceExpression, PsiMethod> METHOD_REFERENCE_STATIC_METHOD_NON_STATIC_QUALIFIER =
    parameterized(PsiMethodReferenceExpression.class, PsiMethod.class, "method.reference.static.method.non.static.qualifier");
  public static final Parameterized<PsiMethodReferenceExpression, PsiMethod> METHOD_REFERENCE_STATIC_METHOD_RECEIVER =
    parameterized(PsiMethodReferenceExpression.class, PsiMethod.class, "method.reference.static.method.receiver");
  public static final Parameterized<PsiMethodReferenceExpression, PsiClass> METHOD_REFERENCE_ENCLOSING_INSTANCE_NOT_IN_SCOPE =
    parameterized(PsiMethodReferenceExpression.class, PsiClass.class, "method.reference.enclosing.instance.not.in.scope")
      .withDescription((psi, cls) -> message("method.reference.enclosing.instance.not.in.scope", formatClass(cls)));
  public static final Simple<PsiReferenceParameterList> METHOD_REFERENCE_PARAMETERIZED_QUALIFIER =
    error("method.reference.parameterized.qualifier");
  
  public static final Parameterized<PsiAnnotation, @NotNull List<PsiAnnotation.@NotNull TargetType>> ANNOTATION_NOT_APPLICABLE =
    error(PsiAnnotation.class, "annotation.not.applicable").<@NotNull List<PsiAnnotation.@NotNull TargetType>>parameterized()
      .withDescription((annotation, types) -> {
        String target = JavaPsiBundle.message("annotation.target." + types.get(0));
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return message("annotation.not.applicable", nameRef != null ? nameRef.getText() : annotation.getText(), target);
      });
  public static final Parameterized<PsiAnnotation, @NotNull List<String>> ANNOTATION_MISSING_ATTRIBUTE =
    error(PsiAnnotation.class, "annotation.missing.attribute")
      .withAnchor(annotation -> annotation.getNameReferenceElement())
      .<@NotNull List<String>>parameterized()
      .withDescription((annotation, attributeNames) -> message(
          "annotation.missing.attribute", attributeNames.stream().map(attr -> "'" + attr + "'").collect(Collectors.joining(", "))));
  public static final Simple<PsiAnnotation> ANNOTATION_CONTAINER_WRONG_PLACE =
    error(PsiAnnotation.class, "annotation.container.wrong.place")
      .withDescription(annotation ->
                            message("annotation.container.wrong.place",
                                    requireNonNull(annotation.resolveAnnotationType()).getQualifiedName()));
  public static final Parameterized<PsiAnnotation, PsiClass> ANNOTATION_CONTAINER_NOT_APPLICABLE =
    parameterized(PsiAnnotation.class, PsiClass.class, "annotation.container.not.applicable")
      .withDescription((annotation, containerClass) -> {
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
        String target = JavaPsiBundle.message("annotation.target." + targets[0]);
        return message("annotation.container.not.applicable", containerClass.getName(), target);
      });
  public static final Simple<PsiAnnotation> ANNOTATION_DUPLICATE =
    error(PsiAnnotation.class, "annotation.duplicate").withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()));
  public static final Simple<PsiAnnotation> ANNOTATION_DUPLICATE_NON_REPEATABLE =
    error(PsiAnnotation.class, "annotation.duplicate.non.repeatable")
      .withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()))
      .withDescription(annotation -> message(
        "annotation.duplicate.non.repeatable", requireNonNull(annotation.resolveAnnotationType()).getQualifiedName()));
  public static final Parameterized<PsiAnnotation, String> ANNOTATION_DUPLICATE_EXPLAINED =
    error(PsiAnnotation.class, "annotation.duplicate.explained")
      .withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()))
      .<String>parameterized()
      .withDescription((annotation, message) -> message("annotation.duplicate.explained", message));
  public static final Parameterized<PsiAnnotationMemberValue, String> ANNOTATION_MALFORMED_REPEATABLE_EXPLAINED =
    parameterized(PsiAnnotationMemberValue.class, String.class, "annotation.malformed.repeatable.explained")
      .withDescription((containerRef, message) -> message("annotation.malformed.repeatable.explained", message));

  public static final Simple<PsiAnnotation> SAFE_VARARGS_ON_RECORD_COMPONENT =
    error("safe.varargs.on.record.component");
  public static final Parameterized<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_FIXED_ARITY = parameterized("safe.varargs.on.fixed.arity");
  public static final Parameterized<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_NON_FINAL_METHOD =
    parameterized("safe.varargs.on.non.final.method");
  public static final Parameterized<PsiAnnotation, PsiMethod> OVERRIDE_ON_STATIC_METHOD = parameterized("override.on.static.method");
  public static final Parameterized<PsiAnnotation, PsiMethod> OVERRIDE_ON_NON_OVERRIDING_METHOD =
    parameterized("override.on.non-overriding.method");

  public static final Simple<PsiReceiverParameter> RECEIVER_WRONG_CONTEXT =
    error(PsiReceiverParameter.class, "receiver.wrong.context").withAnchor(PsiReceiverParameter::getIdentifier);
  public static final Simple<PsiReceiverParameter> RECEIVER_STATIC_CONTEXT =
    error(PsiReceiverParameter.class, "receiver.static.context").withAnchor(PsiReceiverParameter::getIdentifier);
  public static final Simple<PsiReceiverParameter> RECEIVER_WRONG_POSITION =
    error(PsiReceiverParameter.class, "receiver.wrong.position").withAnchor(PsiReceiverParameter::getIdentifier);
  public static final Parameterized<PsiReceiverParameter, PsiType> RECEIVER_TYPE_MISMATCH =
    error(PsiReceiverParameter.class, "receiver.type.mismatch")
      .withAnchor(parameter -> requireNonNullElse(parameter.getTypeElement(), parameter)).parameterized();
  public static final Parameterized<PsiReceiverParameter, @Nullable String> RECEIVER_NAME_MISMATCH =
    error(PsiReceiverParameter.class, "receiver.name.mismatch").withAnchor(PsiReceiverParameter::getIdentifier).parameterized();
  
  // PsiMember = PsiClass | PsiEnumConstant
  public static final Parameterized<PsiMember, PsiMethod> CLASS_NO_ABSTRACT_METHOD =
    error(PsiMember.class, "class.must.implement.method")
      .withRange(member ->
                   member instanceof PsiEnumConstant enumConstant ? enumConstant.getNameIdentifier().getTextRangeInParent() :
                   member instanceof PsiClass aClass ? getClassDeclarationTextRange(aClass) : null)
      .<PsiMethod>parameterized()
      .withDescription((member, abstractMethod) -> {
        PsiClass aClass = member instanceof PsiEnumConstant enumConstant ?
                          requireNonNullElse(enumConstant.getInitializingClass(), member.getContainingClass()) : (PsiClass)member;
        @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String messageKey;
        String referenceName;
        if (member instanceof PsiEnumConstant enumConstant) {
          messageKey = "class.must.implement.method.enum.constant";
          referenceName = enumConstant.getName();
        }
        else {
          messageKey = aClass.isEnum() || aClass.isRecord() || aClass instanceof PsiAnonymousClass
                       ? "class.must.implement.method"
                       : "class.must.implement.method.or.abstract";
          referenceName = formatClass(aClass, false);
        }
        return message(messageKey, referenceName, formatMethod(abstractMethod),
                       formatClass(requireNonNull(abstractMethod.getContainingClass()), false));
      });
  public static final Parameterized<PsiClass, OverrideClashContext> CLASS_INHERITS_ABSTRACT_AND_DEFAULT =
    parameterized(PsiClass.class, OverrideClashContext.class, "class.inherits.abstract.and.default")
      .withAnchor(PsiClass::getNameIdentifier)
      .withDescription((cls, ctx) -> {
        return message("class.inherits.abstract.and.default", formatClass(cls),
                formatMethod(ctx.method()),
                formatClass(requireNonNull(ctx.method().getContainingClass())),
                formatClass(requireNonNull(ctx.superMethod().getContainingClass())));
      });
  public static final Parameterized<PsiClass, OverrideClashContext> CLASS_INHERITS_UNRELATED_DEFAULTS =
    parameterized(PsiClass.class, OverrideClashContext.class, "class.inherits.unrelated.defaults")
      .withAnchor(PsiClass::getNameIdentifier)
      .withDescription((cls, ctx) -> {
        return message("class.inherits.unrelated.defaults", formatClass(cls),
                formatMethod(ctx.method()),
                formatClass(requireNonNull(ctx.method().getContainingClass())),
                formatClass(requireNonNull(ctx.superMethod().getContainingClass())));
      });
  public static final Simple<PsiClass> CLASS_ALREADY_IMPORTED =
    error(PsiClass.class, "class.already.imported").withAnchor(PsiClass::getNameIdentifier)
      .withDescription(cls -> message("class.already.imported", formatClass(cls, false)));
  public static final Parameterized<PsiClass, PsiClass> CLASS_DUPLICATE =
    error(PsiClass.class, "class.duplicate")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .withDescription(cls -> message("class.duplicate", cls.getName()))
      .parameterized();
  public static final Parameterized<PsiClass, PsiClass> CLASS_DUPLICATE_IN_OTHER_FILE =
    error(PsiClass.class, "class.duplicate")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .<PsiClass>parameterized()
      .withDescription((cls, dupCls) -> message("class.duplicate.in.other.file",
                                                FileUtil.toSystemDependentName(dupCls.getContainingFile().getVirtualFile().getPath())));
  public static final Simple<PsiClass> CLASS_CLASHES_WITH_PACKAGE =
    error(PsiClass.class, "class.clashes.with.package")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .withDescription(cls -> message("class.clashes.with.package", cls.getQualifiedName()));
  public static final Simple<PsiJavaCodeReferenceElement> PACKAGE_CLASHES_WITH_CLASS =
    error(PsiJavaCodeReferenceElement.class, "package.clashes.with.class")
      .withDescription(ref -> message("package.clashes.with.class", ref.getText()));
  public static final Simple<PsiClass> CLASS_WRONG_FILE_NAME =
    error(PsiClass.class, "class.wrong.filename")
      .withRange(JavaErrorFormatUtil::getClassDeclarationTextRange)
      .withDescription(cls -> message("class.wrong.filename", cls.getName()));
  public static final Parameterized<PsiClass, PsiClass> CLASS_CYCLIC_INHERITANCE =
    error(PsiClass.class, "class.cyclic.inheritance")
      .withRange(JavaErrorFormatUtil::getClassDeclarationTextRange).<PsiClass>parameterized()
      .withDescription((aClass, circularClass) -> message("class.cyclic.inheritance", formatClass(circularClass)));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_DUPLICATE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.duplicate")
      .withDescription(
        (ref, target) -> message("class.reference.list.duplicate", formatClass(target), ref.getParent().getFirstChild().getText()));
  public static final Simple<PsiJavaCodeReferenceElement> CLASS_REFERENCE_LIST_NAME_EXPECTED =
    error("class.reference.list.name.expected");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_INNER_PRIVATE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.inner.private")
      .withDescription((ref, target) -> message("class.reference.list.inner.private",
                                                formatClass(target), formatClass(requireNonNull(target.getContainingClass()))));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_NO_ENCLOSING_INSTANCE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.no.enclosing.instance")
      .withDescription((ref, target) -> message("class.reference.list.no.enclosing.instance", formatClass(target)));
  public static final Simple<PsiReferenceList> CLASS_CANNOT_EXTEND_MULTIPLE_CLASSES =
    error("class.cannot.extend.multiple.classes");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_INTERFACE = 
    parameterized("class.extends.interface");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_SEALED_LOCAL = 
    parameterized("class.extends.sealed.local");
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> CLASS_EXTENDS_SEALED_NOT_PERMITTED =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "class.extends.sealed.not.permitted")
      .withDescription((ref, cls) -> message("class.extends.sealed.not.permitted", formatClass(cls.subClass())));
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> CLASS_EXTENDS_SEALED_ANOTHER_MODULE =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "class.extends.sealed.another.module");
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> CLASS_EXTENDS_SEALED_ANOTHER_PACKAGE =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "class.extends.sealed.another.package")
      .withDescription((ref, cls) -> {
        return StringUtil.capitalize(message("class.extends.sealed.another.package",
                                             JavaElementKind.fromElement(cls.subClass()).subject(), formatClass(cls.subClass()),
                                             JavaElementKind.fromElement(cls.superClass()).subject(), formatClass(cls.superClass())));
      });
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_PROHIBITED_CLASS = 
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.extends.prohibited.class")
      .withDescription((ref, cls) -> message("class.extends.prohibited.class", cls.getQualifiedName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_INHERITS_TYPE_PARAMETER =
    parameterized("class.inherits.type.parameter");  
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_FINAL = 
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.extends.final").withDescription((ref, cls) -> {
      int choice = cls.isEnum() ? 2 : cls.isRecord() ? 3 : cls.isValueClass() ? 4 : 1;
      return message("class.extends.final", formatClass(cls), choice);
    });
  public static final Simple<PsiAnonymousClass> CLASS_ANONYMOUS_EXTENDS_SEALED =
    error(PsiAnonymousClass.class, "class.anonymous.extends.sealed").withAnchor(PsiAnonymousClass::getBaseClassReference);
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_IMPLEMENTS_CLASS = 
    parameterized("class.implements.class");
  public static final Simple<PsiClass> CLASS_SEALED_NO_INHERITORS =
    error(PsiClass.class, "class.sealed.no.inheritors").withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiClass> CLASS_SEALED_INCOMPLETE_PERMITS =
    error(PsiClass.class, "class.sealed.incomplete.permits").withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiClass> CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS_CAN_BE_FINAL =
    error(PsiClass.class, "class.sealed.inheritor.expected.modifiers.can.be.final").withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiClass> CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS =
    error(PsiClass.class, "class.sealed.inheritor.expected.modifiers").withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiClass> CLASS_SEALED_PERMITS_ON_NON_SEALED =
    error(PsiClass.class, "class.sealed.permits.on.non.sealed")
      .withAnchor(cls -> requireNonNull(cls.getPermitsList()).getFirstChild())
      .withDescription(cls -> message("class.sealed.permits.on.non.sealed", cls.getName()));
  public static final Parameterized<PsiElement, ClassStaticReferenceErrorContext> CLASS_NOT_ENCLOSING =
    parameterized(PsiElement.class, ClassStaticReferenceErrorContext.class, "class.not.enclosing")
      .withDescription((psi, ctx) -> message("class.not.enclosing", formatClass(ctx.outerClass())));
  public static final Parameterized<PsiElement, ClassStaticReferenceErrorContext> CLASS_CANNOT_BE_REFERENCED_FROM_STATIC_CONTEXT =
    parameterized(PsiElement.class, ClassStaticReferenceErrorContext.class, "class.cannot.be.referenced.from.static.context")
      .withDescription((psi, ctx) -> message(
        "class.cannot.be.referenced.from.static.context",
        formatClass(ctx.outerClass()) + "." + (psi instanceof PsiSuperExpression ? JavaKeywords.SUPER : JavaKeywords.THIS)));
  public static final Parameterized<PsiClass, InheritTypeClashContext> CLASS_INHERITANCE_DIFFERENT_TYPE_ARGUMENTS =
    parameterized(PsiClass.class, InheritTypeClashContext.class, "class.inheritance.different.type.arguments")
      .withRange((cls, ctx) -> getClassDeclarationTextRange(cls))
      .withDescription((cls, ctx) -> message("class.inheritance.different.type.arguments",
                                             formatClass(ctx.superClass()),
                                             formatType(ctx.type1()),
                                             formatType(ctx.type2())));
  public static final Parameterized<PsiClass, InheritTypeClashContext> CLASS_INHERITANCE_RAW_AND_GENERIC =
    parameterized(PsiClass.class, InheritTypeClashContext.class, "class.inheritance.raw.and.generic")
      .withRange((cls, ctx) -> getClassDeclarationTextRange(cls))
      .withDescription((cls, ctx) -> message("class.inheritance.raw.and.generic",
                                             formatClass(ctx.superClass()),
                                             formatType(ctx.type1() != null ? ctx.type1() : ctx.type2())));
  public static final Parameterized<PsiClass, OverrideClashContext> CLASS_INHERITANCE_METHOD_CLASH =
    parameterized(PsiClass.class, OverrideClashContext.class, "class.inheritance.method.clash")
      .withAnchor(PsiClass::getNameIdentifier)
      .withDescription((cls, ctx) -> message(
        "class.inheritance.method.clash",
        formatMethod(ctx.superMethod()), formatClass(requireNonNull(ctx.superMethod().getContainingClass())),
        formatMethod(ctx.method()), formatClass(requireNonNull(ctx.method().getContainingClass()))));
  public static final Simple<PsiJavaCodeReferenceElement> CLASS_GENERIC_EXTENDS_EXCEPTION =
    error("class.generic.extends.exception");
  public static final Parameterized<PsiElement, PsiClass> CLASS_NOT_ACCESSIBLE =
    parameterized(PsiElement.class, PsiClass.class, "class.not.accessible")
      .withRange((psi, cls) -> psi instanceof PsiMember member ? getMemberDeclarationTextRange(member) : null)
      .withDescription((psi, cls) -> message("class.not.accessible", formatClass(cls)));
  public static final Simple<PsiCodeBlock> CLASS_INITIALIZER_MUST_COMPLETE_NORMALLY =
    error("class.initializer.must.complete.normally");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_PERMITTED_MUST_HAVE_MODIFIER =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.permitted.must.have.modifier");
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> CLASS_PERMITTED_NOT_DIRECT_SUBCLASS =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "class.permitted.not.direct.subclass")
      .withDescription((ref, ctx) -> message(
        "class.permitted.not.direct.subclass",
        ctx.subClass().getName(),
        ctx.subClass().isInterface() == ctx.superClass().isInterface() ? 1 : 2,
        ctx.superClass().getName()));
  public static final Parameterized<PsiReferenceExpression, PsiClass> CLASS_OR_PACKAGE_EXPECTED =
    parameterized(PsiReferenceExpression.class, PsiClass.class, "class.or.package.expected")
      .withAnchor(ref -> ref.getQualifierExpression());

  public static final Simple<PsiJavaCodeReferenceElement> VALUE_CLASS_EXTENDS_NON_ABSTRACT = error("value.class.extends.non.abstract");
  
  public static final Simple<PsiExpression> INSTANTIATION_ENUM = error("instantiation.enum");
  public static final Parameterized<PsiExpression, PsiClass> INSTANTIATION_ABSTRACT = 
    parameterized(PsiExpression.class, PsiClass.class, "instantiation.abstract")
      .withDescription((expr, aClass) -> message("instantiation.abstract", aClass.getName()));
  public static final Parameterized<PsiElement, PsiClass> INSTANTIATION_LOCAL_CLASS_WRONG_STATIC_CONTEXT =
    parameterized(PsiElement.class, PsiClass.class, "instantiation.local.class.wrong.static.context")
      .withDescription((psi, ctx) -> message("instantiation.local.class.wrong.static.context", ctx.getName()));
  
  public static final Simple<PsiClass> RECORD_NO_HEADER = error(PsiClass.class, "record.no.header")
    .withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiRecordHeader> RECORD_HEADER_REGULAR_CLASS = error("record.header.regular.class");
  public static final Simple<PsiClassInitializer> RECORD_INSTANCE_INITIALIZER = error("record.instance.initializer");
  public static final Simple<PsiField> RECORD_INSTANCE_FIELD = error("record.instance.field");
  public static final Simple<PsiReferenceList> RECORD_EXTENDS = error(PsiReferenceList.class, "record.extends")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Simple<PsiReferenceList> RECORD_PERMITS = error(PsiReferenceList.class, "record.permits")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Simple<PsiRecordComponent> RECORD_COMPONENT_VARARG_NOT_LAST = 
    error("record.component.vararg.not.last");
  public static final Parameterized<PsiRecordComponent, @NotNull TextRange> RECORD_COMPONENT_CSTYLE_DECLARATION = 
    parameterized(PsiRecordComponent.class, TextRange.class, "record.component.cstyle.declaration")
      .withRange((component, range) -> range);
  public static final Simple<PsiRecordComponent> RECORD_COMPONENT_RESTRICTED_NAME = 
    error(PsiRecordComponent.class, "record.component.restricted.name")
      .withAnchor(PsiRecordComponent::getNameIdentifier)
      .withDescription(component -> message("record.component.restricted.name", component.getName()));
  public static final Simple<PsiRecordComponent> RECORD_COMPONENT_NOT_INITIALIZED = 
    error(PsiRecordComponent.class, "record.component.not.initialized")
      .withAnchor(PsiRecordComponent::getNameIdentifier)
      .withDescription(component -> message("record.component.not.initialized", component.getName()));
  public static final Simple<PsiTypeParameterList> RECORD_SPECIAL_METHOD_TYPE_PARAMETERS =
    error(PsiTypeParameterList.class, "record.special.method.type.parameters")
      .withDescription(tpl -> message("record.special.method.type.parameters", getRecordMethodKind(((PsiMethod)tpl.getParent()))));
  public static final Simple<PsiReferenceList> RECORD_SPECIAL_METHOD_THROWS =
    error(PsiReferenceList.class, "record.special.method.throws")
      .withAnchor(PsiReferenceList::getFirstChild)
      .withDescription(throwsList -> message("record.special.method.throws", getRecordMethodKind(((PsiMethod)throwsList.getParent()))));
  public static final Parameterized<PsiMethod, AccessModifier> RECORD_CONSTRUCTOR_STRONGER_ACCESS =
    parameterized(PsiMethod.class, AccessModifier.class, "record.constructor.stronger.access")
      .withAnchor(PsiMethod::getNameIdentifier)
      .withDescription(
        (method, classModifier) -> message("record.constructor.stronger.access", getRecordMethodKind(method), classModifier));
  public static final Parameterized<PsiMethod, JavaIncompatibleTypeErrorContext> RECORD_ACCESSOR_WRONG_RETURN_TYPE =
    parameterized(PsiMethod.class, JavaIncompatibleTypeErrorContext.class, "record.accessor.wrong.return.type")
      .withAnchor(PsiMethod::getReturnTypeElement)
      .withDescription((method, ctx) -> message("record.accessor.wrong.return.type",
                                                ctx.lType().getPresentableText(), requireNonNull(ctx.rType()).getPresentableText()));
  public static final Simple<PsiMethod> RECORD_ACCESSOR_NON_PUBLIC =
    error(PsiMethod.class, "record.accessor.non.public").withAnchor(PsiMethod::getNameIdentifier);
  public static final Simple<PsiMethod> RECORD_NO_CONSTRUCTOR_CALL_IN_NON_CANONICAL =
    error(PsiMethod.class, "record.no.constructor.call.in.non.canonical").withAnchor(PsiMethod::getNameIdentifier);
  public static final Parameterized<PsiParameter, PsiRecordComponent> RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_TYPE =
    parameterized(PsiParameter.class, PsiRecordComponent.class, "record.canonical.constructor.wrong.parameter.type")
      .withAnchor(PsiParameter::getTypeElement)
      .withDescription((parameter, component) -> message(
        "record.canonical.constructor.wrong.parameter.type", component.getName(), component.getType().getPresentableText(),
        parameter.getType().getPresentableText()));
  public static final Parameterized<PsiParameter, PsiRecordComponent> RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_NAME =
    parameterized(PsiParameter.class, PsiRecordComponent.class, "record.canonical.constructor.wrong.parameter.name")
      .withAnchor(PsiVariable::getNameIdentifier)
      .withDescription((parameter, component) -> message(
        "record.canonical.constructor.wrong.parameter.name", component.getName(), parameter.getName()));

  public static final Simple<PsiParameter> VARARG_NOT_LAST_PARAMETER = error("vararg.not.last.parameter");
  public static final Parameterized<PsiParameter, @NotNull TextRange> VARARG_CSTYLE_DECLARATION =
    parameterized(PsiParameter.class, TextRange.class, "vararg.cstyle.array.declaration")
      .withRange((component, range) -> range);

  public static final Simple<PsiReferenceList> ENUM_EXTENDS = error(PsiReferenceList.class, "enum.extends")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Simple<PsiReferenceList> ENUM_PERMITS = error(PsiReferenceList.class, "enum.permits")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Parameterized<PsiReferenceExpression, PsiField> ENUM_CONSTANT_ILLEGAL_ACCESS_IN_CONSTRUCTOR =
    parameterized(PsiReferenceExpression.class, PsiField.class, "enum.constant.illegal.access.in.constructor")
      .withDescription((expr, field) -> {
        int fieldType = field instanceof PsiEnumConstant ? 2 : 1;
        PsiMember initializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
        int initializerType = initializer instanceof PsiMethod ? 1 : initializer instanceof PsiField ? 2 : 3;
        return message("enum.constant.illegal.access.in.constructor", fieldType, initializerType);
      });
  public static final Simple<PsiKeyword> ENUM_CONSTANT_MODIFIER = error(PsiKeyword.class, "enum.constant.modifier");

  public static final Simple<PsiClassInitializer> INTERFACE_CLASS_INITIALIZER = error("interface.class.initializer");
  public static final Simple<PsiMethod> INTERFACE_CONSTRUCTOR = error("interface.constructor");
  public static final Simple<PsiReferenceList> INTERFACE_IMPLEMENTS = error(PsiReferenceList.class, "interface.implements")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> INTERFACE_EXTENDS_CLASS = 
    parameterized("interface.extends.class");

  public static final Parameterized<PsiJavaFile, PsiImplicitClass> CLASS_IMPLICIT_NO_MAIN_METHOD = 
    error(PsiJavaFile.class, "class.implicit.no.main.method")
      .withHighlightType(JavaErrorHighlightType.FILE_LEVEL_ERROR).parameterized();
  public static final Parameterized<PsiJavaFile, PsiImplicitClass> CLASS_IMPLICIT_INVALID_FILE_NAME = 
    error(PsiJavaFile.class, "class.implicit.invalid.file.name")
      .withHighlightType(JavaErrorHighlightType.FILE_LEVEL_ERROR).parameterized();
  public static final Simple<PsiClassInitializer> CLASS_IMPLICIT_INITIALIZER = error("class.implicit.initializer");
  public static final Simple<PsiPackageStatement> CLASS_IMPLICIT_PACKAGE = error("class.implicit.package.statement");
  
  public static final Simple<PsiIdentifier> IDENTIFIER_RESTRICTED = error(PsiIdentifier.class, "identifier.restricted")
    .withDescription(psi -> message("identifier.restricted", psi.getText()));
  
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED = 
    parameterized("type.parameter.extends.interface.expected");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS = 
    parameterized("type.parameter.cannot.be.followed.by.other.bounds");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ENUM = error("type.parameter.on.enum");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ANNOTATION = error("type.parameter.on.annotation");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ANNOTATION_MEMBER = error("type.parameter.on.annotation.member");
  public static final Simple<PsiTypeParameter> TYPE_PARAMETER_DUPLICATE = 
    error(PsiTypeParameter.class, "type.parameter.on.annotation.member")
      .withDescription(typeParameter -> message("type.parameter.duplicate", typeParameter.getName()));
  public static final Parameterized<PsiMethodCallExpression, IncompatibleIntersectionContext> TYPE_PARAMETER_INCOMPATIBLE_UPPER_BOUNDS =
    parameterized(PsiMethodCallExpression.class, IncompatibleIntersectionContext.class, "type.parameter.incompatible.upper.bounds")
      .withRange((call, ctx) -> getRange(call))
      .withDescription((call, ctx) -> message("type.parameter.incompatible.upper.bounds", ctx.parameter().getName(), ctx.message()));
  public static final Parameterized<PsiMethodCallExpression, TypeParameterBoundMismatchContext>
    TYPE_PARAMETER_INFERRED_TYPE_NOT_WITHIN_EXTEND_BOUND =
    parameterized(PsiMethodCallExpression.class, TypeParameterBoundMismatchContext.class,
                  "type.parameter.inferred.type.not.within.extend.bound")
      .withDescription((call, ctx) -> message("type.parameter.inferred.type.not.within.extend.bound", formatClass(ctx.parameter()),
                                              formatType(ctx.bound()), formatType(ctx.actualType())));
  public static final Parameterized<PsiMethodCallExpression, TypeParameterBoundMismatchContext>
    TYPE_PARAMETER_INFERRED_TYPE_NOT_WITHIN_IMPLEMENT_BOUND =
    parameterized(PsiMethodCallExpression.class, TypeParameterBoundMismatchContext.class,
                  "type.parameter.inferred.type.not.within.implement.bound")
      .withDescription((call, ctx) -> message("type.parameter.inferred.type.not.within.implement.bound", formatClass(ctx.parameter()),
                                              formatType(ctx.bound()), formatType(ctx.actualType())));
  public static final Parameterized<PsiTypeElement, TypeParameterBoundMismatchContext> TYPE_PARAMETER_TYPE_NOT_WITHIN_EXTEND_BOUND =
    parameterized(PsiTypeElement.class, TypeParameterBoundMismatchContext.class, "type.parameter.type.not.within.extend.bound")
      .withDescription((call, ctx) -> message("type.parameter.type.not.within.extend.bound",
                                              formatClassOrType(ctx.actualType()),
                                              formatType(ctx.bound())));
  public static final Parameterized<PsiTypeElement, TypeParameterBoundMismatchContext> TYPE_PARAMETER_TYPE_NOT_WITHIN_IMPLEMENT_BOUND =
    parameterized(PsiTypeElement.class, TypeParameterBoundMismatchContext.class, "type.parameter.type.not.within.implement.bound")
      .withDescription((call, ctx) -> message("type.parameter.type.not.within.implement.bound",
                                              formatClassOrType(ctx.actualType()),
                                              formatType(ctx.bound())));
  public static final Parameterized<PsiReferenceParameterList, PsiClass> TYPE_PARAMETER_ABSENT_CLASS =
    parameterized(PsiReferenceParameterList.class, PsiClass.class, "type.parameter.absent.class")
      .withDescription((list, cls) -> message("type.parameter.absent.class", formatClass(cls)));
  public static final Parameterized<PsiReferenceParameterList, PsiMethod> TYPE_PARAMETER_ABSENT_METHOD =
    parameterized(PsiReferenceParameterList.class, PsiMethod.class, "type.parameter.absent.method")
      .withDescription((list, method) -> message("type.parameter.absent.method", formatMethod(method)));
  public static final Parameterized<PsiReferenceParameterList, PsiTypeParameterListOwner> TYPE_PARAMETER_COUNT_MISMATCH =
    parameterized(PsiReferenceParameterList.class, PsiTypeParameterListOwner.class, "type.parameter.count.mismatch")
      .withNavigationShift(1)
      .withDescription((list, owner) -> message("type.parameter.count.mismatch", list.getTypeArgumentCount(),
                                                owner.getTypeParameters().length));
  public static final Simple<PsiTypeElement> TYPE_PARAMETER_ACTUAL_INFERRED_MISMATCH = error("type.parameter.actual.inferred.mismatch");

  public static final Parameterized<PsiMethod, DuplicateMethodsContext> METHOD_DUPLICATE =
    parameterized(PsiMethod.class, DuplicateMethodsContext.class, "method.duplicate")
      .withRange((method, duplicates) -> getMethodDeclarationTextRange(method))
      .withDescription(
        (method, duplicates) -> message("method.duplicate", formatMethod(method), formatClass(requireNonNull(method.getContainingClass()))));
  public static final Simple<PsiMethod> METHOD_NO_PARAMETER_LIST =
    error(PsiMethod.class, "method.no.parameter.list").withAnchor(PsiMethod::getNameIdentifier);
  public static final Simple<PsiJavaCodeReferenceElement> METHOD_THROWS_CLASS_NAME_EXPECTED =
    error("method.throws.class.name.expected");
  public static final Simple<PsiMethod> METHOD_INTERFACE_BODY =
    error(PsiMethod.class, "method.interface.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_ABSTRACT_BODY =
    error(PsiMethod.class, "method.abstract.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Parameterized<PsiKeyword, PsiMethod> METHOD_ABSTRACT_IN_NON_ABSTRACT_CLASS =
    parameterized(PsiKeyword.class, PsiMethod.class, "method.abstract.in.non.abstract.class");
  public static final Simple<PsiMethod> METHOD_NATIVE_BODY =
    error(PsiMethod.class, "method.native.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_STATIC_IN_INTERFACE_SHOULD_HAVE_BODY =
    error(PsiMethod.class, "method.static.in.interface.should.have.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_PRIVATE_IN_INTERFACE_SHOULD_HAVE_BODY =
    error(PsiMethod.class, "method.private.in.interface.should.have.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_DEFAULT_SHOULD_HAVE_BODY =
    error(PsiMethod.class, "method.default.should.have.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_DEFAULT_IN_CLASS =
    error(PsiMethod.class, "method.default.in.class").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_DEFAULT_OVERRIDES_OBJECT_MEMBER =
    error(PsiMethod.class, "method.default.overrides.object.member").withAnchor(PsiMethod::getNameIdentifier)
      .withDescription(method -> message("method.default.overrides.object.member", method.getName()));
  public static final Simple<PsiMethod> METHOD_SHOULD_HAVE_BODY =
    error(PsiMethod.class, "method.should.have.body").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Simple<PsiMethod> METHOD_SHOULD_HAVE_BODY_OR_ABSTRACT =
    error(PsiMethod.class, "method.should.have.body.or.abstract").withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange);
  public static final Parameterized<PsiMember, @NotNull OverrideClashContext> METHOD_STATIC_OVERRIDES_INSTANCE =
    parameterized(PsiMember.class, OverrideClashContext.class, "method.static.overrides.instance")
      .withRange((member, ctx) -> getMemberDeclarationTextRange(member))
      .withDescription((method, ctx) -> message("method.static.overrides.instance",
                                                formatMethod(ctx.method()),
                                                formatClass(requireNonNull(ctx.method().getContainingClass())),
                                                formatMethod(ctx.superMethod()),
                                                formatClass(requireNonNull(ctx.superMethod().getContainingClass()))));
  public static final Parameterized<PsiMember, @NotNull OverrideClashContext> METHOD_INSTANCE_OVERRIDES_STATIC =
    parameterized(PsiMember.class, OverrideClashContext.class, "method.instance.overrides.static")
      .withRange((method, ctx) -> getMemberDeclarationTextRange(method))
      .withDescription((method, ctx) -> message("method.instance.overrides.static",
                                                formatMethod(ctx.method()),
                                                formatClass(requireNonNull(ctx.method().getContainingClass())),
                                                formatMethod(ctx.superMethod()),
                                                formatClass(requireNonNull(ctx.superMethod().getContainingClass()))));
  public static final Parameterized<PsiMethod, PsiMethod> METHOD_OVERRIDES_FINAL =
    parameterized(PsiMethod.class, PsiMethod.class, "method.overrides.final")
      .withRange((method, superMethod) -> getMethodDeclarationTextRange(method))
      .withDescription((method, superMethod) -> {
        PsiClass superClass = superMethod.getContainingClass();
        return message("method.overrides.final",
                       formatMethod(method),
                       formatMethod(superMethod),
                       superClass != null ? formatClass(superClass) : "<unknown>");
      });
  public static final Parameterized<PsiMember, @NotNull OverrideClashContext> METHOD_INHERITANCE_WEAKER_PRIVILEGES =
    parameterized(PsiMember.class, OverrideClashContext.class, "method.inheritance.weaker.privileges")
      .withRange((psi, ctx) -> {
        if (psi instanceof PsiMethod method) {
          PsiModifierList modifierList = method.getModifierList();
          PsiElement keyword = PsiUtil.findModifierInList(modifierList, PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(modifierList)));
          if (keyword != null) {
            return keyword.getTextRange().shiftLeft(method.getTextRange().getStartOffset());
          }
          // in the case of package-private or some crazy third-party plugin where some access modifier implied even if it's absent
          PsiIdentifier identifier = method.getNameIdentifier();
          if (identifier != null) {
            return identifier.getTextRangeInParent();
          }
        }
        return getMemberDeclarationTextRange(psi);
      })
      .withDescription((psi, ctx) -> message(
        "method.inheritance.weaker.privileges",
        ctx.clashMessage(),
        VisibilityUtil.toPresentableText(PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(ctx.method().getModifierList()))),
        VisibilityUtil.toPresentableText(PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(ctx.superMethod().getModifierList())))));
  public static final Parameterized<PsiMember, OverrideClashContext> METHOD_GENERIC_CLASH =
    parameterized(PsiMember.class, OverrideClashContext.class, "method.generic.same.erasure")
      .withRange((member, ctx) -> getMemberDeclarationTextRange(member))
      .withDescription((member, ctx) -> {
        @NonNls String key = ctx.sameClass() ? "method.generic.same.erasure" :
                             ctx.method().hasModifierProperty(PsiModifier.STATIC) ?
                             "method.generic.same.erasure.hide" :
                             "method.generic.same.erasure.override";
        return message(key, ctx.clashMessage());
      });
    
  public static final Parameterized<PsiClass, @NotNull OverrideClashContext> METHOD_INHERITANCE_CLASH_UNRELATED_RETURN_TYPES =
    parameterized(PsiClass.class, OverrideClashContext.class, "method.inheritance.clash.unrelated.return.types")
      .withRange((cls, ctx) -> getClassDeclarationTextRange(cls))
      .withDescription((cls, ctx) -> message("method.inheritance.clash.unrelated.return.types",
                                             formatClashMethodMessage(ctx.superMethod(), ctx.method())));
  public static final Parameterized<PsiMember, @NotNull IncompatibleOverrideReturnTypeContext>
    METHOD_INHERITANCE_CLASH_INCOMPATIBLE_RETURN_TYPES =
    parameterized(PsiMember.class, IncompatibleOverrideReturnTypeContext.class, "method.inheritance.clash.incompatible.return.types")
      .withRange((psi, ctx) -> {
        if (psi instanceof PsiMethod method) {
          PsiTypeElement returnTypeElement = method.getReturnTypeElement();
          if (returnTypeElement != null) {
            return returnTypeElement.getTextRangeInParent();
          }
        }
        if (psi instanceof PsiRecordComponent component) {
          PsiTypeElement typeElement = component.getTypeElement();
          if (typeElement != null) {
            return typeElement.getTextRangeInParent();
          }
        }
        return getMemberDeclarationTextRange(psi);
      })
      .withDescription((cls, ctx) -> message("method.inheritance.clash.incompatible.return.types",
                                             formatClashMethodMessage(ctx.method(), ctx.superMethod())));
  public static final Parameterized<PsiMember, @NotNull IncompatibleOverrideExceptionContext>
    METHOD_INHERITANCE_CLASH_DOES_NOT_THROW =
    parameterized(PsiMember.class, IncompatibleOverrideExceptionContext.class, "method.inheritance.clash.does.not.throw")
      .withRange((psi, ctx) ->
                   ctx.exceptionReference() != null ? ctx.exceptionReference().getTextRange().shiftLeft(psi.getTextRange().getStartOffset()) :
                   getMemberDeclarationTextRange(psi))
      .withDescription((cls, ctx) -> message("method.inheritance.clash.does.not.throw",
                                             formatClashMethodMessage(ctx.method(), ctx.superMethod()),
                                             formatType(ctx.exceptionType())));
  public static final Parameterized<PsiMethod, String> METHOD_MISSING_RETURN_TYPE =
    parameterized(PsiMethod.class, String.class, "method.missing.return.type")
      .withAnchor(method -> requireNonNullElse(method.getNameIdentifier(), method));

  public static final Parameterized<PsiMember, AmbiguousImplicitConstructorCallContext> CONSTRUCTOR_AMBIGUOUS_IMPLICIT_CALL =
    parameterized(PsiMember.class, AmbiguousImplicitConstructorCallContext.class, "constructor.ambiguous.implicit.call")
      .withDescription((member, ctx) -> ctx.description())
      .withRange((member, ctx) -> getMemberDeclarationTextRange(member));
  public static final Parameterized<PsiMember, PsiClass> CONSTRUCTOR_NO_DEFAULT =
    parameterized(PsiMember.class, PsiClass.class, "constructor.no.default")
      .withDescription((member, cls) -> message("constructor.no.default", formatClass(requireNonNull(cls))))
      .withRange((member, ctx) -> getMemberDeclarationTextRange(member));
  public static final Simple<PsiMethod> CONSTRUCTOR_IN_IMPLICIT_CLASS =
    error(PsiMethod.class, "constructor.in.implicit.class")
      .withRange(JavaErrorFormatUtil::getMemberDeclarationTextRange);

  public static final Parameterized<PsiElement, Collection<PsiClassType>> EXCEPTION_UNHANDLED =
    error(PsiElement.class, "exception.unhandled")
      .withRange(JavaErrorFormatUtil::getRange)
      .withHighlightType(JavaErrorHighlightType.UNHANDLED_EXCEPTION)
      .<Collection<PsiClassType>>parameterized()
      .withDescription((psi, unhandled) -> message("exception.unhandled", formatTypes(unhandled), unhandled.size()));
  public static final Parameterized<PsiResourceListElement, Collection<PsiClassType>> EXCEPTION_UNHANDLED_CLOSE =
    error(PsiResourceListElement.class, "exception.unhandled")
      .withHighlightType(JavaErrorHighlightType.UNHANDLED_EXCEPTION)
      .<Collection<PsiClassType>>parameterized()
      .withDescription((psi, unhandled) -> message("exception.unhandled.close", formatTypes(unhandled), unhandled.size()));
  public static final Parameterized<PsiTypeElement, SuperclassSubclassContext> EXCEPTION_MUST_BE_DISJOINT =
    parameterized(PsiTypeElement.class, SuperclassSubclassContext.class, "exception.must.be.disjoint")
      .withDescription((te, ctx) -> message(
        "exception.must.be.disjoint",
        PsiFormatUtil.formatClass(ctx.subClass(), PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME),
        PsiFormatUtil.formatClass(ctx.superClass(), PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME)));
  public static final Parameterized<PsiTypeElement, PsiCatchSection> EXCEPTION_ALREADY_CAUGHT =
    parameterized(PsiTypeElement.class, PsiCatchSection.class, "exception.already.caught")
      .withDescription((te, upperCatch) -> message(
        "exception.already.caught", PsiFormatUtil.formatClass(
          requireNonNull(PsiUtil.resolveClassInClassTypeOnly(te.getType())),
          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME)));
  public static final Parameterized<PsiParameter, PsiClassType> EXCEPTION_NEVER_THROWN_TRY = 
    parameterized(PsiParameter.class, PsiClassType.class, "exception.never.thrown.try")
      .withDescription((parameter, type) -> message("exception.never.thrown.try", formatType(type)));
  public static final Parameterized<PsiTypeElement, PsiClassType> EXCEPTION_NEVER_THROWN_TRY_MULTI = 
    parameterized(PsiTypeElement.class, PsiClassType.class, "exception.never.thrown.try.multi")
      .withDescription((parameter, type) -> message("exception.never.thrown.try.multi", formatType(type)));

  public static final Parameterized<PsiElement, JavaIncompatibleTypeErrorContext> TYPE_INCOMPATIBLE =
    parameterized(PsiElement.class, JavaIncompatibleTypeErrorContext.class, "type.incompatible")
      .withAbsoluteRange((psi, context) -> psi.getParent() instanceof PsiAssignmentExpression assignment ? 
                                           assignment.getTextRange() : getRange(psi).shiftRight(psi.getTextRange().getStartOffset()))
      .withDescription((psi, context) -> context.createDescription())
      .withTooltip((psi, context) -> context.createTooltip());
  public static final Simple<PsiKeyword> TYPE_VOID_ILLEGAL = error("type.void.illegal");
  public static final Simple<PsiExpression> TYPE_VOID_NOT_ALLOWED = error("type.void.not.allowed");
  public static final Parameterized<PsiElement, PsiClass> TYPE_INACCESSIBLE =
    parameterized(PsiElement.class, PsiClass.class, "type.inaccessible")
      .withDescription((psi, cls) -> message("type.inaccessible", formatClass(cls)));
  public static final Simple<PsiTypeElement> TYPE_UNKNOWN_CLASS = error(PsiTypeElement.class, "type.unknown.class")
    .withDescription(type -> message("type.unknown.class", type.getType().getDeepComponentType().getCanonicalText()));
  public static final Simple<PsiTypeElement> TYPE_ARGUMENT_PRIMITIVE = error(PsiTypeElement.class, "type.argument.primitive");
  public static final Simple<PsiReferenceParameterList> TYPE_ARGUMENT_NOT_ALLOWED = error("type.argument.not.allowed");
  public static final Simple<PsiReferenceParameterList> TYPE_ARGUMENT_ON_RAW_TYPE = error("type.argument.on.raw.type");
  public static final Simple<PsiReferenceParameterList> TYPE_ARGUMENT_ON_RAW_METHOD = error("type.argument.on.raw.method");
  public static final Simple<PsiReferenceParameterList> TYPE_ARGUMENT_IN_PERMITS_LIST = error("type.argument.in.permits.list");
  public static final Simple<PsiTypeElement> TYPE_WILDCARD_NOT_EXPECTED = error(PsiTypeElement.class, "type.wildcard.not.expected");
  public static final Simple<PsiTypeElement> TYPE_WILDCARD_MAY_BE_USED_ONLY_AS_REFERENCE_PARAMETERS = 
    error(PsiTypeElement.class, "type.wildcard.may.be.used.only.as.reference.parameters");
  public static final Simple<PsiTypeElement> TYPE_WILDCARD_CANNOT_BE_INSTANTIATED = 
    error(PsiTypeElement.class, "type.wildcard.cannot.be.instantiated")
      .withDescription(type -> message("type.wildcard.cannot.be.instantiated", formatType(type.getType())));
  public static final Simple<PsiJavaCodeReferenceElement> TYPE_RESTRICTED_IDENTIFIER =
    error(PsiJavaCodeReferenceElement.class, "type.restricted.identifier")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription(ref -> message("type.restricted.identifier", ref.getReferenceName()));
  
  public static final Simple<PsiExpression> FOREACH_NOT_APPLICABLE = error(PsiExpression.class, "foreach.not.applicable")
    .withDescription(expression -> message("foreach.not.applicable", formatType(expression.getType())));

  public static final Simple<PsiLocalVariable> LVTI_NO_INITIALIZER = error(PsiLocalVariable.class, "lvti.no.initializer")
    .withAnchor(var -> var.getTypeElement());
  public static final Parameterized<PsiReferenceExpression, PsiLocalVariable> LVTI_SELF_REFERENCED = 
    parameterized(PsiReferenceExpression.class, PsiLocalVariable.class, "lvti.self.referenced")
      .withDescription((ref, var) -> message("lvti.self.referenced", var.getName()));
  public static final Simple<PsiLocalVariable> LVTI_COMPOUND = error(PsiLocalVariable.class, "lvti.compound");
  public static final Simple<PsiLocalVariable> LVTI_VOID = error(PsiLocalVariable.class, "lvti.void")
    .withAnchor(var -> var.getTypeElement());
  public static final Simple<PsiLocalVariable> LVTI_NULL = error(PsiLocalVariable.class, "lvti.null")
    .withAnchor(var -> var.getTypeElement());
  public static final Simple<PsiLocalVariable> LVTI_LAMBDA = error(PsiLocalVariable.class, "lvti.lambda")
    .withAnchor(var -> var.getTypeElement());
  public static final Simple<PsiLocalVariable> LVTI_METHOD_REFERENCE = error(PsiLocalVariable.class, "lvti.method.reference")
    .withAnchor(var -> var.getTypeElement());
  public static final Simple<PsiVariable> LVTI_ARRAY = error(PsiVariable.class, "lvti.array")
    .withAnchor(var -> var.getTypeElement());

  public static final Simple<PsiLabeledStatement> LABEL_WITHOUT_STATEMENT = error(PsiLabeledStatement.class, "label.without.statement")
    .withAnchor(label -> label.getLabelIdentifier());
  public static final Simple<PsiLabeledStatement> LABEL_DUPLICATE = error(PsiLabeledStatement.class, "label.duplicate")
    .withAnchor(label -> label.getLabelIdentifier())
    .withDescription(statement -> message("label.duplicate", statement.getLabelIdentifier().getText()));
  public static final Simple<PsiIdentifier> LABEL_UNRESOLVED = error(PsiIdentifier.class, "label.unresolved")
    .withDescription(label -> message("label.unresolved", label.getText()));
  public static final Parameterized<PsiContinueStatement, PsiIdentifier> LABEL_MUST_BE_LOOP = 
    parameterized(PsiContinueStatement.class, PsiIdentifier.class, "label.must.be.loop")
      .withDescription((statement, label) -> message("label.must.be.loop", label.getText()));
  
  public static final Simple<PsiBreakStatement> BREAK_OUTSIDE_SWITCH_OR_LOOP = error("break.outside.switch.or.loop");
  public static final Simple<PsiBreakStatement> BREAK_OUT_OF_SWITCH_EXPRESSION = error("break.out.of.switch.expression");
  public static final Simple<PsiContinueStatement> CONTINUE_OUTSIDE_LOOP = error("continue.outside.loop");
  public static final Simple<PsiContinueStatement> CONTINUE_OUT_OF_SWITCH_EXPRESSION = error("continue.out.of.switch.expression");
  public static final Simple<PsiYieldStatement> YIELD_UNEXPECTED = error("yield.unexpected");
  public static final Simple<PsiExpression> YIELD_VOID = error("yield.void");
  
  public static final Simple<PsiTypeElement> CATCH_TYPE_PARAMETER = error("catch.type.parameter");

  public static final Parameterized<PsiExpression, PsiType> ARRAY_ILLEGAL_INITIALIZER =
    parameterized(PsiExpression.class, PsiType.class, "array.illegal.initializer")
      .withDescription((expr, type) -> message("array.illegal.initializer", formatType(type)));
  public static final Simple<PsiArrayInitializerExpression> ARRAY_INITIALIZER_NOT_ALLOWED = error("array.initializer.not.allowed");
  public static final Parameterized<PsiExpression, PsiType> ARRAY_TYPE_EXPECTED =
    parameterized(PsiExpression.class, PsiType.class, "array.type.expected")
      .withDescription((expr, type) -> message("array.type.expected", formatType(type)));
  public static final Simple<PsiElement> ARRAY_GENERIC = error("array.generic");
  public static final Simple<PsiReferenceParameterList> ARRAY_EMPTY_DIAMOND = error("array.empty.diamond");
  public static final Simple<PsiReferenceParameterList> ARRAY_TYPE_ARGUMENTS = error("array.type.arguments");
  public static final Simple<PsiTypeElement> ARRAY_TOO_MANY_DIMENSIONS = error("array.too.many.dimensions");

  public static final Parameterized<PsiReferenceExpression, PsiClass> PATTERN_TYPE_PATTERN_EXPECTED =
    parameterized("pattern.type.pattern.expected");
  public static final Simple<PsiPatternVariable> PATTERN_DECONSTRUCTION_VARIABLE = error("pattern.deconstruction.variable");
  public static final Simple<PsiAnnotation> PATTERN_DECONSTRUCTION_ANNOTATION = error("pattern.deconstruction.annotation");
  public static final Parameterized<PsiPattern, PatternTypeContext> PATTERN_UNSAFE_CAST = 
    parameterized(PsiPattern.class, PatternTypeContext.class, "pattern.unsafe.cast")
    .withDescription((psi, ctx) -> message("pattern.unsafe.cast", ctx.contextType().getPresentableText(),
                                           ctx.patternType().getPresentableText()));
  public static final Parameterized<PsiDeconstructionPattern, PatternTypeContext> PATTERN_NOT_EXHAUSTIVE = 
    parameterized(PsiDeconstructionPattern.class, PatternTypeContext.class, "pattern.not.exhaustive")
    .withDescription((psi, ctx) -> message("pattern.not.exhaustive", formatType(ctx.patternType()), formatType(ctx.contextType())));
  public static final Simple<PsiTypeElement> PATTERN_DECONSTRUCTION_REQUIRES_RECORD =
    error(PsiTypeElement.class, "pattern.deconstruction.requires.record")
      .withDescription(type -> message("pattern.deconstruction.requires.record", formatType(type.getType())));
  public static final Parameterized<PsiTypeElement, @Nls String> PATTERN_CANNOT_INFER_TYPE =
    parameterized(PsiTypeElement.class, String.class, "pattern.cannot.infer.type")
      .withDescription((te, inferenceError) -> message("pattern.cannot.infer.type", inferenceError));
  public static final Parameterized<PsiDeconstructionList, DeconstructionCountMismatchContext> PATTERN_DECONSTRUCTION_COUNT_MISMATCH =
    parameterized(PsiDeconstructionList.class, DeconstructionCountMismatchContext.class, "pattern.deconstruction.count.mismatch")
      .withRange((list, ctx) -> {
        if (ctx.recordComponents().length < ctx.patternComponents().length && !ctx.hasMismatch()) {
          PsiPattern[] deconstructionComponents = list.getDeconstructionComponents();
          int endOffset = list.getTextLength();
          int startOffset = deconstructionComponents[ctx.recordComponents().length].getStartOffsetInParent();
          return TextRange.create(startOffset, endOffset);
        }
        return null;
      })
      .withNavigationShift((list, ctx) -> ctx.recordComponents().length < ctx.patternComponents().length && !ctx.hasMismatch() ? 0 : 1)
      .withDescription((list, ctx) -> message("pattern.deconstruction.count.mismatch",
                                              ctx.recordComponents().length, ctx.patternComponents().length));
  public static final Parameterized<PsiTypeTestPattern, JavaIncompatibleTypeErrorContext> PATTERN_INSTANCEOF_SUPERTYPE =
    parameterized(PsiTypeTestPattern.class, JavaIncompatibleTypeErrorContext.class, "pattern.instanceof.supertype")
      .withAnchor(PsiTypeTestPattern::getCheckType)
      .withDescription((expr, context) -> message(
        "pattern.instanceof.supertype", context.lType().getPresentableText(), requireNonNull(context.rType()).getPresentableText()));
  public static final Parameterized<PsiTypeTestPattern, PsiType> PATTERN_INSTANCEOF_EQUALS =
    parameterized(PsiTypeTestPattern.class, PsiType.class, "pattern.instanceof.equals")
      .withAnchor(PsiTypeTestPattern::getCheckType)
      .withDescription((expr, context) -> message("pattern.instanceof.equals", context.getPresentableText()));
  public static final Simple<PsiPattern> PATTERN_EXPECTED_CLASS_OR_ARRAY_TYPE =
    error(PsiPattern.class, "pattern.expected.class.or.array.type");
  
  public static final Simple<PsiTypeElement> INSTANCEOF_TYPE_PARAMETER = error("instanceof.type.parameter");
  public static final Simple<PsiTypeElement> INSTANCEOF_ILLEGAL_GENERIC_TYPE = error("instanceof.illegal.generic.type");
  public static final Parameterized<PsiTypeElement, JavaIncompatibleTypeErrorContext> INSTANCEOF_UNSAFE_CAST =
    parameterized(PsiTypeElement.class, JavaIncompatibleTypeErrorContext.class, "instanceof.unsafe.cast")
      .withDescription((expr, context) -> message(
        "instanceof.unsafe.cast", context.lType().getPresentableText(), requireNonNull(context.rType()).getPresentableText()));
  
  public static final Parameterized<PsiElement, JavaIncompatibleTypeErrorContext> CAST_INCONVERTIBLE =
    parameterized(PsiElement.class, JavaIncompatibleTypeErrorContext.class, "cast.inconvertible")
      .withDescription((psi, ctx) -> message("cast.inconvertible", formatType(ctx.lType()), formatType(ctx.rType())));
  public static final Simple<PsiTypeElement> CAST_INTERSECTION_NOT_INTERFACE = error("cast.intersection.not.interface");
  public static final Simple<PsiTypeElement> CAST_INTERSECTION_UNEXPECTED_TYPE = error("cast.intersection.unexpected.type");
  public static final Simple<PsiTypeElement> CAST_INTERSECTION_REPEATED_INTERFACE = error("cast.intersection.repeated.interface");
  public static final Parameterized<PsiTypeCastExpression, InheritTypeClashContext> CAST_INTERSECTION_INHERITANCE_CLASH = 
    parameterized(PsiTypeCastExpression.class, InheritTypeClashContext.class, "cast.intersection.inheritance.clash")
      .withDescription((cast, ctx) -> message("cast.intersection.inheritance.clash", formatClass(ctx.superClass()),
                                              requireNonNull(ctx.type1()).getPresentableText(),
                                              requireNonNull(ctx.type2()).getPresentableText()));

  public static final Simple<PsiExpression> RESOURCE_DECLARATION_OR_VARIABLE_EXPECTED =
    error("resource.declaration.or.variable.expected");

  public static final Simple<PsiSwitchLabeledRuleStatement> SWITCH_RULE_SHOULD_PRODUCE_RESULT =
    error(PsiSwitchLabeledRuleStatement.class, "switch.rule.should.produce.result")
      .withAnchor(rule -> requireNonNullElse(tryCast(rule.getFirstChild(), PsiKeyword.class), rule));
  public static final Simple<PsiSwitchExpression> SWITCH_EXPRESSION_NO_RESULT = 
    error(PsiSwitchExpression.class, "switch.expression.no.result")
      .withAnchor(switchExpression -> requireNonNullElse(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression));
  public static final Simple<PsiSwitchExpression> SWITCH_EXPRESSION_SHOULD_PRODUCE_RESULT = 
    error(PsiSwitchExpression.class, "switch.expression.should.produce.result")
      .withAnchor(switchExpression -> requireNonNullElse(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression));
  public static final Simple<PsiSwitchExpression> SWITCH_EXPRESSION_CANNOT_BE_VOID = 
    error(PsiSwitchExpression.class, "switch.expression.cannot.be.void")
      .withAnchor(switchExpression -> requireNonNullElse(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression));
  public static final Parameterized<PsiExpression, JavaIncompatibleTypeErrorContext> SWITCH_EXPRESSION_INCOMPATIBLE_TYPE = 
    parameterized(PsiExpression.class, JavaIncompatibleTypeErrorContext.class, "switch.expression.incompatible.type")
      .withDescription((expr, context) -> message("switch.expression.incompatible.type", formatType(context.rType()), formatType(context.lType())));
  public static final Simple<PsiElement> SWITCH_LABEL_EXPECTED = error(PsiElement.class, "switch.label.expected");
  public static final Simple<PsiStatement> SWITCH_DIFFERENT_CASE_KINDS = error(PsiStatement.class, "switch.different.case.kinds")
    .withRange(statement -> {
      if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
        PsiCaseLabelElementList list = rule.getCaseLabelElementList();
        if (list != null) {
          return TextRange.create(0, list.getTextRangeInParent().getEndOffset());
        }
      }
      return null;
    });
  public static final Parameterized<PsiExpression, JavaPsiSwitchUtil.SelectorKind> SWITCH_SELECTOR_TYPE_INVALID =
    parameterized(PsiExpression.class, JavaPsiSwitchUtil.SelectorKind.class, "switch.selector.type.invalid")
      .withDescription((expr, kind) -> kind.getFeature() == null ?
                                       message("switch.selector.type.invalid", formatType(expr.getType())) :
                                       message("switch.selector.type.invalid.level", formatType(expr.getType()), 
                                                  PsiUtil.getLanguageLevel(expr).getShortText()));
  public static final Parameterized<PsiCaseLabelElement, PsiType> SWITCH_NULL_TYPE_INCOMPATIBLE =
    parameterized(PsiCaseLabelElement.class, PsiType.class, "switch.null.type.incompatible")
      .withDescription((label, selectorType) -> message("switch.null.type.incompatible", formatType(selectorType)));
  public static final Simple<PsiReferenceExpression> SWITCH_LABEL_QUALIFIED_ENUM = 
    error(PsiReferenceExpression.class, "switch.label.qualified.enum");
  public static final Simple<PsiExpression> SWITCH_LABEL_CONSTANT_EXPECTED = 
    error(PsiExpression.class, "switch.label.constant.expected");
  public static final Simple<PsiCaseLabelElement> SWITCH_LABEL_UNEXPECTED = error("switch.label.unexpected");
  public static final Parameterized<PsiDefaultCaseLabelElement, PsiCaseLabelElementList> SWITCH_DEFAULT_LABEL_CONTAINS_CASE =
    parameterized("switch.default.label.contains.case");
  public static final Parameterized<PsiExpression, PsiType> SWITCH_LABEL_PATTERN_EXPECTED = 
    parameterized(PsiExpression.class, PsiType.class, "switch.label.pattern.expected")
      .withDescription((expr, type) -> message("switch.label.pattern.expected", formatType(type)));
  public static final Parameterized<PsiElement, Object> SWITCH_LABEL_DUPLICATE =
    parameterized(PsiElement.class, Object.class, "switch.label.duplicate")
      .withDescription((label, value) -> {
        if (value instanceof JavaPsiSwitchUtil.SwitchSpecialValue specialValue) {
          return switch (specialValue) {
            case UNCONDITIONAL_PATTERN -> message("switch.label.duplicate.unconditional.pattern");
            case DEFAULT_VALUE -> message("switch.label.duplicate.default");
            case NULL_VALUE -> message("switch.label.duplicate", JavaKeywords.NULL);
          };
        }
        else if (value instanceof PsiEnumConstant constant) {
          return message("switch.label.duplicate", constant.getName());
        }
        else if (label instanceof PsiLiteralExpression literalExpression) {
          return message("switch.label.duplicate", literalExpression.getValue());
        }
        else {
          return message("switch.label.duplicate", value);
        }
      });
  public static final Simple<PsiCaseLabelElement> SWITCH_FALLTHROUGH_TO_PATTERN = error("switch.fallthrough.to.pattern");
  public static final Simple<PsiCaseLabelElement> SWITCH_MULTIPLE_LABELS_WITH_PATTERN_VARIABLES = 
    error("switch.multiple.labels.with.pattern.variables");
  public static final Parameterized<PsiDefaultCaseLabelElement, PsiCaseLabelElementList> SWITCH_DEFAULT_NULL_ORDER = 
    parameterized("switch.default.null.order");
  public static final Simple<PsiDefaultCaseLabelElement> SWITCH_DEFAULT_LABEL_NOT_ALLOWED = 
    error("switch.default.label.not.allowed");
  public static final Simple<PsiExpression> SWITCH_NULL_LABEL_NOT_ALLOWED = 
    error("switch.null.label.not.allowed");
  public static final Simple<PsiCaseLabelElement> SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS = 
    error("switch.label.combination.constants.and.patterns");
  public static final Simple<PsiCaseLabelElement> SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS_UNNAMED = 
    error("switch.label.combination.constants.and.patterns.unnamed");
  public static final Simple<PsiCaseLabelElement> SWITCH_LABEL_MULTIPLE_PATTERNS = 
    error("switch.label.multiple.patterns");
  public static final Simple<PsiCaseLabelElement> SWITCH_LABEL_MULTIPLE_PATTERNS_UNNAMED = 
    error("switch.label.multiple.patterns.unnamed");
  public static final Parameterized<PsiCaseLabelElement, PsiElement> SWITCH_DOMINANCE_VIOLATION =
    parameterized(PsiCaseLabelElement.class, PsiElement.class, "switch.dominance.violation")
      .withDescription((overWhom, who) -> message("switch.dominance.violation", who.getText()));
  public static final Simple<PsiElement> SWITCH_UNCONDITIONAL_PATTERN_AND_DEFAULT =
    error("switch.unconditional.pattern.and.default");
  public static final Simple<PsiElement> SWITCH_DEFAULT_AND_BOOLEAN = 
    error("switch.default.and.boolean");
  public static final Simple<PsiCaseLabelElement> SWITCH_UNCONDITIONAL_PATTERN_AND_BOOLEAN = 
    error("switch.unconditional.pattern.and.boolean");
  public static final Simple<PsiSwitchBlock> SWITCH_EMPTY = error(PsiSwitchBlock.class, "switch.empty")
    .withAnchor(block -> requireNonNullElse(block.getExpression(), block.getFirstChild()))
    .withDescription(block -> message("switch.empty", JavaElementKind.fromElement(block).subject()));
  public static final Simple<PsiSwitchBlock> SWITCH_INCOMPLETE = error(PsiSwitchBlock.class, "switch.incomplete")
    .withAnchor(block -> requireNonNullElse(block.getExpression(), block.getFirstChild()))
    .withDescription(psi -> message("switch.incomplete", JavaElementKind.fromElement(psi).subject()));
  
  public static final Simple<PsiReferenceExpression> EXPRESSION_EXPECTED = error("expression.expected");
  public static final Parameterized<PsiReferenceExpression, PsiSuperExpression> EXPRESSION_SUPER_UNQUALIFIED_DEFAULT_METHOD = 
    parameterized("expression.super.unqualified.default.method");
  public static final Simple<PsiSuperExpression> EXPRESSION_SUPER_DOT_EXPECTED = 
    error(PsiSuperExpression.class, "expression.super.dot.expected")
      .withRange(expr -> TextRange.from(expr.getTextLength(), 1));
  public static final Parameterized<PsiSuperExpression, PsiClass> EXPRESSION_SUPER_NOT_ENCLOSING_CLASS = 
    parameterized(PsiSuperExpression.class, PsiClass.class, "expression.super.not.enclosing.class")
      .withDescription((expr, cls) -> message("expression.super.not.enclosing.class", formatClass(cls)));
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> EXPRESSION_SUPER_BAD_QUALIFIER_REDUNDANT_EXTENDED =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "expression.super.bad.qualifier.redundant.extended")
      .withDescription((expr, ctx) -> message("expression.super.bad.qualifier.redundant.extended",
                                              formatClass(ctx.subClass()), formatClass(ctx.superClass())));
  public static final Parameterized<PsiSuperExpression, PsiClass> EXPRESSION_SUPER_BAD_QUALIFIER_METHOD_OVERRIDDEN =
    parameterized(PsiSuperExpression.class, PsiClass.class, "expression.super.bad.qualifier.method.overridden")
      .withAnchor(PsiSuperExpression::getQualifier)
      .withDescription((expr, superClass) -> message("expression.super.bad.qualifier.method.overridden",
                                                     ((PsiReferenceExpression)expr.getParent()).getReferenceName(),
                                                     formatClass(superClass)));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> EXPRESSION_SUPER_NO_ENCLOSING_INSTANCE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "expression.super.no.enclosing.instance")
      .withDescription((expr, cls) -> message("expression.super.no.enclosing.instance", formatClass(cls)));
  public static final Simple<PsiJavaCodeReferenceElement> EXPRESSION_QUALIFIED_CLASS_EXPECTED = 
    error(PsiJavaCodeReferenceElement.class, "expression.qualified.class.expected");
  public static final Simple<PsiTypeElement> EXPRESSION_CLASS_TYPE_PARAMETER = error("expression.class.type.parameter");
  public static final Simple<PsiTypeElement> EXPRESSION_CLASS_PARAMETERIZED_TYPE = error("expression.class.parameterized.type");
  
  public static final Parameterized<PsiExpression, PsiVariable> ASSIGNMENT_DECLARED_OUTSIDE_GUARD =
    parameterized(PsiExpression.class, PsiVariable.class, "assignment.declared.outside.guard")
      .withDescription((expr, variable) -> message("assignment.declared.outside.guard", variable.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiVariable> ASSIGNMENT_TO_FINAL_VARIABLE =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "assignment.to.final.variable")
      .withDescription((expr, variable) -> message("assignment.to.final.variable", variable.getName()));
  public static final Simple<PsiExpression> LVALUE_VARIABLE_EXPECTED = error("lvalue.variable.expected"); 
  
  public static final Parameterized<PsiJavaToken, JavaIncompatibleTypeErrorContext> BINARY_OPERATOR_NOT_APPLICABLE =
    parameterized(PsiJavaToken.class, JavaIncompatibleTypeErrorContext.class, "binary.operator.not.applicable")
      .withAnchor(token -> TypeConversionUtil.convertEQtoOperation(token.getTokenType()) == null ? token.getParent() : token)
      .withDescription((token, context) -> {
        String text = token.getText();
        if (TypeConversionUtil.convertEQtoOperation(token.getTokenType()) != null) {
          text = text.replace("=", "");
        }
        return message("binary.operator.not.applicable", text, formatType(context.lType()), formatType(context.rType()));
      });
  public static final Parameterized<PsiUnaryExpression, PsiType> UNARY_OPERATOR_NOT_APPLICABLE =
    parameterized(PsiUnaryExpression.class, PsiType.class, "unary.operator.not.applicable")
      .withDescription((unary, type) -> message("unary.operator.not.applicable",
                                                unary.getOperationSign().getText(), formatType(type)));

  public static final Simple<PsiReferenceParameterList> NEW_EXPRESSION_DIAMOND_NOT_APPLICABLE =
    error("new.expression.diamond.not.applicable");
  public static final Simple<PsiNewExpression> NEW_EXPRESSION_QUALIFIED_MALFORMED =
    error("new.expression.qualified.malformed");
  public static final Parameterized<PsiNewExpression, PsiClass> NEW_EXPRESSION_QUALIFIED_STATIC_CLASS =
    parameterized("new.expression.qualified.static.class");
  public static final Parameterized<PsiNewExpression, PsiClass> NEW_EXPRESSION_QUALIFIED_ANONYMOUS_IMPLEMENTS_INTERFACE =
    parameterized("new.expression.qualified.anonymous.implements.interface");
  public static final Simple<PsiElement> NEW_EXPRESSION_QUALIFIED_QUALIFIED_CLASS_REFERENCE =
    error("new.expression.qualified.qualified.class.reference");
  public static final Simple<PsiReferenceParameterList> NEW_EXPRESSION_DIAMOND_NOT_ALLOWED =
    error("new.expression.diamond.not.allowed");
  public static final Simple<PsiReferenceParameterList> NEW_EXPRESSION_DIAMOND_ANONYMOUS_INNER_NON_PRIVATE =
    error("new.expression.diamond.anonymous.inner.non.private");
  public static final Simple<PsiReferenceParameterList> NEW_EXPRESSION_ANONYMOUS_IMPLEMENTS_INTERFACE_WITH_TYPE_ARGUMENTS =
    error("new.expression.anonymous.implements.interface.with.type.arguments");
  public static final Parameterized<PsiReferenceParameterList, PsiDiamondType.DiamondInferenceResult>
    NEW_EXPRESSION_DIAMOND_INFERENCE_FAILURE =
    parameterized(PsiReferenceParameterList.class, PsiDiamondType.DiamondInferenceResult.class, "new.expression.diamond.inference.failure")
      .withDescription(
        (list, inferenceResult) -> message("new.expression.diamond.inference.failure", inferenceResult.getErrorMessage()));
  public static final Simple<PsiConstructorCall> NEW_EXPRESSION_ARGUMENTS_TO_DEFAULT_CONSTRUCTOR_CALL =
    error(PsiConstructorCall.class, "new.expression.arguments.to.default.constructor.call")
      .withAnchor(call -> call.getArgumentList())
      .withNavigationShift(1);
  public static final Parameterized<PsiConstructorCall, UnresolvedConstructorContext> NEW_EXPRESSION_UNRESOLVED_CONSTRUCTOR =
    parameterized(PsiConstructorCall.class, UnresolvedConstructorContext.class, "new.expression.unresolved.constructor")
      .withAnchor(PsiCall::getArgumentList)
      .withNavigationShift(1)
      .withDescription((call, ctx) -> message("new.expression.unresolved.constructor", 
                                          ctx.psiClass().getName() + formatArgumentTypes(call.getArgumentList(), true)));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> NEW_EXPRESSION_TYPE_PARAMETER =
    parameterized(PsiJavaCodeReferenceElement.class, PsiTypeParameter.class, "new.expression.type.parameter")
      .withDescription((ref, typeParameter) -> message("new.expression.type.parameter", formatClass(typeParameter)));

  public static final Parameterized<PsiElement, String> REFERENCE_MEMBER_BEFORE_CONSTRUCTOR =
    parameterized(PsiElement.class, String.class, "reference.member.before.constructor")
      .withRange((psi, refName) -> getRange(psi))
      .withDescription((psi, refName) -> message("reference.member.before.constructor", refName));
  public static final Parameterized<PsiReferenceParameterList, PsiClass> REFERENCE_TYPE_ARGUMENT_STATIC_CLASS =
    parameterized(PsiReferenceParameterList.class, PsiClass.class, "reference.type.argument.static.class")
      .withDescription((list, cls) -> message("reference.type.argument.static.class", formatClass(cls)));
  public static final Simple<PsiJavaCodeReferenceElement> REFERENCE_TYPE_NEEDS_TYPE_ARGUMENTS =
    error(PsiJavaCodeReferenceElement.class, "reference.type.needs.type.arguments")
      .withDescription(ref -> message("reference.type.needs.type.arguments", requireNonNull(ref.getReferenceNameElement()).getText()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> REFERENCE_LOCAL_CLASS_OTHER_SWITCH_BRANCH =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "reference.local.class.other.switch.branch")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription((ref, cls) -> message("reference.local.class.other.switch.branch", formatClass(cls)));
  public static final Parameterized<PsiReferenceExpression, PsiField> REFERENCE_FIELD_FORWARD =
    parameterized(PsiReferenceExpression.class, PsiField.class, "reference.field.forward")
      .withDescription((ref, field) -> message("reference.field.forward", field.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiField> REFERENCE_FIELD_SELF =
    parameterized(PsiReferenceExpression.class, PsiField.class, "reference.field.self")
      .withDescription((ref, field) -> message("reference.field.self", field.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiField> REFERENCE_ENUM_FORWARD =
    parameterized(PsiReferenceExpression.class, PsiField.class, "reference.enum.forward")
      .withDescription((ref, field) -> message("reference.enum.forward", field.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiField> REFERENCE_ENUM_SELF =
    parameterized(PsiReferenceExpression.class, PsiField.class, "reference.enum.self")
      .withDescription((ref, field) -> message("reference.enum.self", field.getName()));
  public static final Simple<PsiExpression> REFERENCE_QUALIFIER_NOT_EXPRESSION =
    error(PsiExpression.class, "reference.qualifier.not.expression").withHighlightType(JavaErrorHighlightType.WRONG_REF);
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiPrimitiveType> REFERENCE_QUALIFIER_PRIMITIVE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiPrimitiveType.class, "reference.qualifier.primitive")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, type) -> message("reference.qualifier.primitive", type.getPresentableText()));
  public static final Simple<PsiElement> REFERENCE_PENDING =
    error(PsiElement.class, "incomplete.project.state.pending.reference")
      .withHighlightType(JavaErrorHighlightType.PENDING_REF);
  public static final Simple<PsiJavaCodeReferenceElement> REFERENCE_UNRESOLVED =
    error(PsiJavaCodeReferenceElement.class, "reference.unresolved")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription(ref -> message("reference.unresolved", ref.getReferenceName()))
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref));
  public static final Simple<PsiJavaCodeReferenceElement> REFERENCE_IMPLICIT_CLASS =
    error(PsiJavaCodeReferenceElement.class, "reference.implicit.class")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription(ref -> message("reference.implicit.class", ref.getReferenceName()))
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> REFERENCE_CLASS_IN_DEFAULT_PACKAGE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "reference.class.in.default.package")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription((ref, cls) -> message("reference.class.in.default.package", cls.getName()))
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref));
  public static final Parameterized<PsiJavaCodeReferenceElement, List<JavaResolveResult>> REFERENCE_AMBIGUOUS =
    error(PsiJavaCodeReferenceElement.class, "reference.ambiguous")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .<List<JavaResolveResult>>parameterized()
      .withDescription((ref, results) -> {
        String element1 = format(requireNonNull(results.get(0).getElement()));
        String element2 = format(requireNonNull(results.get(1).getElement()));
        boolean comparison = element1.compareTo(element2) < 0;
        return message("reference.ambiguous", ref.getReferenceName(),
                       comparison ? element1 : element2,
                       comparison ? element2 : element1);
      });
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiElement> REFERENCE_NON_STATIC_FROM_STATIC_CONTEXT =
    parameterized(PsiJavaCodeReferenceElement.class, PsiElement.class, "reference.non.static.from.static.context")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, refElement) -> {
        String type = JavaElementKind.fromElement(refElement).lessDescriptive().subject();
        String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
        return message("reference.non.static.from.static.context", type, name);
      });
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> REFERENCE_OUTER_TYPE_PARAMETER_FROM_STATIC_CONTEXT =
    parameterized(PsiJavaCodeReferenceElement.class, PsiTypeParameter.class, "reference.outer.type.parameter.from.static.context")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription((ref, refElement) -> message("reference.outer.type.parameter.from.static.context", refElement.getName()));
  public static final Simple<PsiJavaCodeReferenceElement> REFERENCE_SELECT_FROM_TYPE_PARAMETER =
    error(PsiJavaCodeReferenceElement.class, "reference.select.from.type.parameter");
  public static final Parameterized<PsiElement, String> REFERENCE_PACKAGE_NOT_FOUND =
    parameterized(PsiElement.class, String.class, "reference.package.not.found")
      .withDescription((psi, packageName) -> message("reference.package.not.found", packageName));
  
  public static final Simple<PsiSwitchLabelStatementBase> STATEMENT_CASE_OUTSIDE_SWITCH = error("statement.case.outside.switch");
  public static final Simple<PsiStatement> STATEMENT_INVALID = error("statement.invalid");
  public static final Simple<PsiExpression> STATEMENT_UNREACHABLE_LOOP_BODY = error("statement.unreachable.loop.body");
  public static final Simple<PsiElement> STATEMENT_UNREACHABLE = error(PsiElement.class, "statement.unreachable")
    .withAnchor(statement -> {
      PsiElement keyword = null;
      if (statement instanceof PsiIfStatement ||
          statement instanceof PsiSwitchBlock ||
          statement instanceof PsiLoopStatement ||
          statement instanceof PsiThrowStatement ||
          statement instanceof PsiReturnStatement ||
          statement instanceof PsiYieldStatement ||
          statement instanceof PsiTryStatement ||
          statement instanceof PsiSynchronizedStatement ||
          statement instanceof PsiAssertStatement ||
          statement instanceof PsiLabeledStatement) {
        keyword = statement.getFirstChild();
      }
      return keyword != null ? keyword : statement;
    });
  public static final Simple<PsiStatement> STATEMENT_BAD_EXPRESSION = error("statement.bad.expression");
  public static final Simple<PsiStatement> STATEMENT_DECLARATION_NOT_ALLOWED = error("statement.declaration.not.allowed");
  
  public static final Simple<PsiExpression> GUARD_MISPLACED = error("guard.misplaced");
  public static final Simple<PsiExpression> GUARD_EVALUATED_TO_FALSE = error("guard.evaluated.to.false");

  public static final Simple<PsiComment> COMMENT_SHEBANG_JAVA_FILE = error(PsiComment.class, "comment.shebang.java.file")
    .withRange(psi -> TextRange.create(0, 2));
  public static final Simple<PsiComment> COMMENT_UNCLOSED = error(PsiComment.class, "comment.unclosed")
    .withRange(psi -> TextRange.from(psi.getTextLength() - 1, 1));
  
  public static final Simple<PsiLiteralExpression> LITERAL_ILLEGAL_UNDERSCORE = error("literal.illegal.underscore");
  public static final Simple<PsiLiteralExpression> LITERAL_HEXADECIMAL_NO_DIGITS = error("literal.hexadecimal.no.digits");
  public static final Simple<PsiLiteralExpression> LITERAL_BINARY_NO_DIGITS = error("literal.binary.no.digits");
  public static final Simple<PsiLiteralExpression> LITERAL_INTEGER_TOO_LARGE = error("literal.integer.too.large");
  public static final Simple<PsiLiteralExpression> LITERAL_LONG_TOO_LARGE = error("literal.long.too.large");
  public static final Simple<PsiLiteralExpression> LITERAL_FLOATING_MALFORMED = error("literal.floating.malformed");
  public static final Simple<PsiLiteralExpression> LITERAL_FLOATING_TOO_LARGE = error("literal.floating.too.large");
  public static final Simple<PsiLiteralExpression> LITERAL_FLOATING_TOO_SMALL = error("literal.floating.too.small");
  public static final Parameterized<PsiLiteralExpression, @NotNull TextRange> LITERAL_CHARACTER_ILLEGAL_ESCAPE = 
    parameterized(PsiLiteralExpression.class, TextRange.class, "literal.character.illegal.escape").withRange((psi, range) -> range);
  public static final Simple<PsiLiteralExpression> LITERAL_CHARACTER_TOO_LONG = error("literal.character.too.long");
  public static final Simple<PsiLiteralExpression> LITERAL_CHARACTER_EMPTY = error("literal.character.empty");
  public static final Simple<PsiLiteralExpression> LITERAL_CHARACTER_UNCLOSED = error("literal.character.unclosed");
  public static final Parameterized<PsiLiteralValue, @NotNull TextRange> LITERAL_STRING_ILLEGAL_ESCAPE =
    parameterized(PsiLiteralValue.class, TextRange.class, "literal.string.illegal.escape").withRange((psi, range) -> range);
  public static final Simple<PsiLiteralValue> LITERAL_STRING_ILLEGAL_LINE_END = error("literal.string.illegal.line.end");
  public static final Simple<PsiLiteralExpression> LITERAL_TEXT_BLOCK_UNCLOSED = 
    error(PsiLiteralExpression.class, "literal.text.block.unclosed").withRange(e -> TextRange.from(e.getTextLength(), 0));
  public static final Simple<PsiLiteralValue> LITERAL_TEXT_BLOCK_NO_NEW_LINE = 
    error(PsiLiteralValue.class, "literal.text.block.no.new.line").withRange(e -> TextRange.create(0, 3));
  
  public static final Parameterized<PsiKeyword, String> MODIFIER_NOT_ALLOWED = 
    parameterized(PsiKeyword.class, String.class, "modifier.not.allowed")
    .withDescription((keyword, text) -> message("modifier.not.allowed", text));
  public static final Parameterized<PsiKeyword, String> MODIFIER_REPEATED = 
    parameterized(PsiKeyword.class, String.class, "modifier.repeated")
    .withDescription((keyword, text) -> message("modifier.repeated", text));
  public static final Parameterized<PsiKeyword, String> MODIFIER_INCOMPATIBLE = 
    parameterized(PsiKeyword.class, String.class, "modifier.incompatible")
    .withDescription((keyword, text) -> message("modifier.incompatible", keyword.getText(), text));
  public static final Parameterized<PsiKeyword, String> MODIFIER_NOT_ALLOWED_LOCAL_CLASS = 
    parameterized(PsiKeyword.class, String.class, "modifier.not.allowed.local.class")
    .withDescription((keyword, text) -> message("modifier.not.allowed.local.class", text));
  public static final Parameterized<PsiKeyword, String> MODIFIER_NOT_ALLOWED_NON_SEALED = 
    parameterized(PsiKeyword.class, String.class, "modifier.not.allowed.non.sealed");
  
  public static final Simple<PsiMethodCallExpression> CALL_SUPER_ENUM_CONSTRUCTOR = error("call.super.enum.constructor");
  public static final Parameterized<PsiExpression, PsiClass> CALL_SUPER_QUALIFIER_NOT_INNER_CLASS = 
    parameterized(PsiExpression.class, PsiClass.class, "call.super.qualifier.not.inner.class")
      .withDescription((psi, cls) -> message("call.super.qualifier.not.inner.class", formatClass(cls)));
  public static final Simple<PsiMethodCallExpression> CALL_EXPECTED = error("call.expected");
  public static final Simple<PsiDeconstructionPattern> CALL_PARSED_AS_DECONSTRUCTION_PATTERN =
    error("call.parsed.as.deconstruction.pattern");
  public static final Simple<PsiJavaCodeReferenceElement> CALL_STATIC_INTERFACE_METHOD_QUALIFIER =
    error(PsiJavaCodeReferenceElement.class, "call.static.interface.method.qualifier")
      .withRange(JavaErrorFormatUtil::getRange);
  public static final Parameterized<PsiCall, PsiClass> CALL_FORMAL_VARARGS_ELEMENT_TYPE_INACCESSIBLE_HERE =
    parameterized(PsiCall.class, PsiClass.class, "call.formal.varargs.element.type.inaccessible.here")
      .withAnchor(call -> requireNonNullElse(call.getArgumentList(), call))
      .withDescription((call, cls) -> message("call.formal.varargs.element.type.inaccessible.here", formatClass(cls)));
  public static final Parameterized<PsiCall, String> CALL_TYPE_INFERENCE_ERROR =
    parameterized(PsiCall.class, String.class, "call.type.inference.error")
      .withRange((psi, context) -> getRange(psi))
      .withDescription((psi, context) -> message("call.type.inference.error", context));
  public static final Parameterized<PsiElement, JavaMismatchedCallContext> CALL_WRONG_ARGUMENTS =
    parameterized(PsiElement.class, JavaMismatchedCallContext.class, "call.wrong.arguments")
      .withTooltip((psi, ctx) -> ctx.createTooltip())
      .withNavigationShift((psi, ctx) -> psi instanceof PsiExpressionList ? 1 : 0)
      .withDescription((psi, ctx) -> ctx.createDescription());
  public static final Parameterized<PsiMethodCallExpression, JavaResolveResult[]> CALL_UNRESOLVED =
    parameterized(PsiMethodCallExpression.class, JavaResolveResult[].class, "call.unresolved")
      .withAnchor(PsiMethodCallExpression::getArgumentList)
      .withNavigationShift(1)
      .withDescription((call, results) -> message(
        "call.unresolved", call.getMethodExpression().getReferenceName() + formatArgumentTypes(call.getArgumentList(), true)));
  public static final Parameterized<PsiMethodCallExpression, JavaResolveResult[]> CALL_UNRESOLVED_NAME =
    parameterized(PsiMethodCallExpression.class, JavaResolveResult[].class, "call.unresolved.name")
      .withRange((call, cls) -> getRange(call))
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription((call, results) -> message(
        "call.unresolved.name", call.getMethodExpression().getReferenceName() + formatArgumentTypes(call.getArgumentList(), true)));
  public static final Parameterized<PsiMethodCallExpression, JavaAmbiguousCallContext> CALL_AMBIGUOUS =
    parameterized(PsiMethodCallExpression.class, JavaAmbiguousCallContext.class, "call.ambiguous")
      .withAnchor(PsiMethodCallExpression::getArgumentList)
      .withNavigationShift((psi, ctx) -> 1)
      .withDescription((call, ctx) -> ctx.description())
      .withTooltip((call, ctx) -> ctx.tooltip());
  public static final Parameterized<PsiMethodCallExpression, JavaResolveResult[]> CALL_AMBIGUOUS_NO_MATCH =
    parameterized(PsiMethodCallExpression.class, JavaResolveResult[].class, "call.ambiguous.no.match")
      .withRange((call, cls) -> getRange(call))
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription(
        (call, cls) -> message("call.ambiguous.no.match", call.getMethodExpression().getReferenceName(),
                               requireNonNull(RefactoringChangeUtil.getQualifierClass(call.getMethodExpression())).getName()));
  public static final Parameterized<PsiMethodCallExpression, PsiPrimitiveType> CALL_QUALIFIER_PRIMITIVE =
    parameterized(PsiMethodCallExpression.class, PsiPrimitiveType.class, "call.qualifier.primitive")
      .withRange((call, type) -> getRange(call))
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription((call, type) -> message("call.qualifier.primitive", type.getPresentableText()));
    
  public static final Parameterized<PsiMethodCallExpression, PsiMethod> CALL_DIRECT_ABSTRACT_METHOD_ACCESS =
    parameterized(PsiMethodCallExpression.class, PsiMethod.class, "call.direct.abstract.method.access")
      .withDescription((call, method) -> message("call.direct.abstract.method.access", formatMethod(method)));
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_MUST_BE_FIRST_STATEMENT =
    error(PsiMethodCallExpression.class, "call.constructor.must.be.first.statement")
      .withDescription(call -> message("call.constructor.must.be.first.statement", call.getMethodExpression().getText() + "()"));
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_ONLY_ALLOWED_IN_CONSTRUCTOR =
    error(PsiMethodCallExpression.class, "call.constructor.only.allowed.in.constructor")
      .withDescription(call -> message("call.constructor.only.allowed.in.constructor", call.getMethodExpression().getText() + "()"));
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_MUST_BE_TOP_LEVEL_STATEMENT =
    error(PsiMethodCallExpression.class, "call.constructor.must.be.top.level.statement")
      .withDescription(call -> message("call.constructor.must.be.top.level.statement", call.getMethodExpression().getText() + "()"));
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_DUPLICATE =
    error(PsiMethodCallExpression.class, "call.constructor.duplicate");
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_RECURSIVE =
    error(PsiMethodCallExpression.class, "call.constructor.recursive");
  public static final Simple<PsiMethodCallExpression> CALL_CONSTRUCTOR_RECORD_IN_CANONICAL =
    error(PsiMethodCallExpression.class, "call.constructor.record.in.canonical");
  public static final Parameterized<PsiElement, String> CALL_MEMBER_BEFORE_CONSTRUCTOR =
    parameterized(PsiElement.class, String.class, "call.member.before.constructor")
      .withRange((psi, refName) -> getRange(psi))
      .withDescription((psi, refName) -> message("call.member.before.constructor", refName));

  public static final Simple<PsiExpression> STRING_TEMPLATE_VOID_NOT_ALLOWED_IN_EMBEDDED =
    error("string.template.void.not.allowed.in.embedded");
  public static final Simple<PsiTemplateExpression> STRING_TEMPLATE_PROCESSOR_MISSING =
    error("string.template.processor.missing");
  public static final Parameterized<PsiExpression, PsiType> STRING_TEMPLATE_RAW_PROCESSOR =
    parameterized(PsiExpression.class, PsiType.class, "string.template.raw.processor")
      .withDescription((psi, type) -> message("string.template.raw.processor", type.getPresentableText()));

  public static final Parameterized<PsiElement, JavaResolveResult> ACCESS_PRIVATE =
    parameterized(PsiElement.class, JavaResolveResult.class, "access.private")
      .withRange((psi, cls) -> getRange(psi))
      .withDescription((psi, result) -> message("access.private", formatResolvedSymbol(result), formatResolvedSymbolContainer(result)));
  public static final Parameterized<PsiElement, JavaResolveResult> ACCESS_PROTECTED =
    parameterized(PsiElement.class, JavaResolveResult.class, "access.protected")
      .withRange((psi, cls) -> getRange(psi))
      .withDescription(
        (psi, result) -> message("access.protected", formatResolvedSymbol(result), formatResolvedSymbolContainer(result)));
  public static final Parameterized<PsiElement, JavaResolveResult> ACCESS_PACKAGE_LOCAL =
    parameterized(PsiElement.class, JavaResolveResult.class, "access.package.local")
      .withRange((psi, cls) -> getRange(psi))
      .withDescription(
        (psi, result) -> message("access.package.local", formatResolvedSymbol(result), formatResolvedSymbolContainer(result)));
  public static final Parameterized<PsiElement, JavaResolveResult> ACCESS_GENERIC_PROBLEM =
    parameterized(PsiElement.class, JavaResolveResult.class, "access.generic.problem")
      .withRange((psi, cls) -> getRange(psi))
      .withDescription(
        (psi, result) -> message("access.generic.problem", formatResolvedSymbol(result), formatResolvedSymbolContainer(result)));

  public static final Simple<PsiImportModuleStatement> IMPORT_MODULE_NOT_ALLOWED =
    error(PsiImportModuleStatement.class, "module.import.not.allowed");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> IMPORT_SINGLE_CLASS_CONFLICT =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "import.single.class.conflict")
      .withDescription((ref, cls) -> message("import.single.class.conflict", cls.getQualifiedName()));
  public static final Simple<PsiJavaCodeReferenceElement> IMPORT_SINGLE_STATIC_CLASS_ALREADY_DEFINED =
    error(PsiJavaCodeReferenceElement.class, "import.single.static.class.already.defined")
      .withDescription(ref -> message("import.single.static.class.already.defined", ref.getReferenceName()));
  public static final Simple<PsiJavaCodeReferenceElement> IMPORT_SINGLE_STATIC_CLASS_AMBIGUOUS =
    error(PsiJavaCodeReferenceElement.class, "import.single.static.class.ambiguous")
      .withDescription(ref -> message("import.single.static.class.ambiguous", ref.getReferenceName()));
  public static final Simple<PsiJavaCodeReferenceElement> IMPORT_SINGLE_STATIC_FIELD_ALREADY_DEFINED =
    error(PsiJavaCodeReferenceElement.class, "import.single.static.field.already.defined")
      .withDescription(ref -> message("import.single.static.field.already.defined", ref.getReferenceName()));
  public static final Simple<PsiJavaCodeReferenceElement> IMPORT_SINGLE_STATIC_FIELD_AMBIGUOUS =
    error(PsiJavaCodeReferenceElement.class, "import.single.static.field.ambiguous")
      .withDescription(ref -> message("import.single.static.field.ambiguous", ref.getReferenceName()));
  public static final Simple<PsiJavaCodeReferenceElement> IMPORT_STATIC_ON_DEMAND_RESOLVES_TO_CLASS =
    error(PsiJavaCodeReferenceElement.class, "import.static.on.demand.resolves.to.class")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription(ref -> message("import.static.on.demand.resolves.to.class", ref.getCanonicalText()));
  public static final Simple<PsiJavaToken> IMPORT_LIST_EXTRA_SEMICOLON = error(PsiJavaToken.class, "import.list.extra.semicolon");
  
  public static final Simple<PsiIdentifier> UNDERSCORE_IDENTIFIER = error("underscore.identifier");
  public static final Simple<PsiIdentifier> UNDERSCORE_IDENTIFIER_UNNAMED = error("underscore.identifier.unnamed");
  public static final Simple<PsiIdentifier> UNDERSCORE_IDENTIFIER_LAMBDA = error("underscore.identifier.lambda");
  
  public static final Simple<PsiVariable> UNNAMED_VARIABLE_BRACKETS =
    error(PsiVariable.class, "unnamed.variable.brackets")
      .withRange(var -> {
        TokenSet brackets = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
        return Stream.of(var.getChildren())
          .filter(t -> PsiUtil.isJavaToken(t, brackets))
          .map(PsiElement::getTextRangeInParent)
          .reduce(TextRange::union)
          .orElseThrow(); // Must have at least one
      });
  public static final Simple<PsiLocalVariable> UNNAMED_VARIABLE_WITHOUT_INITIALIZER =
    error(PsiLocalVariable.class, "unnamed.variable.without.initializer")
      .withRange(var -> TextRange.create(0, requireNonNull(var.getNameIdentifier()).getTextRangeInParent().getEndOffset()));
  public static final Simple<PsiField> UNNAMED_FIELD_NOT_ALLOWED =
    error(PsiField.class, "unnamed.field.not.allowed")
      .withRange(var -> TextRange.create(0, requireNonNull(var.getNameIdentifier()).getTextRangeInParent().getEndOffset()));
  public static final Simple<PsiParameter> UNNAMED_METHOD_PARAMETER_NOT_ALLOWED =
    error(PsiParameter.class, "unnamed.method.parameter.not.allowed")
      .withRange(var -> TextRange.create(0, requireNonNull(var.getNameIdentifier()).getTextRangeInParent().getEndOffset()));
  public static final Simple<PsiVariable> UNNAMED_VARIABLE_NOT_ALLOWED_IN_THIS_CONTEXT =
    error(PsiVariable.class, "unnamed.variable.not.allowed.in.this.context")
      .withRange(var -> TextRange.create(0, requireNonNull(var.getNameIdentifier()).getTextRangeInParent().getEndOffset()));
  
  public static final Simple<PsiReturnStatement> RETURN_OUTSIDE_SWITCH_EXPRESSION =
    error(PsiReturnStatement.class, "return.outside.switch.expression");  
  public static final Simple<PsiReturnStatement> RETURN_COMPACT_CONSTRUCTOR =
    error(PsiReturnStatement.class, "return.compact.constructor");
  public static final Simple<PsiReturnStatement> RETURN_OUTSIDE_METHOD =
    error(PsiReturnStatement.class, "return.outside.method");
  public static final Parameterized<PsiReturnStatement, PsiMethod> RETURN_VALUE_MISSING =
    parameterized(PsiReturnStatement.class, PsiMethod.class, "return.value.missing");
  public static final Parameterized<PsiReturnStatement, PsiMethod> RETURN_FROM_CONSTRUCTOR =
    parameterized(PsiReturnStatement.class, PsiMethod.class, "return.from.constructor");
  public static final Parameterized<PsiElement, PsiParameterListOwner> RETURN_MISSING =
    parameterized(PsiElement.class, PsiParameterListOwner.class, "return.missing");
  public static final Parameterized<PsiReturnStatement, PsiMethod> RETURN_FROM_VOID_METHOD =
    parameterized(PsiReturnStatement.class, PsiMethod.class, "return.from.void.method");
  public static final Parameterized<PsiReturnStatement, PsiMethodCallExpression> RETURN_BEFORE_EXPLICIT_CONSTRUCTOR_CALL =
    parameterized(PsiReturnStatement.class, PsiMethodCallExpression.class, "return.before.explicit.constructor.call")
      .withDescription((psi, call) -> message("return.before.explicit.constructor.call", call.getMethodExpression().getText() + "()"));
  
  public static final Simple<PsiField> FIELD_NOT_INITIALIZED =
    error(PsiField.class, "field.not.initialized")
      .withRange(JavaErrorFormatUtil::getFieldDeclarationTextRange)
      .withDescription(var -> message("field.not.initialized", var.getName()));
  public static final Parameterized<PsiElement, PsiField> FIELD_INITIALIZED_BEFORE_CONSTRUCTOR_CALL =
    parameterized(PsiElement.class, PsiField.class, "field.initialized.before.constructor.call")
      .withDescription((psi, field) -> message(
        "field.initialized.before.constructor.call",
        PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY)));
  
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_NOT_INITIALIZED =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.not.initialized")
      .withDescription((ref, var) -> message("variable.not.initialized", var.getName()));
  public static final Parameterized<PsiVariable, PsiVariable> VARIABLE_ALREADY_DEFINED =
    parameterized(PsiVariable.class, PsiVariable.class, "variable.already.defined")
      .withAnchor(var -> requireNonNullElse(var.getNameIdentifier(), var))
      .withDescription((var, oldVar) -> message("variable.already.defined", var.getName()))
      .withTooltip((variable, oldVariable) -> {
        VirtualFile vFile = PsiUtilCore.getVirtualFile(variable);
        if (vFile == null) return HtmlChunk.empty();
        String path = FileUtil.toSystemIndependentName(vFile.getPath());
        Element link = HtmlChunk.link(
          "#navigation/" + path + ":" + oldVariable.getTextOffset(), requireNonNull(variable.getName()));
        return HtmlChunk.raw(message("variable.already.defined", link)).wrapWith(HtmlChunk.html());
      });
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_ALREADY_ASSIGNED =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.already.assigned")
      .withDescription((ref, var) -> message("variable.already.assigned", var.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_ALREADY_ASSIGNED_CONSTRUCTOR =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.already.assigned.constructor")
      .withDescription((ref, var) -> {
        PsiMethod constructor = PsiTreeUtil.getParentOfType(ref, PsiMethod.class);
        assert constructor != null;
        PsiMethodCallExpression thisCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
        assert thisCall != null;
        return message("variable.already.assigned.constructor", var.getName(),
                       thisCall.getTextOffset() + thisCall.getTextLength() > ref.getTextOffset() ? 1 : 2);
      });
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_ALREADY_ASSIGNED_FIELD =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.already.assigned.field")
      .withDescription((ref, var) -> message("variable.already.assigned.field", var.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_ALREADY_ASSIGNED_INITIALIZER =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.already.assigned.initializer")
      .withDescription((ref, var) -> message("variable.already.assigned.initializer", var.getName()));
  public static final Parameterized<PsiReferenceExpression, PsiVariable> VARIABLE_ASSIGNED_IN_LOOP =
    parameterized(PsiReferenceExpression.class, PsiVariable.class, "variable.assigned.in.loop")
      .withDescription((ref, var) -> message("variable.assigned.in.loop", var.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiVariable> VARIABLE_MUST_BE_FINAL =
    parameterized(PsiJavaCodeReferenceElement.class, PsiVariable.class, "variable.must.be.final")
      .withDescription((ref, var) -> message("variable.must.be.final", var.getName()));
  public static final Simple<PsiReferenceExpression> VARIABLE_MUST_BE_FINAL_RESOURCE =
    error(PsiReferenceExpression.class, "variable.must.be.final.resource");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiVariable> VARIABLE_MUST_BE_EFFECTIVELY_FINAL =
    parameterized(PsiJavaCodeReferenceElement.class, PsiVariable.class, "variable.must.be.effectively.final")
      .withDescription((ref, var) -> message("variable.must.be.effectively.final", var.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiVariable> VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA =
    parameterized(PsiJavaCodeReferenceElement.class, PsiVariable.class, "variable.must.be.effectively.final.lambda");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiVariable> VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD =
    parameterized(PsiJavaCodeReferenceElement.class, PsiVariable.class, "variable.must.be.effectively.final.guard");
  
  public static final Simple<PsiPackageStatement> MODULE_NO_PACKAGE = error("module.no.package");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiJavaModule> MODULE_CONFLICTING_PACKAGES = 
    parameterized(PsiJavaCodeReferenceElement.class, PsiJavaModule.class, "module.conflicting.packages")
      .withDescription((ref, module) -> message("module.conflicting.packages", ref.getText(), module.getName()));
  public static final Simple<PsiJavaModule> MODULE_FILE_WRONG_NAME = error(PsiJavaModule.class, "module.file.wrong.name")
    .withRange(JavaErrorFormatUtil::getRange);
  public static final Parameterized<PsiJavaModule, PsiFile> MODULE_FILE_DUPLICATE = 
    parameterized(PsiJavaModule.class, PsiFile.class, "module.file.duplicate")
    .withRange((module, file) -> getRange(module));
  public static final Parameterized<PsiRequiresStatement, String> MODULE_DUPLICATE_REQUIRES =
    parameterized(PsiRequiresStatement.class, String.class, "module.duplicate.requires")
      .withDescription((requires, refName) -> message("module.duplicate.requires", refName));
  public static final Parameterized<PsiPackageAccessibilityStatement, String> MODULE_DUPLICATE_EXPORTS =
    parameterized(PsiPackageAccessibilityStatement.class, String.class, "module.duplicate.exports")
      .withDescription((requires, refName) -> message("module.duplicate.exports", refName));
  public static final Parameterized<PsiPackageAccessibilityStatement, String> MODULE_DUPLICATE_OPENS =
    parameterized(PsiPackageAccessibilityStatement.class, String.class, "module.duplicate.opens")
      .withDescription((requires, refName) -> message("module.duplicate.opens", refName));
  public static final Parameterized<PsiUsesStatement, String> MODULE_DUPLICATE_USES =
    parameterized(PsiUsesStatement.class, String.class, "module.duplicate.uses")
      .withDescription((requires, refName) -> message("module.duplicate.uses", refName));
  public static final Parameterized<PsiProvidesStatement, String> MODULE_DUPLICATE_PROVIDES =
    parameterized(PsiProvidesStatement.class, String.class, "module.duplicate.provides")
      .withDescription((requires, refName) -> message("module.duplicate.provides", refName));
  public static final Parameterized<PsiJavaModule, JavaPsiModuleUtil.ModulePackageConflict> MODULE_CONFLICTING_READS =
    parameterized(PsiJavaModule.class, JavaPsiModuleUtil.ModulePackageConflict.class, "module.conflicting.reads")
      .withRange((module, conflict) -> getRange(module))
      .withDescription((module, conflict) -> message("module.conflicting.reads",
                                                     module.getName(), conflict.packageName(),
                                                     conflict.module1().getName(), conflict.module2().getName()));
  public static final Parameterized<PsiJavaModule, VirtualFile> MODULE_FILE_WRONG_LOCATION =
    parameterized(PsiJavaModule.class, VirtualFile.class, "module.file.wrong.location")
      .withRange((module, file) -> getRange(module));
  public static final Parameterized<PsiPackageAccessibilityStatement, PsiJavaModule> MODULE_OPENS_IN_WEAK_MODULE =
    parameterized(PsiPackageAccessibilityStatement.class, PsiJavaModule.class, "module.opens.in.weak.module");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> MODULE_SERVICE_ENUM =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "module.service.enum")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, cls) -> message("module.service.enum", cls.getName()));
  public static final Simple<PsiJavaCodeReferenceElement> MODULE_SERVICE_ALIEN =
    error(PsiJavaCodeReferenceElement.class, "module.service.alien")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref));
  public static final Simple<PsiJavaModuleReferenceElement> MODULE_NOT_FOUND =
    error(PsiJavaModuleReferenceElement.class, "module.not.found")
      .withHighlightType(JavaErrorHighlightType.WRONG_REF)
      .withDescription(ref -> message("module.not.found", ref.getReferenceText()));
  public static final Simple<PsiJavaModuleReferenceElement> MODULE_NOT_ON_PATH =
    error(PsiJavaModuleReferenceElement.class, "module.not.on.path")
      .withDescription(ref -> message("module.not.on.path", ref.getReferenceText()));
  public static final Parameterized<PsiJavaModuleReferenceElement, Collection<PsiJavaModule>> MODULE_CYCLIC_DEPENDENCE =
    error(PsiJavaModuleReferenceElement.class, "module.cyclic.dependence")
      .<Collection<PsiJavaModule>>parameterized()
      .withDescription(
        (ref, modules) -> message("module.cyclic.dependence", modules.stream().map(PsiJavaModule::getName)
          .sorted().collect(Collectors.joining(", "))));
  public static final Simple<PsiJavaCodeReferenceElement> MODULE_DUPLICATE_IMPLEMENTATION =
    error(PsiJavaCodeReferenceElement.class, "module.duplicate.implementation")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription(ref -> message("module.duplicate.implementation", ref.getQualifiedName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> MODULE_SERVICE_PROVIDER_TYPE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "module.service.provider.type")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, impl) -> message("module.service.provider.type", impl.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> MODULE_SERVICE_NO_CONSTRUCTOR =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "module.service.no.constructor")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, impl) -> message("module.service.no.constructor", impl.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> MODULE_SERVICE_ABSTRACT =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "module.service.abstract")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, impl) -> message("module.service.abstract", impl.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> MODULE_SERVICE_INNER =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "module.service.inner")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref))
      .withDescription((ref, impl) -> message("module.service.inner", impl.getName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, SuperclassSubclassContext> MODULE_SERVICE_IMPLEMENTATION_TYPE =
    parameterized(PsiJavaCodeReferenceElement.class, SuperclassSubclassContext.class, "module.service.implementation.type")
      .withAnchor(ref -> requireNonNullElse(ref.getReferenceNameElement(), ref));

  public static final Simple<PsiJavaModuleReferenceElement> MODULE_DUPLICATE_EXPORTS_TARGET =
    error(PsiJavaModuleReferenceElement.class, "module.duplicate.exports.target")
      .withDescription(ref -> message("module.duplicate.exports.target", ref.getReferenceText()));
  public static final Simple<PsiJavaModuleReferenceElement> MODULE_DUPLICATE_OPENS_TARGET =
    error(PsiJavaModuleReferenceElement.class, "module.duplicate.opens.target")
      .withDescription(ref -> message("module.duplicate.opens.target", ref.getReferenceText()));
  public static final Simple<PsiPackageAccessibilityStatement> MODULE_REFERENCE_PACKAGE_NOT_FOUND =
    error(PsiPackageAccessibilityStatement.class, "module.reference.package.not.found")
      .withAnchor(st -> st.getPackageReference())
      .withDescription(st -> message("module.reference.package.not.found", st.getPackageName()));
  public static final Simple<PsiPackageAccessibilityStatement> MODULE_REFERENCE_PACKAGE_EMPTY =
    error(PsiPackageAccessibilityStatement.class, "module.reference.package.empty")
      .withAnchor(st -> st.getPackageReference())
      .withDescription(st -> message("module.reference.package.empty", st.getPackageName()));

  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_FROM_NAMED =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.from.named")
      .withDescription((psi, info) -> message(
        "module.access.from.named", info.getTarget().getPackageName(), requireNonNull(info.getTarget().getModule()).getName(), info.getCurrent().getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_FROM_UNNAMED =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.from.unnamed")
      .withDescription((psi, info) -> message("module.access.from.unnamed", info.getTarget().getPackageName(), requireNonNull(
        info.getTarget().getModule()).getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_TO_UNNAMED =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.to.unnamed")
      .withDescription((psi, info) -> message("module.access.to.unnamed", info.getTarget().getPackageName(), info.getCurrent().getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_PACKAGE_BAD_NAME =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.package.bad.name")
      .withDescription((psi, info) -> message("module.access.package.bad.name", info.getTarget().getPackageName(), requireNonNull(
        info.getTarget().getModule()).getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_BAD_NAME =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.bad.name")
      .withDescription((psi, info) -> message("module.access.bad.name", requireNonNull(info.getTarget().getModule()).getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_PACKAGE_NOT_IN_GRAPH =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.package.not.in.graph")
      .withDescription((psi, info) -> message("module.access.package.not.in.graph", info.getTarget().getPackageName(), requireNonNull(
        info.getTarget().getModule()).getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_NOT_IN_GRAPH =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.not.in.graph")
      .withDescription((psi, info) -> message("module.access.not.in.graph", requireNonNull(info.getTarget().getModule()).getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_PACKAGE_DOES_NOT_READ =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.package.does.not.read")
      .withDescription((psi, info) -> message("module.access.package.does.not.read", info.getTarget().getPackageName(), requireNonNull(
        info.getTarget().getModule()).getName(), info.getCurrent().getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_DOES_NOT_READ =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.does.not.read")
      .withDescription((psi, info) -> message("module.access.does.not.read", requireNonNull(info.getTarget().getModule()).getName(), info.getCurrent().getName()));
  public static final Parameterized<PsiElement, JpmsModuleAccessInfo> MODULE_ACCESS_JPS_DEPENDENCY_PROBLEM =
    parameterized(PsiElement.class, JpmsModuleAccessInfo.class, "module.access.jps.dependency.problem")
      .withDescription((psi, info) -> message("module.access.jps.dependency.problem", requireNonNull(info.getTarget().getModule()).getName()));
  
  private static @NotNull <Psi extends PsiElement> Simple<Psi> error(
    @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    return new Simple<>(key);
  }

  private static @NotNull <Psi extends PsiElement> Simple<Psi> error(
    @SuppressWarnings("unused") @NotNull Class<Psi> psiClass,
    @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    return error(key);
  }

  private static @NotNull <Psi extends PsiElement, Context> Parameterized<Psi, Context> parameterized(
    @SuppressWarnings("unused") @NotNull Class<Psi> psiClass,
    @SuppressWarnings("unused") @NotNull Class<Context> contextClass,
    @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    return new Parameterized<>(key);
  }

  private static @NotNull <Psi extends PsiElement, Context> Parameterized<Psi, Context> parameterized(
    @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    return new Parameterized<>(key);
  }

  /**
   * Context for errors related to the annotation value
   * @param method corresponding annotation method
   * @param expectedType expected value type
   * @param fromDefaultValue if true, the error is reported for the method default value, rather than for use site
   */
  public record AnnotationValueErrorContext(@NotNull PsiAnnotationMethod method, 
                                            @NotNull PsiType expectedType, 
                                            boolean fromDefaultValue) {
    public @NotNull String typeText() {
      return PsiTypesUtil.removeExternalAnnotations(expectedType()).getInternalCanonicalText();
    }

    public static @NotNull AnnotationValueErrorContext from(@NotNull PsiAnnotationMemberValue value,
                                                             @NotNull PsiAnnotationMethod method,
                                                             @NotNull PsiType expectedType) {
      boolean fromDefaultValue = PsiTreeUtil.isAncestor(method.getDefaultValue(), value, false);
      AnnotationValueErrorContext context = new AnnotationValueErrorContext(method, expectedType, fromDefaultValue);
      return context;
    }
  }

  public record ClassStaticReferenceErrorContext(@NotNull PsiClass outerClass,
                                                 @Nullable PsiClass innerClass,
                                                 @NotNull PsiElement place) {
    public @Nullable PsiModifierListOwner enclosingStaticElement() {
      return PsiUtil.getEnclosingStaticElement(place, outerClass);
    }
  }

  /**
   * A context for {@link #CONSTRUCTOR_AMBIGUOUS_IMPLICIT_CALL} error kind
   * @param psiClass a class where an ambiguous call is performed
   * @param candidate1 first constructor candidate in super class
   * @param candidate2 second constructor candidate in super class
   */
  public record AmbiguousImplicitConstructorCallContext(@NotNull PsiClass psiClass,
                                                        @NotNull PsiMethod candidate1,
                                                        @NotNull PsiMethod candidate2) {
    @Nls String description() {
      String m1 = PsiFormatUtil.formatMethod(candidate1, PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      String m2 = PsiFormatUtil.formatMethod(candidate2, PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      return message("constructor.ambiguous.implicit.call", m1, m2);
    }
  }

  public record OverrideClashContext(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    boolean sameClass() {
      PsiClass cls1 = method.getContainingClass();
      PsiClass cls2 = superMethod.getContainingClass();
      return cls1 != null && cls2 != null && cls1.isEquivalentTo(cls2);
    }

    @NotNull @Nls String clashMessage() {
      return formatClashMethodMessage(method, superMethod);
    }
  }
  
  public record InheritTypeClashContext(@NotNull PsiClass superClass, @Nullable PsiType type1, @Nullable PsiType type2) {}

  public record IncompatibleOverrideReturnTypeContext(@NotNull PsiMethod method,
                                                      @NotNull PsiType methodReturnType,
                                                      @NotNull PsiMethod superMethod,
                                                      @NotNull PsiType superMethodReturnType) {
  }

  public record IncompatibleOverrideExceptionContext(@NotNull PsiMethod method,
                                                     @NotNull PsiMethod superMethod,
                                                     @NotNull PsiClassType exceptionType,
                                                     @Nullable PsiJavaCodeReferenceElement exceptionReference) {
  }

  public record SuperclassSubclassContext(@NotNull PsiClass superClass, @NotNull PsiClass subClass) {
  }

  public record IncompatibleIntersectionContext(@NotNull PsiTypeParameter parameter, @NotNull @Nls String message) {}

  public record TypeParameterBoundMismatchContext(@NotNull PsiTypeParameter parameter,
                                                  @NotNull PsiType bound,
                                                  @NotNull PsiType actualType) {

  }
  
  public record UnresolvedConstructorContext(@NotNull PsiClass psiClass, @NotNull JavaResolveResult @NotNull [] results) {
  }
  
  public record PatternTypeContext(@NotNull PsiType contextType, @NotNull PsiType patternType) {}
  
  public record DeconstructionCountMismatchContext(@NotNull PsiPattern @NotNull [] patternComponents,
                                                   @NotNull PsiRecordComponent @NotNull [] recordComponents,
                                                   boolean hasMismatch) {}

  public record DuplicateMethodsContext(@NotNull List<@NotNull PsiMethod> methods) {}
}
