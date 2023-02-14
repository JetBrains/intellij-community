// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.*;

public final class ToArrayConversion {
  static void addConversions(final @NotNull PsiFile file,
                             final PsiElement element, final String prefix, final PsiType itemType,
                             final Consumer<? super LookupElement> result, @Nullable final PsiElement qualifier,
                             final PsiType expectedType) {
    final PsiType componentType = PsiUtil.extractIterableTypeParameter(itemType, true);
    if (componentType == null || !(expectedType instanceof PsiArrayType type)) return;

    if (!type.getComponentType().isAssignableFrom(componentType) ||
        componentType instanceof PsiClassType && ((PsiClassType) componentType).hasParameters()) {
      return;
    }

    final String bracketSpace =
      getSpace(CodeStyle.getLanguageSettings(file).SPACE_WITHIN_BRACKETS);
    boolean hasEmptyArrayField = false;
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass != null) {
      for (final PsiField field : psiClass.getAllFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) &&
            JavaPsiFacade.getInstance(field.getProject()).getResolveHelper().isAccessible(field, element, null) &&
            type.isAssignableFrom(field.getType()) && ConstructionUtils.isEmptyArrayInitializer(field.getInitializer())) {
          boolean needQualify;
          try {
            needQualify = !field.isEquivalentTo(((PsiReferenceExpression)createExpression(field.getName(), element)).resolve());
          }
          catch (IncorrectOperationException e) {
            continue;
          }

          PsiClass containingClass = field.getContainingClass();
          if (containingClass == null) continue;

          addToArrayConversion(file, element, prefix,
                               (needQualify ? containingClass.getQualifiedName() + "." : "") + field.getName(),
                               (needQualify ? containingClass.getName() + "." : "") + field.getName(), result, qualifier);
          hasEmptyArrayField = true;
        }
      }
    }

    if (!hasEmptyArrayField) {
      addToArrayConversion(file, element, prefix,
                           "new " + componentType.getCanonicalText() + "[" + bracketSpace + "0" + bracketSpace + "]",
                           "new " + componentType.getPresentableText() + "[0]", result, qualifier);
    }
  }

  private static void addToArrayConversion(@NotNull PsiFile file,
                                           final PsiElement element,
                                           final String prefix,
                                           @NonNls final String expressionString,
                                           @NonNls String presentableString,
                                           final Consumer<? super LookupElement> result,
                                           PsiElement qualifier) {
    final boolean callSpace = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
    final PsiExpression conversion;
    try {
      conversion = createExpression(
        getQualifierText(qualifier) + prefix + ".toArray(" +
        getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", element);
    }
    catch (IncorrectOperationException e) {
      return;
    }

    String[] lookupStrings = {prefix + ".toArray(" + getSpace(callSpace) + expressionString +
                              getSpace(callSpace) + ")", presentableString};
    result.consume(new ExpressionLookupItem(conversion, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method), prefix + ".toArray(" + presentableString + ")", lookupStrings) {
        @Override
        public void handleInsert(@NotNull InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);
          super.handleInsert(context);
        }
      });
  }
}
