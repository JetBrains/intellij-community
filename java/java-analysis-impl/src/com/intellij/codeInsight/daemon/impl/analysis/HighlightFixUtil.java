// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceAssignmentFromVoidWithStatementIntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceGetClassWithClassLiteralFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityActionWrapper;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HighlightFixUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil");

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
            (CommonClassNames.JAVA_LANG_OBJECT.equals(arrayComponentType.getCanonicalText()) ||
             !((PsiClassType)fromType).isRaw()) &&
            InheritanceUtil.isInheritor(fromType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(fromType, expression.getResolveScope());
          if (collectionItemType != null && arrayComponentType.isConvertibleFrom(collectionItemType)) {
            QuickFixAction
              .registerQuickFixAction(info, QUICK_FIX_FACTORY.createCollectionToArrayFix(collection, expression, (PsiArrayType)toType));
          }
        }
      }
    }
  }

  /**
   * make element protected/package-private/public suggestion
   * for private method in the interface it should add default modifier as well
   */
  static void registerAccessQuickFixAction(@NotNull PsiMember refElement,
                                           @NotNull PsiJavaCodeReferenceElement place,
                                           @Nullable HighlightInfo errorResult,
                                           final PsiElement fileResolveScope) {
    if (errorResult == null) return;
    PsiClass accessObjectClass = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
    }
    registerReplaceInaccessibleFieldWithGetterSetterFix(refElement, place, accessObjectClass,
                                                        errorResult);

    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      List<IntentionAction> fix =
        JvmElementActionFactories.createModifierActions(packageLocalClassInTheMiddle, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
      QuickFixAction.registerQuickFixActions(errorResult, null, fix);
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
                                              @Nullable final PsiExpression lExpr,
                                              @Nullable HighlightInfo highlightInfo) {
    if (highlightInfo == null || !(expression instanceof  PsiReferenceExpression)) return;

    final PsiElement element = ((PsiReferenceExpression)expression).resolve();
    if (!(element instanceof PsiVariable)) return;

    registerChangeVariableTypeFixes((PsiVariable)element, type, lExpr, highlightInfo);

    if (lExpr instanceof PsiMethodCallExpression && lExpr.getParent() instanceof PsiAssignmentExpression) {
      final PsiElement parent = lExpr.getParent();
      if (parent.getParent() instanceof PsiStatement) {
        final PsiMethod method = ((PsiMethodCallExpression)lExpr).resolveMethod();
        if (method != null && PsiType.VOID.equals(method.getReturnType())) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new ReplaceAssignmentFromVoidWithStatementIntentionAction(parent, lExpr));
        }
      }
    }
  }

  static void registerUnhandledExceptionFixes(PsiElement element, HighlightInfo errorResult, List<? extends PsiClassType> unhandled) {
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToCatchFix());
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSurroundWithTryCatchFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToExistingCatch(element));
    if (unhandled.size() == 1) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createGeneralizeCatchFix(element, unhandled.get(0)));
    }
  }

  static void registerStaticProblemQuickFixAction(@NotNull PsiElement refElement, HighlightInfo errorResult, @NotNull PsiJavaCodeReferenceElement place) {
    if (refElement instanceof PsiModifierListOwner) {
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions((JvmModifiersOwner)refElement, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true)));
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(staticParent, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createCreateFieldFromUsageFix((PsiReferenceExpression)place));
    }
    if (place instanceof PsiReferenceExpression && place.getParent() instanceof PsiMethodCallExpression) {
      ReplaceGetClassWithClassLiteralFix.registerFix((PsiMethodCallExpression)place.getParent(), errorResult);
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
      final PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, PriorityActionWrapper
          .lowPriority(method, QUICK_FIX_FACTORY.createMethodReturnFix(method, toType, true)));
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
    final PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    final PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

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
                                                                          HighlightInfo error) {
    if (refElement instanceof PsiField && place instanceof PsiReferenceExpression) {
      final PsiField psiField = (PsiField)refElement;
      final PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.isOnAssignmentLeftHand((PsiExpression)place)) {
          final PsiMethod setterPrototype = PropertyUtilBase.generateSetterPrototype(psiField);
          final PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
          if (setter != null && PsiUtil.isAccessible(setter, place, accessObjectClass)) {
            final PsiElement element = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
            if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)element).getOperationTokenType() == JavaTokenType.EQ) {
              QuickFixAction.registerQuickFixAction(error, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, setter, true));
            }
          }
        }
        else if (PsiUtil.isAccessedForReading((PsiExpression)place)) {
          final PsiMethod getterPrototype = PropertyUtilBase.generateGetterPrototype(psiField);
          final PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, place, accessObjectClass)) {
            QuickFixAction.registerQuickFixAction(error, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, getter, false));
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
}
