// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil;
import com.intellij.java.codeserver.core.JavaPsiModifierUtil;
import com.intellij.java.codeserver.core.JavaPsiSealedUtil;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.checkRecordExhaustiveness;
import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.findMissedClasses;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public final class HighlightFixUtil {
  private static final Logger LOG = Logger.getInstance(HighlightFixUtil.class);
  private static final CallMatcher COLLECTION_TO_ARRAY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterCount(0);

  static void registerCollectionToArrayFixAction(@NotNull Consumer<? super CommonIntentionAction> info,
                                                 @Nullable PsiType fromType,
                                                 @Nullable PsiType toType,
                                                 @NotNull PsiExpression expression) {
    if (toType instanceof PsiArrayType) {
      PsiType arrayComponentType = ((PsiArrayType)toType).getComponentType();
      if (!(arrayComponentType instanceof PsiPrimitiveType) &&
          !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter)) {
        PsiExpression collection = expression;
        if (expression instanceof PsiMethodCallExpression call && COLLECTION_TO_ARRAY.test(call)) {
          collection = call.getMethodExpression().getQualifierExpression();
          if (collection == null) return;
          fromType = collection.getType();
        }
        if (fromType instanceof PsiClassType &&
            (CommonClassNames.JAVA_LANG_OBJECT.equals(arrayComponentType.getCanonicalText()) || !((PsiClassType)fromType).isRaw()) &&
            InheritanceUtil.isInheritor(fromType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(fromType, expression.getResolveScope());
          if (collectionItemType != null && arrayComponentType.isConvertibleFrom(collectionItemType)) {
            info.accept(QuickFixFactory.getInstance().createCollectionToArrayFix(collection, expression, (PsiArrayType)toType));
          }
        }
      }
    }
  }

  /**
   * Make an element protected/package-private/public suggestion.
   * For private method in the interface it should add default modifier as well.
   */
  static void registerAccessQuickFixAction(@NotNull Consumer<? super CommonIntentionAction> info,
                                           @NotNull PsiJvmMember refElement,
                                           @NotNull PsiJavaCodeReferenceElement place,
                                           @Nullable PsiElement fileResolveScope) {
    PsiClass accessObjectClass = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
    }
    if (place instanceof PsiReferenceExpression ref) {
      FieldAccessFixer fixer = FieldAccessFixer.create(ref, refElement, place);
      if (fixer != null) {
        info.accept(new ReplaceInaccessibleFieldWithGetterSetterFix(ref, fixer));
      }
    }

    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass packageLocalClassInTheMiddle = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions(packageLocalClassInTheMiddle, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
      fixes.forEach(info);
      return;
    }

    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      assert modifierListCopy != null;
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      int minAccessLevel = PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        minAccessLevel = PsiUtil.ACCESS_LEVEL_PROTECTED;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minAccessLevel = PsiUtil.ACCESS_LEVEL_PUBLIC;
      }
      PsiClass containingClass = refElement.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        minAccessLevel = PsiUtil.ACCESS_LEVEL_PUBLIC;
      }
      int[] accessLevels = {PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PUBLIC,};
      for (int i = ArrayUtil.indexOf(accessLevels, minAccessLevel); i < accessLevels.length; i++) {
        @SuppressWarnings("MagicConstant") @PsiUtil.AccessLevel
        int level = accessLevels[i];
        modifierListCopy.setModifierProperty(PsiUtil.getAccessModifier(level), true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          List<IntentionAction> fixes = JvmElementActionFactories
            .createModifierActions(refElement, MemberRequestsKt.modifierRequest(JvmUtil.getAccessModifier(level), true));
          fixes.forEach(info);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  static void registerChangeVariableTypeFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                              @NotNull PsiExpression expression,
                                              @NotNull PsiType type) {
    if (!(expression instanceof PsiReferenceExpression ref)) return;
    if (!(ref.resolve() instanceof PsiVariable variable)) return;

    registerChangeVariableTypeFixes(info, variable, type);
  }

  static void registerUnhandledExceptionFixes(@NotNull PsiElement element, @NotNull Consumer<? super CommonIntentionAction> info) {
    final QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    info.accept(quickFixFactory.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    info.accept(quickFixFactory.createAddExceptionToCatchFix());
    info.accept(quickFixFactory.createAddExceptionToExistingCatch(element));
    info.accept(quickFixFactory.createAddExceptionToThrowsFix(element));
    info.accept(quickFixFactory.createSurroundWithTryCatchFix(element));
  }

  static void registerStaticProblemQuickFixAction(@NotNull Consumer<? super CommonIntentionAction> info,
                                                  @NotNull PsiElement refElement,
                                                  @NotNull PsiJavaCodeReferenceElement place) {
    if (place instanceof PsiReferenceExpression && place.getParent() instanceof PsiMethodCallExpression) {
      ReplaceGetClassWithClassLiteralFix.registerFix((PsiMethodCallExpression)place.getParent(), info);
    }
    if (refElement instanceof PsiJvmModifiersOwner && !(refElement instanceof PsiParameter)) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions((PsiJvmModifiersOwner)refElement, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true));
      fixes.forEach(info);
    }
    // make context non-static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      info.accept(QuickFixFactory.getInstance().createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      info.accept(QuickFixFactory.getInstance().createCreateFieldFromUsageFix((PsiReferenceExpression)place));
    }
  }

  private static boolean isInstanceReference(@NotNull PsiJavaCodeReferenceElement place) {
    PsiElement qualifier = place.getQualifier();
    if (qualifier == null) return true;
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement q = ((PsiReference)qualifier).resolve();
    if (q instanceof PsiClass) return false;
    if (q != null) return true;
    String qname = ((PsiJavaCodeReferenceElement)qualifier).getQualifiedName();
    return qname == null || !Character.isLowerCase(qname.charAt(0));
  }

  static void registerChangeVariableTypeFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                              @NotNull PsiVariable parameter,
                                              @Nullable PsiType itemType) {
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      info.accept(action);
    }
  }

  static @Nullable IntentionAction createChangeReturnTypeFix(@Nullable PsiExpression expr, @NotNull PsiType toType) {
    if (expr instanceof PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method != null) {
        return PriorityIntentionActionWrapper
          .lowPriority(QuickFixFactory.getInstance().createMethodReturnFix(method, toType, true));
      }
    }
    return null;
  }

  /**
   * @param variable variable to create change type fixes for
   * @param itemType a desired variable type
   * @return a list of created fix actions
   */
  public static @NotNull List<IntentionAction> getChangeVariableTypeFixes(@NotNull PsiVariable variable, @Nullable PsiType itemType) {
    if (itemType instanceof PsiMethodReferenceType) return Collections.emptyList();
    List<IntentionAction> result = new ArrayList<>();
    if (itemType != null && PsiTypesUtil.allTypeParametersResolved(variable, itemType)) {
      for (ChangeVariableTypeQuickFixProvider fixProvider : ChangeVariableTypeQuickFixProvider.EP_NAME.getExtensionList()) {
        Collections.addAll(result, fixProvider.getFixes(variable, itemType));
      }
      IntentionAction changeFix = getChangeParameterClassFix(variable.getType(), itemType);
      if (changeFix != null) result.add(changeFix);
    }
    else if (itemType instanceof PsiArrayType) {
      PsiType type = variable.getType();
      if (type instanceof PsiArrayType && type.getArrayDimensions() == itemType.getArrayDimensions()) {
        PsiType componentType = type.getDeepComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          PsiClassType boxedType = ((PsiPrimitiveType)componentType).getBoxedType(variable);
          if (boxedType != null) {
            return getChangeVariableTypeFixes(variable, PsiTypesUtil.createArrayType(boxedType, type.getArrayDimensions()));
          }
        }
      }
    }

    return result;
  }

  private static @Nullable IntentionAction getChangeParameterClassFix(@NotNull PsiType lType, @NotNull PsiType rType) {
    PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

    if (rClass == null || lClass == null) return null;
    if (rClass instanceof PsiAnonymousClass) return null;
    if (rClass.isInheritor(lClass, true)) return null;
    if (lClass.isInheritor(rClass, true)) return null;
    if (lClass == rClass) return null;

    return QuickFixFactory.getInstance().createChangeParameterClassFix(rClass, (PsiClassType)lType);
  }

  static void registerLambdaReturnTypeFixes(@NotNull Consumer<? super CommonIntentionAction> info, PsiLambdaExpression lambda, PsiExpression expression) {
    PsiType type = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    if (type != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(info, expression, type);
    }
  }

  static void registerChangeParameterClassFix(@NotNull Consumer<? super CommonIntentionAction> info,
                                              @NotNull PsiType lType,
                                              @Nullable PsiType rType) {
    if (rType == null) return;
    IntentionAction action = getChangeParameterClassFix(lType, rType);
    if (action != null) {
      info.accept(action);
    }
  }

  private static @Nullable PsiSwitchStatement findInitializingSwitch(@NotNull PsiVariable variable,
                                                                     @NotNull PsiElement topBlock,
                                                                     @NotNull PsiElement readPoint) {
    PsiSwitchStatement switchForAll = null;
    for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(variable, topBlock)) {
      if (PsiUtil.isAccessedForWriting(reference)) {
        PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(reference, PsiSwitchStatement.class);
        if (switchStatement == null || !PsiTreeUtil.isAncestor(topBlock, switchStatement, true)) return null;
        if (switchForAll != null) {
          if (switchForAll != switchStatement) return null;
        }
        else {
          switchForAll = switchStatement;
        }
      }
    }
    if (switchForAll == null) return null;
    if (SwitchUtils.calculateBranchCount(switchForAll) < 0) return null;
    List<PsiSwitchLabelStatementBase> labels =
      PsiTreeUtil.getChildrenOfTypeAsList(switchForAll.getBody(), PsiSwitchLabelStatementBase.class);
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(topBlock.getProject()).getControlFlow(topBlock, policy);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
    int switchOffset = controlFlow.getStartOffset(switchForAll);
    int readOffset = controlFlow.getStartOffset(readPoint);
    if (switchOffset == -1 || readOffset == -1 || !ControlFlowUtil.isDominator(controlFlow, switchOffset, readOffset)) return null;
    boolean[] offsets = ControlFlowUtil.getVariablePossiblyUnassignedOffsets(variable, controlFlow);
    for (PsiSwitchLabelStatementBase label : labels) {
      int offset = controlFlow.getStartOffset(label);
      if (offset == -1 || offsets[offset]) return null;
    }
    return switchForAll;
  }

  static @Nullable IntentionAction createInsertSwitchDefaultFix(@NotNull PsiVariable variable,
                                                                @NotNull PsiElement topBlock,
                                                                @NotNull PsiElement readPoint) {
    PsiSwitchStatement switchStatement = findInitializingSwitch(variable, topBlock, readPoint);
    if (switchStatement != null) {
      String message = QuickFixBundle.message("add.default.branch.to.variable.initializing.switch.fix.name", variable.getName());
      return QuickFixFactory.getInstance().createAddSwitchDefaultFix(switchStatement, message);
    }
    return null;
  }

  public static void registerFixesForExpressionStatement(@NotNull Consumer<? super CommonIntentionAction> info,
                                                         @NotNull PsiElement statement) {
    if (!(statement instanceof PsiExpressionStatement)) return;
    if (!(statement.getParent() instanceof PsiCodeBlock block)) return; 
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (expression instanceof PsiAssignmentExpression) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (!type.equals(PsiTypes.voidType())) {
      info.accept(PriorityIntentionActionWrapper.highPriority(QuickFixFactory.getInstance().createIterateFix(expression)));
      if (PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) == block.getRBrace() && block.getParent() instanceof PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType != null && isPossibleReturnValue(expression, type, returnType)) {
          info.accept(QuickFixFactory.getInstance().createInsertReturnFix(expression));
        }
      }
      info.accept(QuickFixFactory.getInstance().createIntroduceVariableAction(expression));
    }
  }

  private static boolean isPossibleReturnValue(PsiExpression expression, PsiType type, PsiType returnType) {
    if (returnType.isAssignableFrom(type)) return true;
    if (!(type instanceof PsiClassType) || !(returnType instanceof PsiClassType)) return false;
    if (!returnType.isAssignableFrom(TypeConversionUtil.erasure(type))) return false;
    PsiExpression copy = (PsiExpression)LambdaUtil.copyWithExpectedType(expression, returnType);
    PsiType copyType = copy.getType();
    return copyType != null && returnType.isAssignableFrom(copyType);
  }

  static void registerSpecifyVarTypeFix(@NotNull PsiLocalVariable variable, @NotNull Consumer<? super CommonIntentionAction> info) {
    PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
    if (block == null) return;
    PsiTreeUtil.processElements(block, PsiReferenceExpression.class, ref -> {
      if (ref.isReferenceTo(variable) &&
          ref.getParent() instanceof PsiAssignmentExpression assignment &&
          assignment.getLExpression() == ref &&
          assignment.getOperationTokenType() == JavaTokenType.EQ) {
        PsiExpression rExpression = assignment.getRExpression();
        if (rExpression != null) {
          PsiType type = rExpression.getType();
          if (type instanceof PsiPrimitiveType primitiveType && !PsiTypes.voidType().equals(type) && variable.getInitializer() != null) {
            type = primitiveType.getBoxedType(variable);
          }
          if (type != null) {
            type = GenericsUtil.getVariableTypeByExpressionType(type);
            if (PsiTypesUtil.isDenotableType(type, variable) && !PsiTypes.voidType().equals(type)) {
              info.accept(QuickFixFactory.getInstance().createSetVariableTypeFix(variable, type));
            }
            return false;
          }
        }
      }
      return true;
    });
  }

  static void registerQualifyMethodCallFix(@NotNull Consumer<? super CommonIntentionAction> info,
                                           CandidateInfo @NotNull [] methodCandidates,
                                           @NotNull PsiMethodCallExpression methodCall,
                                           @NotNull PsiExpressionList exprList) {
    if (methodCall.getMethodExpression().getQualifierExpression() != null) return;
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodCall.getMethodExpression(), method);
        if (qualifier == null) continue;
        info.accept(new QualifyMethodCallFix(methodCall, qualifier.getText()));
      }
    }
  }

  private static void registerStaticMethodQualifierFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                         @NotNull PsiMethodCallExpression methodCall) {
    info.accept(QuickFixFactory.getInstance().createStaticImportMethodFix(methodCall));
    info.accept(QuickFixFactory.getInstance().createQualifyStaticMethodCallFix(methodCall));
    info.accept(QuickFixFactory.getInstance().addMethodQualifierFix(methodCall));
  }

  private static void registerUsageFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                         @NotNull PsiMethodCallExpression methodCall) {
    QuickFixFactory.getInstance().createCreateMethodFromUsageFixes(methodCall).forEach(info);
  }

  private static void registerThisSuperFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                             @NotNull PsiMethodCallExpression methodCall) {
    QuickFixFactory.getInstance().createCreateConstructorFromCallExpressionFixes(methodCall).forEach(info);
  }

  static void registerMethodCallIntentions(@NotNull Consumer<? super CommonIntentionAction> info,
                                           @NotNull PsiMethodCallExpression methodCall,
                                           @NotNull PsiExpressionList list) {
    PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      PsiElement resolve = referenceExpression.resolve();
      if (resolve instanceof PsiClass psiClass &&
          psiClass.getContainingClass() != null &&
          !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
        List<IntentionAction> actions = JvmElementActionFactories.createModifierActions(
          psiClass, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true));
        actions.forEach(info);
      }
    }
    else if (qualifierExpression instanceof PsiSuperExpression superExpression && superExpression.getQualifier() == null) {
      QualifySuperArgumentFix.registerQuickFixAction(superExpression, info);
    }

    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(methodCall.getProject());
    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    IntentionAction action2 = QuickFixFactory.getInstance().createSurroundWithArrayFix(methodCall, null);
    info.accept(action2);

    CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, info);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, info);

    CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(candidates, methodCall, info);

    WrapWithAdapterMethodCallFix.registerCastActions(methodCandidates, methodCall, info);
    IntentionAction action1 = QuickFixFactory.getInstance().createReplaceAddAllArrayToCollectionFix(methodCall);
    info.accept(action1);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(methodCandidates, methodCall, info);
    MethodReturnFixFactory.INSTANCE.registerCastActions(methodCandidates, methodCall, info);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), info);
    QualifyThisArgumentFix.registerQuickFixAction(methodCandidates, methodCall, info);
    registerMethodAccessLevelIntentions(info, methodCandidates, methodCall, list);

    if (!PermuteArgumentsFix.registerFix(info, methodCall, methodCandidates) &&
        !MoveParenthesisFix.registerFix(info, methodCall, methodCandidates)) {
      registerChangeMethodSignatureFromUsageIntentions(info, methodCandidates, list);
    }

    QuickFixFactory.getInstance().getVariableTypeFromCallFixes(methodCall, list).forEach(info);

    if (methodCandidates.length == 0) {
      registerStaticMethodQualifierFixes(info, methodCall);
    }

    registerThisSuperFixes(info, methodCall);
    registerUsageFixes(info, methodCall);

    RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, info);
    info.accept(RemoveRepeatingCallFix.createFix(methodCall));
    registerChangeParameterClassFix(info, methodCall, list);
  }

  private static void registerMethodAccessLevelIntentions(@NotNull Consumer<? super CommonIntentionAction> info,
                                                          CandidateInfo @NotNull [] methodCandidates,
                                                          @NotNull PsiMethodCallExpression methodCall,
                                                          @NotNull PsiExpressionList exprList) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        registerAccessQuickFixAction(info, method, methodCall.getMethodExpression(), methodCandidate.getCurrentFileResolveScope());
      }
    }
  }

  static void registerChangeParameterClassFix(@NotNull Consumer<? super CommonIntentionAction> info,
                                              @NotNull PsiCall methodCall,
                                              @NotNull PsiExpressionList list) {
    JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != expressions.length) return;
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      PsiParameter parameter = parameters[i];
      PsiType expressionType = expression.getType();
      PsiType parameterType = substitutor.substitute(parameter.getType());
      if (expressionType == null ||
          expressionType instanceof PsiPrimitiveType ||
          TypeConversionUtil.isNullType(expressionType) ||
          expressionType instanceof PsiArrayType) {
        continue;
      }
      if (parameterType instanceof PsiPrimitiveType ||
          TypeConversionUtil.isNullType(parameterType) ||
          parameterType instanceof PsiArrayType) {
        continue;
      }
      if (parameterType.isAssignableFrom(expressionType)) continue;
      PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
      PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
      if (parameterClass == null || expressionClass == null) continue;
      if (expressionClass instanceof PsiAnonymousClass) continue;
      if (expressionClass.equals(parameterClass)) {
        // The class might be the same, but type arguments are incompatible
        continue;
      }
      if (expressionClass.isInheritor(parameterClass, true)) continue;
      IntentionAction action = QuickFixFactory.getInstance().createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType);
      info.accept(action);
    }
  }

  static void registerChangeMethodSignatureFromUsageIntentions(@NotNull Consumer<? super CommonIntentionAction> info,
                                                               JavaResolveResult @NotNull [] candidates,
                                                               @NotNull PsiExpressionList list) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerChangeMethodSignatureFromUsageIntention(info, expressions, candidate, list);
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntention(@NotNull Consumer<? super CommonIntentionAction> info,
                                                                      PsiExpression @NotNull [] expressions,
                                                                      @NotNull JavaResolveResult candidate,
                                                                      @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      IntentionAction fix = QuickFixFactory.getInstance()
        .createChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
      info.accept(fix);
      IntentionAction f2 =
        QuickFixFactory.getInstance()
          .createChangeMethodSignatureFromUsageReverseOrderFix(method, expressions, substitutor, context, false, 2);
      info.accept(f2);
    }
  }

  static void registerMethodReturnFixAction(@NotNull Consumer<? super CommonIntentionAction> info,
                                            @NotNull MethodCandidateInfo candidate,
                                            @NotNull PsiCall methodCall) {
    if (candidate.getInferenceErrorMessage() != null && methodCall.getParent() instanceof PsiReturnStatement) {
      PsiMethod containerMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiLambdaExpression.class);
      if (containerMethod != null) {
        PsiMethod method = candidate.getElement();
        PsiExpression methodCallCopy =
          JavaPsiFacade.getElementFactory(method.getProject()).createExpressionFromText(methodCall.getText(), methodCall);
        PsiType methodCallTypeByArgs = methodCallCopy.getType();
        //ensure type params are not included
        methodCallTypeByArgs = JavaPsiFacade.getElementFactory(method.getProject())
          .createRawSubstitutor(method).substitute(methodCallTypeByArgs);
        if (methodCallTypeByArgs != null) {
          info.accept(QuickFixFactory.getInstance().createMethodReturnFix(containerMethod, methodCallTypeByArgs, true));
        }
      }
    }
  }

  static MethodCandidateInfo @NotNull [] toMethodCandidates(JavaResolveResult @NotNull [] resolveResults) {
    return Arrays.stream(resolveResults)
      .filter(result -> result instanceof MethodCandidateInfo)
      .map(result -> (MethodCandidateInfo)result)
      .filter(CandidateInfo::isAccessible)
      .toArray(MethodCandidateInfo[]::new);
  }

  static void registerFixesOnInvalidConstructorCall(@NotNull Consumer<? super CommonIntentionAction> info,
                                                    @NotNull PsiConstructorCall constructorCall,
                                                    @NotNull PsiClass aClass,
                                                    JavaResolveResult @NotNull [] results) {
    ConstructorParametersFixer.registerFixActions(constructorCall, info);
    ChangeTypeArgumentsFix.registerIntentions(results, constructorCall, info, aClass);
    PsiMethod[] constructors = aClass.getConstructors();
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(constructors, constructorCall, info);
    IntentionAction action = QuickFixFactory.getInstance().createSurroundWithArrayFix(constructorCall, null);
    info.accept(action);
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return;
    if (!PermuteArgumentsFix.registerFix(info, constructorCall, toMethodCandidates(results))) {
      registerChangeMethodSignatureFromUsageIntentions(info, results, list);
    }
    QuickFixFactory.getInstance().createCreateConstructorFromUsageFixes(constructorCall).forEach(info);
    registerChangeParameterClassFix(info, constructorCall, list);
    RemoveRedundantArgumentsFix.registerIntentions(results, list, info);
  }

  static void registerTargetTypeFixesBasedOnApplicabilityInference(@NotNull Consumer<? super CommonIntentionAction> info,
                                                                   @NotNull PsiMethodCallExpression methodCall,
                                                                   @NotNull MethodCandidateInfo resolveResult,
                                                                   @NotNull PsiMethod resolved) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    PsiVariable variable = null;
    if (parent instanceof PsiVariable) {
      variable = (PsiVariable)parent;
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression referenceExpression) {
        PsiElement resolve = referenceExpression.resolve();
        if (resolve instanceof PsiVariable) {
          variable = (PsiVariable)resolve;
        }
      }
    }

    if (variable != null) {
      PsiType rType = methodCall.getType();
      if (rType != null && !variable.getType().isAssignableFrom(rType)) {
        PsiType expectedTypeByApplicabilityConstraints = resolveResult.getSubstitutor(false).substitute(resolved.getReturnType());
        if (expectedTypeByApplicabilityConstraints != null && !variable.getType().isAssignableFrom(expectedTypeByApplicabilityConstraints) &&
            PsiTypesUtil.allTypeParametersResolved(variable, expectedTypeByApplicabilityConstraints)) {
          registerChangeVariableTypeFixes(info, variable, expectedTypeByApplicabilityConstraints);
        }
      }
    }
  }

  static void registerCallInferenceFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                         @NotNull PsiMethodCallExpression callExpression) {
    JavaResolveResult result = callExpression.getMethodExpression().advancedResolve(true);
    if (!(result instanceof MethodCandidateInfo resolveResult)) return;
    PsiMethod method = resolveResult.getElement();
    registerMethodCallIntentions(info, callExpression, callExpression.getArgumentList());
    PsiType actualType = ((PsiExpression)callExpression.copy()).getType();
    if (!PsiTypesUtil.mentionsTypeParameters(actualType, Set.of(method.getTypeParameters()))) {
      registerMethodReturnFixAction(info, resolveResult, callExpression);
    }
    registerTargetTypeFixesBasedOnApplicabilityInference(info, callExpression, resolveResult, method);
  }

  static void registerImplementsExtendsFix(@NotNull Consumer<? super CommonIntentionAction> info,
                                           @NotNull PsiMethodCallExpression methodCall,
                                           @NotNull PsiMethod resolvedMethod) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(methodCall)) return;
    if (!resolvedMethod.isConstructor() || !resolvedMethod.getParameterList().isEmpty()) return;
    PsiClass psiClass = resolvedMethod.getContainingClass();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return;
    PsiClass containingClass = PsiUtil.getContainingClass(methodCall);
    if (containingClass == null) return;
    PsiReferenceList extendsList = containingClass.getExtendsList();
    if (extendsList != null && extendsList.getReferenceElements().length > 0) return;
    PsiReferenceList implementsList = containingClass.getImplementsList();
    if (implementsList == null) return;
    for (PsiClassType type : implementsList.getReferencedTypes()) {
      PsiClass superInterface = type.resolve();
      if (superInterface != null && !superInterface.isInterface()) {
        for (PsiMethod constructor : superInterface.getConstructors()) {
          if (!constructor.getParameterList().isEmpty()) {
            info.accept(QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(containingClass, type));
          }
        }
      }
    }
  }

  static void registerVariableParameterizedTypeFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                     @NotNull PsiVariable variable,
                                                     @NotNull PsiReferenceParameterList parameterList) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType classType)) return;

    if (DumbService.getInstance(variable.getProject()).isDumb()) return;

    String shortName = classType.getClassName();
    PsiFile psiFile = parameterList.getContainingFile();
    Project project = psiFile.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);
    PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(project));
    PsiElementFactory factory = facade.getElementFactory();
    for (PsiClass aClass : classes) {
      if (aClass == null) continue;
      if (isPotentiallyCompatible(aClass, parameterList)) {
        PsiType[] actualTypeParameters = parameterList.getTypeArguments();
        PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<>();
        for (int j = 0; j < Math.min(classTypeParameters.length, actualTypeParameters.length); j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        PsiType suggestedType = factory.createType(aClass, substitutor);
        registerChangeVariableTypeFixes(info, variable, suggestedType);
      }
    }
  }

  static @Nullable PsiType determineReturnType(@NotNull PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      PsiManager manager = method.getManager();
      PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
      if (returnStatements.length == 0) return CachedValueProvider.Result.create(PsiTypes.voidType(), method);
      PsiType expectedType = null;
      for (PsiReturnStatement returnStatement : returnStatements) {
        ReturnModel returnModel = ReturnModel.create(returnStatement);
        if (returnModel == null) return CachedValueProvider.Result.create(null, method);
        expectedType = lub(expectedType, returnModel.myLeastType, returnModel.myType, method, manager);
      }
      return CachedValueProvider.Result.create(expectedType, method);
    });
  }

  private static @NotNull PsiType lub(@Nullable PsiType currentType,
                                      @NotNull PsiType leastValueType,
                                      @NotNull PsiType valueType,
                                      @NotNull PsiMethod method,
                                      @NotNull PsiManager manager) {
    if (currentType == null || PsiTypes.voidType().equals(currentType)) return valueType;
    if (currentType == valueType) return currentType;

    if (TypeConversionUtil.isPrimitiveAndNotNull(valueType)) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(currentType)) {
        int r1 = TypeConversionUtil.getTypeRank(currentType);
        int r2 = TypeConversionUtil.getTypeRank(leastValueType);
        return r1 >= r2 ? currentType : valueType;
      }
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(currentType);
      if (valueType.equals(unboxedType)) return currentType;
      PsiClassType boxedType = ((PsiPrimitiveType)valueType).getBoxedType(method);
      if (boxedType == null) return valueType;
      valueType = boxedType;
    }

    if (TypeConversionUtil.isPrimitiveAndNotNull(currentType)) {
      currentType = ((PsiPrimitiveType)currentType).getBoxedType(method);
    }

    return requireNonNullElse(GenericsUtil.getLeastUpperBound(currentType, valueType, manager), requireNonNullElse(currentType, valueType));
  }

  static void registerAmbiguousCallFixes(@NotNull Consumer<? super @Nullable CommonIntentionAction> sink,
                                         @NotNull PsiMethodCallExpression methodCall,
                                         @NotNull JavaResolveResult @NotNull [] resolveResults) {
    PsiExpressionList list = methodCall.getArgumentList();
    MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, sink);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, sink);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, sink);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), sink);
    PermuteArgumentsFix.registerFix(sink, methodCall, candidates);
    registerChangeParameterClassFix(sink, methodCall, list);
    registerMethodCallIntentions(sink, methodCall, list);
  }

  static void registerIncompatibleTypeFixes(@NotNull Consumer<? super @Nullable CommonIntentionAction> sink,
                                            @NotNull PsiElement anchor,
                                            @NotNull PsiType lType, @Nullable PsiType rType) {
    QuickFixFactory factory = QuickFixFactory.getInstance();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (anchor instanceof PsiJavaCodeReferenceElement && parent instanceof PsiReferenceList &&
        parent.getParent() instanceof PsiMethod method && method.getThrowsList() == parent) {
      // Incompatible type in throws clause
      PsiClass usedClass = PsiUtil.resolveClassInClassTypeOnly(rType);
      if (usedClass != null && lType instanceof PsiClassType throwableType) {
        sink.accept(factory.createExtendsListFix(usedClass, throwableType, true));
      }
    }
    if (anchor instanceof PsiExpression expression) {
      AddTypeArgumentsConditionalFix.register(sink, expression, lType);
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(sink, expression, lType, rType);
      sink.accept(ChangeNewOperatorTypeFix.createFix(expression, lType));
      if (PsiTypes.booleanType().equals(lType) && expression instanceof PsiAssignmentExpression assignment &&
          assignment.getOperationTokenType() == JavaTokenType.EQ) {
        sink.accept(factory.createAssignmentToComparisonFix(assignment));
      }
      else if (expression instanceof PsiMethodCallExpression callExpression) {
        registerCallInferenceFixes(sink, callExpression);
      }
      if (parent instanceof PsiArrayInitializerExpression initializerList) {
        PsiType sameType = JavaHighlightUtil.sameType(initializerList.getInitializers());
        sink.accept(sameType == null ? null : VariableArrayTypeFix.createFix(initializerList, sameType));
      }
      else if (parent instanceof PsiConditionalExpression ternary) {
        PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(ternary.getThenExpression());
        PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(ternary.getElseExpression());
        PsiExpression otherSide = elseExpression == expression ? thenExpression : elseExpression;
        if (otherSide != null && !TypeConversionUtil.isVoidType(rType)) {
          if (TypeConversionUtil.isVoidType(otherSide.getType())) {
            registerIncompatibleTypeFixes(sink, ternary, lType, rType);
          } else {
            PsiExpression expressionCopy = PsiElementFactory.getInstance(expression.getProject())
              .createExpressionFromText(ternary.getText(), ternary);
            PsiType expectedType = expression.getType();
            PsiType actualType = expressionCopy.getType();
            if (expectedType != null && actualType != null && !expectedType.isAssignableFrom(actualType)) {
              registerIncompatibleTypeFixes(sink, ternary, expectedType, actualType);
            }
          }
        }
      }
      else if (parent instanceof PsiReturnStatement && rType != null && !PsiTypes.voidType().equals(rType)) {
        if (PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiMethod method) {
          sink.accept(factory.createMethodReturnFix(method, rType, true, true));
          PsiType expectedType = determineReturnType(method);
          if (expectedType != null && !PsiTypes.voidType().equals(expectedType) && !expectedType.equals(rType)) {
            sink.accept(factory.createMethodReturnFix(method, expectedType, true, true));
          }
        }
      }
      else if (parent instanceof PsiVariable var &&
               PsiUtil.skipParenthesizedExprDown(var.getInitializer()) == expression && rType != null) {
        registerChangeVariableTypeFixes(sink, var, rType);
      }
      else if (parent instanceof PsiAssignmentExpression assignment &&
               PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()) == expression) {
        PsiExpression lExpr = assignment.getLExpression();

        sink.accept(factory.createChangeToAppendFix(assignment.getOperationTokenType(), lType, assignment));
        if (rType != null) {
          registerChangeVariableTypeFixes(sink, lExpr, rType);
          if (expression instanceof PsiMethodCallExpression call && assignment.getParent() instanceof PsiStatement &&
              PsiTypes.voidType().equals(rType)) {
            sink.accept(new ReplaceAssignmentFromVoidWithStatementIntentionAction(assignment, call));
          }
        }
      }
    }
    if (anchor instanceof PsiParameter parameter && parent instanceof PsiForeachStatement forEach) {
      registerChangeVariableTypeFixes(sink, parameter, lType);
      PsiExpression iteratedValue = forEach.getIteratedValue();
      if (iteratedValue != null && rType != null) {
        PsiType type = iteratedValue.getType();
        if (type instanceof PsiArrayType) {
          AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(sink, iteratedValue, rType.createArrayType(), type);
        }
      }
    }
    registerChangeParameterClassFix(sink, lType, rType);
  }

  private static @NotNull LanguageLevel getApplicableLevel(@NotNull PsiFile psiFile, @NotNull JavaFeature feature) {
    LanguageLevel standardLevel = feature.getStandardLevel();
    LanguageLevel featureLevel = feature.getMinimumLevel();
    if (featureLevel.isPreview()) {
      JavaSdkVersion sdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(psiFile);
      if (sdkVersion != null) {
        if (standardLevel != null && sdkVersion.isAtLeast(JavaSdkVersion.fromLanguageLevel(standardLevel))) {
          return standardLevel;
        }
        LanguageLevel previewLevel = sdkVersion.getMaxLanguageLevel().getPreviewLevel();
        if (previewLevel != null && previewLevel.isAtLeast(featureLevel)) {
          return previewLevel;
        }
      }
    }
    return featureLevel;
  }

  public static @NotNull List<CommonIntentionAction> getIncreaseLanguageLevelFixes(
    @NotNull PsiElement element, @NotNull JavaFeature feature) {
    if (PsiUtil.isAvailable(feature, element)) return List.of();
    if (feature.isLimited()) return List.of(); //no reason for applying it because it can be outdated
    LanguageLevel applicableLevel = getApplicableLevel(element.getContainingFile(), feature);
    if (applicableLevel == LanguageLevel.JDK_X) return List.of(); // do not suggest to use experimental level
    QuickFixFactory factory = QuickFixFactory.getInstance();
    return List.of(factory.createIncreaseLanguageLevelFix(applicableLevel),
                   factory.createUpgradeSdkFor(applicableLevel),
                   factory.createShowModulePropertiesFix(element));
  }

  static void registerFixesOnInvalidSelector(@NotNull Consumer<? super @Nullable CommonIntentionAction> sink,
                                             @NotNull PsiExpression selector) {
    QuickFixFactory factory = QuickFixFactory.getInstance();
    if (selector.getParent() instanceof PsiSwitchStatement switchStatement) {
      sink.accept(factory.createConvertSwitchToIfIntention(switchStatement));
    }
    PsiType selectorType = selector.getType();
    if (PsiTypes.longType().equals(selectorType) ||
        PsiTypes.floatType().equals(selectorType) ||
        PsiTypes.doubleType().equals(selectorType)) {
      sink.accept(factory.createAddTypeCastFix(PsiTypes.intType(), selector));
      sink.accept(factory.createWrapWithAdapterFix(PsiTypes.intType(), selector));
    }
  }

  static @Nullable IntentionAction createPrimitiveToBoxedPatternFix(@NotNull PsiElement anchor) {
    PsiTypeElement element = null;
    PsiType operandType = null;
    if (anchor instanceof PsiInstanceOfExpression instanceOfExpression) {
      element = InstanceOfUtils.findCheckTypeElement(instanceOfExpression);
      operandType = instanceOfExpression.getOperand().getType();
    }
    else if (anchor instanceof PsiPattern pattern) {
      element = JavaPsiPatternUtil.getPatternTypeElement(pattern);
      PsiSwitchBlock block = PsiTreeUtil.getParentOfType(element, PsiSwitchBlock.class);
      if (block != null) {
        PsiExpression selector = block.getExpression();
        if (selector != null) {
          operandType = selector.getType();
        }
      }
    }
    if (element != null && operandType != null && TypeConversionUtil.isPrimitiveAndNotNull(element.getType())) {
      return QuickFixFactory.getInstance().createReplacePrimitiveWithBoxedTypeAction(operandType, requireNonNull(element));
    }
    return null;
  }

  /**
   * Checks if the specified element is possibly a reference to a static member of a class,
   * when the {@code new} keyword is removed.
   * The element is split into two parts: the qualifier and the reference element.
   * If they both exist and the qualifier references a class and the reference element text matches either
   * the name of a static field or the name of a static method of the class
   * then the method returns true
   *
   * @param element an element to examine
   * @return true if the new expression can actually be a call to a class member (field or method), false otherwise.
   */
  @Contract(value = "null -> false", pure = true)
  private static boolean isCallToStaticMember(@Nullable PsiElement element) {
    if (!(element instanceof PsiNewExpression newExpression)) return false;

    PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
    if (reference == null) return false;

    PsiElement qualifier = reference.getQualifier();
    PsiElement memberName = reference.getReferenceNameElement();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement referenceElement) || memberName == null) return false;

    PsiClass clazz = tryCast(referenceElement.resolve(), PsiClass.class);
    if (clazz == null) return false;

    if (newExpression.getArgumentList() == null) {
      PsiField field = clazz.findFieldByName(memberName.getText(), true);
      return field != null && field.hasModifierProperty(PsiModifier.STATIC);
    }
    PsiMethod[] methods = clazz.findMethodsByName(memberName.getText(), true);
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        if (!containingClass.isInterface() || containingClass == clazz) {
          // a static method in an interface is not resolvable from its subclasses
          return true;
        }
      }
    }
    return false;
  }

  static @Nullable CommonIntentionAction createUnresolvedReferenceFix(PsiJavaCodeReferenceElement psi) {
    return PsiTreeUtil.skipParentsOfType(psi, PsiJavaCodeReferenceElement.class) instanceof PsiNewExpression newExpression &&
           isCallToStaticMember(newExpression) ? new RemoveNewKeywordFix(newExpression) : null;
  }

  /**
   * @return true if type parameters of a class are potentially compatible with type arguments in the list
   * (that is: number of parameters is the same, and argument types are within bounds)
   */
  private static boolean isPotentiallyCompatible(@NotNull PsiClass psiClass, @NotNull PsiReferenceParameterList referenceParameterList) {
    PsiTypeElement[] referenceElements = referenceParameterList.getTypeParameterElements();

    PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
    int targetParametersNum = typeParameters.length;
    int refParametersNum = referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum) return false;

    // bounds check
    for (int i = 0; i < targetParametersNum; i++) {
      PsiType type = referenceElements[i].getType();
      if (ContainerUtil.exists(
        typeParameters[i].getSuperTypes(),
        bound -> !bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
                 GenericsUtil.checkNotInBounds(type, bound, referenceParameterList))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Registers actions to fix switch block completeness (exhaustiveness)
   *
   * @param info  sink
   * @param block switch block which is not exhaustive
   */
  static void addCompletenessFixes(@NotNull Consumer<? super CommonIntentionAction> info, @NotNull PsiSwitchBlock block) {
    List<? extends PsiCaseLabelElement> elements = JavaPsiSwitchUtil.getCaseLabelElements(block);
    PsiExpression selector = block.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
    JavaPsiSwitchUtil.SelectorKind kind = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType);
    if (selectorClass != null && kind == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      Set<PsiEnumConstant> enumElements = StreamEx.of(elements)
        .select(PsiReferenceExpression.class)
        .map(PsiReferenceExpression::resolve)
        .select(PsiEnumConstant.class)
        .toSet();
      addEnumCompletenessFixes(info, block, selectorClass, enumElements);
      return;
    }
    List<PsiType> sealedTypes = getAbstractSealedTypes(JavaPsiPatternUtil.deconstructSelectorType(selectorType));
    if (!sealedTypes.isEmpty()) {
      addSealedClassCompletenessFixes(info, block, selectorType, elements);
      return;
    }
    //records are final; checking intersections are not needed
    if (selectorClass != null && selectorClass.isRecord()) {
      if (checkRecordCaseSetNotEmpty(elements)) {
        addRecordExhaustivenessFixes(info, block, elements, selectorType, selectorClass);
      }
    }
    else {
      if (kind == JavaPsiSwitchUtil.SelectorKind.BOOLEAN) {
        QuickFixFactory factory = QuickFixFactory.getInstance();
        info.accept(factory.createAddMissingBooleanPrimitiveBranchesFix(block));
        info.accept(factory.createAddMissingBooleanPrimitiveBranchesFixWithNull(block));
      }
    }
  }

  private static void addEnumCompletenessFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                               @NotNull PsiSwitchBlock block,
                                               @NotNull PsiClass selectorClass,
                                               @NotNull Set<PsiEnumConstant> enumElements) {
    LinkedHashSet<String> missingConstants =
      StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).remove(enumElements::contains)
        .map(PsiField::getName)
        .toCollection(LinkedHashSet::new);
    if (!missingConstants.isEmpty()) {
      IntentionAction enumBranchesFix = QuickFixFactory.getInstance().createAddMissingEnumBranchesFix(block, missingConstants);
      info.accept(PriorityIntentionActionWrapper.highPriority(enumBranchesFix));
    }
  }

  private static @NotNull List<PsiType> getAbstractSealedTypes(@NotNull List<PsiType> selectorTypes) {
    return selectorTypes.stream()
      .filter(type -> {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
        return psiClass != null && (JavaPsiSealedUtil.isAbstractSealed(psiClass));
      })
      .toList();
  }

  private static void addRecordExhaustivenessFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                   @NotNull PsiSwitchBlock block,
                                                   @NotNull List<? extends PsiCaseLabelElement> elements,
                                                   @NotNull PsiType selectorClassType,
                                                   @NotNull PsiClass selectorClass) {
    JavaPatternExhaustivenessUtil.RecordExhaustivenessResult
      exhaustivenessResult = checkRecordExhaustiveness(elements, selectorClassType, block);

    if (!exhaustivenessResult.isExhaustive() && exhaustivenessResult.canBeAdded()) {
      IntentionAction fix =
        QuickFixFactory.getInstance().createAddMissingRecordClassBranchesFix(
          block, selectorClass, exhaustivenessResult.getMissedBranchesByType(), elements);
      info.accept(fix);
    }
  }

  private static boolean checkRecordCaseSetNotEmpty(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiPattern pattern && !JavaPsiPatternUtil.isGuarded(pattern));
  }

  private static @NotNull Set<PsiClass> getMissedClassesInSealedHierarchy(@NotNull PsiType selectorType,
                                                                          @NotNull List<? extends PsiCaseLabelElement> elements,
                                                                          @NotNull PsiSwitchBlock block) {
    Set<PsiClass> classes = findMissedClasses(block, selectorType, elements);
    List<PsiClass> missedSealedClasses = StreamEx.of(classes).sortedBy(t -> t.getQualifiedName()).toList();
    Set<PsiClass> missedClasses = new LinkedHashSet<>();
    //if T is intersection types, it is allowed to choose any of them to cover
    PsiExpression selector = requireNonNull(block.getExpression());
    for (PsiClass missedClass : missedSealedClasses) {
      PsiClassType missedClassType = TypeUtils.getType(missedClass);
      if (JavaPsiPatternUtil.covers(selector, missedClassType, selectorType)) {
        missedClasses.clear();
        missedClasses.add(missedClass);
        break;
      }
      else {
        missedClasses.add(missedClass);
      }
    }
    return missedClasses;
  }

  private static void addSealedClassCompletenessFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                      @NotNull PsiSwitchBlock block, @NotNull PsiType selectorType,
                                                      @NotNull List<? extends PsiCaseLabelElement> elements) {
    Set<PsiClass> missedClasses = getMissedClassesInSealedHierarchy(selectorType, elements, block);
    List<String> allNames = collectLabelElementNames(elements, missedClasses);
    Set<String> missingCases = ContainerUtil.map2LinkedSet(missedClasses, PsiClass::getQualifiedName);
    QuickFixFactory factory = QuickFixFactory.getInstance();
    info.accept(factory.createAddMissingSealedClassBranchesFix(block, missingCases, allNames));
    info.accept(factory.createAddMissingSealedClassBranchesFixWithNull(block, missingCases, allNames));
  }

  private static @NotNull List<String> collectLabelElementNames(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                                @NotNull Set<? extends PsiClass> missingClasses) {
    return StreamEx.of(elements).map(PsiElement::getText)
      .append(StreamEx.of(missingClasses).map(PsiClass::getQualifiedName))
      .distinct()
      .toList();
  }

  private static final class ReturnModel {
    final PsiReturnStatement myStatement;
    final PsiType myType;
    final PsiType myLeastType;

    @Contract(pure = true)
    private ReturnModel(@NotNull PsiReturnStatement statement, @NotNull PsiType type) {
      myStatement = statement;
      myType = myLeastType = type;
    }

    @Contract(pure = true)
    private ReturnModel(@NotNull PsiReturnStatement statement, @NotNull PsiType type, @NotNull PsiType leastType) {
      myStatement = statement;
      myType = type;
      myLeastType = leastType;
    }

    private static @Nullable ReturnModel create(@NotNull PsiReturnStatement statement) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) return new ReturnModel(statement, PsiTypes.voidType());
      if (ExpressionUtils.nonStructuralChildren(value).anyMatch(c -> c instanceof PsiFunctionalExpression)) return null;
      PsiType type = RefactoringChangeUtil.getTypeByExpression(value);
      if (type == null || type instanceof PsiClassType classType && classType.resolve() == null) return null;
      return new ReturnModel(statement, type, getLeastValueType(value, type));
    }

    private static @NotNull PsiType getLeastValueType(@NotNull PsiExpression returnValue, @NotNull PsiType type) {
      if (type instanceof PsiPrimitiveType) {
        int rank = TypeConversionUtil.getTypeRank(type);
        if (rank < TypeConversionUtil.BYTE_RANK || rank > TypeConversionUtil.INT_RANK) return type;
        PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(returnValue.getProject()).getConstantEvaluationHelper();
        Object res = evaluator.computeConstantExpression(returnValue);
        if (res instanceof Number number) {
          long value = number.longValue();
          if (-128 <= value && value <= 127) return PsiTypes.byteType();
          if (-32768 <= value && value <= 32767) return PsiTypes.shortType();
          if (0 <= value && value <= 0xFFFF) return PsiTypes.charType();
        }
      }
      return type;
    }
  }
}