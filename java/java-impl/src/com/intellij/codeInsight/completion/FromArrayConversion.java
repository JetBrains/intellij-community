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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.createExpression;
import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;

/**
 * @author peter
 */
class FromArrayConversion {
  static void addConversions(final PsiElement element,
                             final String prefix,
                             final PsiType itemType,
                             final Consumer<LookupElement> result,
                             @Nullable PsiElement qualifier,
                             final PsiType expectedType) throws IncorrectOperationException {
    final String methodName = getArraysConversionMethod(itemType, expectedType);
    if (methodName == null) return;

    final String qualifierText = ReferenceExpressionCompletionContributor.getQualifierText(qualifier);
    final PsiExpression conversion = createExpression("java.util.Arrays." + methodName + "(" + qualifierText + prefix + ")", element);
    final String presentable = "Arrays." + methodName + "(" + qualifierText + prefix + ")";
    String[] lookupStrings = {StringUtil.isEmpty(qualifierText) ? presentable : prefix, prefix, presentable, methodName + "(" + prefix + ")"};
    result.consume(new ExpressionLookupItem(conversion, PlatformIcons.METHOD_ICON, presentable, lookupStrings) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        int startOffset = context.getStartOffset() - qualifierText.length();
        final Project project = element.getProject();
        final String callSpace = getSpace(
          CodeStyleSettingsManager.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
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
