// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author gregsh
 */
public final class JavaTypeProvider extends ExpressionTypeProvider<PsiElement> {
  @NotNull
  @Override
  public String getInformationHint(@NotNull PsiElement element) {
    return StringUtil.escapeXmlEntities(getTypePresentation(element));
  }

  private static @NotNull @NlsSafe String getTypePresentation(@NotNull PsiElement element) {
    PsiType type = null;
    PsiLocalVariable psiVariable = getPossibleLocaleVariable(element);
    if (psiVariable != null) {
      type = psiVariable.getType();
    }
    if (element instanceof PsiParameter parameter) {
      type = parameter.getType();
    }
    else if (element instanceof PsiExpression psiExpression) {
      type = psiExpression.getType();
    }
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

  @Nullable
  private static PsiLocalVariable getPossibleLocaleVariable(@NotNull PsiElement elementAt) {
    if (elementAt instanceof PsiIdentifier && elementAt.getParent() instanceof PsiLocalVariable psiVariable &&
        psiVariable.getTypeElement().isInferredType()) {
      return psiVariable;
    }
    if (elementAt instanceof PsiKeyword keyword && keyword.getTokenType() == JavaTokenType.VAR_KEYWORD &&
        keyword.getParent() != null && keyword.getParent().getParent() instanceof PsiLocalVariable psiVariable) {
      return psiVariable;
    }
    return null;
  }

  @Nullable
  private static PsiParameter getPossibleParameter(@NotNull PsiElement elementAt) {
    if (elementAt instanceof PsiIdentifier identifier &&
        identifier.getParent() instanceof PsiParameter parameter &&
        (parameter.getTypeElement() == null || parameter.getTypeElement().isInferredType())) {
      return parameter;
    }
    if (elementAt instanceof PsiKeyword keyword && keyword.getTokenType() == JavaTokenType.VAR_KEYWORD &&
        keyword.getParent() != null && keyword.getParent().getParent() instanceof PsiParameter parameter) {
      return parameter;
    }
    return null;
  }

  @NotNull
  @Override
  public List<PsiElement> getExpressionsAt(@NotNull PsiElement elementAt) {
    PsiVariable psiVariable = getPossibleLocaleVariable(elementAt);
    if (psiVariable != null) {
      PsiTypeElement element = psiVariable.getTypeElement();
      if (element.isInferredType() && psiVariable.getInitializer() != null && psiVariable.getNameIdentifier() != null) {
        return Collections.singletonList(elementAt);
      }
    }
    ArrayList<PsiElement> elements = new ArrayList<>();
    PsiParameter parameter = getPossibleParameter(elementAt);
    if (parameter != null) {
      elements.add(parameter);
    }
    elements.addAll(SyntaxTraverser.psiApi().parents(elementAt)
                      .filter(PsiExpression.class)
                      .filter(JavaTypeProvider::isLargestNonTrivialExpression)
                      .map(t -> (PsiElement)t)
                      .toList());
    return elements;
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
  public @Nls String getAdvancedInformationHint(@NotNull PsiElement element) {
    PsiExpression expression = null;
    PsiVariable psiVariable = getPossibleLocaleVariable(element);
    if (psiVariable != null) {
      expression = psiVariable.getInitializer();
    }
    else if (element instanceof PsiExpression psiExpression) {
      expression = psiExpression;
    }
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return getInformationHint(element);
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
      }
      else {
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
        else if (dfType instanceof DfReferenceType refType) {
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
