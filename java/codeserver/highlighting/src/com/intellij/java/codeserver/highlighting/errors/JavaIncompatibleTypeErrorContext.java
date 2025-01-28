// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.HtmlChunk.*;

/**
 * Incompatible type context
 * 
 * @param lType left type (left-side of assignment, return type of the method if return value is incompatible, etc.)
 * @param rType right type (right-side of assignment, type of incompatible method return value, etc.; might be null)
 * @param reasonForIncompatibleTypes textual reason for incompatible type (probably inference error)
 * @see JavaErrorKinds#TYPE_INCOMPATIBLE
 */
public record JavaIncompatibleTypeErrorContext(@NotNull PsiType lType, @Nullable PsiType rType, 
                                               @Nullable @Nls String reasonForIncompatibleTypes) {
  private static final @NlsSafe String ANONYMOUS = "anonymous ";

  public JavaIncompatibleTypeErrorContext(@NotNull PsiType lType, @Nullable PsiType rType) {
    this(lType, rType, null);
  }

  private @Nls @NotNull String getReasonForIncompatibleTypes() {
    if (rType instanceof PsiMethodReferenceType referenceType) {
      JavaResolveResult[] results = referenceType.getExpression().multiResolve(false);
      if (results.length > 1) {
        PsiElement element1 = results[0].getElement();
        PsiElement element2 = results[1].getElement();
        if (element1 instanceof PsiMethod && element2 instanceof PsiMethod) {
          String candidate1 = JavaErrorFormatUtil.format(element1);
          String candidate2 = JavaErrorFormatUtil.format(element2);
          return JavaCompilationErrorBundle.message("type.incompatible.reason.ambiguous.method.reference", candidate1, candidate2);
        }
      }
    }
    return "";
  }

  @NotNull HtmlChunk createTooltip() {
    return createTooltip(
      reasonForIncompatibleTypes == null ? getReasonForIncompatibleTypes() : XmlStringUtil.escapeString(reasonForIncompatibleTypes));
  }

  private @NotNull HtmlChunk createTooltip(@NotNull @Nls String reason) {
    HtmlChunk styledReason = reason.isEmpty() ? empty() :
                             tag("table").child(
                               tag("tr").child(
                                 tag("td").style("padding-top: 10px; padding-left: 4px;").addRaw(reason)));
    IncompatibleTypesTooltipComposer tooltipComposer = (lTypeString, lTypeArguments, rTypeString, rTypeArguments) ->
      createRequiredProvidedTypeMessage(lTypeString, lTypeArguments, rTypeString, rTypeArguments, styledReason);
    return createIncompatibleTypesTooltip(tooltipComposer);
  }

  @NotNull HtmlChunk createDescription() {
    PsiType baseLType = PsiUtil.convertAnonymousToBaseType(lType);
    PsiType baseRType = rType == null ? null : PsiUtil.convertAnonymousToBaseType(rType);
    boolean leftAnonymous = PsiUtil.resolveClassInClassTypeOnly(lType) instanceof PsiAnonymousClass;
    String lTypeString = JavaErrorFormatUtil.formatType(leftAnonymous ? lType : baseLType);
    String rTypeString = JavaErrorFormatUtil.formatType(leftAnonymous ? rType : baseRType);
    return raw(JavaCompilationErrorBundle.message("type.incompatible", lTypeString, rTypeString));
  }

  static @NotNull HtmlChunk createRequiredProvidedTypeMessage(@NotNull HtmlChunk lType,
                                                              @Nls @NotNull String lTypeArguments,
                                                              @NotNull HtmlChunk rType,
                                                              @Nls @NotNull String rTypeArguments,
                                                              @NotNull HtmlChunk styledReason) {
    return html().child(
      body().children(
        tag("table").children(
          tag("tr").children(
            tag("td").style("padding: 0px 16px 8px 4px;").setClass(JavaCompilationError.JAVA_DISPLAY_GRAYED)
              .addText(JavaCompilationErrorBundle.message("type.incompatible.tooltip.required.type")),
            tag("td").style("padding: 0px 4px 8px 0px;").child(lType),
            raw(lTypeArguments)
          ),
          tag("tr").children(
            tag("td").style("padding: 0px 16px 0px 4px;").setClass(JavaCompilationError.JAVA_DISPLAY_GRAYED)
              .addText(JavaCompilationErrorBundle.message("type.incompatible.tooltip.provided.type")),
            tag("td").style("padding: 0px 4px 0px 0px;").child(rType),
            raw(rTypeArguments)
          )
        ),
        styledReason
      )
    );
  }

  @NotNull
  @NlsContexts.Tooltip
  HtmlChunk createIncompatibleTypesTooltip(@NotNull IncompatibleTypesTooltipComposer consumer) {
    PsiType baseLType = PsiUtil.convertAnonymousToBaseType(lType);
    PsiType baseRType = rType == null ? null : PsiUtil.convertAnonymousToBaseType(rType);
    boolean leftAnonymous = PsiUtil.resolveClassInClassTypeOnly(lType) instanceof PsiAnonymousClass;
    PsiType lType = leftAnonymous ? this.lType : baseLType;
    PsiType rType = leftAnonymous ? this.rType : baseRType;
    TypeData lTypeData = typeData(lType);
    TypeData rTypeData = typeData(rType);
    PsiTypeParameter[] lTypeParams = lTypeData.typeParameters();
    PsiTypeParameter[] rTypeParams = rTypeData.typeParameters();

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    boolean skipColumns = consumer.skipTypeArgsColumns();
    StringBuilder requiredRow = new StringBuilder();
    StringBuilder foundRow = new StringBuilder();
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeData.substitutor().substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeData.substitutor().substitute(rTypeParameter);
      boolean matches = lSubstitutedType == rSubstitutedType ||
                        lSubstitutedType != null &&
                        rSubstitutedType != null &&
                        TypeConversionUtil.typesAgree(lSubstitutedType, rSubstitutedType, true);
      String openBrace = i == 0 ? "&lt;" : "";
      String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      boolean showShortType = showShortType(lSubstitutedType, rSubstitutedType);

      requiredRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 8px 0px;'>")
        .append(lTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(lSubstitutedType, true, showShortType))
        .append(i < lTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");

      foundRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 0px 0px;'>")
        .append(rTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(rSubstitutedType, matches, showShortType))
        .append(i < rTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");
    }
    PsiType lRawType = lType instanceof PsiClassType classType ? classType.rawType() : lType;
    PsiType rRawType = rType instanceof PsiClassType classType ? classType.rawType() : rType;
    boolean assignable = rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);
    boolean shortType = showShortType(lRawType, rRawType);
    return consumer.consume(redIfNotMatch(lRawType, true, shortType),
                            requiredRow.toString(),
                            redIfNotMatch(rRawType, assignable, shortType),
                            foundRow.toString());
  }

  private static @NotNull TypeData typeData(PsiType type) {
    PsiTypeParameter[] parameters;
    PsiSubstitutor substitutor;
    if (type instanceof PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      substitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      parameters = psiClass == null || classType.isRaw() ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    else {
      substitutor = PsiSubstitutor.EMPTY;
      parameters = PsiTypeParameter.EMPTY_ARRAY;
    }
    return new TypeData(parameters, substitutor);
  }

  static @NotNull @NlsSafe HtmlChunk redIfNotMatch(@Nullable PsiType type, boolean matches, boolean shortType) {
    if (type == null) return empty();
    String typeText;
    if (shortType || type instanceof PsiCapturedWildcardType) {
      typeText = PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiAnonymousClass
                 ? ANONYMOUS + type.getPresentableText()
                 : type.getPresentableText();
    }
    else {
      typeText = type.getCanonicalText();
    }
    return span()
      .setClass(matches ? JavaCompilationError.JAVA_DISPLAY_INFORMATION : JavaCompilationError.JAVA_DISPLAY_ERROR).addText(typeText);
  }

  static boolean showShortType(@Nullable PsiType lType, @Nullable PsiType rType) {
    if (Comparing.equal(lType, rType)) return true;

    return lType != null && rType != null &&
           (!lType.getPresentableText().equals(rType.getPresentableText()) ||
            lType.getCanonicalText().equals(rType.getCanonicalText()));
  }


  @FunctionalInterface
  interface IncompatibleTypesTooltipComposer {
    @NotNull
    @NlsContexts.Tooltip
    HtmlChunk consume(@NotNull HtmlChunk lRawType,
                      @NotNull @NlsSafe String lTypeArguments,
                      @NotNull HtmlChunk rRawType,
                      @NotNull @NlsSafe String rTypeArguments);

    /**
     * Override if expected/actual pair layout is a row
     */
    default boolean skipTypeArgsColumns() {
      return false;
    }
  }

  private record TypeData(@NotNull PsiTypeParameter @NotNull [] typeParameters, @NotNull PsiSubstitutor substitutor) {
  }
}
