/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceAssignmentFromVoidWithStatementIntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceGetClassWithClassLiteralFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityActionWrapper;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
      IntentionAction fix =
        QUICK_FIX_FACTORY.createModifierListFix(packageLocalClassInTheMiddle, PsiModifier.PUBLIC, true, true);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      return;
    }

    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      assert modifierListCopy != null;
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      String minModifier = PsiModifier.PACKAGE_LOCAL;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        minModifier = PsiModifier.PROTECTED;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      PsiClass containingClass = refElement.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC,};
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        @PsiModifier.ModifierConstant String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(refElement, modifier, true, true);
          TextRange fixRange = new TextRange(errorResult.startOffset, errorResult.endOffset);
          PsiElement ref = place.getReferenceNameElement();
          if (ref != null) {
            fixRange = fixRange.union(ref.getTextRange());
          }
          QuickFixAction.registerQuickFixAction(errorResult, fixRange, fix);
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

  static void registerUnhandledExceptionFixes(PsiElement element, HighlightInfo errorResult, List<PsiClassType> unhandled) {
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToCatchFix());
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSurroundWithTryCatchFix(element));
    if (unhandled.size() == 1) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createGeneralizeCatchFix(element, unhandled.get(0)));
    }
  }

  static void registerStaticProblemQuickFixAction(@NotNull PsiElement refElement, HighlightInfo errorResult, @NotNull PsiJavaCodeReferenceElement place) {
    if (refElement instanceof PsiModifierListOwner) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix((PsiModifierListOwner)refElement, PsiModifier.STATIC, true, false));
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
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
                                              @NotNull HighlightInfo highlightInfo) {
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, action);
    }
    registerChangeReturnTypeFix(highlightInfo, expr, parameter.getType());
  }

  static void registerChangeReturnTypeFix(@NotNull HighlightInfo highlightInfo, @Nullable PsiExpression expr, @NotNull PsiType toType) {
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
      for (ChangeVariableTypeQuickFixProvider fixProvider : Extensions.getExtensions(ChangeVariableTypeQuickFixProvider.EP_NAME)) {
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
