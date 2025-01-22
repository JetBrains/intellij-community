// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext.*;

public record JavaMismatchedCallContext(@NotNull PsiExpressionList list,
                                        @NotNull MethodCandidateInfo candidate,
                                        @NotNull List<PsiExpression> mismatchedExpressions) {
  @NotNull HtmlChunk createDescription() {
    PsiMethod resolvedMethod = candidate.getElement();
    PsiExpression[] expressions = list.getExpressions();
    PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
    if (argCountMismatch(parameters, expressions)) {
      return HtmlChunk.raw(
        JavaCompilationErrorBundle.message("call.wrong.arguments.count.mismatch", parameters.length, expressions.length));
    }
    PsiClass parent = resolvedMethod.getContainingClass();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    String containerName = parent == null ? "" : HighlightMessageUtil.getSymbolName(parent, substitutor);
    String argTypes = JavaErrorFormatUtil.formatArgumentTypes(list, false);
    String methodName = HighlightMessageUtil.getSymbolName(resolvedMethod, substitutor);
    return HtmlChunk.raw(JavaCompilationErrorBundle.message("call.wrong.arguments", methodName, containerName, argTypes));
  }

  @NotNull HtmlChunk createTooltip() {
    PsiMethod resolvedMethod = candidate.getElement();
    PsiClass parent = resolvedMethod.getContainingClass();
    if (parent != null) {
      PsiExpression[] expressions = list.getExpressions();
      PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
      if (mismatchedExpressions.size() == 1 && parameters.length > 0) {
        return createOneArgMismatchTooltip(expressions, parameters);
      }
      if (argCountMismatch(parameters, expressions)) {
        return createMismatchedArgumentCountTooltip(parameters.length, expressions.length);
      }
      if (mismatchedExpressions.isEmpty()) {
        return HtmlChunk.empty();
      }
      return createMismatchedArgumentsHtmlTooltip();
    }
    return HtmlChunk.empty();
  }

  private @NotNull HtmlChunk createMismatchedArgumentsHtmlTooltip() {
    PsiMethod method = candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return createMismatchedArgumentsHtmlTooltip(parameters, substitutor);
  }

  private @NotNull HtmlChunk createMismatchedArgumentsHtmlTooltip(PsiParameter @NotNull [] parameters,
                                                                  @NotNull PsiSubstitutor substitutor) {
    PsiExpression[] expressions = list.getExpressions();
    if (argCountMismatch(parameters, expressions)) {
      return createMismatchedArgumentCountTooltip(parameters.length, expressions.length);
    }

    HtmlBuilder message = new HtmlBuilder();
    message.append(getTypeMismatchTable(candidate, substitutor, parameters, expressions));

    String errorMessage = candidate.getInferenceErrorMessage();
    message.append(getTypeMismatchErrorHtml(errorMessage));
    return message.wrapWithHtmlBody();
  }

  private @NotNull HtmlChunk createOneArgMismatchTooltip(PsiExpression @NotNull [] expressions, PsiParameter @NotNull [] parameters) {
    PsiExpression wrongArg = mismatchedExpressions.get(0);
    PsiType argType = wrongArg != null ? wrongArg.getType() : null;
    if (argType != null) {
      int idx = ArrayUtil.find(expressions, wrongArg);
      if (idx > parameters.length - 1 && !parameters[parameters.length - 1].isVarArgs()) return HtmlChunk.empty();
      PsiType paramType =
        candidate.getSubstitutor().substitute(PsiTypesUtil.getParameterType(parameters, idx, candidate.isVarargs()));
      String errorMessage = candidate.getInferenceErrorMessage();
      HtmlChunk reason = getTypeMismatchErrorHtml(errorMessage);
      return new JavaIncompatibleTypeErrorContext(paramType, argType).createIncompatibleTypesTooltip(
        (lRawType, lTypeArguments, rRawType, rTypeArguments) ->
          createRequiredProvidedTypeMessage(lRawType, lTypeArguments, rRawType, rTypeArguments, reason));
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk getTypeMismatchTable(@Nullable MethodCandidateInfo info,
                                                         @NotNull PsiSubstitutor substitutor,
                                                         PsiParameter @NotNull [] parameters,
                                                         PsiExpression[] expressions) {
    HtmlBuilder table = new HtmlBuilder();
    HtmlChunk.Element td = HtmlChunk.tag("td");
    HtmlChunk requiredHeader = td.style("padding-left: 16px; padding-right: 24px;")
      .setClass(JavaCompilationError.JAVA_DISPLAY_GRAYED)
      .addText(JavaCompilationErrorBundle.message("type.incompatible.tooltip.required.type"));
    HtmlChunk providedHeader = td.style("padding-right: 28px;")
      .setClass(JavaCompilationError.JAVA_DISPLAY_GRAYED)
      .addText(JavaCompilationErrorBundle.message("type.incompatible.tooltip.provided.type"));
    table.append(HtmlChunk.tag("tr").children(td, requiredHeader, providedHeader));

    String parameterNameStyle = "padding:1px 4px 1px 4px;";

    boolean varargAdded = false;
    for (int i = 0; i < Math.max(parameters.length, expressions.length); i++) {
      boolean varargs = info != null && info.isVarargs();
      if (assignmentCompatible(i, parameters, expressions, substitutor, varargs)) continue;
      PsiParameter parameter = null;
      if (i < parameters.length) {
        parameter = parameters[i];
        varargAdded = parameter.isVarArgs();
      }
      else if (!varargAdded) {
        parameter = parameters[parameters.length - 1];
        varargAdded = true;
      }
      PsiType parameterType = substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
      PsiExpression expression = i < expressions.length ? expressions[i] : null;
      boolean showShortType = showShortType(parameterType, expression != null ? expression.getType() : null);
      HtmlChunk.Element nameCell = td;
      HtmlChunk.Element typeCell = td.style("padding-left: 16px; padding-right: 24px;");
      if (parameter != null) {
        nameCell = nameCell.child(td.style(parameterNameStyle)
                                    .setClass(JavaCompilationError.JAVA_DISPLAY_PARAMETER)
                                    .addText(parameter.getName() + ":")
                                    .wrapWith("tr").wrapWith("table"));
        typeCell = typeCell.child(redIfNotMatch(substitutor.substitute(parameter.getType()), true, showShortType));
      }

      HtmlChunk.Element mismatchedCell = td.style("padding-right: 28px;");
      if (expression != null) {
        mismatchedCell = mismatchedCell.child(mismatchedExpressionType(parameterType, expression));
      }
      table.append(HtmlChunk.tag("tr").children(nameCell, typeCell, mismatchedCell));
    }
    return table.wrapWith("table");
  }
  
  private static @NotNull HtmlChunk mismatchedExpressionType(PsiType parameterType, @NotNull PsiExpression expression) {
    return new JavaIncompatibleTypeErrorContext(parameterType, expression.getType()).createIncompatibleTypesTooltip(
      new IncompatibleTypesTooltipComposer() {
        @Override
        public @NotNull HtmlChunk consume(@NotNull HtmlChunk lRawType,
                                          @NotNull String lTypeArguments,
                                          @NotNull HtmlChunk rRawType,
                                          @NotNull String rTypeArguments) {
          return new HtmlBuilder().append(rRawType).appendRaw(rTypeArguments).toFragment();
      }

      @Override
      public boolean skipTypeArgsColumns() {
        return true;
      }
    });
  }

  private static @NotNull HtmlChunk createMismatchedArgumentCountTooltip(int expected, int actual) {
    return HtmlChunk.text(JavaCompilationErrorBundle.message("call.wrong.arguments.count.mismatch", expected, actual))
      .wrapWith("html");
  }

  private static boolean assignmentCompatible(int i,
                                              PsiParameter @NotNull [] parameters,
                                              PsiExpression @NotNull [] expressions,
                                              @NotNull PsiSubstitutor substitutor,
                                              boolean varargs) {
    PsiExpression expression = i < expressions.length ? expressions[i] : null;
    if (expression == null) return true;
    PsiType paramType = substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
    return paramType != null && TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression) ||
           IncompleteModelUtil.isIncompleteModel(expression) && IncompleteModelUtil.isPotentiallyConvertible(paramType, expression);
  }

  private static @NotNull List<PsiExpression> mismatchedArgs(PsiExpression @NotNull [] expressions,
                                                             PsiSubstitutor substitutor,
                                                             PsiParameter @NotNull [] parameters,
                                                             boolean varargs) {
    List<PsiExpression> result = new ArrayList<>();
    for (int i = 0; i < Math.max(parameters.length, expressions.length); i++) {
      if (parameters.length == 0 || !assignmentCompatible(i, parameters, expressions, substitutor, varargs)) {
        result.add(i < expressions.length ? expressions[i] : null);
      }
    }

    return result;
  }

  private static @NotNull HtmlChunk getTypeMismatchErrorHtml(@Nls String errorMessage) {
    if (errorMessage == null) {
      return HtmlChunk.empty();
    }
    return HtmlChunk.tag("td").style("padding-left: 4px; padding-top: 10;")
      .addText(JavaCompilationErrorBundle.message("type.incompatible.reason.inference", errorMessage))
      .wrapWith("tr").wrapWith("table");
  }

  private static boolean argCountMismatch(@NotNull PsiParameter @NotNull [] parameters, @NotNull PsiExpression @NotNull [] expressions) {
    return (parameters.length == 0 || !parameters[parameters.length - 1].isVarArgs()) &&
           parameters.length != expressions.length;
  }

  public static @NotNull JavaMismatchedCallContext create(@NotNull PsiExpressionList list, @NotNull MethodCandidateInfo candidateInfo) {
    PsiMethod resolvedMethod = candidateInfo.getElement();
    if (resolvedMethod.getContainingClass() == null) {
      return new JavaMismatchedCallContext(list, candidateInfo, List.of());
    }
    PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
    PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    List<PsiExpression> mismatchedExpressions = mismatchedArgs(expressions, substitutor, parameters, candidateInfo.isVarargs());
    return new JavaMismatchedCallContext(list, candidateInfo, mismatchedExpressions);
  }
}
