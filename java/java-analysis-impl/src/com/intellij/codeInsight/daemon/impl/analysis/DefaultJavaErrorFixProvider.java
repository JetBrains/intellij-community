// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiModifierUtil;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaMismatchedCallContext;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.fixes.MakeDefaultLastCaseFix;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
public final class DefaultJavaErrorFixProvider extends AbstractJavaErrorFixProvider {
  private final QuickFixFactory myFactory = QuickFixFactory.getInstance();

  DefaultJavaErrorFixProvider() {
    multi(UNSUPPORTED_FEATURE, error -> HighlightFixUtil.getIncreaseLanguageLevelFixes(error.psi(), error.context()));
    multi(PREVIEW_API_USAGE, error -> HighlightFixUtil.getIncreaseLanguageLevelFixes(error.psi(), error.context().feature()));
    JavaFixProvider<PsiElement, Object> genericRemover = error -> myFactory.createDeleteFix(error.psi());
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_MEMBER_THROWS_NOT_ALLOWED, ANNOTATION_ATTRIBUTE_DUPLICATE,
                                            ANNOTATION_NOT_ALLOWED_EXTENDS, ANNOTATION_NOT_ALLOWED_IN_PERMIT_LIST, 
                                            RECEIVER_STATIC_CONTEXT, RECEIVER_WRONG_POSITION, RECORD_HEADER_REGULAR_CLASS, 
                                            INTERFACE_CLASS_INITIALIZER, INTERFACE_CONSTRUCTOR, CLASS_IMPLICIT_INITIALIZER, 
                                            CLASS_IMPLICIT_PACKAGE, RECORD_EXTENDS, ENUM_EXTENDS, RECORD_PERMITS, ENUM_PERMITS, 
                                            ANNOTATION_PERMITS, NEW_EXPRESSION_DIAMOND_NOT_ALLOWED, REFERENCE_TYPE_ARGUMENT_STATIC_CLASS,
                                            STATEMENT_CASE_OUTSIDE_SWITCH, NEW_EXPRESSION_DIAMOND_NOT_APPLICABLE,
                                            NEW_EXPRESSION_ANONYMOUS_IMPLEMENTS_INTERFACE_WITH_TYPE_ARGUMENTS,
                                            CALL_DIRECT_ABSTRACT_METHOD_ACCESS, RECORD_SPECIAL_METHOD_TYPE_PARAMETERS,
                                            RECORD_SPECIAL_METHOD_THROWS, ARRAY_TYPE_ARGUMENTS, ARRAY_EMPTY_DIAMOND,
                                            IMPORT_LIST_EXTRA_SEMICOLON, ENUM_CONSTANT_MODIFIER, METHOD_REFERENCE_PARAMETERIZED_QUALIFIER,
                                            CONSTRUCTOR_IN_IMPLICIT_CLASS, TYPE_ARGUMENT_IN_PERMITS_LIST, MODULE_NO_PACKAGE,
                                            MODULE_DUPLICATE_REQUIRES, MODULE_DUPLICATE_EXPORTS, MODULE_DUPLICATE_OPENS, 
                                            MODULE_DUPLICATE_USES, MODULE_DUPLICATE_PROVIDES, MODULE_OPENS_IN_WEAK_MODULE)) {
      fix(kind, genericRemover);
    }

    createModifierFixes();
    createClassFixes();
    createConstructorFixes();
    createMethodFixes();
    createExpressionFixes();
    createVariableFixes();
    createPatternFixes();
    createStatementFixes();
    createExceptionFixes();
    createGenericFixes();
    createRecordFixes();
    createTypeFixes();
    createAccessFixes();
    createAnnotationFixes();
    createReceiverParameterFixes();
    createModuleFixes();
  }

  private void createModuleFixes() {
    fix(MODULE_FILE_WRONG_NAME, error -> myFactory.createRenameFileFix(PsiJavaModule.MODULE_INFO_FILE));
    fix(MODULE_FILE_DUPLICATE, error ->
      error.context() != null ? new GoToSymbolFix(error.context(), JavaErrorBundle.message("module.open.duplicate.text")) : null);
    JavaFixProvider<PsiStatement, String> mergeModuleFix = error -> MergeModuleStatementsFix.createFix(error.psi());
    fix(MODULE_DUPLICATE_REQUIRES, mergeModuleFix);
    fix(MODULE_DUPLICATE_PROVIDES, mergeModuleFix);
    fix(MODULE_DUPLICATE_OPENS, mergeModuleFix);
    fix(MODULE_DUPLICATE_USES, mergeModuleFix);
    fix(MODULE_DUPLICATE_EXPORTS, mergeModuleFix);
    fix(MODULE_FILE_WRONG_LOCATION, error -> new MoveFileFix(error.psi().getContainingFile().getVirtualFile(),
                                                             error.context(), QuickFixBundle.message("move.file.to.source.root.text")));
    fix(MODULE_OPENS_IN_WEAK_MODULE, error -> removeModifierFix(error.context(), PsiModifier.OPEN));
    fix(MODULE_DUPLICATE_EXPORTS_TARGET,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("delete.reference.fix.text")));
    fix(MODULE_DUPLICATE_OPENS_TARGET,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("delete.reference.fix.text")));
    fix(IMPORT_MODULE_NOT_ALLOWED, error -> myFactory.createReplaceOnDemandImport(error.psi(), QuickFixBundle.message("replace.import.module.fix.text")));
    fix(MODULE_DUPLICATE_IMPLEMENTATION,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("delete.reference.fix.text")));
    multi(MODULE_NOT_ON_PATH, error -> {
      PsiJavaModuleReference ref = error.psi().getReference();
      if (ref != null) {
        List<IntentionAction> registrar = new ArrayList<>();
        myFactory.registerOrderEntryFixes(ref, registrar);
        return registrar;
      }
      return List.of();
    });
    fix(MODULE_SERVICE_IMPLEMENTATION_TYPE, error -> {
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(error.context().superClass());
      return myFactory.createExtendsListFix(error.context().subClass(), type, true);
    });
    JavaFixProvider<PsiPackageAccessibilityStatement, Void> createClassInPackage = error -> {
      String packageName = error.psi().getPackageName();
      Module module = ModuleUtilCore.findModuleForFile(error.psi().getContainingFile());
      return module == null ? null : myFactory.createCreateClassInPackageInModuleFix(module, packageName);
    };
    fix(MODULE_REFERENCE_PACKAGE_NOT_FOUND, createClassInPackage);
    fix(MODULE_REFERENCE_PACKAGE_EMPTY, createClassInPackage);

    JavaFixProvider<PsiElement, JpmsModuleAccessInfo> fixExports = error -> {
      if (error.context().getTarget().getPackageName().isEmpty()) return null;
      Module jpsModule = error.context().getCurrent().getJpsModule();
      PsiJavaModule targetModule = error.context().getTarget().getModule();
      if (targetModule instanceof PsiCompiledElement && jpsModule != null) {
        return new AddExportsOptionFix(jpsModule, targetModule.getName(), error.context().getTarget().getPackageName(),
                                       error.context().getCurrent().getName());
      }
      if (!(targetModule instanceof PsiCompiledElement) && error.context().getCurrent().getModule() != null) {
        return new AddExportsDirectiveFix(requireNonNull(targetModule), error.context().getTarget().getPackageName(),
                                          error.context().getCurrent().getName());
      }
      return null;
    };
    fix(MODULE_ACCESS_FROM_UNNAMED, fixExports);
    fix(MODULE_ACCESS_FROM_NAMED, fixExports);
    JavaFixProvider<PsiElement, JpmsModuleAccessInfo> fixModuleOptions =
      error -> new AddModulesOptionFix(requireNonNull(error.context().getCurrent().getJpsModule()), 
                                       requireNonNull(error.context().getTarget().getModule()).getName());
    fix(MODULE_ACCESS_PACKAGE_NOT_IN_GRAPH, fixModuleOptions);
    fix(MODULE_ACCESS_NOT_IN_GRAPH, fixModuleOptions);
    JavaFixProvider<PsiElement, JpmsModuleAccessInfo> fixRequires =
      error -> new AddRequiresDirectiveFix(requireNonNull(error.context().getCurrent().getModule()),
                                           requireNonNull(error.context().getTarget().getModule()).getName());
    fix(MODULE_ACCESS_PACKAGE_DOES_NOT_READ, fixRequires);
    fix(MODULE_ACCESS_DOES_NOT_READ, fixRequires);
    fixes(MODULE_ACCESS_JPS_DEPENDENCY_PROBLEM, (error, sink) -> {
      if (error.psi() instanceof PsiJavaModuleReferenceElement ref) {
        PsiJavaModuleReference reference = ref.getReference();
        if (reference != null) {
          List<CommonIntentionAction> list = new ArrayList<>();
          myFactory.registerOrderEntryFixes(reference, list);
          list.forEach(sink);
        }
      }
    });
  }

  private void createStatementFixes() {
    fix(RETURN_OUTSIDE_SWITCH_EXPRESSION, error -> error.psi().getReturnValue() != null ? new ReplaceWithYieldFix(error.psi()) : null);
    fixes(RETURN_VALUE_MISSING, (error, sink) -> {
      PsiMethod method = error.context();
      sink.accept(myFactory.createMethodReturnFix(method, PsiTypes.voidType(), true));
      PsiType expectedType = HighlightFixUtil.determineReturnType(method);
      if (expectedType != null && !PsiTypes.voidType().equals(expectedType)) {
        sink.accept(myFactory.createMethodReturnFix(method, expectedType, true, true));
      }
    });
    JavaFixesPusher<PsiReturnStatement, PsiMethod> fixReturnFromVoid = (error, sink) -> {
      PsiMethod method = error.context();
      if (method != null && method.getBody() != null) {
        PsiType valueType = RefactoringChangeUtil.getTypeByExpression(requireNonNull(error.psi().getReturnValue()));
        if (valueType != null) {
          sink.accept(myFactory.createDeleteReturnFix(method, error.psi()));
          sink.accept(myFactory.createMethodReturnFix(method, valueType, true));
        }
        PsiType expectedType = HighlightFixUtil.determineReturnType(method);
        if (expectedType != null && !PsiTypes.voidType().equals(expectedType) && !expectedType.equals(valueType)) {
          sink.accept(myFactory.createMethodReturnFix(method, expectedType, true, true));
        }
      }
    };
    fixes(RETURN_FROM_CONSTRUCTOR, fixReturnFromVoid);
    fixes(RETURN_FROM_VOID_METHOD, fixReturnFromVoid);
    fix(RETURN_MISSING, error -> myFactory.createAddReturnFix(error.context()));
    fix(RETURN_MISSING, error -> error.context() instanceof PsiMethod method ?
                                 myFactory.createMethodReturnFix(method, PsiTypes.voidType(), true) : null);
    fixes(STATEMENT_BAD_EXPRESSION, (error, sink) -> {
      if (error.psi() instanceof PsiExpressionStatement expressionStatement) {
        HighlightFixUtil.registerFixesForExpressionStatement(sink, expressionStatement);
        PsiElement parent = expressionStatement.getParent();
        if (parent instanceof PsiCodeBlock ||
            parent instanceof PsiIfStatement ||
            parent instanceof PsiLoopStatement loop && loop.getBody() == expressionStatement) {
          sink.accept(PriorityIntentionActionWrapper.lowPriority(myFactory.createDeleteSideEffectAwareFix(expressionStatement)));
        }
      }
    });
    fix(STATEMENT_UNREACHABLE,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("delete.unreachable.statement.fix.text")));
    fix(STATEMENT_UNREACHABLE_LOOP_BODY, error -> myFactory.createSimplifyBooleanFix(error.psi(), false));
    fix(FOREACH_NOT_APPLICABLE, error -> myFactory.createNotIterableForEachLoopFix(error.psi()));
    fix(SWITCH_LABEL_EXPECTED, error -> {
      PsiSwitchLabeledRuleStatement previousRule = PsiTreeUtil.getPrevSiblingOfType(error.psi(), PsiSwitchLabeledRuleStatement.class);
      return previousRule == null ? null : myFactory.createWrapSwitchRuleStatementsIntoBlockFix(previousRule);
    });
    fixes(SWITCH_SELECTOR_TYPE_INVALID, (error, sink) -> {
      HighlightFixUtil.registerFixesOnInvalidSelector(sink, error.psi());
      JavaFeature feature = error.context().getFeature();
      if (feature != null) {
        HighlightFixUtil.getIncreaseLanguageLevelFixes(error.psi(), feature).forEach(sink);
      }
    });
    fix(SWITCH_LABEL_DUPLICATE, error -> {
      if (error.psi() instanceof PsiCaseLabelElement caseLabel) {
        return myFactory.createDeleteSwitchLabelFix(caseLabel);
      }
      else if (error.context() == JavaPsiSwitchUtil.SwitchSpecialValue.DEFAULT_VALUE) {
        return myFactory.createDeleteDefaultFix(null, error.psi());
      }
      return null;
    });
    fix(SYNTAX_ERROR, error -> error.psi().getParent() instanceof PsiSwitchLabeledRuleStatement rule &&
                               error.psi().getErrorDescription().equals(JavaPsiBundle.message("expected.switch.rule"))
                               ? myFactory.createWrapSwitchRuleStatementsIntoBlockFix(rule)
                               : null);
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
    fixes(METHOD_MISSING_RETURN_TYPE, (error, sink) -> {
      String className = error.context();
      PsiMethod method = error.psi();
      if (className != null) {
        sink.accept(myFactory.createRenameElementFix(method, className));
      }
      PsiType expectedType = HighlightFixUtil.determineReturnType(method);
      if (expectedType != null) {
        sink.accept(myFactory.createMethodReturnFix(method, expectedType, true, true));
      }
    });
    fixes(METHOD_ABSTRACT_IN_NON_ABSTRACT_CLASS, (error, sink) -> {
      PsiMethod method = error.context();
      sink.accept(method.getBody() != null ? removeModifierFix(method, PsiModifier.ABSTRACT) : myFactory.createAddMethodBodyFix(method));
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && !aClass.isEnum() && !aClass.isRecord()) {
        sink.accept(addModifierFix(aClass, PsiModifier.ABSTRACT));
      }
    });
    fix(METHOD_GENERIC_CLASH, error ->
      error.context().method() instanceof SyntheticElement ?
      null : myFactory.createSameErasureButDifferentMethodsFix(error.context().method(), error.context().superMethod()));
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
    });
    fixes(EXCEPTION_UNHANDLED_CLOSE, (error, sink) -> HighlightFixUtil.registerUnhandledExceptionFixes(error.psi(), sink));
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
  }

  private void createModifierFixes() {
    fix(MODIFIER_NOT_ALLOWED, error -> {
      @SuppressWarnings("MagicConstant") @PsiModifier.ModifierConstant String modifier = error.context();
      PsiModifierList list = (PsiModifierList)error.psi().getParent();
      PsiElement parent = list.getParent();
      if (parent instanceof PsiClass aClass && !aClass.isInterface()
          && (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier))) {
        return myFactory.createChangeModifierFix();
      }
      if (parent instanceof PsiModifierListOwner owner) {
        return removeModifierFix(owner, modifier);
      }
      return myFactory.createModifierListFix(list, modifier, false, false);
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
  
  private void createPatternFixes() {
    fixes(PATTERN_DECONSTRUCTION_COUNT_MISMATCH, (error, sink) -> {
      if (error.context().hasMismatch()) return;
      PsiRecordComponent[] recordComponents = error.context().recordComponents();
      PsiPattern[] patternComponents = error.context().patternComponents();
      PsiDeconstructionList list = error.psi();
      if (patternComponents.length < recordComponents.length) {
        PsiRecordComponent[] missingRecordComponents =
          Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        List<AddMissingDeconstructionComponentsFix.Pattern> missingPatterns =
          ContainerUtil.map(missingRecordComponents, component -> AddMissingDeconstructionComponentsFix.Pattern.create(component, list));
        sink.accept(new AddMissingDeconstructionComponentsFix(list, missingPatterns));
      }
      else {
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        int diff = patternComponents.length - recordComponents.length;
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text", diff);
        sink.accept(QuickFixFactory.getInstance().createDeleteFix(elementsToDelete, text));
      }
    });
    fix(UNSUPPORTED_FEATURE, error -> {
      if (error.context() == JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS) {
        return HighlightFixUtil.createPrimitiveToBoxedPatternFix(error.psi());
      }
      return null;
    });
    fix(CAST_INCONVERTIBLE, error -> {
      if (error.psi() instanceof PsiInstanceOfExpression instanceOfExpression &&
          TypeConversionUtil.isPrimitiveAndNotNull(error.context().rType())) {
        return myFactory.createReplacePrimitiveWithBoxedTypeAction(
          error.context().lType(), requireNonNull(InstanceOfUtils.findCheckTypeElement(instanceOfExpression)));
      }
      return null;
    });
    JavaFixProvider<PsiTypeTestPattern, Object> redundantInstanceOfFix = error -> {
      PsiPatternVariable variable = error.psi().getPatternVariable();
      if (variable != null && !VariableAccessUtils.variableIsUsed(variable, variable.getDeclarationScope())) {
        return new RedundantInstanceofFix(error.psi().getParent());
      }
      return null;
    };
    fix(PATTERN_INSTANCEOF_EQUALS, redundantInstanceOfFix);
    fix(PATTERN_INSTANCEOF_SUPERTYPE, redundantInstanceOfFix);
  }

  private void createVariableFixes() {
    fix(UNNAMED_VARIABLE_BRACKETS, error -> new NormalizeBracketsFix(error.psi()));
    fix(UNNAMED_VARIABLE_WITHOUT_INITIALIZER, error -> myFactory.createAddVariableInitializerFix(error.psi()));
    fixes(LVTI_NO_INITIALIZER, (error, sink) -> HighlightFixUtil.registerSpecifyVarTypeFix(error.psi(), sink));
    fixes(LVTI_NULL, (error, sink) -> HighlightFixUtil.registerSpecifyVarTypeFix(error.psi(), sink));
    JavaFixProvider<PsiJavaCodeReferenceElement, PsiVariable> innerClassAccessFix = error -> {
      PsiVariable variable = error.context();
      PsiElement scope = requireNonNull(ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, error.psi()));
      return myFactory.createVariableAccessFromInnerClassFix(variable, scope);
    };
    fix(VARIABLE_MUST_BE_FINAL, innerClassAccessFix);
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL, innerClassAccessFix);
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, innerClassAccessFix);
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, innerClassAccessFix);
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL, error -> myFactory.createMakeVariableEffectivelyFinalFix(error.context()));
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> myFactory.createMakeVariableEffectivelyFinalFix(error.context()));
    fix(VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, error -> myFactory.createMakeVariableEffectivelyFinalFix(error.context()));
    fix(VARIABLE_ALREADY_ASSIGNED, error -> myFactory.createDeferFinalAssignmentFix(error.context(), error.psi()));
    fix(VARIABLE_ALREADY_ASSIGNED, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(VARIABLE_ALREADY_ASSIGNED_FIELD, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(VARIABLE_ALREADY_ASSIGNED_CONSTRUCTOR, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(VARIABLE_ALREADY_ASSIGNED_INITIALIZER, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(VARIABLE_ASSIGNED_IN_LOOP, error -> removeModifierFix(error.context(), PsiModifier.FINAL));
    fix(VARIABLE_ALREADY_DEFINED, error -> myFactory.createNavigateToAlreadyDeclaredVariableFix(error.context()));
    fix(VARIABLE_ALREADY_DEFINED,
        error -> error.psi() instanceof PsiLocalVariable local ? myFactory.createReuseVariableDeclarationFix(local) : null);

    fixes(FIELD_NOT_INITIALIZED, (error, sink) -> {
      PsiField field = error.psi();
      sink.accept(myFactory.createCreateConstructorParameterFromFieldFix(field));
      sink.accept(myFactory.createInitializeFinalFieldInConstructorFix(field));
      sink.accept(myFactory.createAddVariableInitializerFix(field));
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && !containingClass.isInterface()) {
        sink.accept(removeModifierFix(field, PsiModifier.FINAL));
      }
    });
    fixes(VARIABLE_NOT_INITIALIZED, (error, sink) -> {
      PsiVariable variable = error.context();
      if (!(variable instanceof LightRecordField)) {
        sink.accept(myFactory.createAddVariableInitializerFix(variable));
      }
      if (variable instanceof PsiLocalVariable) {
        PsiElement topBlock = PsiUtil.getVariableCodeBlock(variable, null);
        if (topBlock != null) {
          sink.accept(HighlightFixUtil.createInsertSwitchDefaultFix(variable, topBlock, error.psi()));
        }
      }
      if (variable instanceof PsiField field) {
        sink.accept(removeModifierFix(field, PsiModifier.FINAL));
      }
    });
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
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(error.context().actualType());
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
    fixes(CALL_UNRESOLVED_NAME,
          (error, sink) -> HighlightFixUtil.registerMethodCallIntentions(sink, error.psi(), error.psi().getArgumentList()));
    fix(CALL_QUALIFIER_PRIMITIVE, error -> myFactory.createRenameWrongRefFix(error.psi().getMethodExpression()));
    fixes(CALL_QUALIFIER_PRIMITIVE,
          (error, sink) -> HighlightFixUtil.registerMethodCallIntentions(sink, error.psi(), error.psi().getArgumentList()));
    fixes(CALL_AMBIGUOUS, (error, sink) -> HighlightFixUtil.registerAmbiguousCallFixes(sink, error.psi(), error.context().results()));
    fixes(CALL_AMBIGUOUS_NO_MATCH, (error, sink) -> HighlightFixUtil.registerAmbiguousCallFixes(sink, error.psi(), error.context()));
    fixes(REFERENCE_NON_STATIC_FROM_STATIC_CONTEXT, (error, sink) -> {
      HighlightFixUtil.registerStaticProblemQuickFixAction(sink, error.context(), error.psi());
      if (error.psi().getParent() instanceof PsiMethodCallExpression methodCall) {
        HighlightFixUtil.registerMethodCallIntentions(sink, methodCall, methodCall.getArgumentList());
      }
      else if (error.psi() instanceof PsiReferenceExpression expression) {
        sink.accept(myFactory.createRenameWrongRefFix(expression));
      }
    });
    fixes(CALL_UNRESOLVED, (error, sink) -> {
      PsiMethodCallExpression methodCall = error.psi();
      JavaResolveResult[] resolveResults = error.context();
      if (resolveResults.length == 1) {
        PsiElement element = resolveResults[0].getElement();
        if (element != null && !resolveResults[0].isStaticsScopeCorrect()) {
          HighlightFixUtil.registerStaticProblemQuickFixAction(sink, element, methodCall.getMethodExpression());
        }
      }
      HighlightFixUtil.registerAmbiguousCallFixes(sink, methodCall, resolveResults);
    });
    fix(CALL_DIRECT_ABSTRACT_METHOD_ACCESS, error -> {
      PsiMethod method = error.context();
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      String name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
      String text = QuickFixBundle.message("remove.modifier.fix", name, VisibilityUtil.toPresentableText(PsiModifier.ABSTRACT));
      return myFactory.createAddMethodBodyFix(method, text);
    });
    multi(CALL_CONSTRUCTOR_MUST_BE_FIRST_STATEMENT, error -> HighlightFixUtil.getIncreaseLanguageLevelFixes(
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
    fix(REFERENCE_UNRESOLVED, error -> HighlightFixUtil.createUnresolvedReferenceFix(error.psi()));
    fix(REFERENCE_QUALIFIER_PRIMITIVE,
        error -> error.psi() instanceof PsiReferenceExpression ref ? myFactory.createRenameWrongRefFix(ref) : null);
    fix(CAST_INTERSECTION_NOT_INTERFACE, error -> {
      PsiTypeElement conjunct = error.psi();
      return new FlipIntersectionSidesFix(((PsiClassType)conjunct.getType()).getClassName(), conjunct, 
                                          PsiTreeUtil.getParentOfType(conjunct, PsiTypeElement.class, true));
    });
    fix(CAST_INTERSECTION_REPEATED_INTERFACE, error -> new DeleteRepeatedInterfaceFix(error.psi()));
    fix(EXPRESSION_CLASS_PARAMETERIZED_TYPE, error -> {
      PsiTypeElement operand = error.psi();
      final PsiJavaCodeReferenceElement referenceElement = operand.getInnermostComponentReferenceElement();
      if (referenceElement != null) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null) {
          return myFactory.createDeleteFix(parameterList);
        }
      }
      return null;
    });
    fix(METHOD_REFERENCE_RETURN_TYPE_ERROR, error -> AdjustFunctionContextFix.createFix(error.psi()));
    fix(METHOD_REFERENCE_UNRESOLVED_METHOD, error -> myFactory.createCreateMethodFromUsageFix(error.psi()));
    fix(METHOD_REFERENCE_UNRESOLVED_CONSTRUCTOR, error -> myFactory.createCreateMethodFromUsageFix(error.psi()));
    fix(METHOD_REFERENCE_INFERENCE_ERROR, error -> myFactory.createCreateMethodFromUsageFix(error.psi()));
    fix(METHOD_REFERENCE_STATIC_METHOD_NON_STATIC_QUALIFIER, error -> removeModifierFix(error.context(), PsiModifier.STATIC));
    fix(METHOD_REFERENCE_STATIC_METHOD_RECEIVER, error -> removeModifierFix(error.context(), PsiModifier.STATIC));
    fix(METHOD_REFERENCE_NON_STATIC_METHOD_IN_STATIC_CONTEXT, error -> addModifierFix(error.context(), PsiModifier.STATIC));
    fix(ASSIGNMENT_TO_FINAL_VARIABLE, error -> {
      PsiVariable variable = error.context();
      PsiElement scope = ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, error.psi());
      return scope == null || variable instanceof PsiField
             ? removeModifierFix(variable, PsiModifier.FINAL)
             : myFactory.createVariableAccessFromInnerClassFix(variable, scope);
    });
    JavaFixProvider<PsiElement, Object> qualifyFix = error -> {
      if (!(error.psi() instanceof PsiReferenceExpression ref)) return null;
      PsiClass parentClass = PsiUtil.getContainingClass(ref);
      if (parentClass == null || !PsiUtil.isInnerClass(parentClass)) return null;
      String referenceName = ref.getReferenceName();
      PsiClass containingClass = requireNonNull(parentClass.getContainingClass());
      PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
      return fieldInContainingClass != null && ref.getQualifierExpression() == null ? new QualifyWithThisFix(containingClass, ref) : null;
    };
    fix(REFERENCE_MEMBER_BEFORE_CONSTRUCTOR, qualifyFix);
    fix(CALL_MEMBER_BEFORE_CONSTRUCTOR, qualifyFix);
    fix(CLASS_OR_PACKAGE_EXPECTED, error -> myFactory.createRemoveQualifierFix(
      requireNonNull(error.psi().getQualifierExpression()), error.psi(), error.context()));
    fix(SWITCH_LABEL_QUALIFIED_ENUM, error -> myFactory.createDeleteFix(
      requireNonNull(error.psi().getQualifier()), JavaErrorBundle.message("qualified.enum.constant.in.switch.remove.fix")));
    fix(SWITCH_DEFAULT_LABEL_CONTAINS_CASE, error -> myFactory.createReplaceCaseDefaultWithDefaultFix(error.context()));
    JavaFixProvider<PsiCaseLabelElement, Void> splitCase = error -> myFactory.createSplitSwitchBranchWithSeveralCaseValuesAction();
    fix(SWITCH_MULTIPLE_LABELS_WITH_PATTERN_VARIABLES, splitCase);
    fix(SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS, splitCase);
    fix(SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS_UNNAMED, splitCase);
    fix(SWITCH_LABEL_MULTIPLE_PATTERNS, splitCase);
    fix(SWITCH_LABEL_MULTIPLE_PATTERNS_UNNAMED, splitCase);
    fix(SWITCH_DEFAULT_NULL_ORDER, error -> myFactory.createReverseCaseDefaultNullFixFix(error.context()));
    fixes(SWITCH_DOMINANCE_VIOLATION, (error, sink) -> {
      PsiElement who = error.context();
      PsiCaseLabelElement overWhom = error.psi();
      if (who instanceof PsiKeyword && JavaKeywords.DEFAULT.equals(who.getText()) ||
          JavaPsiSwitchUtil.isInCaseNullDefaultLabel(who)) {
        PsiSwitchLabelStatementBase labelStatementBase = PsiTreeUtil.getParentOfType(who, PsiSwitchLabelStatementBase.class);
        if (labelStatementBase != null) {
          sink.accept(new MakeDefaultLastCaseFix(labelStatementBase));
        }
      }
      else if (who instanceof PsiCaseLabelElement whoElement) {
        if (!JavaPsiPatternUtil.dominates(overWhom, whoElement) && overWhom.getParent() != whoElement.getParent()) {
          sink.accept(myFactory.createMoveSwitchBranchUpFix(whoElement, overWhom));
        }
        sink.accept(myFactory.createDeleteSwitchLabelFix(overWhom));
      }
    });
    fix(SWITCH_UNCONDITIONAL_PATTERN_AND_DEFAULT, error -> error.psi() instanceof PsiCaseLabelElement elementCoversType
           ? myFactory.createDeleteSwitchLabelFix(elementCoversType)
           : myFactory.createDeleteDefaultFix(null, error.psi()));
    fix(SWITCH_UNCONDITIONAL_PATTERN_AND_BOOLEAN, error -> myFactory.createDeleteSwitchLabelFix(error.psi()));
    fix(SWITCH_DEFAULT_AND_BOOLEAN, error -> myFactory.createDeleteDefaultFix(null, error.psi()));
    JavaFixesPusher<PsiSwitchBlock, Void> switchFixes = (error, sink) -> {
      sink.accept(myFactory.createAddSwitchDefaultFix(error.psi(), null));
      HighlightFixUtil.addCompletenessFixes(sink, error.psi());
    };
    fixes(SWITCH_EMPTY, switchFixes);
    fixes(SWITCH_INCOMPLETE, switchFixes);
  }

  private void createAccessFixes() {
    JavaFixesPusher<PsiElement, JavaResolveResult> accessFix = (error, sink) -> {
      if (error.psi() instanceof PsiJavaCodeReferenceElement ref && 
          error.context().isStaticsScopeCorrect() && error.context().getElement() instanceof PsiJvmMember member) {
        HighlightFixUtil.registerAccessQuickFixAction(sink, member, ref, null);
        if (ref instanceof PsiReferenceExpression expression) {
          sink.accept(myFactory.createRenameWrongRefFix(expression));
        }
      }
    };
    fixes(ACCESS_PRIVATE, accessFix);
    fixes(ACCESS_PROTECTED, accessFix);
    fixes(ACCESS_PACKAGE_LOCAL, accessFix);
    fixes(ACCESS_GENERIC_PROBLEM, accessFix);
  }

  private void createTypeFixes() {
    fixes(TYPE_INCOMPATIBLE, (error, sink) -> 
      HighlightFixUtil.registerIncompatibleTypeFixes(sink, error.psi(), error.context().lType(), error.context().rType()));
    fixes(SWITCH_EXPRESSION_INCOMPATIBLE_TYPE, (error, sink) ->
      HighlightFixUtil.registerIncompatibleTypeFixes(sink,
                                                     requireNonNull(PsiTreeUtil.getParentOfType(error.psi(), PsiSwitchExpression.class)),
                                                     error.context().lType(), error.context().rType()));
    fixes(CALL_TYPE_INFERENCE_ERROR, (error, sink) -> {
      if (error.psi() instanceof PsiMethodCallExpression callExpression) {
        HighlightFixUtil.registerCallInferenceFixes(sink, callExpression);
      }
    });
    fixes(LAMBDA_INFERENCE_ERROR, (error, sink) -> {
      if (error.psi().getParent() instanceof PsiExpressionList list && 
          list.getParent() instanceof PsiMethodCallExpression callExpression) {
        MethodCandidateInfo resolveResult = error.context();
        PsiMethod method = resolveResult.getElement();
        HighlightFixUtil.registerMethodCallIntentions(sink, callExpression, callExpression.getArgumentList());
        if (!PsiTypesUtil.mentionsTypeParameters(((PsiExpression)callExpression.copy()).getType(), Set.of(method.getTypeParameters()))) {
          HighlightFixUtil.registerMethodReturnFixAction(sink, resolveResult, callExpression);
        }
        HighlightFixUtil.registerTargetTypeFixesBasedOnApplicabilityInference(sink, callExpression, resolveResult, method);
        LambdaUtil.getReturnExpressions(error.psi())
          .stream().map(PsiExpression::getType).distinct()
          .map(type -> AdjustFunctionContextFix.createFix(type, error.psi()))
          .forEach(sink);
      }
    });
    fixes(LAMBDA_RETURN_TYPE_ERROR, (error, sink) -> {
      if (error.psi() instanceof PsiExpression expr) {
        sink.accept(AdjustFunctionContextFix.createFix(expr));
        PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(expr, PsiLambdaExpression.class);
        if (lambda != null) {
          HighlightFixUtil.registerLambdaReturnTypeFixes(sink, lambda, expr);
        }
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
          sink, resolveHelper.getReferencedMethodCandidates(methodCall, false), methodCall, list);
        HighlightFixUtil.registerMethodCallIntentions(sink, methodCall, list);
        HighlightFixUtil.registerMethodReturnFixAction(sink, candidate, methodCall);
        HighlightFixUtil.registerTargetTypeFixesBasedOnApplicabilityInference(sink, methodCall, candidate, candidate.getElement());
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
    fix(TYPE_ARGUMENT_PRIMITIVE, error -> {
      PsiTypeElement typeElement = error.psi();
      PsiType type = typeElement.getType();
      PsiPrimitiveType toConvert =
        (PsiPrimitiveType)(type instanceof PsiWildcardType wildcardType ? requireNonNull(wildcardType.getBound()) : type);
      PsiClassType boxedType = toConvert.getBoxedType(typeElement);
      if (boxedType != null) {
        return QuickFixFactory.getInstance().createReplacePrimitiveWithBoxedTypeAction(
          typeElement, toConvert.getPresentableText(), boxedType.getCanonicalText());
      }
      return null;
    });
  }

  private void createClassFixes() {
    fix(CLASS_INHERITS_UNRELATED_DEFAULTS, error -> myFactory.createImplementMethodsFix(error.psi()));
    fix(CLASS_INHERITS_ABSTRACT_AND_DEFAULT, error -> myFactory.createImplementMethodsFix(error.psi()));
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
    fix(CLASS_NO_ABSTRACT_METHOD, error -> {
      if (error.psi() instanceof PsiClass aClass && !(aClass instanceof PsiAnonymousClass) && !aClass.isEnum()) {
        return maybeAddModifierFix(aClass, PsiModifier.ABSTRACT);
      }
      return null;
    });
    fix(CLASS_REFERENCE_LIST_DUPLICATE,
        error -> myFactory.createRemoveDuplicateExtendsAction(HighlightNamesUtil.formatClass(error.context())));
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
    fix(CLASS_SEALED_NO_INHERITORS, error -> addModifierFix(error.psi(), PsiModifier.NON_SEALED));
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
    fix(CLASS_GENERIC_EXTENDS_EXCEPTION, error -> {
      PsiJavaCodeReferenceElement ref = error.psi();
      PsiMember owner = PsiTreeUtil.getParentOfType(ref, PsiClass.class, PsiMethod.class);
      if (owner instanceof PsiClass klass && !(klass instanceof PsiAnonymousClass) && ref.resolve() instanceof PsiClass throwableClass) {
        PsiClassType classType = JavaPsiFacade.getElementFactory(error.project()).createType(throwableClass);
        return myFactory.createExtendsListFix(klass, classType, false);
      }
      return null;
    });
    fix(CLASS_EXTENDS_SEALED_LOCAL, error -> myFactory.createConvertLocalToInnerAction(error.context()));
    fix(CLASS_EXTENDS_SEALED_ANOTHER_PACKAGE, error -> {
      if (error.context().superClass().getContainingFile() instanceof PsiClassOwner classOwner) {
        return myFactory.createMoveClassToPackageFix(error.context().subClass(), classOwner.getPackageName());
      }
      return null;
    });
    fix(CLASS_EXTENDS_SEALED_NOT_PERMITTED, error -> error.context().superClass() instanceof PsiCompiledElement
           ? null
           : myFactory.createAddToPermitsListFix(error.context().subClass(), error.context().superClass()));
    fixes(CLASS_PERMITTED_MUST_HAVE_MODIFIER, (error, sink) -> {
      PsiClass inheritorClass = error.context();
      sink.accept(addModifierFix(inheritorClass, PsiModifier.NON_SEALED));
      boolean hasInheritors = DirectClassInheritorsSearch.search(inheritorClass).findFirst() != null;
      if (!inheritorClass.isInterface() && !inheritorClass.hasModifierProperty(PsiModifier.ABSTRACT) || hasInheritors) {
        IntentionAction action = hasInheritors ?
                                 myFactory.createSealClassFromPermitsListFix(inheritorClass) :
                                 addModifierFix(inheritorClass, PsiModifier.FINAL);
        sink.accept(action);
      }
    });
    multi(CLASS_PERMITTED_NOT_DIRECT_SUBCLASS, error ->
      myFactory.createExtendSealedClassFixes(error.psi(), error.context().superClass(), error.context().subClass()));
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
                                            FUNCTIONAL_INTERFACE_SEALED, OVERRIDE_ON_STATIC_METHOD,
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
    multi(ANNOTATION_ATTRIBUTE_NAME_MISSING, error -> myFactory.createAddAnnotationAttributeNameFixes(error.psi()));
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
    fix(CALL_STATIC_INTERFACE_METHOD_QUALIFIER,
        error -> error.psi() instanceof PsiReferenceExpression ref ?
                 myFactory.createAccessStaticViaInstanceFix(ref, ref.advancedResolve(true)) : null);
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
}
