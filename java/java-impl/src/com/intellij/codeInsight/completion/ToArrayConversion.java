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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.*;

/**
 * @author peter
 */
public class ToArrayConversion {
  static void addConversions(final PsiElement element, final String prefix, final PsiType itemType,
                             final Consumer<LookupElement> result, @Nullable final PsiElement qualifier,
                             final PsiType expectedType) {
    final PsiType componentType = PsiUtil.extractIterableTypeParameter(itemType, true);
    if (componentType == null || !(expectedType instanceof PsiArrayType)) return;

    final PsiArrayType type = (PsiArrayType)expectedType;
    if (!type.getComponentType().isAssignableFrom(componentType) ||
        componentType instanceof PsiClassType && ((PsiClassType) componentType).hasParameters()) {
      return;
    }

    final String bracketSpace =
      getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_BRACKETS);
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

          addToArrayConversion(element, prefix,
                               (needQualify ? containingClass.getQualifiedName() + "." : "") + field.getName(),
                               (needQualify ? containingClass.getName() + "." : "") + field.getName(), result, qualifier);
          hasEmptyArrayField = true;
        }
      }
    }

    if (!hasEmptyArrayField) {
      addToArrayConversion(element, prefix,
                           "new " + componentType.getCanonicalText() + "[" + bracketSpace + "0" + bracketSpace + "]",
                           "new " + componentType.getPresentableText() + "[0]", result, qualifier);
    }
  }

  private static void addToArrayConversion(final PsiElement element, final String prefix, @NonNls final String expressionString, @NonNls String presentableString, final Consumer<LookupElement> result, PsiElement qualifier) {
    final boolean callSpace = CodeStyleSettingsManager.getSettings(element.getProject())
      .getCommonSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
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
    result.consume(new ExpressionLookupItem(conversion, PlatformIcons.METHOD_ICON, prefix + ".toArray(" + presentableString + ")", lookupStrings) {
        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);
          super.handleInsert(context);
        }
      });
  }
}
