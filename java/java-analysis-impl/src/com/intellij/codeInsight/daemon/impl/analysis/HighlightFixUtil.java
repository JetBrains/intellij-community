// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QualifyMethodCallFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceAssignmentFromVoidWithStatementIntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceGetClassWithClassLiteralFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HighlightFixUtil {
  private static final Logger LOG = Logger.getInstance(HighlightFixUtil.class);

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private static final CallMatcher COLLECTION_TO_ARRAY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterCount(0);

  static void registerCollectionToArrayFixAction(@NotNull HighlightInfo.Builder info,
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
            IntentionAction action = QUICK_FIX_FACTORY.createCollectionToArrayFix(collection, expression, (PsiArrayType)toType);
            info.registerFix(action, null, null, null, null);
          }
        }
      }
    }
  }

  /**
   * Make element protected/package-private/public suggestion.
   * For private method in the interface it should add default modifier as well.
   */
  static void registerAccessQuickFixAction(@Nullable HighlightInfo.Builder info,
                                           @NotNull TextRange fixRange,
                                           @NotNull PsiJvmMember refElement,
                                           @NotNull PsiJavaCodeReferenceElement place,
                                           @Nullable PsiElement fileResolveScope,
                                           @Nullable TextRange parentFixRange) {
    if (info == null) return;
    PsiClass accessObjectClass = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
    }
    registerReplaceInaccessibleFieldWithGetterSetterFix(info, refElement, place, accessObjectClass, parentFixRange);

    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions(packageLocalClassInTheMiddle, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
      QuickFixAction.registerQuickFixActions(info, parentFixRange, fixes);
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
        @PsiUtil.AccessLevel
        int level = accessLevels[i];
        modifierListCopy.setModifierProperty(PsiUtil.getAccessModifier(level), true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          List<IntentionAction> fixes = JvmElementActionFactories
            .createModifierActions(refElement, MemberRequestsKt.modifierRequest(JvmUtil.getAccessModifier(level), true));
          PsiElement ref = place.getReferenceNameElement();
          QuickFixAction.registerQuickFixActions(info, ref == null ? fixRange : fixRange.union(ref.getTextRange()), fixes);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  static PsiClass getPackageLocalClassInTheMiddle(@NotNull PsiElement place) {
    if (place instanceof PsiReferenceExpression expression) {
      // check for package-private classes in the middle
      while (true) {
        if (expression.resolve() instanceof PsiField field) {
          PsiClass aClass = field.getContainingClass();
          if (aClass != null && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, place)) {
            return aClass;
          }
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) break;
        expression = (PsiReferenceExpression)qualifier;
      }
    }
    return null;
  }

  static void registerChangeVariableTypeFixes(@NotNull PsiExpression expression,
                                              @NotNull PsiType type,
                                              @Nullable PsiExpression lExpr,
                                              @Nullable HighlightInfo.Builder highlightInfo) {
    if (highlightInfo == null || !(expression instanceof  PsiReferenceExpression)) return;

    PsiElement element = ((PsiReferenceExpression)expression).resolve();
    if (!(element instanceof PsiVariable)) return;

    registerChangeVariableTypeFixes((PsiVariable)element, type, lExpr, highlightInfo);

    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(lExpr);
    if (stripped instanceof PsiMethodCallExpression && lExpr.getParent() instanceof PsiAssignmentExpression) {
      PsiElement parent = lExpr.getParent();
      if (parent.getParent() instanceof PsiStatement) {
        PsiMethod method = ((PsiMethodCallExpression)stripped).resolveMethod();
        if (method != null && PsiTypes.voidType().equals(method.getReturnType())) {
          highlightInfo.registerFix(new ReplaceAssignmentFromVoidWithStatementIntentionAction(parent, stripped), null, null, null, null);
        }
      }
    }
  }

  static void registerUnhandledExceptionFixes(@NotNull PsiElement element, @Nullable HighlightInfo.Builder info) {
    IntentionAction action4 = QUICK_FIX_FACTORY.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element);
    if (info != null) {
      info.registerFix(action4, null, null, null, null);
    }
    IntentionAction action3 = QUICK_FIX_FACTORY.createAddExceptionToCatchFix();
    if (info != null) {
      info.registerFix(action3, null, null, null, null);
    }
    IntentionAction action2 = QUICK_FIX_FACTORY.createAddExceptionToExistingCatch(element);
    if (info != null) {
      info.registerFix(action2, null, null, null, null);
    }
    IntentionAction action1 = QUICK_FIX_FACTORY.createAddExceptionToThrowsFix(element);
    if (info != null) {
      info.registerFix(action1, null, null, null, null);
    }
    IntentionAction action = QUICK_FIX_FACTORY.createSurroundWithTryCatchFix(element);
    if (info != null) {
      info.registerFix(action, null, null, null, null);
    }
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
      IntentionAction action = QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false);
      if (info != null) {
        info.registerFix(action, null, null, null, null);
      }
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      IntentionAction action = QUICK_FIX_FACTORY.createCreateFieldFromUsageFix((PsiReferenceExpression)place);
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
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, null, null);
      }
    }
    registerChangeReturnTypeFix(highlightInfo, expr, parameter.getType());
  }

  static void registerChangeReturnTypeFix(@Nullable HighlightInfo.Builder highlightInfo, @Nullable PsiExpression expr, @NotNull PsiType toType) {
    if (expr instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null) {
        IntentionAction action = PriorityIntentionActionWrapper
          .lowPriority(QUICK_FIX_FACTORY.createMethodReturnFix(method, toType, true));
        if (highlightInfo != null) {
          highlightInfo.registerFix(action, null, null, null, null);
        }
      }
    }
  }

  /**
   * @param variable variable to create change type fixes for
   * @param itemType a desired variable type
   * @return a list of created fix actions
   */
  @NotNull
  public static List<IntentionAction> getChangeVariableTypeFixes(@NotNull PsiVariable variable, @Nullable PsiType itemType) {
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

  @Nullable
  private static IntentionAction getChangeParameterClassFix(@NotNull PsiType lType, @NotNull PsiType rType) {
    PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

    if (rClass == null || lClass == null) return null;
    if (rClass instanceof PsiAnonymousClass) return null;
    if (rClass.isInheritor(lClass, true)) return null;
    if (lClass.isInheritor(rClass, true)) return null;
    if (lClass == rClass) return null;

    return QUICK_FIX_FACTORY.createChangeParameterClassFix(rClass, (PsiClassType)lType);
  }

  private static void registerReplaceInaccessibleFieldWithGetterSetterFix(@NotNull HighlightInfo.Builder builder,
                                                                          @NotNull PsiMember refElement,
                                                                          @NotNull PsiJavaCodeReferenceElement place,
                                                                          @Nullable PsiClass accessObjectClass,
                                                                          @Nullable TextRange parentFixRange) {
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
              IntentionAction action = QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(ref, setter, true);
              builder.registerFix(action, null, null, parentFixRange, null);
            }
          }
        }
        else if (PsiUtil.isAccessedForReading(ref)) {
          PsiMethod getterPrototype = PropertyUtilBase.generateGetterPrototype(psiField);
          PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, ref, accessObjectClass)) {
            IntentionAction action = QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(ref, getter, false);
            builder.registerFix(action, null, null, parentFixRange, null);
          }
        }
      }
    }
  }

  static void registerLambdaReturnTypeFixes(@Nullable HighlightInfo.Builder info, @NotNull TextRange range, PsiLambdaExpression lambda, PsiExpression expression) {
    if (info == null) return;
    PsiType type = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    if (type != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(info, range, expression, type);
    }
  }

  static void registerChangeParameterClassFix(@NotNull PsiType lType, @NotNull PsiType rType, @Nullable HighlightInfo.Builder info) {
    IntentionAction action = getChangeParameterClassFix(lType, rType);
    if (info != null && action != null) {
      info.registerFix(action, null, null, null, null);
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

  @Nullable
  static IntentionAction createInsertSwitchDefaultFix(@NotNull PsiVariable variable,
                                                      @NotNull PsiElement topBlock,
                                                      @NotNull PsiElement readPoint) {
    PsiSwitchStatement switchStatement = findInitializingSwitch(variable, topBlock, readPoint);
    if (switchStatement != null) {
      String message = QuickFixBundle.message("add.default.branch.to.variable.initializing.switch.fix.name", variable.getName());
      return QUICK_FIX_FACTORY.createAddSwitchDefaultFix(switchStatement, message);
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
      IntentionAction action = QUICK_FIX_FACTORY.createModifierListFix(var, PsiModifier.FINAL, false, false);
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, null, null);
      }
    }
  }

  public static void registerFixesForExpressionStatement(@NotNull PsiStatement statement, @NotNull List<? super IntentionAction> registrar) {
    if (!(statement instanceof PsiExpressionStatement)) return;
    PsiCodeBlock block = ObjectUtils.tryCast(statement.getParent(), PsiCodeBlock.class);
    if (block == null) return;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (expression instanceof PsiAssignmentExpression) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (!type.equals(PsiTypes.voidType())) {
      IntentionAction action = PriorityIntentionActionWrapper.highPriority(QUICK_FIX_FACTORY.createIterateFix(expression));
      registrar.add(action);
    }
    if (PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) == block.getRBrace()) {
      PsiElement blockParent = block.getParent();
      if (blockParent instanceof PsiMethod) {
        PsiType returnType = ((PsiMethod)blockParent).getReturnType();
        if (returnType != null && isPossibleReturnValue(expression, type, returnType)) {
          IntentionAction action = QUICK_FIX_FACTORY.createInsertReturnFix(expression);
          registrar.add(action);
        }
      }
    }
    if (!type.equals(PsiTypes.voidType())) {
      IntentionAction action = QUICK_FIX_FACTORY.createIntroduceVariableAction(expression);
      registrar.add(action);
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
                  IntentionAction fix = QUICK_FIX_FACTORY.createSetVariableTypeFix(variable, type);
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
                                           @Nullable HighlightInfo.Builder highlightInfo) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodCall.getMethodExpression(), method);
        if (qualifier == null) continue;
        var fix = new QualifyMethodCallFix(methodCall, qualifier.getText());
        TextRange fixRange = HighlightMethodUtil.getFixRange(methodCall);
        if (highlightInfo != null) {
          highlightInfo.registerFix(fix, null, null, fixRange, null);
        }
      }
    }
  }
}