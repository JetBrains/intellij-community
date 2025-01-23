// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

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
    registerReplaceInaccessibleFieldWithGetterSetterFix(info, refElement, place, accessObjectClass);

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

  static void registerChangeVariableTypeFixes(@NotNull PsiExpression expression,
                                              @NotNull PsiType type,
                                              @Nullable PsiExpression lExpr,
                                              @NotNull Consumer<? super CommonIntentionAction> info) {
    if (!(expression instanceof PsiReferenceExpression ref)) return;
    if (!(ref.resolve() instanceof PsiVariable variable)) return;

    registerChangeVariableTypeFixes(variable, type, lExpr, info);

    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(lExpr);
    if (stripped instanceof PsiMethodCallExpression call && lExpr.getParent() instanceof PsiAssignmentExpression assignment) {
      if (assignment.getParent() instanceof PsiStatement) {
        PsiMethod method = call.resolveMethod();
        if (method != null && PsiTypes.voidType().equals(method.getReturnType())) {
          info.accept(new ReplaceAssignmentFromVoidWithStatementIntentionAction(assignment, stripped));
        }
      }
    }
  }

  static void registerUnhandledExceptionFixes(@NotNull PsiElement element, @NotNull Consumer<? super CommonIntentionAction> info) {
    final QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    info.accept(quickFixFactory.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    info.accept(quickFixFactory.createAddExceptionToCatchFix());
    info.accept(quickFixFactory.createAddExceptionToExistingCatch(element));
    info.accept(quickFixFactory.createAddExceptionToThrowsFix(element));
    info.accept(quickFixFactory.createSurroundWithTryCatchFix(element));
  }

  static void registerStaticProblemQuickFixAction(@Nullable HighlightInfo.Builder info, @NotNull PsiElement refElement, @NotNull PsiJavaCodeReferenceElement place) {
    if (place instanceof PsiReferenceExpression && place.getParent() instanceof PsiMethodCallExpression) {
      ReplaceGetClassWithClassLiteralFix.registerFix((PsiMethodCallExpression)place.getParent(), info);
    }
    if (refElement instanceof PsiJvmModifiersOwner) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions((PsiJvmModifiersOwner)refElement, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true));
      QuickFixAction.registerQuickFixActions(info, null, fixes);
    }
    // make context non-static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(staticParent, PsiModifier.STATIC, false, false);
      if (info != null) {
        info.registerFix(action, null, null, null, null);
      }
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      IntentionAction action = QuickFixFactory.getInstance().createCreateFieldFromUsageFix((PsiReferenceExpression)place);
      if (info != null) {
        info.registerFix(action, null, null, null, null);
      }
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

  static void registerChangeVariableTypeFixes(@NotNull PsiVariable parameter,
                                              @Nullable PsiType itemType,
                                              @Nullable PsiExpression expr,
                                              @Nullable HighlightInfo.Builder highlightInfo) {
    registerChangeVariableTypeFixes(parameter, itemType, expr, HighlightUtil.asConsumer(highlightInfo));
  }

  static void registerChangeVariableTypeFixes(@NotNull PsiVariable parameter,
                                              @Nullable PsiType itemType,
                                              @Nullable PsiExpression expr,
                                              @NotNull Consumer<? super CommonIntentionAction> info) {
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      info.accept(action);
    }
    IntentionAction fix = createChangeReturnTypeFix(expr, parameter.getType());
    if (fix != null) {
      info.accept(fix);
    }
  }

  static @Nullable IntentionAction createChangeReturnTypeFix(@Nullable PsiExpression expr, @NotNull PsiType toType) {
    if (expr instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
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

  private static void registerReplaceInaccessibleFieldWithGetterSetterFix(@NotNull Consumer<? super CommonIntentionAction> info,
                                                                          @NotNull PsiMember refElement,
                                                                          @NotNull PsiJavaCodeReferenceElement place,
                                                                          @Nullable PsiClass accessObjectClass) {
    if (refElement instanceof PsiField psiField && place instanceof PsiReferenceExpression ref) {
      if (PsiTypes.nullType().equals(psiField.getType())) return;
      PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.isOnAssignmentLeftHand(ref)) {
          PsiMethod setterPrototype = PropertyUtilBase.generateSetterPrototype(psiField);
          PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
          if (setter != null && PsiUtil.isAccessible(setter, ref, accessObjectClass)) {
            PsiElement element = PsiTreeUtil.skipParentsOfType(ref, PsiParenthesizedExpression.class);
            if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)element).getOperationTokenType() == JavaTokenType.EQ) {
              IntentionAction action = QuickFixFactory.getInstance().createReplaceInaccessibleFieldWithGetterSetterFix(ref, setter, true);
              info.accept(action);
            }
          }
        }
        else if (PsiUtil.isAccessedForReading(ref)) {
          PsiMethod getterPrototype = PropertyUtilBase.generateGetterPrototype(psiField);
          PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, ref, accessObjectClass)) {
            IntentionAction action = QuickFixFactory.getInstance().createReplaceInaccessibleFieldWithGetterSetterFix(ref, getter, false);
            info.accept(action);
          }
        }
      }
    }
  }

  static void registerLambdaReturnTypeFixes(@NotNull Consumer<? super CommonIntentionAction> info, PsiLambdaExpression lambda, PsiExpression expression) {
    PsiType type = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    if (type != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(info, expression, type);
    }
  }

  static void registerChangeParameterClassFix(@NotNull PsiType lType,
                                              @NotNull PsiType rType,
                                              @NotNull Consumer<? super CommonIntentionAction> info) {
    IntentionAction action = getChangeParameterClassFix(lType, rType);
    if (action != null) {
      info.accept(action);
    }
  }

  private static PsiSwitchStatement findInitializingSwitch(@NotNull PsiVariable variable,
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

  static void registerMakeNotFinalAction(@NotNull PsiVariable var, @Nullable HighlightInfo.Builder highlightInfo) {
    if (var instanceof PsiField) {
      QuickFixAction.registerQuickFixActions(
        highlightInfo, null,
        JvmElementActionFactories.createModifierActions((PsiField)var, MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false))
      );
    }
    else {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(var, PsiModifier.FINAL, false, false);
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, null, null);
      }
    }
  }

  public static void registerFixesForExpressionStatement(@NotNull PsiElement statement, @NotNull List<? super IntentionAction> registrar) {
    if (!(statement instanceof PsiExpressionStatement)) return;
    PsiCodeBlock block = ObjectUtils.tryCast(statement.getParent(), PsiCodeBlock.class);
    if (block == null) return;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (expression instanceof PsiAssignmentExpression) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (!type.equals(PsiTypes.voidType())) {
      registrar.add(PriorityIntentionActionWrapper.highPriority(QuickFixFactory.getInstance().createIterateFix(expression)));
      if (PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) == block.getRBrace() && block.getParent() instanceof PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType != null && isPossibleReturnValue(expression, type, returnType)) {
          registrar.add(QuickFixFactory.getInstance().createInsertReturnFix(expression));
        }
      }
      registrar.add(QuickFixFactory.getInstance().createIntroduceVariableAction(expression));
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

  static void registerSpecifyVarTypeFix(@NotNull PsiLocalVariable variable, @NotNull HighlightInfo.Builder info) {
    PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
    if (block == null) return;
    PsiTreeUtil.processElements(block, PsiReferenceExpression.class, ref -> {
      if (ref.isReferenceTo(variable)) {
        PsiAssignmentExpression assignment = ObjectUtils.tryCast(ref.getParent(), PsiAssignmentExpression.class);
        if (assignment != null) {
          if (assignment.getLExpression() == ref &&
              assignment.getOperationTokenType() == JavaTokenType.EQ) {
            PsiExpression rExpression = assignment.getRExpression();
            if (rExpression != null) {
              PsiType type = rExpression.getType();
              if (type instanceof PsiPrimitiveType && !PsiTypes.voidType().equals(type) && variable.getInitializer() != null) {
                type = ((PsiPrimitiveType)type).getBoxedType(variable);
              }
              if (type != null) {
                type = GenericsUtil.getVariableTypeByExpressionType(type);
                if (PsiTypesUtil.isDenotableType(type, variable) && !PsiTypes.voidType().equals(type)) {
                  IntentionAction fix = QuickFixFactory.getInstance().createSetVariableTypeFix(variable, type);
                  info.registerFix(fix, null, null, null, null);
                }
                return false;
              }
            }
          }
        }
      }
      return true;
    });
  }

  static void registerQualifyMethodCallFix(CandidateInfo @NotNull [] methodCandidates,
                                           @NotNull PsiMethodCallExpression methodCall,
                                           @NotNull PsiExpressionList exprList,
                                           @NotNull Consumer<? super CommonIntentionAction> info) {
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

  private static void registerStaticMethodQualifierFixes(@NotNull PsiMethodCallExpression methodCall,
                                                         @NotNull Consumer<? super CommonIntentionAction> info) {
    info.accept(QuickFixFactory.getInstance().createStaticImportMethodFix(methodCall));
    info.accept(QuickFixFactory.getInstance().createQualifyStaticMethodCallFix(methodCall));
    info.accept(QuickFixFactory.getInstance().addMethodQualifierFix(methodCall));
  }

  private static void registerUsageFixes(@NotNull PsiMethodCallExpression methodCall,
                                         @NotNull Consumer<? super CommonIntentionAction> info) {
    QuickFixFactory.getInstance().createCreateMethodFromUsageFixes(methodCall).forEach(info);
  }

  private static void registerThisSuperFixes(@NotNull PsiMethodCallExpression methodCall,
                                             @NotNull Consumer<? super CommonIntentionAction> info) {
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
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, info);

    if (!PermuteArgumentsFix.registerFix(info, methodCall, methodCandidates) &&
        !MoveParenthesisFix.registerFix(info, methodCall, methodCandidates)) {
      registerChangeMethodSignatureFromUsageIntentions(methodCandidates, list, info);
    }

    QuickFixFactory.getInstance().getVariableTypeFromCallFixes(methodCall, list).forEach(info);

    if (methodCandidates.length == 0) {
      registerStaticMethodQualifierFixes(methodCall, info);
    }

    registerThisSuperFixes(methodCall, info);
    registerUsageFixes(methodCall, info);

    RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, info);
    registerChangeParameterClassFix(methodCall, list, info);
  }

  private static void registerMethodAccessLevelIntentions(CandidateInfo @NotNull [] methodCandidates,
                                                          @NotNull PsiMethodCallExpression methodCall,
                                                          @NotNull PsiExpressionList exprList,
                                                          @NotNull Consumer<? super CommonIntentionAction> info) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        registerAccessQuickFixAction(info, method, methodCall.getMethodExpression(), methodCandidate.getCurrentFileResolveScope());
      }
    }
  }

  static void registerChangeParameterClassFix(@NotNull PsiCall methodCall,
                                              @NotNull PsiExpressionList list,
                                              @NotNull Consumer<? super CommonIntentionAction> info) {
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
      if (expressionClass.isInheritor(parameterClass, true)) continue;
      IntentionAction action = QuickFixFactory.getInstance().createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType);
      info.accept(action);
    }
  }

  static void registerChangeMethodSignatureFromUsageIntentions(JavaResolveResult @NotNull [] candidates,
                                                               @NotNull PsiExpressionList list,
                                                               @NotNull Consumer<? super CommonIntentionAction> info) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerChangeMethodSignatureFromUsageIntention(expressions, info, candidate, list);
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntention(PsiExpression @NotNull [] expressions,
                                                                      @NotNull Consumer<? super CommonIntentionAction> info,
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
    List<MethodCandidateInfo> candidateList = new ArrayList<>(resolveResults.length);
    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo candidate)) continue;
      if (candidate.isAccessible()) candidateList.add(candidate);
    }
    return candidateList.toArray(new MethodCandidateInfo[0]);
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
      registerChangeMethodSignatureFromUsageIntentions(results, list, info);
    }
    QuickFixFactory.getInstance().createCreateConstructorFromUsageFixes(constructorCall).forEach(info);
    registerChangeParameterClassFix(constructorCall, list, info);
    RemoveRedundantArgumentsFix.registerIntentions(results, list, info);
  }

  static void registerTargetTypeFixesBasedOnApplicabilityInference(@NotNull PsiMethodCallExpression methodCall,
                                                                   @NotNull MethodCandidateInfo resolveResult,
                                                                   @NotNull PsiMethod resolved,
                                                                   @NotNull Consumer<? super CommonIntentionAction> info) {
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
          registerChangeVariableTypeFixes(variable, expectedTypeByApplicabilityConstraints, methodCall, info);
        }
      }
    }
  }

  static void registerCallInferenceFixes(@NotNull PsiMethodCallExpression callExpression, @NotNull Consumer<? super CommonIntentionAction> info) {
    JavaResolveResult result = callExpression.getMethodExpression().advancedResolve(true);
    if (!(result instanceof MethodCandidateInfo resolveResult)) return;
    PsiMethod method = resolveResult.getElement();
    registerMethodCallIntentions(info, callExpression, callExpression.getArgumentList());
    PsiType actualType = ((PsiExpression)callExpression.copy()).getType();
    if (!PsiTypesUtil.mentionsTypeParameters(actualType, Set.of(method.getTypeParameters()))) {
      registerMethodReturnFixAction(info, resolveResult, callExpression);
    }
    registerTargetTypeFixesBasedOnApplicabilityInference(callExpression, resolveResult, method, info);
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
    PsiFile file = parameterList.getContainingFile();
    Project project = file.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);
    PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(project));
    PsiElementFactory factory = facade.getElementFactory();
    JavaSdkVersion version = Objects.requireNonNullElse(JavaVersionService.getInstance().getJavaSdkVersion(file),
                                                        JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(file)));
    for (PsiClass aClass : classes) {
      if (aClass == null) {
        continue;
      }
      if (GenericsHighlightUtil.checkReferenceTypeArgumentList(aClass, parameterList, PsiSubstitutor.EMPTY, false, version) == null) {
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
        registerChangeVariableTypeFixes(variable, suggestedType, variable.getInitializer(), info);
      }
    }
  }
}