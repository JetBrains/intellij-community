// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHAR_SEQUENCE;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
public final class StringBufferReplaceableByStringInspection extends BaseInspection implements CleanupLocalInspectionTool {

  private static final String STRING_JOINER = "java.util.StringJoiner";
  private static final CallMatcher STRING_JOINER_ADD = CallMatcher.instanceCall(STRING_JOINER, "add").parameterCount(1);

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final String typeText = ((PsiType)infos[1]).getPresentableText();
    final boolean possibleSideEffect = (boolean)infos[2];
    return new StringBufferReplaceableByStringFix(typeText, possibleSideEffect);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    if (element instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("new.string.buffer.replaceable.by.string.problem.descriptor");
    }
    final String typeText = ((PsiType)infos[1]).getPresentableText();
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.problem.descriptor", typeText);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringVisitor();
  }

  private static boolean isConcatenatorConstruction(PsiNewExpression expression) {
    final PsiType type = expression.getType();
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) ||
        TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
      return true;
    }
    if (TypeUtils.typeEquals(STRING_JOINER, type)) {
      final PsiExpressionList args = expression.getArgumentList();
      if (args == null) return false;
      final PsiExpression[] expressions = args.getExpressions();
      return expressions.length == 1 && ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(expressions[0]), "");
    }
    return false;
  }

  private static boolean isConcatenatorType(PsiType type) {
    return TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) ||
           TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type) ||
           TypeUtils.typeEquals(STRING_JOINER, type);
  }

  private static boolean isAppendCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    if (STRING_JOINER_ADD.test(methodCallExpression)) return true;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"append".equals(methodName)) {
      return false;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 3) {
      return (arguments[0].getType() instanceof PsiArrayType || TypeUtils.isJavaLangString(arguments[0].getType())) &&
             PsiTypes.intType().equals(arguments[1].getType()) && PsiTypes.intType().equals(arguments[2].getType());
    }
    return arguments.length == 1;
  }

  private static boolean isToStringCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"toString".equals(methodName)) {
      return false;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    return arguments.length == 0;
  }

  @Nullable
  private static PsiExpression getCompleteExpression(PsiExpression qualifier) {
    while (true) {
      if (ExpressionUtils.isImplicitToStringCall(qualifier)) {
        return qualifier;
      }
      final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(qualifier);
      if (call == null) return null;
      if (isToStringCall(call)) {
        return call;
      }
      if (!isAppendCall(call)) return null;
      qualifier = call;
    }
  }

  private static class StringBufferReplaceableByStringFix extends PsiUpdateModCommandQuickFix {
    private final String myType;
    private final boolean myPossibleSideEffect;

    StringBufferReplaceableByStringFix(String type, boolean possibleSideEffect) {
      myType = type;
      myPossibleSideEffect = possibleSideEffect;
    }

    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myType, "String");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      new Fixer().fix(element);
    }
    
    private class Fixer {
      private int currentLine = -1;

      private void fix(PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiVariable variable)) {
          if (parent instanceof PsiNewExpression newExpression) {
            final PsiExpression stringBuilderExpression = getCompleteExpression(newExpression);
            if (stringBuilderExpression != null) {
              final CommentTracker tracker = new CommentTracker();
              final StringBuilder stringExpression = buildStringExpression(stringBuilderExpression, tracker, new StringBuilder());
              if (stringExpression != null) {
                String replacement = addConversionToStringIfNecessary(stringBuilderExpression, stringExpression.toString());
                tracker.replaceExpressionAndRestoreComments(stringBuilderExpression, replacement);
              }
            }
          }
          return;
        }
        final String variableName = variable.getName();
        if (variableName == null) {
          return;
        }
        final PsiTypeElement originalTypeElement = variable.getTypeElement();
        if (originalTypeElement == null) {
          return;
        }
        final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        if (initializer == null) {
          return;
        }
        final CommentTracker tracker = new CommentTracker();
        final StringBuilder builder;
        if (isAppendCall(initializer)) {
          builder = buildStringExpression(initializer, tracker, new StringBuilder());
          if (builder == null) {
            return;
          }
        } else if (initializer instanceof PsiNewExpression newExpression) {
          final PsiExpressionList argumentList = newExpression.getArgumentList();
          if (argumentList == null) {
            return;
          }
          final PsiExpression[] arguments = argumentList.getExpressions();
          if (arguments.length == 0 || TypeUtils.typeEquals(STRING_JOINER, newExpression.getType())) {
            builder = new StringBuilder();
          }
          else {
            final PsiExpression argument = arguments[0];
            if (PsiTypes.intType().equals(argument.getType())) {
              builder = new StringBuilder();
            }
            else if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
              builder = new StringBuilder("(").append(tracker.text(argument)).append(')');
            }
            else {
              builder = new StringBuilder(tracker.text(argument));
            }
          }
        } else {
          return;
        }
        final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (codeBlock == null) {
          return;
        }
        final StringExpressionCollector visitor = new StringExpressionCollector(variable);
        codeBlock.accept(visitor);
        final List<PsiExpression> expressions = visitor.getExpressions();
        for (PsiExpression expression : expressions) {
          if (expression == null || buildStringExpression(expression, tracker, builder) == null) {
            return;
          }
        }
        final String expressionText = addConversionToStringIfNecessary(element, builder.toString());
        final List<PsiElement> toDelete = new SmartList<>();
        toDelete.add(variable);
        for (int i = 0, size = expressions.size() - 1; i < size; i++) {
          toDelete.add(expressions.get(i).getParent());
        }

        final PsiExpression lastExpression = expressions.get(expressions.size() - 1);
        final boolean useVariable = myPossibleSideEffect || expressionText.contains("\n") && !isVariableInitializer(lastExpression);
        if (useVariable) {
          final PsiElement last = toDelete.remove(toDelete.size() - 1);
          final PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(last, PsiStatement.class);
          if (statement == null) {
            return;
          }
          final @NonNls String modifier =
            JavaCodeStyleSettings.getInstance(lastExpression.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "";
          toDelete.forEach(tracker::delete);
          final String statementText = modifier + CommonClassNames.JAVA_LANG_STRING + ' ' + variableName + "=" + expressionText + ';';
          tracker.replaceAndRestoreComments(statement, statementText);
          PsiReplacementUtil.replaceExpression(lastExpression, variableName);
        }
        else {
          tracker.replaceExpressionAndRestoreComments(lastExpression, expressionText, toDelete);
        }
      }

      @NotNull
      private static String addConversionToStringIfNecessary(PsiElement context, String concatText) {
        concatText = concatText.trim();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
        PsiExpression expression = factory.createExpressionFromText(concatText, context);
        PsiPolyadicExpression concat = tryCast(expression, PsiPolyadicExpression.class);
        if (concat != null && concat.getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiExpression[] operands = concat.getOperands();
          if (operands[0] != null && !TypeUtils.isJavaLangString(operands[0].getType()) && !TypeUtils.isJavaLangString(operands[1].getType())) {
            operands[0].replace(factory.createExpressionFromText(convertToString(operands[0]), context));
            return concat.getText();
          }
        }
        else if (!TypeUtils.isJavaLangString(expression.getType())) {
          return convertToString(expression);
        }
        return concatText;
      }

      private static @NonNls @NotNull String convertToString(PsiExpression expression) {
        PsiType type = expression.getType();
        PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
        if (stripped != null) {
          expression = stripped;
        }
        if (type instanceof PsiPrimitiveType && expression instanceof PsiLiteralExpression literalExpression) {
          Object value = literalExpression.getValue();
          if (value instanceof Character) {
            return '"' + StringUtil.escapeStringCharacters(value.toString()) + '"';
          }
          return "\"" + value + '"';
        }
        return "String.valueOf(" + expression.getText() + ")";
      }

      private static boolean isVariableInitializer(PsiExpression expression) {
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiVariable variable)) {
          return false;
        }
        final PsiExpression initializer = variable.getInitializer();
        return initializer == expression;
      }

      @Nullable
      StringBuilder buildStringExpression(@NotNull PsiElement element, @NotNull CommentTracker tracker, @NonNls StringBuilder result) {
        if (currentLine < 0) {
          currentLine = getLineNumber(element);
        }
        if (element instanceof PsiNewExpression newExpression) {
          final PsiExpressionList argumentList = newExpression.getArgumentList();
          if (argumentList == null) {
            return null;
          }
          appendFormattedPlusIfNeeded(argumentList, result, "");
          final PsiExpression[] arguments = argumentList.getExpressions();
          if (arguments.length == 1 && !TypeUtils.typeEquals(STRING_JOINER, newExpression.getType())) {
            final PsiExpression argument = arguments[0];
            final PsiType type = argument.getType();
            if (!PsiTypes.intType().equals(type)) {
              if (type != null && type.equalsToText(JAVA_LANG_CHAR_SEQUENCE)) {
                result.append("String.valueOf(").append(tracker.textWithComments(argument)).append(')');
              }
              else {
                result.append(tracker.textWithComments(argument, ParenthesesUtils.ADDITIVE_PRECEDENCE));
              }
            }
          }
          return result;
        }
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child instanceof PsiExpressionList ||  child instanceof PsiWhiteSpace
              || child instanceof PsiJavaToken || child instanceof PsiReferenceParameterList) {
            continue;
          }
          if (buildStringExpression(child, tracker, result) == null) {
            return null;
          }
        }

        if (element instanceof PsiMethodCallExpression methodCallExpression) {
          final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
          final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
          final String referenceName = methodExpression.getReferenceName();
          if ("toString".equals(referenceName)) {
            if (result.isEmpty()) {
              result.append("\"\"");
            }
          }
          else if ("append".equals(referenceName) || "add".equals(referenceName)){
            String commentsBefore = "";
            final ASTNode dot = ((CompositeElement)methodExpression.getNode()).findChildByRole(ChildRole.DOT);
            if (dot != null && !result.isEmpty()) {
              commentsBefore = tracker.commentsBefore(dot.getPsi());
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
              return null;
            }
            if (arguments.length == 3) {
              final PsiExpression firstArg = arguments[0];
              appendFormattedPlusIfNeeded(argumentList, result, commentsBefore);
              if (TypeUtils.isJavaLangString(firstArg.getType())) {
                result.append(tracker.textWithComments(firstArg, PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE))
                  .append(".substring(")
                  .append(tracker.textWithComments(arguments[1]))
                  .append(",")
                  .append(tracker.textWithComments(arguments[2]))
                  .append(")");
              } else {
                result.append("String.valueOf").append(tracker.textWithComments(argumentList));
              }
              return result;
            }
            final PsiExpression argument = arguments[0];
            final PsiType type = argument.getType();
            final String argumentText = tracker.textWithComments(argument);
            appendFormattedPlusIfNeeded(argument, result, commentsBefore);
            if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE ||
                (type instanceof PsiPrimitiveType && ParenthesesUtils.getPrecedence(argument) == ParenthesesUtils.ADDITIVE_PRECEDENCE)) {
              result.append('(').append(argumentText).append(')');
            }
            else if (type instanceof PsiArrayType) {
              result.append("String.valueOf(").append(argumentText).append(')');
            }
            else {
              if (StringUtil.startsWithChar(argumentText, '+')) {
                result.append(' ');
              }
              result.append(argumentText);
            }
          }
        }
        if (element instanceof PsiExpression expression && ExpressionUtils.isImplicitToStringCall(expression)) {
          if (result.isEmpty()) {
            result.append("\"\"");
          }
        }
        return result;
      }

      private void appendFormattedPlusIfNeeded(PsiElement anchor, StringBuilder out, String commentsBefore) {
        final boolean operationSignOnNextLine =
          CodeStyle.getLanguageSettings(anchor.getContainingFile(), JavaLanguage.INSTANCE).BINARY_OPERATION_SIGN_ON_NEXT_LINE;
        final int lineNumber = getLineNumber(anchor);
        final boolean insertNewLine = currentLine != lineNumber;
        final boolean notEmpty = !out.isEmpty();
        if (notEmpty && !operationSignOnNextLine) {
          out.append(insertNewLine ? '+' + commentsBefore : commentsBefore + '+');
        } else {
          out.append(commentsBefore);
        }
        if (insertNewLine && notEmpty && !hasTrailingLineBreak(out)) {
          out.append("\n "); // space is added to force line reformatting if the next line starts with comment
        }
        if (notEmpty && operationSignOnNextLine) {
          out.append('+');
        }
        currentLine = lineNumber;
      }

      private static boolean hasTrailingLineBreak(StringBuilder sb) {
        for(int i=sb.length()-1; i>=0; i--) {
          if (sb.charAt(i) == '\n') return true;
          if (!Character.isWhitespace(sb.charAt(i))) return false;
        }
        return false;
      }

      private static int getLineNumber(PsiElement element) {
        final Document document = element.getContainingFile().getViewProvider().getDocument();
        assert document != null;
        return document.getLineNumber(element.getTextRange().getStartOffset());
      }
    }

    private static class StringExpressionCollector extends JavaRecursiveElementWalkingVisitor {

      private final PsiVariable myVariable;
      private final List<PsiExpression> myExpressions = new SmartList<>();

      StringExpressionCollector(@NotNull PsiVariable variable) {
        myVariable = variable;
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          return;
        }
        final PsiElement target = expression.resolve();
        if (!myVariable.equals(target)) {
          return;
        }
        if (ExpressionUtils.isImplicitToStringCall(expression)) {
          myExpressions.add(expression);
        }
        else {
          PsiMethodCallExpression methodCallExpression = null;
          PsiElement parent = expression.getParent();
          PsiElement grandParent = parent.getParent();
          while (parent instanceof PsiReferenceExpression && grandParent instanceof PsiMethodCallExpression) {
            methodCallExpression = (PsiMethodCallExpression)grandParent;
            parent = methodCallExpression.getParent();
            grandParent = parent.getParent();
            if ("toString".equals(methodCallExpression.getMethodExpression().getReferenceName())) {
              break;
            }
          }
          myExpressions.add(methodCallExpression);
        }
      }

      public List<PsiExpression> getExpressions() {
        return myExpressions;
      }
    }
  }

  private static class StringBufferReplaceableByStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiType type = variable.getType();
      if (!isConcatenatorType(type)) return;
      final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
      if (!isNewStringConcatenatorChain(initializer)) return;
      final PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, null);
      if (codeBlock == null) return;
      final ReplaceableByStringVisitor visitor = new ReplaceableByStringVisitor(variable);
      codeBlock.accept(visitor);
      if (!visitor.isReplaceable()) return;
      registerVariableError(variable, variable, type, visitor.isPossibleSideEffect());
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!isConcatenatorConstruction(expression)) return;
      final PsiExpression completeExpression = getCompleteExpression(expression);
      if (completeExpression == null) return;
      registerNewExpressionError(expression, expression, type, false);
    }

    private static boolean isNewStringConcatenatorChain(PsiExpression expression) {
      while (true) {
        if (expression == null) return false;
        if (expression instanceof PsiNewExpression) {
          return isConcatenatorConstruction((PsiNewExpression)expression);
        }
        if (!(expression instanceof PsiMethodCallExpression call)) return false;
        if (!isAppendCall(call)) return false;
        expression = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
      }
    }
  }

  private static class ReplaceableByStringVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiElement myParent;
    private final PsiVariable myVariable;
    private boolean myReplaceable = true;
    private PsiExpression myPossibleSideEffect;
    private boolean myToStringFound;

    ReplaceableByStringVisitor(@NotNull PsiVariable variable) {
      myVariable = variable;
      myParent = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiIfStatement.class, PsiLoopStatement.class);
    }

    private void setPossibleSideEffect(PsiExpression expression) {
      if (myPossibleSideEffect != null && expression != null) return;
      myPossibleSideEffect = expression;
    }

    public boolean isReplaceable() {
      return myReplaceable && myToStringFound;
    }

    public boolean isPossibleSideEffect() {
      return myPossibleSideEffect != null;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!myReplaceable) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getTextOffset() > myVariable.getTextOffset() && !myToStringFound && !isArgumentOfStringBuilderMethod(expression)
          && !isToStringCallForCurrentBuilder(expression.getRExpression())) {
        setPossibleSideEffect(expression);
      }
    }

    private boolean isToStringCallForCurrentBuilder(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      PsiExpression downExpression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(downExpression instanceof PsiMethodCallExpression psiMethodCallExpression)) {
        return false;
      }
      return isToStringCall(psiMethodCallExpression) && isCallToStringBuilderMethod(psiMethodCallExpression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      if (expression.getTextOffset() > myVariable.getTextOffset() && !myToStringFound && !isArgumentOfStringBuilderMethod(expression)) {
        setPossibleSideEffect(expression);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (expression.getTextOffset() < myVariable.getTextOffset() || myToStringFound) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        setPossibleSideEffect(expression);
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        setPossibleSideEffect(expression);
        return;
      }
      final String name = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name) ||
        CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name) ||
        STRING_JOINER.equals(name)) {
        return;
      }
      if (isArgumentOfStringBuilderMethod(expression)) {
        return;
      }
      setPossibleSideEffect(expression);
    }

    private boolean isArgumentOfStringBuilderMethod(PsiExpression expression) {
      final PsiExpressionList parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, true, PsiStatement.class);
      if (parent == null) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
        return isCallToStringBuilderMethod(methodCallExpression) || isArgumentOfStringBuilderMethod(methodCallExpression);
      }
      if (grandParent instanceof PsiNewExpression newExpression) {
        final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(grandParent, PsiLocalVariable.class, true, PsiExpressionList.class);
        if (!myVariable.equals(variable)) {
          return false;
        }
        final PsiMethod constructor = newExpression.resolveMethod();
        if (constructor == null) {
          return false;
        }
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass == null) {
          return false;
        }
        final String name = aClass.getQualifiedName();
        return CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name) ||
               CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name);
      }
      return false;
    }

    private boolean isCallToStringBuilderMethod(PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      while (qualifier instanceof PsiMethodCallExpression call) {
        qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
      }
      if (!(qualifier instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!myVariable.equals(target)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String name1 = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name1) ||
             CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name1);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (!myReplaceable || expression.getTextOffset() < myVariable.getTextOffset()) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null || !expression.isReferenceTo(myVariable)) return;
      if (myToStringFound) {
        myReplaceable = false;
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class, PsiIfStatement.class, PsiLoopStatement.class);
      if (myParent != element) {
        myReplaceable = false;
        return;
      }
      if (ExpressionUtils.isImplicitToStringCall(expression)) {
        if (myPossibleSideEffect != null && PsiTreeUtil.isAncestor(myPossibleSideEffect, expression, true)) {
          setPossibleSideEffect(null);
        }
        myToStringFound = true;
        return;
      }
      PsiElement parent = expression.getParent();
      while (true) {
        if (!(parent instanceof PsiReferenceExpression)) {
          myReplaceable = false;
          return;
        }
        final PsiElement grandParent = parent.getParent();
        if (!isAppendCall(grandParent)) {
          if (!isToStringCall(grandParent)) {
            myReplaceable = false;
            return;
          }
          if (myPossibleSideEffect != null && PsiTreeUtil.isAncestor(myPossibleSideEffect, grandParent, true)) {
            // assume that a method call where the toString() is used as an argument, if present,
            // does not modify any state used in the string concatenation. If a case like this is found
            // in the wild this check can be removed, at the cost of an uglier quick fix.
            setPossibleSideEffect(null);
          }
          myToStringFound = true;
          return;
        }
        if (myPossibleSideEffect != null) {
          myReplaceable = false;
          return;
        }
        parent = grandParent.getParent();
        if (parent instanceof PsiExpressionStatement) {
          return;
        }
      }
    }
  }
}
