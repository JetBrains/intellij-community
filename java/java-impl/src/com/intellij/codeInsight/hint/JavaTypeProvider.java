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
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColorUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

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
    return StringUtil.escapeXmlEntities(getTypePresentation(element));
  }

  private static @NotNull String getTypePresentation(@NotNull PsiExpression element) {
    PsiType type = element.getType();
    if (type instanceof PsiLambdaExpressionType) {
      type = ((PsiLambdaExpressionType)type).getExpression().getFunctionalInterfaceType();
    }
    else if (type instanceof PsiMethodReferenceType) {
      type = ((PsiMethodReferenceType)type).getExpression().getFunctionalInterfaceType();
    }
    return type == null ? "<unknown>" : type.getPresentableText();
  }

  @NotNull
  @Override
  public String getErrorHint() {
    return JavaBundle.message("error.hint.no.expression.found");
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
    List<Pair<String, String>> infoLines = new ArrayList<>();
    String basicType = getTypePresentation(expression);
    if (result != null) {
      DfType dfType = result.getDfType(expression);
      PsiType type = expression.getType();
      Set<Object> values = result.getExpressionValues(expression);
      if (!values.isEmpty()) {
        if (values.size() == 1) {
          infoLines.add(Pair.create("Value", DfConstantType.renderValue(values.iterator().next())));
        } else {
          infoLines.add(Pair.create("Value (one of)", StreamEx.of(values).map(DfConstantType::renderValue).sorted().joining(", ")));
        }
      } else {
        if (dfType instanceof DfAntiConstantType) {
          List<Object> nonValues = new ArrayList<>(((DfAntiConstantType<?>)dfType).getNotValues());
          nonValues.remove(null); // Nullability: not-null will be displayed, so this just duplicates nullability info
          if (!nonValues.isEmpty()) {
            infoLines.add(Pair.create("Not equal to", StreamEx.of(nonValues).map(DfConstantType::renderValue).sorted().joining(", ")));
          }
        }
        if (dfType instanceof DfIntegralType) {
          String rangeText = ((DfIntegralType)dfType).getRange().getPresentationText(type);
          if (!rangeText.equals("any value")) {
            infoLines.add(Pair.create("Range", rangeText));
          }
        }
        else if (dfType instanceof DfReferenceType) {
          DfReferenceType refType = (DfReferenceType)dfType;
          infoLines.add(Pair.create("Nullability", refType.getNullability().getPresentationName()));
          infoLines.add(Pair.create("Constraints", refType.getConstraint().getPresentationText(type)));
          if (refType.getMutability() != Mutability.UNKNOWN) {
            infoLines.add(Pair.create("Mutability", refType.getMutability().toString()));
          }
          infoLines.add(Pair.create("Locality", refType.isLocal() ? "local object" : ""));
          SpecialField field = refType.getSpecialField();
          if (field != null) {
            infoLines.add(Pair.create(StringUtil.wordsToBeginFromUpperCase(field.toString()),
                                      field.getPresentationText(refType.getSpecialFieldType(), type)));
          }
        }
      }
    }
    infoLines.removeIf(pair -> pair.getSecond().isEmpty());
    if (!infoLines.isEmpty()) {
      infoLines.add(0, Pair.create("Type", basicType));
      return StreamEx.of(infoLines).map(pair -> makeHtmlRow(pair.getFirst(), pair.getSecond())).joining("", "<table>", "</table>");
    }
    return basicType;
  }

  private static String makeHtmlRow(@NotNull String titleText, String contentText) {
    String titleCell = "<td align='left' valign='top' style='color:" +
                       ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR) + "'>" + StringUtil.escapeXmlEntities(titleText) + ":</td>";
    String contentCell = "<td>" + StringUtil.escapeXmlEntities(contentText) + "</td>";
    return "<tr>" + titleCell + contentCell + "</tr>";
  }
}
