// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.format.FormatDecode;
import com.siyeh.ig.format.MessageFormatUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

/**
 * @author Bas Leijdekkers
 */
public final class StringConcatenationArgumentToLogCallInspection extends BaseInspection {

  private static final @NonNls Set<String> logNames = Set.of(
    "trace",
    "debug",
    "info",
    "warn",
    "error",
    "fatal",
    "log"
  );
  private static final String LOG4J_LOGGER = "org.apache.logging.log4j.Logger";
  private static final String LOG4J_BUILDER = "org.apache.logging.log4j.LogBuilder";
  private static final String GET_LOGGER = "getLogger";
  private static final CallMatcher MESSAGE_FORMAT_FORMAT = anyOf(
    staticCall("java.text.MessageFormat", "format").parameterCount(2)
  );
  private static final String SLF4J_LOGGER = "org.slf4j.Logger";

  @SuppressWarnings("PublicField") public int warnLevel = 0;

  @Override
  public @NotNull OptPane getOptionsPane() {
    @Nls String[] options = {
      InspectionGadgetsBundle.message("all.levels.option"),
      InspectionGadgetsBundle.message("warn.level.and.lower.option"),
      InspectionGadgetsBundle.message("info.level.and.lower.option"),
      InspectionGadgetsBundle.message("debug.level.and.lower.option"),
      InspectionGadgetsBundle.message("trace.level.option")
    };
    return pane(
      dropdown("warnLevel", InspectionGadgetsBundle.message("warn.on.label"),
               EntryStream.of(options).mapKeyValue((idx, name) -> option(String.valueOf(idx), name))
                 .toArray(OptDropdown.Option.class))
    );
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.problem.descriptor");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (warnLevel != 0) {
      node.addContent(new Element("option").setAttribute("name", "warnLevel").setAttribute("value", String.valueOf(warnLevel)));
    }
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    if (!(infos[0] instanceof ProblemType problemType)) {
      return null;
    }

    if (!(infos[1] instanceof PsiMethodCallExpression logCall)) {
      return null;
    }

    if (!(infos[2] instanceof PsiExpression targetExpression)) {
      return null;
    }

    if (isFormattedLog4J(logCall)) return null;

    return getQuickFix(problemType, targetExpression);
  }

  public static @Nullable PsiUpdateModCommandQuickFix getQuickFix(@NotNull ProblemType problemType, @NotNull  PsiExpression targetExpression) {
    return switch (problemType) {
      case CONCATENATION ->
        StringConcatenationArgumentToLogCallFix.isAvailable(targetExpression) ? new StringConcatenationArgumentToLogCallFix() : null;
      case STRING_FORMAT -> StringFormatArgumentToLogCallFix.create(targetExpression);
      case MESSAGE_FORMAT -> MessageFormatArgumentToLogCallFix.create(targetExpression);
    };
  }

  private static boolean isFormattedLog4J(@NotNull PsiMethodCallExpression logCall) {
    PsiExpression qualifierExpression = logCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null) {
      return false;
    }

    boolean isLogBuilder = InheritanceUtil.isInheritor(qualifierExpression.getType(), LOG4J_BUILDER);
    if (isLogBuilder || InheritanceUtil.isInheritor(qualifierExpression.getType(), LOG4J_LOGGER)) {

      if (isLogBuilder) {
        while (qualifierExpression != null &&
               !InheritanceUtil.isInheritor(qualifierExpression.getType(), LOG4J_LOGGER)) {
          if (qualifierExpression instanceof PsiMethodCallExpression nextCall) {
            qualifierExpression = PsiUtil.skipParenthesizedExprDown(nextCall.getMethodExpression().getQualifierExpression());
          }
          else {
            qualifierExpression = null;
          }
        }
      }

      if (qualifierExpression != null) {
        boolean isFormatted = true;
        if (qualifierExpression instanceof PsiMethodCallExpression callExpression) {
          PsiMethod method = callExpression.resolveMethod();
          if (method != null &&
              method.getContainingFile() == qualifierExpression.getContainingFile() &&
              (method.hasModifierProperty(PsiModifier.PRIVATE) ||
               method.hasModifierProperty(PsiModifier.STATIC))) {
            PsiReturnStatement[] statements = PsiUtil.findReturnStatements(method);
            if (statements.length == 1) {
              PsiReturnStatement statement = statements[0];
              qualifierExpression = statement.getReturnValue();
            }
          }
        }

        if (qualifierExpression instanceof PsiReferenceExpression referenceExpression &&
            referenceExpression.resolve() instanceof PsiVariable loggerVariable) {
          if (!loggerVariable.isPhysical() ||
              (loggerVariable.getInitializer() instanceof PsiMethodCallExpression callExpression &&
               GET_LOGGER.equals(callExpression.getMethodExpression().getReferenceName()))) {
            isFormatted = false;
          }
        }

        if (qualifierExpression instanceof PsiMethodCallExpression callExpression &&
            GET_LOGGER.equals(callExpression.getMethodExpression().getReferenceName())) {
          isFormatted = false;
        }

        if (isFormatted) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationArgumentToLogCallVisitor();
  }

  public interface EvaluatedStringFix {
    void fix(@NotNull PsiMethodCallExpression logCall);
  }

  private static class StringConcatenationArgumentToLogCallFix extends PsiUpdateModCommandQuickFix implements  EvaluatedStringFix {

    StringConcatenationArgumentToLogCallFix() { }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      fix(methodCallExpression);
    }

    @Override
    public void fix(@NotNull PsiMethodCallExpression methodCallExpression) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final @NonNls StringBuilder newMethodCall = new StringBuilder(methodCallExpression.getMethodExpression().getText());
      newMethodCall.append('(');
      PsiExpression argument = arguments[0];
      int usedArguments;
      if (!(argument instanceof PsiPolyadicExpression)) {
        if (!TypeUtils.expressionHasTypeOrSubtype(argument, "org.slf4j.Marker") || arguments.length < 2) {
          return;
        }
        newMethodCall.append(argument.getText()).append(',');
        argument = arguments[1];
        usedArguments = 2;
        if (!(argument instanceof PsiPolyadicExpression)) {
          return;
        }
      }
      else {
        usedArguments = 1;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)argument;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
      boolean varArgs = false;
      for (PsiMethod otherMethod : methods) {
        if (otherMethod.isVarArgs()) {
          varArgs = true;
          break;
        }
      }
      final List<PsiExpression> newArguments = new ArrayList<>();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean addPlus = false;
      boolean inStringLiteral = false;
      boolean isStringBlock = false;
      StringBuilder logText = new StringBuilder();
      int indent = 0;
      for (PsiExpression operand : operands) {
        if (ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          final String text = operand.getText();
          if (ExpressionUtils.hasStringType(operand) && operand instanceof PsiLiteralExpression literalExpression) {
            final int count = StringUtil.getOccurrenceCount(text, "{}");
            for (int i = 0; i < count && usedArguments + i < arguments.length; i++) {
              newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)arguments[i + usedArguments].copy()));
            }
            usedArguments += count;
            if (!inStringLiteral) {
              if (addPlus) {
                newMethodCall.append('+');
              }
              inStringLiteral = true;
            }
            if (!isStringBlock && literalExpression.isTextBlock()) {
              indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
            }
            isStringBlock = isStringBlock || literalExpression.isTextBlock();
            logText.append(literalExpression.getValue());
          }
          else if (operand instanceof PsiLiteralExpression && PsiTypes.charType().equals(operand.getType()) && inStringLiteral) {
            final Object value = ((PsiLiteralExpression)operand).getValue();
            if (value instanceof Character) {
              logText.append(value);
            }
          }
          else {
            if (inStringLiteral) {
              addLogStrings(newMethodCall, logText, isStringBlock, indent);
              isStringBlock = false;
              inStringLiteral = false;
            }
            if (addPlus) {
              newMethodCall.append('+');
            }
            newMethodCall.append(text);
          }
        }
        else {
          newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)operand.copy()));
          if (!inStringLiteral) {
            if (addPlus) {
              newMethodCall.append('+');
            }
            inStringLiteral = true;
          }
          logText.append("{}");
        }
        addPlus = true;
      }
      while (usedArguments < arguments.length) {
        newArguments.add(arguments[usedArguments++]);
      }
      if (inStringLiteral) {
        addLogStrings(newMethodCall, logText, isStringBlock, indent);
      }
      if (!varArgs && newArguments.size() > 2) {
        newMethodCall.append(", new Object[]{");
        boolean comma = false;
        for (PsiExpression newArgument : newArguments) {
          if (comma) {
            newMethodCall.append(',');
          }
          else {
            comma = true;
          }
          if (newArgument != null) {
            newMethodCall.append(newArgument.getText());
          }
        }
        newMethodCall.append('}');
      }
      else {
        if (newArguments.size() == 1 && newArguments.get(0) != null &&
            InheritanceUtil.isInheritor(newArguments.get(0).getType(), CommonClassNames.JAVA_LANG_THROWABLE)) {
          newMethodCall.append(", String.valueOf(").append(newArguments.get(0).getText()).append(")");
        }
        else {
          for (PsiExpression newArgument : newArguments) {
            newMethodCall.append(',');
            if (newArgument != null) {
              newMethodCall.append(newArgument.getText());
            }
          }
        }
      }
      newMethodCall.append(')');
      PsiReplacementUtil.replaceExpression(methodCallExpression, newMethodCall.toString());
    }

    private static void addLogStrings(StringBuilder methodCall, StringBuilder logText, boolean isStringBlock, int indent) {
      if (!isStringBlock) {
        methodCall.append('"')
          .append(StringUtil.escapeStringCharacters(logText.toString()))
          .append('"');
        logText.delete(0, logText.length());
        return;
      }
      String delimiters = "\n" + " ".repeat(indent);

      String preparedText = StreamEx.of(logText.toString().split("\n", -1))
        .map(line -> line.endsWith(" ") ? line.substring(0, line.length() - 1) + "\\s" : line)
        .joining(delimiters, delimiters, "");
      preparedText = PsiLiteralUtil.escapeTextBlockCharacters(preparedText, true, true, false);
      methodCall.append("\"\"\"")
        .append(preparedText)
        .append("\"\"\"");
      logText.delete(0, logText.length());
    }

    public static boolean isAvailable(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          return true;
        }
      }
      return false;
    }
  }

  private abstract static class FormatArgumentToLogCallFix extends PsiUpdateModCommandQuickFix implements EvaluatedStringFix {

    private final @NotNull Map<TextRange, Integer> myTextMapping;

    private final @NotNull String format;

    private FormatArgumentToLogCallFix(@NotNull Map<TextRange, Integer> textMapping,
                                       @NotNull String format) {
      myTextMapping = textMapping;
      this.format = format;
    }

    @Override
    public void fix(@NotNull PsiMethodCallExpression callExpression) {
      PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
      if (expressions.length < 1 || expressions.length > 2) {
        return;
      }

      PsiExpression expression = expressions[0];
      if (!(expression instanceof PsiMethodCallExpression formatCallExpression)) {
        return;
      }

      StringBuilder builder = new StringBuilder();
      CommentTracker tracker = new CommentTracker();
      for (PsiElement child : callExpression.getChildren()) {
        if (child instanceof PsiExpressionList expressionList) {
          builder.append(createNewArgumentsFromCall(formatCallExpression, tracker, expressionList.getExpressions()));
        }
        else {
          builder.append(tracker.text(child));
        }
      }

      tracker.replace(callExpression, builder.toString());
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element.getParent() instanceof PsiReferenceExpression referenceExpression &&
            referenceExpression.getParent() instanceof PsiMethodCallExpression callExpression)) {
        return;
      }
      fix(callExpression);
    }

    private @NotNull String createNewArgumentsFromCall(@NotNull PsiMethodCallExpression formatCallExpression,
                                                       @NotNull CommentTracker tracker,
                                                       PsiExpression @NotNull  [] allArguments) {
      List<String> arguments = new ArrayList<>();
      List<Map.Entry<TextRange, Integer>> placeholders =
        myTextMapping.entrySet()
          .stream()
          .sorted(Comparator.<Map.Entry<TextRange, Integer>>comparingInt(t -> t.getKey().getStartOffset()).reversed())
          .toList();
      String formatWithPlaceholders = format;
      PsiExpression[] expressions = formatCallExpression.getArgumentList().getExpressions();
      for (Map.Entry<TextRange, Integer> placeholder : placeholders) {
        formatWithPlaceholders = formatWithPlaceholders.substring(0, placeholder.getKey().getStartOffset()) + "{}" +
                                 formatWithPlaceholders.substring(placeholder.getKey().getEndOffset());

        arguments.add(tracker.text(expressions[placeholder.getValue()]));
      }
      arguments.add(formatWithPlaceholders);
      Collections.reverse(arguments);
      if (allArguments.length > 1) {
        for (int i = 1; i < allArguments.length; i++) {
          arguments.add(tracker.text(allArguments[i]));
        }
      }
      return "(" + String.join(", ", arguments) + ")";
    }
  }

  private static class MessageFormatArgumentToLogCallFix extends FormatArgumentToLogCallFix {

    private MessageFormatArgumentToLogCallFix(@NotNull Map<TextRange, Integer> result,
                                              @NotNull String format) {
      super(result, format);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix");
    }


    static @Nullable PsiUpdateModCommandQuickFix create(@NotNull PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression callExpression)) {
        return null;
      }
      PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
      if (arguments.length == 0) return null;
      PsiExpression firstArgument = arguments[0];
      if (firstArgument == null) return null;
      TextInfo textInfo = getTextInfo(firstArgument);
      if (textInfo == null) return null;
      String pattern = textInfo.formattedString();
      String text = textInfo.text();
      if (pattern == null || text.isEmpty()) return null;
      MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat(pattern);
      if (!result.valid()) {
        return null;
      }

      Map<TextRange, Integer> mapping = new HashMap<>();

      List<MessageFormatUtil.MessageFormatPlaceholder> placeholders = result.placeholders();
      for (MessageFormatUtil.MessageFormatPlaceholder placeholder : placeholders) {
        if (!placeholder.isString()) {
          return null;
        }

        if (placeholder.index() + 1 >= arguments.length) {
          return null;
        }

        TextRange actualRange = ExpressionUtils.findStringLiteralRange(firstArgument, placeholder.range().getStartOffset(),
                                                                 placeholder.range().getEndOffset());
        if (actualRange == null) {
          return null;
        }

        mapping.put(actualRange, placeholder.index() + 1);
      }
      Set<Integer> argumentIndexes = new HashSet<>(mapping.values());
      if (argumentIndexes.size() != arguments.length - 1) {
        return null;
      }
      return new MessageFormatArgumentToLogCallFix(mapping, text);
    }
  }

  private record TextInfo(String text, String formattedString) {
  }

  private static @Nullable TextInfo getTextInfo(@NotNull PsiExpression expression) {
    String text = null;
    String formattedString = null;
    if (expression instanceof PsiLiteralExpression literalExpression) {
      if (!(literalExpression.getValue() instanceof String value)) {
        return null;
      }
      formattedString = value;
      text = literalExpression.getText();
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression && ExpressionUtils.hasStringType(polyadicExpression)) {
      PsiConstantEvaluationHelper constantEvaluationHelper =
        JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
      Object o = constantEvaluationHelper.computeConstantExpression(polyadicExpression);
      if (o instanceof String value) {
        formattedString = value;
        text = polyadicExpression.getText();
      }
    }
    return new TextInfo(text, formattedString);
  }

  private static class StringFormatArgumentToLogCallFix extends FormatArgumentToLogCallFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix");
    }

    private StringFormatArgumentToLogCallFix(@NotNull Map<TextRange, Integer> result,
                                             @NotNull String format) {
      super(result, format);
    }

    static @Nullable PsiUpdateModCommandQuickFix create(@NotNull PsiExpression originalExpression) {
      if (!(originalExpression instanceof PsiMethodCallExpression callExpression)) {
        return null;
      }

      FormatDecode.FormatArgument formatArgument =
        FormatDecode.FormatArgument.extract(callExpression, List.of("format"), List.of("String"), true);
      if (formatArgument == null || formatArgument.getIndex() != 1) {
        return null;
      }
      PsiExpression expression = formatArgument.getExpression();
      if (expression == null) return null;
      TextInfo textInfo = getTextInfo(expression);
      if (textInfo == null) return null;
      String formattedString = textInfo.formattedString();
      String text = textInfo.text();
      if (formattedString == null || text == null) {
        return null;
      }
      PsiExpression[] arguments = Objects.requireNonNull(callExpression.getArgumentList()).getExpressions();
      int argumentCount = arguments.length - formatArgument.getIndex();
      FormatDecode.Validator[] validators;
      try {
        validators = FormatDecode.decodeNoVerify(formattedString, argumentCount);
      }
      catch (FormatDecode.IllegalFormatException e) {
        return null;
      }

      if (argumentCount != validators.length) return null;
      Map<TextRange, Integer> result = new HashMap<>();
      for (int i = 0; i < validators.length; i++) {
        int index = formatArgument.getIndex() + i;
        if (index >= arguments.length) return null;

        FormatDecode.Validator metaValidator = validators[i];
        if (metaValidator == null) continue;
        Collection<FormatDecode.Validator> unpacked = metaValidator instanceof FormatDecode.MultiValidator multi ?
                                                      multi.getValidators() : List.of(metaValidator);
        if (unpacked.size() != 1) return null;
        FormatDecode.Validator validator = unpacked.iterator().next();
        if (validator == null) return null;
        PsiExpression argument = arguments[index];
        if (!possibleToConvert(validator, argument)) return null;
        TextRange stringRange = validator.getRange();
        if (stringRange == null) return null;
        TextRange range = ExpressionUtils.findStringLiteralRange(expression, stringRange.getStartOffset(),
                                                                 stringRange.getEndOffset());
        if (range == null) return null;
        result.put(range, index);
      }
      int start = 0;
      while ((start = formattedString.indexOf("%n", start)) != -1) {
        int escaped = 0;
        while (true) {
          if (start - escaped == 0) {
            break;
          }
          if(formattedString.charAt(start - escaped - 1) == '%') {
            escaped++;
            continue;
          }
          break;
        }
        if (escaped % 2 == 1) {
          start++;
          continue;
        }
        TextRange range = ExpressionUtils.findStringLiteralRange(expression, start, start + 2);
        if (range == null) {
          return null;
        }
        text = StringUtil.replaceSubstring(text, range, "\\n");
        start++;
      }
      return new StringFormatArgumentToLogCallFix(result, text);
    }

    private static boolean possibleToConvert(@NotNull FormatDecode.Validator validator, PsiExpression argument) {
      FormatDecode.Spec spec = validator.getSpec();
      if (spec == null) return false;
      if (spec.conversion() == null ||
          !StringUtil.isEmpty(spec.width()) ||
          !StringUtil.isEmpty(spec.dateSpec()) ||
          !StringUtil.isEmpty(spec.flags()) ||
          !StringUtil.isEmpty(spec.precision())) {
        return false;
      }
      return switch (spec.conversion()) {
        case "s" -> true;
        case "b" -> argument.getType() != null && TypeConversionUtil.isBooleanType(argument.getType());
        case "d" -> argument.getType() != null && TypeConversionUtil.isIntegralNumberType(argument.getType());
        default -> false;
      };
    }
  }

  public enum ProblemType {
    CONCATENATION, STRING_FORMAT, MESSAGE_FORMAT
  }

  private class StringConcatenationArgumentToLogCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!logNames.contains(referenceName)) {
        return;
      }
      switch (warnLevel) {
        case 4:
          if ("debug".equals(referenceName)) return;
        case 3:
          if ("info".equals(referenceName)) return;
        case 2:
          if ("warn".equals(referenceName)) return;
        case 1:
          if ("error".equals(referenceName) || "fatal".equals(referenceName)) return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, SLF4J_LOGGER) &&
          !InheritanceUtil.isInheritor(containingClass, LOG4J_LOGGER) &&
          !InheritanceUtil.isInheritor(containingClass, LOG4J_BUILDER)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }

      LogConcatenationContext result = getLogConcatenationContext(arguments);
      if (result == null) return;

      registerMethodCallError(expression, result.problemType(), expression, result.argument());
    }
  }


  public record LogConcatenationContext(@NotNull PsiExpression argument, @NotNull ProblemType problemType) {
  }

  public static @Nullable LogConcatenationContext getLogConcatenationContext(PsiExpression @NotNull [] arguments) {
    PsiExpression argument = arguments[0];

    ProblemType problemType = null;

    if (argument instanceof PsiMethodCallExpression callExpression && (
      arguments.length == 1 ||
      (arguments.length == 2 && arguments[1] != null &&
       InheritanceUtil.isInheritor(arguments[1].getType(), CommonClassNames.JAVA_LANG_THROWABLE))
    )) {
      FormatDecode.FormatArgument formatArgument =
        FormatDecode.FormatArgument.extract(callExpression, List.of("format"), List.of("String"), true);
      if (formatArgument != null) {
        problemType = ProblemType.STRING_FORMAT;
      }
      else if (MESSAGE_FORMAT_FORMAT.test(callExpression)) {
        problemType = ProblemType.MESSAGE_FORMAT;
      }
    }

    if (problemType == null) {
      if (!ExpressionUtils.hasStringType(argument)) {
        if (arguments.length < 2) {
          return null;
        }
        argument = arguments[1];
      }
      if (!ExpressionUtils.hasStringType(argument)) {
        return null;
      }

      if (!containsNonConstantConcatenation(argument)) {
        return null;
      }
      problemType = ProblemType.CONCATENATION;
    }

    return new LogConcatenationContext(argument, problemType);
  }

  private static boolean containsNonConstantConcatenation(@Nullable PsiExpression expression) {
    if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      return containsNonConstantConcatenation(parenthesizedExpression.getExpression());
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      if (!ExpressionUtils.hasStringType(polyadicExpression)) {
        return false;
      }
      if (!JavaTokenType.PLUS.equals(polyadicExpression.getOperationTokenType())) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          return true;
        }
      }
    }
    return false;
  }
}