// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.java.codeserver.highlighting.errors.JavaMismatchedCallContext;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Fixes attached to error messages provided by {@link JavaErrorCollector}.
 * To add new fixes use {@link #fix(JavaErrorKind, JavaFixProvider)},
 * {@link #fixes(JavaErrorKind, JavaFixesPusher)}, or {@link #multi(JavaErrorKind, JavaFixesProvider)}
 * methods and return a fix or a list of fixes from lambda.
 */
@Service(Service.Level.APP)
final class JavaErrorFixProvider {
  @FunctionalInterface
  private interface JavaFixProvider<Psi extends PsiElement, Context> {
    @Nullable CommonIntentionAction provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesPusher<Psi, Context> asPusher() {
      return (error, registrar) -> registrar.accept(provide(error));
    }
  }

  @FunctionalInterface
  private interface JavaFixesProvider<Psi extends PsiElement, Context> {
    @NotNull List<? extends @NotNull CommonIntentionAction> provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesPusher<Psi, Context> asPusher() {
      return (error, registrar) -> provide(error).forEach(registrar);
    }
  }

  @FunctionalInterface
  private interface JavaFixesPusher<Psi extends PsiElement, Context> {
    /**
     * @param error error to register fixes for
     * @param sink  a sink where fixes should be submitted. Submitting null is allowed and treated as null-op
     */
    void provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error,
                 @NotNull Consumer<? super @Nullable CommonIntentionAction> sink);
  }

  private final QuickFixFactory myFactory = QuickFixFactory.getInstance();
  private final Map<JavaErrorKind<?, ?>, List<JavaFixesPusher<?, ?>>> myFixes = new HashMap<>();

  public static JavaErrorFixProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaErrorFixProvider.class);
  }

  JavaErrorFixProvider() {
    multi(UNSUPPORTED_FEATURE, error -> HighlightUtil.getIncreaseLanguageLevelFixes(error.psi(), error.context()));
    JavaFixProvider<PsiElement, Object> genericRemover = error -> myFactory.createDeleteFix(error.psi());
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_MEMBER_THROWS_NOT_ALLOWED, ANNOTATION_ATTRIBUTE_DUPLICATE,
                                            ANNOTATION_NOT_ALLOWED_EXTENDS, RECEIVER_STATIC_CONTEXT, RECEIVER_WRONG_POSITION,
                                            RECORD_HEADER_REGULAR_CLASS, INTERFACE_CLASS_INITIALIZER, INTERFACE_CONSTRUCTOR,
                                            CLASS_IMPLICIT_INITIALIZER, CLASS_IMPLICIT_PACKAGE,
                                            RECORD_EXTENDS, ENUM_EXTENDS, RECORD_PERMITS, ENUM_PERMITS, ANNOTATION_PERMITS,
                                            NEW_EXPRESSION_DIAMOND_NOT_ALLOWED, REFERENCE_TYPE_ARGUMENT_STATIC_CLASS,
                                            STATEMENT_CASE_OUTSIDE_SWITCH, NEW_EXPRESSION_DIAMOND_NOT_APPLICABLE,
                                            NEW_EXPRESSION_ANONYMOUS_IMPLEMENTS_INTERFACE_WITH_TYPE_ARGUMENTS,
                                            CALL_DIRECT_ABSTRACT_METHOD_ACCESS, RECORD_SPECIAL_METHOD_TYPE_PARAMETERS,
                                            RECORD_SPECIAL_METHOD_THROWS, ARRAY_TYPE_ARGUMENTS, ARRAY_EMPTY_DIAMOND)) {
      fix(kind, genericRemover);
    }

    createModifierFixes();
    createClassFixes();
    createConstructorFixes();
    createMethodFixes();
    createExpressionFixes();
    createExceptionFixes();
    createGenericFixes();
    createRecordFixes();
    createTypeFixes();
    createAccessFixes();
    createAnnotationFixes();
    createReceiverParameterFixes();
  }

  private void createMethodFixes() {
    JavaFixProvider<PsiMethod, Void> addBody = error -> myFactory.createAddMethodBodyFix(error.psi());
    fix(METHOD_DEFAULT_SHOULD_HAVE_BODY, addBody);
    fix(METHOD_STATIC_IN_INTERFACE_SHOULD_HAVE_BODY, addBody);
    fix(METHOD_PRIVATE_IN_INTERFACE_SHOULD_HAVE_BODY, addBody);
    fix(METHOD_SHOULD_HAVE_BODY, addBody);
    fix(METHOD_SHOULD_HAVE_BODY_OR_ABSTRACT, addBody);
    fix(METHOD_DEFAULT_IN_CLASS, error -> removeModifierFix(error.psi(), PsiModifier.DEFAULT));
    fix(METHOD_ABSTRACT_BODY, error -> removeModifierFix(error.psi(), PsiModifier.ABSTRACT));
    fix(METHOD_SHOULD_HAVE_BODY_OR_ABSTRACT, error -> maybeAddModifierFix(error.psi(), PsiModifier.ABSTRACT));
    JavaFixProvider<PsiMethod, Void> deleteBody = error -> myFactory.createDeleteMethodBodyFix(error.psi());
    fix(METHOD_ABSTRACT_BODY, deleteBody);
    fix(METHOD_INTERFACE_BODY, deleteBody);
    fix(METHOD_NATIVE_BODY, deleteBody);
    fix(METHOD_NO_PARAMETER_LIST, error -> myFactory.createAddParameterListFix(error.psi()));
    fixes(METHOD_INTERFACE_BODY, (error, sink) -> {
      PsiMethod method = error.psi();
      if (PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, method) && Stream.of(method.findDeepestSuperMethods())
        .map(PsiMethod::getContainingClass)
        .filter(Objects::nonNull)
        .map(PsiClass::getQualifiedName)
        .noneMatch(CommonClassNames.JAVA_LANG_OBJECT::equals)) {
        sink.accept(PriorityIntentionActionWrapper.highPriority(addModifierFix(method, PsiModifier.DEFAULT)));
        sink.accept(addModifierFix(method, PsiModifier.STATIC));
      }
    });
    fix(METHOD_ABSTRACT_BODY, error -> myFactory.createPushDownMethodFix());
    fix(METHOD_NATIVE_BODY, error -> myFactory.createPushDownMethodFix());
    fix(METHOD_STATIC_OVERRIDES_INSTANCE, error -> removeModifierFix(error.context().method(), PsiModifier.STATIC));
    fix(METHOD_INSTANCE_OVERRIDES_STATIC, error -> maybeAddModifierFix(error.context().method(), PsiModifier.STATIC));
    fix(METHOD_STATIC_OVERRIDES_INSTANCE, error -> maybeAddModifierFix(error.context().superMethod(), PsiModifier.STATIC));
    fix(METHOD_INSTANCE_OVERRIDES_STATIC, error -> removeModifierFix(error.context().superMethod(), PsiModifier.STATIC));
    fix(METHOD_OVERRIDES_FINAL, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(METHOD_INHERITANCE_WEAKER_PRIVILEGES,
        error -> error.psi() instanceof PsiMethod ? myFactory.createChangeModifierFix() :
                 error.psi() instanceof PsiClass cls ? myFactory.createImplementMethodsFix(cls) : null);
    multi(METHOD_INHERITANCE_CLASH_DOES_NOT_THROW, error -> List.of(
      myFactory.createMethodThrowsFix(error.context().method(), error.context().exceptionType(), false, false),
      myFactory.createMethodThrowsFix(error.context().superMethod(), error.context().exceptionType(), true, true)
    ));
    fix(VARARG_NOT_LAST_PARAMETER, error -> myFactory.createMakeVarargParameterLastFix(error.psi()));
    fixes(METHOD_INHERITANCE_CLASH_INCOMPATIBLE_RETURN_TYPES, (error, sink) -> {
      IncompatibleOverrideReturnTypeContext context = error.context();
      PsiMethod method = context.method();
      if (method instanceof LightRecordMethod recordMethod) {
        HighlightFixUtil.getChangeVariableTypeFixes(recordMethod.getRecordComponent(), context.superMethodReturnType()).forEach(sink);
      }
      else {
        sink.accept(myFactory.createMethodReturnFix(method, context.superMethodReturnType(), false));
      }
      sink.accept(myFactory.createSuperMethodReturnFix(context.superMethod(), context.methodReturnType()));
      PsiClass returnClass = PsiUtil.resolveClassInClassTypeOnly(context.methodReturnType());
      if (returnClass != null && context.superMethodReturnType() instanceof PsiClassType classType) {
        sink.accept(myFactory.createChangeParameterClassFix(returnClass, classType));
      }
    });
    fix(VARARG_CSTYLE_DECLARATION, error -> new NormalizeBracketsFix(error.psi()));
  }

  private void createExceptionFixes() {
    fix(EXCEPTION_MUST_BE_DISJOINT, error -> myFactory.createDeleteMultiCatchFix(error.psi()));
    fix(EXCEPTION_NEVER_THROWN_TRY_MULTI, error -> myFactory.createDeleteMultiCatchFix(error.psi()));
    fixes(EXCEPTION_ALREADY_CAUGHT, (error, sink) -> {
      PsiParameter parameter = PsiTreeUtil.getParentOfType(error.psi(), PsiParameter.class, false);
      if (parameter == null) return;
      boolean multiCatch = PsiUtil.getParameterTypeElements(parameter).size() > 1;
      sink.accept(multiCatch ? myFactory.createDeleteMultiCatchFix(error.psi()) : myFactory.createDeleteCatchFix(parameter));
      PsiCatchSection catchSection = (PsiCatchSection)requireNonNull(parameter.getDeclarationScope());
      PsiCatchSection upperCatchSection = error.context();
      sink.accept(myFactory.createMoveCatchUpFix(catchSection, upperCatchSection));
    });
    fix(EXCEPTION_NEVER_THROWN_TRY, error -> myFactory.createDeleteCatchFix(error.psi()));
  }

  private void createConstructorFixes() {
    JavaFixesPusher<PsiMember, Object> constructorCallFixes = (error, sink) -> {
        if (error.psi() instanceof PsiClass cls) {
          sink.accept(myFactory.createCreateConstructorMatchingSuperFix(cls));
        }
        else if (error.psi() instanceof PsiMethod method) {
          sink.accept(myFactory.createInsertSuperFix(method));
          sink.accept(myFactory.createInsertThisFix(method));
        }
      };
    fixes(CONSTRUCTOR_AMBIGUOUS_IMPLICIT_CALL, constructorCallFixes);
    fixes(CONSTRUCTOR_NO_DEFAULT, constructorCallFixes);
    fix(CONSTRUCTOR_AMBIGUOUS_IMPLICIT_CALL, error -> myFactory.createAddDefaultConstructorFix(
      requireNonNull(error.context().psiClass().getSuperClass())));
    fix(CONSTRUCTOR_NO_DEFAULT, error -> myFactory.createAddDefaultConstructorFix(error.context()));
    fixes(EXCEPTION_UNHANDLED, (error, sink) -> {
      PsiElement element = error.psi();
      HighlightFixUtil.registerUnhandledExceptionFixes(element, sink);
      if (element instanceof PsiMethod method) {
        sink.accept(myFactory.createAddExceptionToThrowsFix(method, error.context()));
        PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          sink.accept(myFactory.createCreateConstructorMatchingSuperFix(aClass));
        }
      }
      else if (element instanceof PsiClass cls) {
        sink.accept(myFactory.createCreateConstructorMatchingSuperFix(cls));
      }
      ErrorFixExtensionPoint.registerFixes(sink, element, "unhandled.exceptions");
    });
  }

  private void createModifierFixes() {
    fix(MODIFIER_NOT_ALLOWED, error -> {
      @SuppressWarnings("MagicConstant") @PsiModifier.ModifierConstant String modifier = error.context();
      PsiModifierList list = (PsiModifierList)error.psi().getParent();
      if (list.getParent() instanceof PsiClass aClass && !aClass.isInterface()
          && (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier))) {
        return myFactory.createChangeModifierFix();
      }
      return removeModifierFix((PsiModifierListOwner)list.getParent(), modifier);
    });
    JavaFixProvider<PsiKeyword, Object> removeModifier = error -> {
      @SuppressWarnings("MagicConstant") @PsiModifier.ModifierConstant String modifier = error.psi().getText();
      PsiModifierList list = (PsiModifierList)error.psi().getParent();
      return removeModifierFix((PsiModifierListOwner)list.getParent(), modifier);
    };
    fix(MODIFIER_NOT_ALLOWED_LOCAL_CLASS, removeModifier);
    fix(MODIFIER_REPEATED, removeModifier);
    fix(MODIFIER_INCOMPATIBLE, removeModifier);
    fix(MODIFIER_NOT_ALLOWED_NON_SEALED, removeModifier);
  }

  private void createExpressionFixes() {
    fix(NEW_EXPRESSION_QUALIFIED_MALFORMED, error -> myFactory.createRemoveNewQualifierFix(error.psi(), null));
    fix(NEW_EXPRESSION_QUALIFIED_STATIC_CLASS, 
        error -> error.context().isEnum() ? null : removeModifierFix(error.context(), PsiModifier.STATIC));
    fix(NEW_EXPRESSION_QUALIFIED_STATIC_CLASS, error -> myFactory.createRemoveNewQualifierFix(error.psi(), error.context()));
    fix(NEW_EXPRESSION_QUALIFIED_ANONYMOUS_IMPLEMENTS_INTERFACE, error -> myFactory.createRemoveNewQualifierFix(error.psi(), null));
    fix(NEW_EXPRESSION_QUALIFIED_QUALIFIED_CLASS_REFERENCE,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("remove.qualifier.fix")));
    fix(NEW_EXPRESSION_DIAMOND_INFERENCE_FAILURE,
        error -> {
          if (error.context() == PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT &&
              !PsiUtil.isLanguageLevel9OrHigher(error.psi())) {
            return myFactory.createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_9);
          }
          return null;
        });
    fixes(NEW_EXPRESSION_ARGUMENTS_TO_DEFAULT_CONSTRUCTOR_CALL, (error, sink) -> {
      PsiConstructorCall constructorCall = error.psi();
      PsiJavaCodeReferenceElement classReference =
        constructorCall instanceof PsiNewExpression newExpression ? newExpression.getClassOrAnonymousClassReference() : null;
      if (classReference != null) {
        ConstructorParametersFixer.registerFixActions(constructorCall, sink);
      }
      QuickFixFactory.getInstance().createCreateConstructorFromUsageFixes(constructorCall).forEach(sink);
      RemoveRedundantArgumentsFix.registerIntentions(requireNonNull(constructorCall.getArgumentList()), sink);
    });
    fixes(NEW_EXPRESSION_UNRESOLVED_CONSTRUCTOR, (error, sink) -> {
      PsiConstructorCall constructorCall = error.psi();
      PsiExpressionList list = constructorCall.getArgumentList();
      if (list != null) {
        JavaResolveResult[] results = error.context().results();
        WrapExpressionFix.registerWrapAction(results, list.getExpressions(), sink);
        HighlightFixUtil.registerFixesOnInvalidConstructorCall(sink, constructorCall, error.context().psiClass(), results);
      }
    });
    fix(TYPE_PARAMETER_ABSENT_CLASS, error -> myFactory.createChangeClassSignatureFromUsageFix(error.context(), error.psi()));
    fix(TYPE_PARAMETER_COUNT_MISMATCH,
        error -> error.context() instanceof PsiClass cls ? myFactory.createChangeClassSignatureFromUsageFix(cls, error.psi()) : null);
    JavaFixProvider<PsiTypeElement, TypeParameterBoundMismatchContext> addBoundFix = error -> {
      if (error.context().bound() instanceof PsiClassType bound) {
        PsiClass psiClass = bound.resolve();
        if (psiClass != null) {
          return myFactory.createExtendsListFix(psiClass, bound, true);
        }
      }
      return null;
    };
    fix(TYPE_PARAMETER_TYPE_NOT_WITHIN_EXTEND_BOUND, addBoundFix);
    fix(TYPE_PARAMETER_TYPE_NOT_WITHIN_IMPLEMENT_BOUND, addBoundFix);
    fixes(TYPE_PARAMETER_ABSENT_CLASS, (error, sink) -> {
      PsiReferenceParameterList referenceParameterList = error.psi();
      PsiElement grandParent = referenceParameterList.getParent().getParent();
      if (!(grandParent instanceof PsiTypeElement)) return;
      if (!(PsiTreeUtil.skipParentsOfType(grandParent, PsiTypeElement.class) instanceof PsiVariable variable)) return;
      if (error.context().getTypeParameters().length == 0) {
        sink.accept(PriorityIntentionActionWrapper.highPriority(myFactory.createDeleteFix(referenceParameterList)));
      }
      HighlightFixUtil.registerVariableParameterizedTypeFixes(sink, variable, referenceParameterList);
    });
    fix(LITERAL_CHARACTER_TOO_LONG, error -> myFactory.createConvertToStringLiteralAction());
    fix(LITERAL_CHARACTER_EMPTY, error -> myFactory.createConvertToStringLiteralAction());
    fix(PATTERN_TYPE_PATTERN_EXPECTED, error -> {
      String patternVarName = new VariableNameGenerator(error.psi(), VariableKind.LOCAL_VARIABLE).byName("ignored").generate(true);
      return myFactory.createReplaceWithTypePatternFix(error.psi(), error.context(), patternVarName);
    });
    fix(ARRAY_INITIALIZER_NOT_ALLOWED, error -> myFactory.createAddNewArrayExpressionFix(error.psi()));
    fix(ARRAY_GENERIC, error -> error.psi() instanceof PsiReferenceParameterList list ? myFactory.createDeleteFix(list) : null);
    fix(ARRAY_TYPE_EXPECTED, error -> error.psi().getParent() instanceof PsiArrayAccessExpression accessExpression ?
                                      myFactory.createReplaceWithListAccessFix(accessExpression) : null);
    fix(ARRAY_ILLEGAL_INITIALIZER, error -> {
      if (error.psi().getParent() instanceof PsiArrayInitializerExpression initializerExpression) {
        PsiType sameType = JavaHighlightUtil.sameType(initializerExpression.getInitializers());
        if (sameType != null) {
          return VariableArrayTypeFix.createFix(initializerExpression, sameType);
        }
      }
      return null;
    });
    fixes(CALL_EXPECTED, (error, sink) -> {
      PsiMethodCallExpression methodCall = error.psi();
      JavaResolveResult result = methodCall.getMethodExpression().advancedResolve(true);
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass psiClass) {
        sink.accept(myFactory.createInsertNewFix(methodCall, psiClass));
      }
      else {
        sink.accept(myFactory.createStaticImportMethodFix(methodCall));
        sink.accept(myFactory.createQualifyStaticMethodCallFix(methodCall));
        sink.accept(myFactory.addMethodQualifierFix(methodCall));
        myFactory.createCreateMethodFromUsageFixes(methodCall).forEach(sink);
        if (resolved instanceof PsiVariable variable && PsiUtil.isAvailable(JavaFeature.LAMBDA_EXPRESSIONS, methodCall)) {
          PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(variable.getType());
          if (method != null) {
            sink.accept(myFactory.createInsertMethodCallFix(methodCall, method));
          }
        }
      }
    });
    fix(CALL_DIRECT_ABSTRACT_METHOD_ACCESS, error -> {
      PsiMethod method = error.context();
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      String name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
      String text = QuickFixBundle.message("remove.modifier.fix", name, VisibilityUtil.toPresentableText(PsiModifier.ABSTRACT));
      return myFactory.createAddMethodBodyFix(method, text);
    });
    multi(CALL_CONSTRUCTOR_MUST_BE_FIRST_STATEMENT, error -> HighlightUtil.getIncreaseLanguageLevelFixes(
      error.psi(), JavaFeature.STATEMENTS_BEFORE_SUPER));
    fix(STRING_TEMPLATE_PROCESSOR_MISSING, error -> new MissingStrProcessorFix(error.psi()));
    fixes(UNARY_OPERATOR_NOT_APPLICABLE, (error, sink) -> {
      PsiUnaryExpression unary = error.psi();
      if (unary instanceof PsiPrefixExpression prefixExpression && unary.getOperationTokenType() == JavaTokenType.EXCL) {
        sink.accept(myFactory.createNegationBroadScopeFix(prefixExpression));
        PsiExpression operand = unary.getOperand();
        if (operand != null) {
          AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(sink, operand, PsiTypes.booleanType(), operand.getType());
          sink.accept(HighlightFixUtil.createChangeReturnTypeFix(operand, PsiTypes.booleanType()));
        }
      }
    });
    fixes(EXPRESSION_SUPER_UNQUALIFIED_DEFAULT_METHOD, (error, sink) ->
      QualifySuperArgumentFix.registerQuickFixAction(error.context(), sink));
  }

  private void createAccessFixes() {
    JavaFixesPusher<PsiJavaCodeReferenceElement, JavaResolveResult> accessFix = (error, sink) -> {
      if (error.context().isStaticsScopeCorrect() && error.context().getElement() instanceof PsiJvmMember member) {
        HighlightFixUtil.registerAccessQuickFixAction(sink, member, error.psi(), null);
      }
    };
    fixes(ACCESS_PRIVATE, accessFix);
    fixes(ACCESS_PROTECTED, accessFix);
    fixes(ACCESS_PACKAGE_LOCAL, accessFix);
    fixes(ACCESS_GENERIC_PROBLEM, accessFix);
  }

  private void createTypeFixes() {
    fixes(TYPE_INCOMPATIBLE, (error, sink) -> {
      JavaIncompatibleTypeErrorContext context = error.context();
      PsiElement anchor = error.psi();
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
      PsiType lType = context.lType();
      PsiType rType = context.rType();
      if (anchor instanceof PsiJavaCodeReferenceElement && parent instanceof PsiReferenceList &&
          parent.getParent() instanceof PsiMethod method && method.getThrowsList() == parent) {
        // Incompatible type in throws clause
        PsiClass usedClass = PsiUtil.resolveClassInClassTypeOnly(rType);
        if (usedClass != null && lType instanceof PsiClassType throwableType) {
          sink.accept(myFactory.createExtendsListFix(usedClass, throwableType, true));
        }
      }
      if (anchor instanceof PsiExpression expression) {
        AddTypeArgumentsConditionalFix.register(sink, expression, lType);
        AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(sink, expression, lType, rType);
        if (!(expression.getParent() instanceof PsiConditionalExpression && PsiTypes.voidType().equals(lType))) {
          sink.accept(HighlightFixUtil.createChangeReturnTypeFix(expression, lType));
        }
        sink.accept(ChangeNewOperatorTypeFix.createFix(expression, lType));
        if (PsiTypes.booleanType().equals(lType) && expression instanceof PsiAssignmentExpression assignment &&
            assignment.getOperationTokenType() == JavaTokenType.EQ) {
          sink.accept(myFactory.createAssignmentToComparisonFix(assignment));
        }
        else if (expression instanceof PsiMethodCallExpression callExpression) {
          HighlightFixUtil.registerCallInferenceFixes(callExpression, sink);
        }
        if (parent instanceof PsiArrayInitializerExpression initializerList) {
          PsiType sameType = JavaHighlightUtil.sameType(initializerList.getInitializers());
          sink.accept(sameType == null ? null : VariableArrayTypeFix.createFix(initializerList, sameType));
        }
        else if (parent instanceof PsiReturnStatement && rType != null) {
          if (PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiMethod containingMethod) {
            sink.accept(myFactory.createMethodReturnFix(containingMethod, rType, true, true));
          }
        }
        else if (parent instanceof PsiLocalVariable var && rType != null) {
          HighlightFixUtil.registerChangeVariableTypeFixes(var, rType, var.getInitializer(), sink);
        }
        else if (parent instanceof PsiAssignmentExpression assignment && assignment.getRExpression() == expression) {
          PsiExpression lExpr = assignment.getLExpression();

          sink.accept(myFactory.createChangeToAppendFix(assignment.getOperationTokenType(), lType, assignment));
          if (rType != null) {
            HighlightFixUtil.registerChangeVariableTypeFixes(lExpr, rType, expression, sink);
            HighlightFixUtil.registerChangeVariableTypeFixes(expression, lType, lExpr, sink);
          }
        }
      }
      if (anchor instanceof PsiParameter parameter && parent instanceof PsiForeachStatement forEach) {
        HighlightFixUtil.registerChangeVariableTypeFixes(parameter, rType, forEach.getIteratedValue(), sink);
      }
    });
    fixes(CALL_TYPE_INFERENCE_ERROR, (error, sink) -> {
      if (error.psi() instanceof PsiMethodCallExpression callExpression) {
        HighlightFixUtil.registerCallInferenceFixes(callExpression, sink);
      }
    });
    fixes(CALL_WRONG_ARGUMENTS, (error, sink) -> {
      JavaMismatchedCallContext context = error.context();
      PsiExpressionList list = context.list();
      Project project = error.project();
      PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(project);
      MethodCandidateInfo candidate = context.candidate();
      PsiElement parent = list.getParent();
      if (parent instanceof PsiAnonymousClass) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiMethodCallExpression methodCall) {
        PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
        PsiType actualType = ((PsiExpression)methodCall.copy()).getType();
        if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
          AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(sink, methodCall, expectedTypeByParent, actualType);
        }
        HighlightFixUtil.registerQualifyMethodCallFix(
          resolveHelper.getReferencedMethodCandidates(methodCall, false), methodCall, list, sink);
        HighlightFixUtil.registerMethodCallIntentions(sink, methodCall, list);
        HighlightFixUtil.registerMethodReturnFixAction(sink, candidate, methodCall);
        HighlightFixUtil.registerTargetTypeFixesBasedOnApplicabilityInference(methodCall, candidate, candidate.getElement(), sink);
        HighlightFixUtil.registerImplementsExtendsFix(sink, methodCall, candidate.getElement());
      }
      if (parent instanceof PsiConstructorCall constructorCall) {
        JavaResolveResult[] methodCandidates = JavaResolveResult.EMPTY_ARRAY;
        PsiClass aClass = requireNonNull(candidate.getElement().getContainingClass());
        if (constructorCall instanceof PsiNewExpression newExpression) {
          methodCandidates = resolveHelper.getReferencedMethodCandidates(newExpression, true);
        } else if (constructorCall instanceof PsiEnumConstant enumConstant) {
          PsiClassType type = JavaPsiFacade.getElementFactory(project).createType(aClass);
          methodCandidates = resolveHelper.multiResolveConstructor(type, list, enumConstant);
        }
        HighlightFixUtil.registerFixesOnInvalidConstructorCall(sink, constructorCall, aClass, methodCandidates);
        HighlightFixUtil.registerMethodReturnFixAction(sink, candidate, constructorCall);
      }
    });
  }

  private void createClassFixes() {
    fix(CLASS_NO_ABSTRACT_METHOD, error -> {
      if (error.psi() instanceof PsiClass aClass && !(aClass instanceof PsiAnonymousClass) && !aClass.isEnum()) {
        return maybeAddModifierFix(aClass, PsiModifier.ABSTRACT);
      }
      return null;
    });
    fixes(CLASS_NO_ABSTRACT_METHOD, (error, sink) -> {
      PsiMember member = error.psi();
      PsiClass aClass = member instanceof PsiEnumConstant enumConstant ?
                        requireNonNullElse(enumConstant.getInitializingClass(), member.getContainingClass()) : (PsiClass)member;
      PsiClass containingClass = requireNonNull(error.context().getContainingClass());
      PsiMethod anyMethodToImplement = member instanceof PsiEnumConstant ? ClassUtil.getAnyAbstractMethod(aClass) : 
                                       ClassUtil.getAnyMethodToImplement(aClass);
      if (anyMethodToImplement == null) return;
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(error.project()).arePackagesTheSame(aClass, containingClass)) {
        sink.accept(myFactory.createImplementMethodsFix(member));
      }
      else {
        sink.accept(addModifierFix(anyMethodToImplement, PsiModifier.PROTECTED));
        sink.accept(addModifierFix(anyMethodToImplement, PsiModifier.PUBLIC));
      }
    });
    fix(CLASS_REFERENCE_LIST_DUPLICATE,
        error -> myFactory.createRemoveDuplicateExtendsAction(HighlightUtil.formatClass(error.context())));
    multi(CLASS_REFERENCE_LIST_INNER_PRIVATE, error -> List.of(
      addModifierFix(error.context(), PsiModifier.PUBLIC),
      addModifierFix(error.context(), PsiModifier.PROTECTED)));
    fix(CLASS_DUPLICATE, error -> myFactory.createRenameFix(requireNonNullElse(error.psi().getNameIdentifier(), error.psi())));
    fix(CLASS_CLASHES_WITH_PACKAGE, error -> myFactory.createRenameFix(requireNonNullElse(error.psi().getNameIdentifier(), error.psi())));
    fix(CLASS_DUPLICATE, error -> myFactory.createNavigateToDuplicateElementFix(error.context()));
    fix(CLASS_DUPLICATE_IN_OTHER_FILE, error -> myFactory.createNavigateToDuplicateElementFix(error.context()));
    fixes(INSTANTIATION_ABSTRACT, (error, sink) -> {
      PsiClass aClass = error.context();
      PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        sink.accept(removeModifierFix(aClass, PsiModifier.ABSTRACT));
      }
      if (anyAbstractMethod != null && error.psi() instanceof PsiNewExpression newExpression && newExpression.getClassReference() != null) {
        sink.accept(myFactory.createImplementAbstractClassMethodsFix(newExpression));
      }
    });
    fixes(CLASS_WRONG_FILE_NAME, (error, sink) -> {
      PsiClass aClass = error.psi();
      PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
      PsiClass[] classes = file.getClasses();
      boolean containsClassForFile = ContainerUtil.exists(classes, otherClass ->
        !otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
        otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
        file.getVirtualFile().getNameWithoutExtension().equals(otherClass.getName()));
      if (!containsClassForFile) {
        sink.accept(myFactory.createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION));
      }
      if (classes.length > 1) {
        sink.accept(myFactory.createMoveClassToSeparateFileFix(aClass));
      }
      sink.accept(myFactory.createModifierListFix(aClass, PsiModifier.PUBLIC, false, false));
      if (!containsClassForFile) {
        sink.accept(myFactory.createRenameElementFix(aClass));
      }
    });
    fix(CLASS_SEALED_INCOMPLETE_PERMITS, error -> myFactory.createFillPermitsListFix(requireNonNull(error.psi().getNameIdentifier())));
    multi(CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS_CAN_BE_FINAL, error -> List.of(
      addModifierFix(error.psi(), PsiModifier.FINAL),
      addModifierFix(error.psi(), PsiModifier.SEALED),
      addModifierFix(error.psi(), PsiModifier.NON_SEALED)));
    multi(CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS, error -> List.of(
      addModifierFix(error.psi(), PsiModifier.SEALED),
      addModifierFix(error.psi(), PsiModifier.NON_SEALED)));
    fix(CLASS_IMPLICIT_NO_MAIN_METHOD, error -> myFactory.createAddMainMethodFix(error.context()));
    fix(UNSUPPORTED_FEATURE, error -> {
      if (error.context() != JavaFeature.IMPLICIT_CLASSES) return null;
      PsiMember member = PsiTreeUtil.getNonStrictParentOfType(error.psi(), PsiMember.class);
      if (member == null || member instanceof PsiClass) return null;
      if (!(member.getContainingClass() instanceof PsiImplicitClass implicitClass)) return null;
      boolean hasClassToRelocate = PsiTreeUtil.findChildOfType(implicitClass, PsiClass.class) != null;
      return hasClassToRelocate ? new MoveMembersIntoClassFix(implicitClass) : null;
    });
    fixes(UNSUPPORTED_FEATURE, (error, sink) -> {
      if (error.context() != JavaFeature.INNER_STATICS) return;
      PsiMember member = PsiTreeUtil.getParentOfType(error.psi(), PsiMember.class);
      if (member == null) return;
      if (PsiUtil.isJavaToken(error.psi(), JavaTokenType.STATIC_KEYWORD)) {
        sink.accept(removeModifierFix(member, PsiModifier.STATIC));
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.getContainingClass() != null) {
        sink.accept(addModifierFix(containingClass, PsiModifier.STATIC));
      }
    });
    fix(INTERFACE_CONSTRUCTOR, error -> myFactory.createConvertInterfaceToClassFix(requireNonNull(error.psi().getContainingClass())));
    fix(INTERFACE_CLASS_INITIALIZER, error -> myFactory.createConvertInterfaceToClassFix(requireNonNull(error.psi().getContainingClass())));
    fix(INTERFACE_IMPLEMENTS, error -> {
      PsiClassType[] referencedTypes = error.psi().getReferencedTypes();
      if (referencedTypes.length > 0 && error.psi().getParent() instanceof PsiClass aClass) {
        return myFactory.createChangeExtendsToImplementsFix(aClass, referencedTypes[0]);
      }
      return null;
    });
    JavaFixProvider<PsiJavaCodeReferenceElement, PsiClass> extendsToImplementsFix = error -> {
      PsiJavaCodeReferenceElement ref = error.psi();
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(ref);
      return myFactory.createChangeExtendsToImplementsFix(error.context(), type);
    };
    fix(CLASS_EXTENDS_INTERFACE, extendsToImplementsFix);
    fix(CLASS_IMPLEMENTS_CLASS, extendsToImplementsFix);
    fix(INTERFACE_EXTENDS_CLASS, extendsToImplementsFix);
    fix(CLASS_SEALED_PERMITS_ON_NON_SEALED, error -> addModifierFix(error.psi(), PsiModifier.SEALED));
    fix(CLASS_EXTENDS_FINAL, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(CLASS_ANONYMOUS_EXTENDS_SEALED, error -> myFactory.createConvertAnonymousToInnerAction(error.psi()));
    JavaFixProvider<PsiElement, ClassStaticReferenceErrorContext> makeInnerStatic = error -> {
      PsiClass innerClass = error.context().innerClass();
      return innerClass == null || innerClass.getContainingClass() == null ? null : addModifierFix(innerClass, PsiModifier.STATIC);
    };
    fix(CLASS_NOT_ENCLOSING, makeInnerStatic);
    fix(CLASS_CANNOT_BE_REFERENCED_FROM_STATIC_CONTEXT, makeInnerStatic);
    fix(CLASS_CANNOT_BE_REFERENCED_FROM_STATIC_CONTEXT, 
        error -> removeModifierFix(requireNonNull(error.context().enclosingStaticElement()), PsiModifier.STATIC));
  }

  private void createAnnotationFixes() {
    fix(SAFE_VARARGS_ON_NON_FINAL_METHOD,
        error -> myFactory.createModifierListFix(error.context(), PsiModifier.FINAL, true, true));
    fixes(OVERRIDE_ON_NON_OVERRIDING_METHOD, (error, sink) -> {
      List<CommonIntentionAction> registrar = new ArrayList<>();
      myFactory.registerPullAsAbstractUpFixes(error.context(), registrar);
      registrar.forEach(sink);
    });
    JavaFixProvider<PsiElement, Object> annotationRemover = error ->
      error.psi() instanceof PsiAnnotation annotation ? myFactory.createDeleteFix(annotation, JavaAnalysisBundle.message(
        "remove.annotation")) : null;
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_NOT_ALLOWED_CLASS, ANNOTATION_NOT_ALLOWED_HERE,
                                            ANNOTATION_NOT_ALLOWED_REF, ANNOTATION_NOT_ALLOWED_VAR,
                                            ANNOTATION_NOT_ALLOWED_VOID, LAMBDA_MULTIPLE_TARGET_METHODS, LAMBDA_NO_TARGET_METHOD,
                                            LAMBDA_NOT_FUNCTIONAL_INTERFACE, ANNOTATION_NOT_APPLICABLE,
                                            LAMBDA_FUNCTIONAL_INTERFACE_SEALED, OVERRIDE_ON_STATIC_METHOD,
                                            OVERRIDE_ON_NON_OVERRIDING_METHOD, SAFE_VARARGS_ON_FIXED_ARITY,
                                            SAFE_VARARGS_ON_NON_FINAL_METHOD, SAFE_VARARGS_ON_RECORD_COMPONENT,
                                            ANNOTATION_CONTAINER_WRONG_PLACE, ANNOTATION_CONTAINER_NOT_APPLICABLE)) {
      fix(kind, annotationRemover);
    }
    fix(ANNOTATION_NOT_ALLOWED_VAR, error -> {
      PsiAnnotationOwner owner = error.psi().getOwner();
      PsiTypeElement type = owner instanceof PsiTypeElement te ? te :
                            PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class,
                                                            PsiTypeParameterList.class) instanceof PsiTypeElement te ? te :
                            null;
      return type != null && type.isInferredType() ? new ReplaceVarWithExplicitTypeFix(type) : null;
    });
    fixes(ANNOTATION_NOT_APPLICABLE, (error, sink) -> {
      if (!BaseIntentionAction.canModify(requireNonNull(error.psi().resolveAnnotationType()))) return;
      error.context().forEach(targetType -> sink.accept(myFactory.createAddAnnotationTargetFix(error.psi(), targetType)));
    });
    fix(ANNOTATION_NOT_ALLOWED_STATIC, error -> new MoveAnnotationOnStaticMemberQualifyingTypeFix(error.psi()));
    fix(ANNOTATION_MISSING_ATTRIBUTE, error -> myFactory.createAddMissingRequiredAnnotationParametersFix(
      error.psi(), PsiMethod.EMPTY_ARRAY, error.context()));
    multi(ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING, error -> myFactory.createAddAnnotationAttributeNameFixes(error.psi()));
    multi(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> {
      PsiNameValuePair pair = error.psi();
      if (pair.getName() != null) return List.of();
      return myFactory.createAddAnnotationAttributeNameFixes(pair);
    });
    fix(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> myFactory.createCreateAnnotationMethodFromUsageFix(error.psi()));
    fix(ANNOTATION_ATTRIBUTE_DUPLICATE, error -> myFactory.createMergeDuplicateAttributesFix(error.psi()));
    JavaFixProvider<PsiAnnotationMemberValue, AnnotationValueErrorContext> incompatibleTypeFix = error -> {
      PsiAnnotationMemberValue value = error.psi();
      PsiAnnotationMethod method = error.context().method();
      PsiType type = null;
      if (value instanceof PsiAnnotation annotation) {
        PsiClass annotationClass = annotation.resolveAnnotationType();
        if (annotationClass != null) {
          type = TypeUtils.getType(annotationClass);
        }
      } else if (value instanceof PsiArrayInitializerMemberValue arrayInitializer) {
        PsiAnnotationMemberValue[] initializers = arrayInitializer.getInitializers();
        PsiType componentType = initializers.length == 0 ? error.context().expectedType() :
                                initializers[0] instanceof PsiExpression firstInitializer ? firstInitializer.getType() : null;
        if (componentType != null) {
          type = componentType.createArrayType();
        }
      } else if (value instanceof PsiExpression expression) {
        type = expression.getType();
      }
      if (type == null) return null;
      return myFactory.createAnnotationMethodReturnFix(method, type, error.context().fromDefaultValue());
    };
    fix(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE, incompatibleTypeFix);
    fix(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE,
        error -> myFactory.createSurroundWithQuotesAnnotationParameterValueFix(error.psi(), error.context().expectedType()));
    fix(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, incompatibleTypeFix);
    fix(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, error -> {
      PsiAnnotationMemberValue[] initializers = error.psi().getInitializers();
      if (initializers.length != 1 || !(initializers[0] instanceof PsiExpression firstInitializer)) return null;
      PsiType expectedType = error.context().expectedType();
      if (!TypeConversionUtil.areTypesAssignmentCompatible(expectedType, firstInitializer)) return null;
      return myFactory.createUnwrapArrayInitializerMemberValueAction(error.psi());
    });
    multi(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      List.of(myFactory.createDeleteFix(requireNonNull(error.psi().getAnnotationList()),
                                        JavaAnalysisBundle.message("intention.text.remove.annotation")),
              new MoveAnnotationToPackageInfoFileFix(error.psi())));
    fix(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      myFactory.createDeleteFix(error.psi(), JavaAnalysisBundle.message("intention.text.remove.annotation")));
    fix(ANNOTATION_DUPLICATE_NON_REPEATABLE, error -> myFactory.createCollapseAnnotationsFix(error.psi()));
    fix(ANNOTATION_MEMBER_MAY_NOT_HAVE_PARAMETERS, error -> myFactory.createRemoveParameterListFix((PsiMethod)error.psi().getParent()));
  }

  private void createGenericFixes() {
    fix(TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED, error -> {
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(error.psi());
      return myFactory.createMoveBoundClassToFrontFix(error.context(), type);
    });
    fix(TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS, error -> {
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(error.psi());
      return myFactory.createExtendsListFix(error.context(), type, false);
    });
    fix(CALL_STATIC_INTERFACE_METHOD_QUALIFIER, error -> myFactory.createAccessStaticViaInstanceFix(
      error.psi(), error.psi().advancedResolve(true)));
  }

  private void createRecordFixes() {
    fix(RECORD_NO_HEADER, error -> myFactory.createAddEmptyRecordHeaderFix(error.psi()));
    fix(RECORD_INSTANCE_FIELD, error -> addModifierFix(error.psi(), PsiModifier.STATIC));
    fix(RECORD_INSTANCE_INITIALIZER, error -> addModifierFix(error.psi(), PsiModifier.STATIC));
    fix(RECORD_COMPONENT_VARARG_NOT_LAST, error -> myFactory.createMakeVarargParameterLastFix(error.psi()));
    fix(RECORD_COMPONENT_RESTRICTED_NAME, error -> myFactory.createRenameFix(error.psi()));
    fix(RECORD_COMPONENT_CSTYLE_DECLARATION, error -> new NormalizeBracketsFix(error.psi()));
    fix(RECORD_CONSTRUCTOR_STRONGER_ACCESS, error -> addModifierFix(error.psi(), error.context().toPsiModifier()));
    fix(RECORD_ACCESSOR_NON_PUBLIC, error -> addModifierFix(error.psi(), PsiModifier.PUBLIC));
    fix(RECORD_ACCESSOR_WRONG_RETURN_TYPE, error -> myFactory.createMethodReturnFix(error.psi(), error.context().lType(), false));
    fix(RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_TYPE, error -> {
      PsiParameter parameter = error.psi();
      PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
      return myFactory.createMethodParameterTypeFix(method, method.getParameterList().getParameterIndex(parameter),
                                                    error.context().getType(), false);
    });
    fix(RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_NAME, error -> {
      PsiParameter parameter = error.psi();
      PsiParameter[] parameters = ((PsiParameterList)parameter.getParent()).getParameters();
      String componentName = error.context().getName();
      return ContainerUtil.exists(parameters, p -> p.getName().equals(componentName))
             ? null
             : myFactory.createRenameElementFix(parameter, componentName);
    });
  }

  private void createReceiverParameterFixes() {
    fix(RECEIVER_TYPE_MISMATCH, error -> myFactory.createReceiverParameterTypeFix(error.psi(), error.context()));
    fix(RECEIVER_NAME_MISMATCH,
        error -> error.context() == null ? null : myFactory.createReceiverParameterNameFix(error.psi(), error.context()));
    fix(RECEIVER_STATIC_CONTEXT,
        error -> error.psi().getParent().getParent() instanceof PsiMethod method ? removeModifierFix(method, PsiModifier.STATIC) : null);
    fix(RECEIVER_WRONG_POSITION, error -> {
      if (error.psi().getParent().getParent() instanceof PsiMethod method) {
        PsiReceiverParameter firstReceiverParameter = PsiTreeUtil.getChildOfType(method.getParameterList(), PsiReceiverParameter.class);
        if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstReceiverParameter), JavaTokenType.LPARENTH)) {
          return myFactory.createMakeReceiverParameterFirstFix(error.psi());
        }
      }
      return null;
    });
  }

  private @NotNull IntentionAction addModifierFix(@NotNull PsiModifierListOwner owner, @PsiModifier.ModifierConstant String modifier) {
    JvmModifier jvmModifier = JvmModifier.fromPsiModifier(modifier);
    if (jvmModifier != null && owner instanceof JvmModifiersOwner jvmModifiersOwner) {
      IntentionAction action = ContainerUtil.getFirstItem(JvmElementActionFactories.createModifierActions(
        jvmModifiersOwner, MemberRequestsKt.modifierRequest(jvmModifier, true)));
      if (action != null) {
        return action;
      }
    }
    return myFactory.createModifierListFix(owner, modifier, true, false);
  }

  private @Nullable IntentionAction maybeAddModifierFix(@NotNull PsiModifierListOwner owner, @PsiModifier.ModifierConstant String modifier) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null && JavaPsiModifierUtil.getIncompatibleModifier(modifier, modifierList) != null) return null;
    return addModifierFix(owner, modifier);
  }

  private @NotNull IntentionAction removeModifierFix(@NotNull PsiModifierListOwner owner, @PsiModifier.ModifierConstant String modifier) {
    JvmModifier jvmModifier = JvmModifier.fromPsiModifier(modifier);
    if (jvmModifier != null && owner instanceof JvmModifiersOwner jvmModifiersOwner) {
      IntentionAction action = ContainerUtil.getFirstItem(JvmElementActionFactories.createModifierActions(
        jvmModifiersOwner, MemberRequestsKt.modifierRequest(jvmModifier, false)));
      if (action != null) {
        return action;
      }
    }
    return myFactory.createModifierListFix(owner, modifier, false, false);
  }

  private <Psi extends PsiElement, Context> void fix(@NotNull JavaErrorKind<Psi, Context> kind,
                                                     @NotNull JavaFixProvider<? super Psi, ? super Context> fixProvider) {
    fixes(kind, fixProvider.asPusher());
  }

  private <Psi extends PsiElement, Context> void multi(@NotNull JavaErrorKind<Psi, Context> kind,
                                                       @NotNull JavaFixesProvider<? super Psi, ? super Context> fixProvider) {
    fixes(kind, fixProvider.asPusher());
  }

  private <Psi extends PsiElement, Context> void fixes(@NotNull JavaErrorKind<Psi, Context> kind,
                                                       @NotNull JavaFixesPusher<? super Psi, ? super Context> fixProvider) {
    myFixes.computeIfAbsent(kind, k -> new ArrayList<>()).add(fixProvider);
  }

  void processFixes(@NotNull JavaCompilationError<?, ?> error, @NotNull Consumer<? super @NotNull CommonIntentionAction> sink) {
    var providers = myFixes.get(error.kind());
    if (providers == null) return;
    for (var provider : providers) {
      @SuppressWarnings("unchecked") var fn = (JavaFixesPusher<PsiElement, Object>)provider;
      fn.provide(error, fix -> {
        if (fix != null) {
          sink.accept(fix);
        }
      });
    }
  }
}
