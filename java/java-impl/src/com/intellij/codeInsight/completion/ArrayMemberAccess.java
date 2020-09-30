// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.*;

/**
 * @author peter
 */
final class ArrayMemberAccess {
  static void addMemberAccessors(final PsiElement element, final String prefix, final PsiType itemType,
                                 final PsiElement qualifier, final Consumer<? super LookupElement> result, PsiModifierListOwner object,
                                 final PsiType expectedType)
    throws IncorrectOperationException {
    if (itemType instanceof PsiArrayType && expectedType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      final PsiExpression conversion = createExpression(getQualifierText(qualifier) + prefix + "[0]", element);
      result.consume(new ExpressionLookupItem(conversion, object.getIcon(Iconable.ICON_FLAG_VISIBILITY), prefix + "[...]", prefix) {
        @Override
        public void handleInsert(@NotNull InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ARRAY_MEMBER);

          final int tailOffset = context.getTailOffset();
          final String callSpace = getSpace(
            CodeStyle.getLanguageSettings(context.getFile()).SPACE_WITHIN_BRACKETS);
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
