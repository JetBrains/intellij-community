// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind.Parameterized;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind.Simple;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle.message;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorFormatUtil.formatClass;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorFormatUtil.formatMethod;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * All possible Java error kinds
 */
public final class JavaErrorKinds {
  private JavaErrorKinds() {}

  public static final Parameterized<PsiElement, @NotNull JavaFeature> UNSUPPORTED_FEATURE =
    parameterized(PsiElement.class, JavaFeature.class, "insufficient.language.level")
      .withRawDescription((element, feature) -> {
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

  public static final Simple<PsiAnnotation> ANNOTATION_NOT_ALLOWED_HERE = error("annotation.not.allowed.here");
  public static final Simple<PsiPackageStatement> ANNOTATION_NOT_ALLOWED_ON_PACKAGE =
    error(PsiPackageStatement.class, "annotation.not.allowed.on.package")
      .withAnchor(statement -> requireNonNull(statement.getAnnotationList()));
  public static final Simple<PsiReferenceList> ANNOTATION_MEMBER_THROWS_NOT_ALLOWED =
    error(PsiReferenceList.class, "annotation.member.may.not.have.throws.list").withAnchor(list -> requireNonNull(list.getFirstChild()));
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
  public static final Simple<PsiNameValuePair> ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING =
    error("annotation.attribute.annotation.name.is.missing");
  public static final Simple<PsiAnnotationMemberValue> ANNOTATION_ATTRIBUTE_NON_CLASS_LITERAL =
    error("annotation.attribute.non.class.literal");
  public static final Simple<PsiExpression> ANNOTATION_ATTRIBUTE_NON_ENUM_CONSTANT = error("annotation.attribute.non.enum.constant");
  public static final Simple<PsiExpression> ANNOTATION_ATTRIBUTE_NON_CONSTANT = error("annotation.attribute.non.constant");
  public static final Simple<PsiTypeElement> ANNOTATION_CYCLIC_TYPE = error("annotation.cyclic.element.type");
  public static final Parameterized<PsiMethod, PsiMethod> ANNOTATION_MEMBER_CLASH =
    error(PsiMethod.class, "annotation.member.clash")
      .withAnchor(curMethod -> requireNonNull(curMethod.getNameIdentifier()))
      .<PsiMethod>parameterized()
      .withRawDescription((curMethod, clashMethod) -> {
        PsiClass containingClass = requireNonNull(clashMethod.getContainingClass());
        return message("annotation.member.clash", formatMethod(clashMethod), formatClass(containingClass));
      });
  public static final Parameterized<PsiTypeElement, PsiType> ANNOTATION_METHOD_INVALID_TYPE =
    parameterized(PsiTypeElement.class, PsiType.class, "annotation.member.invalid.type")
      .withRawDescription((element, type) ->
                            message("annotation.member.invalid.type", type == null ? null : type.getPresentableText()));
  public static final Parameterized<PsiAnnotationMemberValue, AnnotationValueErrorContext> ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE =
    parameterized(PsiAnnotationMemberValue.class, AnnotationValueErrorContext.class, 
                  "annotation.attribute.incompatible.type").withRawDescription((value, context) -> {
      String text = value instanceof PsiAnnotation annotation ? requireNonNull(annotation.getNameReferenceElement()).getText() :
                    PsiTypesUtil.removeExternalAnnotations(requireNonNull(((PsiExpression)value).getType())).getInternalCanonicalText();
      return message("annotation.attribute.incompatible.type", context.typeText(), text);
    });
  public static final Parameterized<PsiArrayInitializerMemberValue, AnnotationValueErrorContext> ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER =
    parameterized(PsiArrayInitializerMemberValue.class, AnnotationValueErrorContext.class, 
                  "annotation.attribute.illegal.array.initializer").withRawDescription((element, context) -> {
      return message("annotation.attribute.illegal.array.initializer", context.typeText());
    });
  public static final Parameterized<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_DUPLICATE =
    parameterized(PsiNameValuePair.class, String.class, "annotation.attribute.duplicate")
      .withRawDescription((pair, attribute) -> message("annotation.attribute.duplicate", attribute));
  public static final Parameterized<PsiNameValuePair, String> ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD =
    error(PsiNameValuePair.class, "annotation.attribute.unknown.method")
      .withAnchor(pair -> requireNonNull(pair.getReference()).getElement())
      .withHighlightType(pair -> pair.getName() == null ? JavaErrorHighlightType.ERROR : JavaErrorHighlightType.WRONG_REF)
      .<String>parameterized()
      .withRawDescription((pair, methodName) -> message("annotation.attribute.unknown.method", methodName));
  public static final Simple<PsiReferenceList> ANNOTATION_PERMITS = error(PsiReferenceList.class, "annotation.permits")
    .withAnchor(PsiReferenceList::getFirstChild);

  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final Parameterized<PsiElement, PsiClass> LAMBDA_NOT_FUNCTIONAL_INTERFACE =
    parameterized(PsiElement.class, PsiClass.class, "lambda.not.a.functional.interface")
      .withRawDescription((element, aClass) -> message("lambda.not.a.functional.interface", aClass.getName()));
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final Parameterized<PsiElement, PsiClass> LAMBDA_NO_TARGET_METHOD =
    parameterized("lambda.no.target.method.found");
  // Can be anchored on @FunctionalInterface annotation or at call site
  public static final Parameterized<PsiElement, PsiClass> LAMBDA_MULTIPLE_TARGET_METHODS =
    parameterized(PsiElement.class, PsiClass.class, "lambda.multiple.sam.candidates")
      .withRawDescription((psi, aClass) -> message("lambda.multiple.sam.candidates", aClass.getName()));
  public static final Parameterized<PsiAnnotation, PsiClass> LAMBDA_FUNCTIONAL_INTERFACE_SEALED =
    parameterized("lambda.sealed.functional.interface");
  public static final Parameterized<PsiAnnotation, @NotNull List<PsiAnnotation.@NotNull TargetType>> ANNOTATION_NOT_APPLICABLE =
    error(PsiAnnotation.class, "annotation.not.applicable").<@NotNull List<PsiAnnotation.@NotNull TargetType>>parameterized()
      .withValidator((annotation, types) -> {
        if (types.isEmpty()) {
          throw new IllegalArgumentException("types must not be empty");
        }
      })
      .withRawDescription((annotation, types) -> {
        String target = JavaPsiBundle.message("annotation.target." + types.get(0));
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return message("annotation.not.applicable", nameRef != null ? nameRef.getText() : annotation.getText(), target);
      });
  public static final Parameterized<PsiAnnotation, @NotNull List<String>> ANNOTATION_MISSING_ATTRIBUTE =
    error(PsiAnnotation.class, "annotation.missing.attribute")
      .withAnchor(annotation -> annotation.getNameReferenceElement())
      .<@NotNull List<String>>parameterized()
      .withRawDescription((annotation, attributeNames) -> message(
          "annotation.missing.attribute", attributeNames.stream().map(attr -> "'" + attr + "'").collect(Collectors.joining(", "))));
  public static final Simple<PsiAnnotation> ANNOTATION_CONTAINER_WRONG_PLACE =
    error(PsiAnnotation.class, "annotation.container.wrong.place")
      .withRawDescription(annotation ->
                            message("annotation.container.wrong.place",
                                    requireNonNull(annotation.resolveAnnotationType()).getQualifiedName()));
  public static final Parameterized<PsiAnnotation, PsiClass> ANNOTATION_CONTAINER_NOT_APPLICABLE =
    parameterized(PsiAnnotation.class, PsiClass.class, "annotation.container.not.applicable")
      .withRawDescription((annotation, containerClass) -> {
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
        String target = JavaPsiBundle.message("annotation.target." + targets[0]);
        return message("annotation.container.not.applicable", containerClass.getName(), target);
      });
  public static final Simple<PsiAnnotation> ANNOTATION_DUPLICATE =
    error(PsiAnnotation.class, "annotation.duplicate").withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()));
  public static final Simple<PsiAnnotation> ANNOTATION_DUPLICATE_NON_REPEATABLE =
    error(PsiAnnotation.class, "annotation.duplicate.non.repeatable")
      .withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()))
      .withRawDescription(annotation -> message(
        "annotation.duplicate.non.repeatable", requireNonNull(annotation.resolveAnnotationType()).getQualifiedName()));
  public static final Parameterized<PsiAnnotation, String> ANNOTATION_DUPLICATE_EXPLAINED =
    error(PsiAnnotation.class, "annotation.duplicate.explained")
      .withAnchor(annotation -> requireNonNull(annotation.getNameReferenceElement()))
      .<String>parameterized()
      .withRawDescription((annotation, message) -> message("annotation.duplicate.explained", message));
  public static final Parameterized<PsiAnnotationMemberValue, String> ANNOTATION_MALFORMED_REPEATABLE_EXPLAINED =
    parameterized(PsiAnnotationMemberValue.class, String.class, "annotation.malformed.repeatable.explained")
      .withRawDescription((containerRef, message) -> message("annotation.malformed.repeatable.explained", message));

  public static final Simple<PsiAnnotation> SAFE_VARARGS_ON_RECORD_COMPONENT =
    error("safe.varargs.on.record.component");
  public static final Parameterized<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_FIXED_ARITY = parameterized("safe.varargs.on.fixed.arity");
  public static final Parameterized<PsiAnnotation, PsiMethod> SAFE_VARARGS_ON_NON_FINAL_METHOD =
    parameterized("safe.varargs.on.non.final.method");
  public static final Parameterized<PsiAnnotation, PsiMethod> OVERRIDE_ON_STATIC_METHOD = parameterized("override.on.static.method");
  public static final Parameterized<PsiAnnotation, PsiMethod> OVERRIDE_ON_NON_OVERRIDING_METHOD =
    parameterized("override.on.non-overriding.method");

  public static final Simple<PsiMethod> METHOD_DUPLICATE =
    error(PsiMethod.class, "method.duplicate")
      .withRange(JavaErrorFormatUtil::getMethodDeclarationTextRange)
      .withRawDescription(
        method -> message("method.duplicate", formatMethod(method), formatClass(requireNonNull(method.getContainingClass()))));
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
                   member instanceof PsiClass aClass ? JavaErrorFormatUtil.getClassDeclarationTextRange(aClass) : null)
      .<PsiMethod>parameterized()
      .withRawDescription((member, abstractMethod) -> {
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
  public static final Simple<PsiClass> CLASS_ALREADY_IMPORTED =
    error(PsiClass.class, "class.already.imported").withAnchor(PsiClass::getNameIdentifier)
      .withRawDescription(cls -> message("class.already.imported", formatClass(cls, false)));
  public static final Parameterized<PsiClass, PsiClass> CLASS_DUPLICATE =
    error(PsiClass.class, "class.duplicate")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .withRawDescription(cls -> message("class.duplicate", cls.getName()))
      .parameterized();
  public static final Parameterized<PsiClass, PsiClass> CLASS_DUPLICATE_IN_OTHER_FILE =
    error(PsiClass.class, "class.duplicate")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .<PsiClass>parameterized()
      .withRawDescription((cls, dupCls) -> message("class.duplicate.in.other.file",
                                                   FileUtil.toSystemDependentName(dupCls.getContainingFile().getVirtualFile().getPath())));
  public static final Simple<PsiClass> CLASS_CLASHES_WITH_PACKAGE =
    error(PsiClass.class, "class.clashes.with.package")
      .withAnchor(cls -> requireNonNullElse(cls.getNameIdentifier(), cls))
      .withHighlightType(cls -> cls instanceof PsiImplicitClass ? JavaErrorHighlightType.FILE_LEVEL_ERROR : JavaErrorHighlightType.ERROR)
      .withRawDescription(cls -> message("class.clashes.with.package", cls.getQualifiedName()));
  public static final Simple<PsiClass> CLASS_WRONG_FILE_NAME =
    error(PsiClass.class, "class.wrong.filename")
      .withRange(JavaErrorFormatUtil::getClassDeclarationTextRange)
      .withRawDescription(cls -> message("class.wrong.filename", cls.getName()));
  public static final Parameterized<PsiClass, PsiClass> CLASS_CYCLIC_INHERITANCE =
    error(PsiClass.class, "class.cyclic.inheritance")
      .withRange(JavaErrorFormatUtil::getClassDeclarationTextRange).<PsiClass>parameterized()
      .withRawDescription((aClass, circularClass) -> message("class.cyclic.inheritance", formatClass(circularClass)));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_DUPLICATE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.duplicate")
      .withRawDescription(
        (ref, target) -> message("class.reference.list.duplicate", formatClass(target), ref.getParent().getFirstChild().getText()));
  public static final Simple<PsiJavaCodeReferenceElement> CLASS_REFERENCE_LIST_NAME_EXPECTED =
    error("class.reference.list.name.expected");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_INNER_PRIVATE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.inner.private")
      .withRawDescription((ref, target) -> message("class.reference.list.inner.private",
                                                   formatClass(target), formatClass(requireNonNull(target.getContainingClass()))));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_REFERENCE_LIST_NO_ENCLOSING_INSTANCE =
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.reference.list.no.enclosing.instance")
      .withRawDescription((ref, target) -> message("class.reference.list.no.enclosing.instance", formatClass(target)));
  public static final Simple<PsiReferenceList> CLASS_CANNOT_EXTEND_MULTIPLE_CLASSES =
    error("class.cannot.extend.multiple.classes");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_INTERFACE = 
    parameterized("class.extends.interface");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_PROHIBITED_CLASS = 
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.extends.prohibited.class")
      .withRawDescription((ref, cls) -> message("class.extends.prohibited.class", cls.getQualifiedName()));
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_INHERITS_TYPE_PARAMETER =
    parameterized("class.inherits.type.parameter");  
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> CLASS_EXTENDS_FINAL = 
    parameterized(PsiJavaCodeReferenceElement.class, PsiClass.class, "class.extends.final").withRawDescription((ref, cls) -> {
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
      .withRawDescription(cls -> message("class.sealed.permits.on.non.sealed", cls.getName()));
  
  public static final Simple<PsiJavaCodeReferenceElement> VALUE_CLASS_EXTENDS_NON_ABSTRACT = error("value.class.extends.non.abstract");
  
  public static final Simple<PsiExpression> INSTANTIATION_ENUM = error("instantiation.enum");
  public static final Parameterized<PsiExpression, PsiClass> INSTANTIATION_ABSTRACT = 
    parameterized(PsiExpression.class, PsiClass.class, "instantiation.abstract")
      .withRawDescription((expr, aClass) -> message("instantiation.abstract", aClass.getName()));
  
  public static final Simple<PsiClass> RECORD_NO_HEADER = error(PsiClass.class, "record.no.header")
    .withAnchor(PsiClass::getNameIdentifier);
  public static final Simple<PsiRecordHeader> RECORD_HEADER_REGULAR_CLASS = error("record.header.regular.class");
  public static final Simple<PsiClassInitializer> RECORD_INSTANCE_INITIALIZER = error("record.instance.initializer");
  public static final Simple<PsiField> RECORD_INSTANCE_FIELD = error("record.instance.field");
  public static final Simple<PsiReferenceList> RECORD_EXTENDS = error(PsiReferenceList.class, "record.extends")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Simple<PsiReferenceList> RECORD_PERMITS = error(PsiReferenceList.class, "record.permits")
    .withAnchor(PsiReferenceList::getFirstChild);

  public static final Simple<PsiReferenceList> ENUM_EXTENDS = error(PsiReferenceList.class, "enum.extends")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Simple<PsiReferenceList> ENUM_PERMITS = error(PsiReferenceList.class, "enum.permits")
    .withAnchor(PsiReferenceList::getFirstChild);

  public static final Simple<PsiClassInitializer> INTERFACE_CLASS_INITIALIZER = error("interface.class.initializer");
  public static final Simple<PsiMethod> INTERFACE_CONSTRUCTOR = error("interface.constructor");
  public static final Simple<PsiReferenceList> INTERFACE_IMPLEMENTS = error(PsiReferenceList.class, "interface.implements")
    .withAnchor(PsiReferenceList::getFirstChild);
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiClass> INTERFACE_EXTENDS_CLASS = 
    parameterized("interface.extends.class");

  public static final Parameterized<PsiJavaFile, PsiImplicitClass> CLASS_IMPLICIT_NO_MAIN_METHOD = 
    error(PsiJavaFile.class, "class.implicit.no.main.method")
      .withHighlightType(psi -> JavaErrorHighlightType.FILE_LEVEL_ERROR).parameterized();
  public static final Parameterized<PsiJavaFile, PsiImplicitClass> CLASS_IMPLICIT_INVALID_FILE_NAME = 
    error(PsiJavaFile.class, "class.implicit.invalid.file.name")
      .withHighlightType(psi -> JavaErrorHighlightType.FILE_LEVEL_ERROR).parameterized();
  public static final Simple<PsiClassInitializer> CLASS_IMPLICIT_INITIALIZER = error("class.implicit.initializer");
  public static final Simple<PsiPackageStatement> CLASS_IMPLICIT_PACKAGE = error("class.implicit.package.statement");
  
  public static final Simple<PsiIdentifier> IDENTIFIER_RESTRICTED = error(PsiIdentifier.class, "identifier.restricted")
    .withRawDescription(psi -> message("identifier.restricted", psi.getText()));
  
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED = 
    parameterized("type.parameter.extends.interface.expected");
  public static final Parameterized<PsiJavaCodeReferenceElement, PsiTypeParameter> TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS = 
    parameterized("type.parameter.cannot.be.followed.by.other.bounds");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ENUM = error("type.parameter.on.enum");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ANNOTATION = error("type.parameter.on.annotation");
  public static final Simple<PsiTypeParameterList> TYPE_PARAMETER_ON_ANNOTATION_MEMBER = error("type.parameter.on.annotation.member");
  public static final Simple<PsiTypeParameter> TYPE_PARAMETER_DUPLICATE = 
    error(PsiTypeParameter.class, "type.parameter.on.annotation.member")
      .withRawDescription(typeParameter -> message("type.parameter.duplicate", typeParameter.getName()));

  public static final Simple<PsiJavaCodeReferenceElement> METHOD_THROWS_CLASS_NAME_EXPECTED =
    error("method.throws.class.name.expected");

  public static final Parameterized<PsiElement, JavaIncompatibleTypeError> TYPE_INCOMPATIBLE =
    parameterized(PsiElement.class, JavaIncompatibleTypeError.class, "type.incompatible")
      .withDescription((psi, context) -> context.createDescription())
      .withTooltip((psi, context) -> context.createTooltip());

  public static final Simple<PsiNewExpression> NEW_EXPRESSION_QUALIFIED_MALFORMED =
    error("new.expression.qualified.malformed");
  public static final Parameterized<PsiNewExpression, PsiClass> NEW_EXPRESSION_QUALIFIED_STATIC_CLASS =
    parameterized("new.expression.qualified.static.class");
  public static final Parameterized<PsiNewExpression, PsiClass> NEW_EXPRESSION_QUALIFIED_ANONYMOUS_IMPLEMENTS_INTERFACE =
    parameterized("new.expression.qualified.anonymous.implements.interface");
  public static final Simple<PsiElement> NEW_EXPRESSION_QUALIFIED_QUALIFIED_CLASS_REFERENCE =
    error("new.expression.qualified.qualified.class.reference");

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
  public static final Parameterized<PsiLiteralExpression, @NotNull TextRange> LITERAL_STRING_ILLEGAL_ESCAPE =
    parameterized(PsiLiteralExpression.class, TextRange.class, "literal.string.illegal.escape").withRange((psi, range) -> range);
  public static final Simple<PsiLiteralExpression> LITERAL_STRING_ILLEGAL_LINE_END = error("literal.string.illegal.line.end");
  public static final Simple<PsiLiteralExpression> LITERAL_TEXT_BLOCK_UNCLOSED = 
    error(PsiLiteralExpression.class, "literal.text.block.unclosed").withRange(e -> TextRange.from(e.getTextLength(), 0));
  public static final Simple<PsiLiteralExpression> LITERAL_TEXT_BLOCK_NO_NEW_LINE = 
    error(PsiLiteralExpression.class, "literal.text.block.no.new.line").withRange(e -> TextRange.create(0, 3));


  private static @NotNull <Psi extends PsiElement> Simple<Psi> error(@NotNull String key) {
    return new Simple<>(key);
  }

  private static @NotNull <Psi extends PsiElement> Simple<Psi> error(@SuppressWarnings("unused") @NotNull Class<Psi> psiClass,
                                                                     @NotNull String key) {
    return error(key);
  }

  private static @NotNull <Psi extends PsiElement, Context> Parameterized<Psi, Context> parameterized(
    @SuppressWarnings("unused") @NotNull Class<Psi> psiClass,
    @SuppressWarnings("unused") @NotNull Class<Context> contextClass,
    @NotNull String key) {
    return new Parameterized<>(key);
  }

  private static @NotNull <Psi extends PsiElement, Context> Parameterized<Psi, Context> parameterized(
    @NotNull String key) {
    return new Parameterized<>(key);
  }

  /**
   * Context for errors related to annotation value
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
}
