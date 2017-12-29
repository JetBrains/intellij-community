/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.createExpression;
import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getQualifierText;
import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;

/**
 * @author peter
 */
class ArrayMemberAccess {
  static void addMemberAccessors(final PsiElement element, final String prefix, final PsiType itemType,
                                 final PsiElement qualifier, final Consumer<LookupElement> result, PsiModifierListOwner object,
                                 final PsiType expectedType)
    throws IncorrectOperationException {
    if (itemType instanceof PsiArrayType && expectedType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      final PsiExpression conversion = createExpression(getQualifierText(qualifier) + prefix + "[0]", element);
      result.consume(new ExpressionLookupItem(conversion, object.getIcon(Iconable.ICON_FLAG_VISIBILITY), prefix + "[...]", prefix) {
        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ARRAY_MEMBER);

          final int tailOffset = context.getTailOffset();
          final String callSpace = getSpace(
            CodeStyleSettingsManager.getSettings(element.getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_BRACKETS);
          context.getDocument().insertString(tailOffset, "[" + callSpace + callSpace + "]");
          context.getEditor().getCaretModel().moveToOffset(tailOffset + 1 + callSpace.length());
        }
      });
    }
  }

  @Nullable
  static ExpressionLookupItem accessFirstElement(PsiElement element, LookupElement item) {
    if (item.getObject() instanceof PsiLocalVariable) {
      final PsiLocalVariable variable = (PsiLocalVariable)item.getObject();
      final PsiType type = variable.getType();
      final PsiExpression expression = variable.getInitializer();
      if (type instanceof PsiArrayType && expression instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)expression;
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if (dimensions.length == 1 && "1".equals(dimensions[0].getText()) && newExpression.getArrayInitializer() == null) {
          final String text = variable.getName() + "[0]";
          return new ExpressionLookupItem(createExpression(text, element), variable.getIcon(Iconable.ICON_FLAG_VISIBILITY), text, text);
        }
      }
    }
    return null;
  }
}
