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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
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

  private static @NotNull @NlsSafe String getTypePresentation(@NotNull PsiExpression element) {
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
  public @Nls String getAdvancedInformationHint(@NotNull PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return CodeInsightBundle.message("unknown.node.text");
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expression);
    List<Pair<@Nls String, @Nls String>> infoLines = new ArrayList<>();
    String basicType = getTypePresentation(expression);
    if (result != null) {
      DfType dfType = result.getDfType(expression);
      PsiType type = expression.getType();
      Set<Object> values = result.getExpressionValues(expression);
      if (!values.isEmpty()) {
        infoLines.add(Pair.create(
          JavaBundle.message("type.information.value"),
          StreamEx.of(values).map(DfaPsiUtil::renderValue).sorted().collect(NlsMessages.joiningOr())));
      } else {
        if (dfType instanceof DfAntiConstantType) {
          List<Object> nonValues = new ArrayList<>(((DfAntiConstantType<?>)dfType).getNotValues());
          nonValues.remove(null); // Nullability: not-null will be displayed, so this just duplicates nullability info
          if (!nonValues.isEmpty()) {
            infoLines.add(Pair.create(
              JavaBundle.message("type.information.not.equal.to"),
              StreamEx.of(nonValues).map(DfaPsiUtil::renderValue).sorted().collect(NlsMessages.joiningNarrowAnd())));
          }
        }
        if (dfType instanceof DfIntegralType) {
          String rangeText = JvmPsiRangeSetUtil.getPresentationText(((DfIntegralType)dfType).getRange(), type);
          if (!rangeText.equals(InspectionsBundle.message("long.range.set.presentation.any"))) {
            infoLines.add(Pair.create(JavaBundle.message("type.information.range"), rangeText));
          }
        }
        else if (dfType instanceof DfFloatingPointType && !(dfType instanceof DfConstantType)) {
          String presentation = dfType.toString().replaceFirst("^(double|float) ?", ""); //NON-NLS
          if (!presentation.isEmpty()) {
            infoLines.add(Pair.create(JavaBundle.message("type.information.range"), presentation));
          }
        }
        else if (dfType instanceof DfReferenceType) {
          DfReferenceType refType = (DfReferenceType)dfType;
          infoLines.add(Pair.create(JavaBundle.message("type.information.nullability"), refType.getNullability().getPresentationName()));
          infoLines.add(Pair.create(JavaBundle.message("type.information.constraints"), refType.getConstraint().getPresentationText(type)));
          if (refType.getMutability() != Mutability.UNKNOWN) {
            infoLines.add(Pair.create(JavaBundle.message("type.information.mutability"), refType.getMutability().getPresentationName()));
          }
          infoLines.add(Pair.create(JavaBundle.message("type.information.locality"),
                                    refType.isLocal() ? JavaBundle.message("type.information.local.object") : ""));
          SpecialField field = refType.getSpecialField();
          // ENUM_ORDINAL is not precise enough yet, and could be confusing for users
          if (field != null && field != SpecialField.ENUM_ORDINAL) {
            infoLines.add(Pair.create(field.getPresentationName(), field.getPresentationText(refType.getSpecialFieldType(), type)));
          }
        }
      }
    }
    infoLines.removeIf(pair -> pair.getSecond().isEmpty());
    if (!infoLines.isEmpty()) {
      infoLines.add(0, Pair.create(JavaBundle.message("type.information.type"), basicType));
      HtmlChunk[] rows = ContainerUtil.map2Array(infoLines, HtmlChunk.class, pair -> makeHtmlRow(pair.getFirst(), pair.getSecond()));
      return HtmlChunk.tag("table").children(rows).toString();
    }
    return basicType;
  }

  private static HtmlChunk makeHtmlRow(@NotNull @Nls String titleText, @Nls String contentText) {
    HtmlChunk titleCell = HtmlChunk.tag("td").attr("align", "left").attr("valign", "top")
      .style("color:" + ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR))
      .addText(titleText + ":");
    HtmlChunk contentCell = HtmlChunk.tag("td").addText(contentText);
    return HtmlChunk.tag("tr").children(titleCell, contentCell);
  }
}
