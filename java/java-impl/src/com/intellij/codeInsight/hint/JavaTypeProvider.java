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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactMap;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author gregsh
 */
public class JavaTypeProvider extends ExpressionTypeProvider<PsiExpression> {
  @NotNull
  @Override
  public String getInformationHint(@NotNull PsiExpression element) {
    PsiType type = element.getType();
    if (type instanceof PsiLambdaExpressionType) {
      type = ((PsiLambdaExpressionType)type).getExpression().getFunctionalInterfaceType();
    }
    else if (type instanceof PsiMethodReferenceType) {
      type = ((PsiMethodReferenceType)type).getExpression().getFunctionalInterfaceType();
    }
    String text = type == null ? "<unknown>" : type.getPresentableText();
    return StringUtil.escapeXmlEntities(text);
  }

  @NotNull
  @Override
  public String getErrorHint() {
    return "No expression found";
  }

  @NotNull
  @Override
  public List<PsiExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return SyntaxTraverser.psiApi().parents(elementAt)
      .filter(PsiExpression.class)
      .filter(JavaTypeProvider::isLargestNonTrivialExpression)
      .toList();
  }

  private static boolean isLargestNonTrivialExpression(@NotNull PsiExpression e) {
    PsiElement p = e.getParent();
    if (p instanceof PsiUnaryExpression) return false;
    if (p instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)p).getMethodExpression() == e) {
      return false;
    }
    return true;
  }

  @Override
  public boolean hasAdvancedInformation() {
    return true;
  }

  @NotNull
  @Override
  public String getAdvancedInformationHint(@NotNull PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return "<unknown>";
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expression);
    String advancedTypeInfo = "";
    String basicTypeEscaped = getInformationHint(expression);
    if (result != null) {
      DfaFactMap map = result.getAllFacts(expression);
      PsiType type = expression.getType();
      if (map != null) {
        advancedTypeInfo = map.facts(new DfaFactMap.FactMapper<String>() {
          @Override
          public <T> String apply(DfaFactType<T> factType, T value) {
            return formatFact(factType, value, type);
          }
        }).joining();
      }
      List<Object> nonValues = new ArrayList<>(result.getValuesNotEqualToExpression(expression));
      nonValues.remove(null); // Nullability: not-null will be displayed, so this just duplicates nullability info
      if (!nonValues.isEmpty()) {
        advancedTypeInfo = makeHtmlRow("Not equal to", StringUtil.join(nonValues, DfaConstValue::renderValue, ", ")) + advancedTypeInfo;
      }
      Set<Object> values = result.getExpressionValues(expression);
      if (!values.isEmpty()) {
        if (values.size() == 1) {
          advancedTypeInfo = makeHtmlRow("Value", DfaConstValue.renderValue(values.iterator().next())) + advancedTypeInfo;
        } else {
          advancedTypeInfo = makeHtmlRow("Value (one of)", StringUtil.join(values, DfaConstValue::renderValue, ", ")) + advancedTypeInfo;
        }
      }
    }
    return advancedTypeInfo.isEmpty()
           ? basicTypeEscaped
           : "<table>" + makeHtmlRow("Type", basicTypeEscaped) + advancedTypeInfo + "</table>";
  }

  private static <T> String formatFact(@NotNull DfaFactType<T> factType, @NotNull T value, @Nullable PsiType type) {
    String presentationText = factType.getPresentationText(value, type);
    return presentationText.isEmpty() ? "" : makeHtmlRow(factType.getName(value), StringUtil.escapeXmlEntities(presentationText));
  }

  private static String makeHtmlRow(@NotNull String titleText, String contentHtml) {
    String titleCell = "<td align='left' valign='top' style='color:" +
                       ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR) + "'>" + StringUtil.escapeXmlEntities(titleText) + ":</td>";
    String contentCell = "<td>" + contentHtml + "</td>";
    return "<tr>" + titleCell + contentCell + "</tr>";
  }
}
