// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.createExpression;
import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;

/**
 * @author peter
 */
final class FromArrayConversion {
  static void addConversions(final PsiElement element,
                             final String prefix,
                             final PsiType itemType,
                             final Consumer<? super LookupElement> result,
                             @Nullable PsiElement qualifier,
                             @NotNull PsiType expectedType) throws IncorrectOperationException {
    final String methodName = getArraysConversionMethod(itemType, expectedType);
    if (methodName == null) return;

    final String qualifierText = ReferenceExpressionCompletionContributor.getQualifierText(qualifier);
    final PsiExpression conversion = createExpression("java.util.Arrays." + methodName + "(" + qualifierText + prefix + ")", element);
    PsiType type = conversion.getType();
    if (type == null || !expectedType.isAssignableFrom(type)) return;

    final String presentable = "Arrays." + methodName + "(" + qualifierText + prefix + ")";
    String[] lookupStrings = {StringUtil.isEmpty(qualifierText) ? presentable : prefix, prefix, presentable, methodName + "(" + prefix + ")"};
    result.consume(new ExpressionLookupItem(conversion, PlatformIcons.METHOD_ICON, presentable, lookupStrings) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        int startOffset = context.getStartOffset() - qualifierText.length();
        final Project project = element.getProject();
        final String callSpace = getSpace(
          CodeStyle.getLanguageSettings(context.getFile()).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
        final String newText = "java.util.Arrays." + methodName + "(" + callSpace + qualifierText + prefix + callSpace + ")";
        context.getDocument().replaceString(startOffset, context.getTailOffset(), newText);

        context.commitDocument();
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(context.getFile(), startOffset, startOffset + CommonClassNames.JAVA_UTIL_ARRAYS.length());
      }
    });
  }

  @Nullable
  private static String getArraysConversionMethod(PsiType itemType, PsiType expectedType) {
    String methodName = "asList";
    PsiType componentType = PsiUtil.extractIterableTypeParameter(expectedType, true);
    if (componentType == null) {
      methodName = "stream";
      componentType = getStreamComponentType(expectedType);
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(componentType);
      if (unboxedType != null) {
        componentType = unboxedType;
      }
    }

    if (componentType == null ||
        !(itemType instanceof PsiArrayType) ||
        !componentType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      return null;

    }
    return methodName;
  }

  private static PsiType getStreamComponentType(PsiType expectedType) {
    return PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, 0, true);
  }
}
