// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
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
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
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

  static void registerCollectionToArrayFixAction(@Nullable HighlightInfo info,
                                                 @Nullable PsiType fromType,
                                                 @Nullable PsiType toType,
                                                 @NotNull PsiExpression expression) {
    if (toType instanceof PsiArrayType) {
      PsiType arrayComponentType = ((PsiArrayType)toType).getComponentType();
      if (!(arrayComponentType instanceof PsiPrimitiveType) &&
          !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter)) {
        PsiExpression collection = expression;
        if (expression instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
          if (COLLECTION_TO_ARRAY.test(call)) {
            collection = call.getMethodExpression().getQualifierExpression();
            if (collection == null) return;
            fromType = collection.getType();
          }
        }
        if (fromType instanceof PsiClassType &&
            (CommonClassNames.JAVA_LANG_OBJECT.equals(arrayComponentType.getCanonicalText()) || !((PsiClassType)fromType).isRaw()) &&
            InheritanceUtil.isInheritor(fromType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(fromType, expression.getResolveScope());
          if (collectionItemType != null && arrayComponentType.isConvertibleFrom(collectionItemType)) {
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createCollectionToArrayFix(collection, expression, (PsiArrayType)toType));
          }
        }
      }
    }
  }

  /**
   * Make element protected/package-private/public suggestion.
   * For private method in the interface it should add default modifier as well.
   */
  static void registerAccessQuickFixAction(@NotNull PsiJvmMember refElement,
                                           @NotNull PsiJavaCodeReferenceElement place,
                                           @Nullable HighlightInfo errorResult,
                                           PsiElement fileResolveScope,
                                           TextRange parentFixRange) {
    if (errorResult == null) return;
    PsiClass accessObjectClass = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
    }
    registerReplaceInaccessibleFieldWithGetterSetterFix(refElement, place, accessObjectClass, errorResult, parentFixRange);

    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions(packageLocalClassInTheMiddle, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
      QuickFixAction.registerQuickFixActions(errorResult, parentFixRange, fixes);
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
          TextRange fixRange = new TextRange(errorResult.startOffset, errorResult.endOffset);
          PsiElement ref = place.getReferenceNameElement();
          if (ref != null) {
            fixRange = fixRange.union(ref.getTextRange());
          }
          QuickFixAction.registerQuickFixActions(errorResult, fixRange, fixes);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  static PsiClass getPackageLocalClassInTheMiddle(@NotNull PsiElement place) {
    if (place instanceof PsiReferenceExpression) {
      // check for package-private classes in the middle
      PsiReferenceExpression expression = (PsiReferenceExpression)place;
      while (true) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
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
                                              @Nullable HighlightInfo highlightInfo) {
    if (highlightInfo == null || !(expression instanceof  PsiReferenceExpression)) return;

    PsiElement element = ((PsiReferenceExpression)expression).resolve();
    if (!(element instanceof PsiVariable)) return;

    registerChangeVariableTypeFixes((PsiVariable)element, type, lExpr, highlightInfo);

    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(lExpr);
    if (stripped instanceof PsiMethodCallExpression && lExpr.getParent() instanceof PsiAssignmentExpression) {
      PsiElement parent = lExpr.getParent();
      if (parent.getParent() instanceof PsiStatement) {
        PsiMethod method = ((PsiMethodCallExpression)stripped).resolveMethod();
        if (method != null && PsiType.VOID.equals(method.getReturnType())) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new ReplaceAssignmentFromVoidWithStatementIntentionAction(parent, stripped));
        }
      }
    }
  }

  static void registerUnhandledExceptionFixes(PsiElement element, HighlightInfo errorResult) {
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToCatchFix());
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToExistingCatch(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSurroundWithTryCatchFix(element));
  }

  static void registerStaticProblemQuickFixAction(@NotNull PsiElement refElement, HighlightInfo errorResult, @NotNull PsiJavaCodeReferenceElement place) {
    if (place instanceof PsiReferenceExpression && place.getParent() instanceof PsiMethodCallExpression) {
      ReplaceGetClassWithClassLiteralFix.registerFix((PsiMethodCallExpression)place.getParent(), errorResult);
    }
    if (refElement instanceof PsiJvmModifiersOwner) {
      List<IntentionAction> fixes =
        JvmElementActionFactories.createModifierActions((PsiJvmModifiersOwner)refElement, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true));
      QuickFixAction.registerQuickFixActions(errorResult, null, fixes);
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      QuickFixAction.registerQuickFixAction(
        errorResult, QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createCreateFieldFromUsageFix((PsiReferenceExpression)place));
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
                                              PsiType itemType,
                                              @Nullable PsiExpression expr,
                                              @Nullable HighlightInfo highlightInfo) {
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, action);
    }
    registerChangeReturnTypeFix(highlightInfo, expr, parameter.getType());
  }

  static void registerChangeReturnTypeFix(@Nullable HighlightInfo highlightInfo, @Nullable PsiExpression expr, @NotNull PsiType toType) {
    if (expr instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, PriorityIntentionActionWrapper
          .lowPriority(QUICK_FIX_FACTORY.createMethodReturnFix(method, toType, true)));
      }
    }
  }

  /**
   * @param variable variable to create change type fixes for
   * @param itemType a desired variable type
   * @return a list of created fix actions
   */
  @NotNull
  public static List<IntentionAction> getChangeVariableTypeFixes(@NotNull PsiVariable variable, PsiType itemType) {
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
  static IntentionAction getChangeParameterClassFix(PsiType lType, PsiType rType) {
    PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

    if (rClass == null || lClass == null) return null;
    if (rClass instanceof PsiAnonymousClass) return null;
    if (rClass.isInheritor(lClass, true)) return null;
    if (lClass.isInheritor(rClass, true)) return null;
    if (lClass == rClass) return null;

    return QUICK_FIX_FACTORY.createChangeParameterClassFix(rClass, (PsiClassType)lType);
  }

  private static void registerReplaceInaccessibleFieldWithGetterSetterFix(PsiMember refElement,
                                                                          PsiJavaCodeReferenceElement place,
                                                                          PsiClass accessObjectClass,
                                                                          HighlightInfo error,
                                                                          TextRange parentFixRange) {
    if (refElement instanceof PsiField && place instanceof PsiReferenceExpression) {
      PsiField psiField = (PsiField)refElement;
      PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.isOnAssignmentLeftHand((PsiExpression)place)) {
          PsiMethod setterPrototype = PropertyUtilBase.generateSetterPrototype(psiField);
          PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
          if (setter != null && PsiUtil.isAccessible(setter, place, accessObjectClass)) {
            PsiElement element = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
            if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)element).getOperationTokenType() == JavaTokenType.EQ) {
              QuickFixAction.registerQuickFixAction(error, parentFixRange, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, setter, true));
            }
          }
        }
        else if (PsiUtil.isAccessedForReading((PsiExpression)place)) {
          PsiMethod getterPrototype = PropertyUtilBase.generateGetterPrototype(psiField);
          PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, place, accessObjectClass)) {
            QuickFixAction.registerQuickFixAction(error, parentFixRange, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, getter, false));
          }
        }
      }
    }
  }

  static void registerLambdaReturnTypeFixes(HighlightInfo info, PsiLambdaExpression lambda, PsiExpression expression) {
    PsiType type = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    if (type != null) {
      PsiType exprType = expression.getType();
      if (exprType != null && TypeConversionUtil.areTypesConvertible(exprType, type)) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddTypeCastFix(type, expression));
      }
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithOptionalFix(type, expression));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapExpressionFix(type, expression));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithAdapterFix(type, expression));
    }
  }

  static void registerChangeParameterClassFix(PsiType lType, PsiType rType, HighlightInfo info) {
    QuickFixAction.registerQuickFixAction(info, getChangeParameterClassFix(lType, rType));
  }

  static PsiSwitchStatement findInitializingSwitch(@NotNull PsiVariable variable,
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

  static void registerMakeNotFinalAction(@NotNull PsiVariable var, @Nullable HighlightInfo highlightInfo) {
    if (var instanceof PsiField) {
      QuickFixAction.registerQuickFixActions(
        highlightInfo, null,
        JvmElementActionFactories.createModifierActions((PsiField)var, MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false))
      );
    }
    else {
      QuickFixAction.registerQuickFixAction(
        highlightInfo,
        QUICK_FIX_FACTORY.createModifierListFix(var, PsiModifier.FINAL, false, false)
      );
    }
  }

  static void registerFixesForExpressionStatement(HighlightInfo info, PsiElement statement) {
    if (info == null) return;
    if (!(statement instanceof PsiExpressionStatement)) return;
    PsiCodeBlock block = ObjectUtils.tryCast(statement.getParent(), PsiCodeBlock.class);
    if (block == null) return;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (expression instanceof PsiAssignmentExpression) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (!type.equals(PsiType.VOID)) {
      QuickFixAction.registerQuickFixAction(info, PriorityIntentionActionWrapper.highPriority(QUICK_FIX_FACTORY.createIterateFix(expression)));
    }
    if (PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) == block.getRBrace()) {
      PsiElement blockParent = block.getParent();
      if (blockParent instanceof PsiMethod) {
        PsiType returnType = ((PsiMethod)blockParent).getReturnType();
        if (returnType != null && isPossibleReturnValue(expression, type, returnType)) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createInsertReturnFix(expression));
        }
      }
    }
    if (!type.equals(PsiType.VOID)) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createIntroduceVariableAction(expression));
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

  static void registerSpecifyVarTypeFix(@NotNull PsiLocalVariable variable, @NotNull HighlightInfo info) {
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
              if (type instanceof PsiPrimitiveType && variable.getInitializer() != null) {
                type = ((PsiPrimitiveType)type).getBoxedType(variable);
              }
              if (type != null) {
                type = GenericsUtil.getVariableTypeByExpressionType(type);
                IntentionAction fix = QUICK_FIX_FACTORY.createSetVariableTypeFix(variable, type);
                QuickFixAction.registerQuickFixAction(info, fix);
                return false;
              }
            }
          }
        }
      }
      return true;
    });
  }
}