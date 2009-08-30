package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeStringLiteralToCharInMethodCallFix implements IntentionAction {
  private final PsiLiteralExpression myLiteral;
  private final PsiCall myCall;

  public ChangeStringLiteralToCharInMethodCallFix(final PsiLiteralExpression literal, final PsiCall methodCall) {
    myLiteral = literal;
    myCall = methodCall;
  }

  @NotNull
  public String getText() {
    final String convertedValue = convertedValue();
    final boolean isString = isString(myLiteral.getType());
    return QuickFixBundle.message("fix.single.character.string.to.char.literal.text", myLiteral.getText(),
                                  quote(convertedValue, ! isString), isString ? PsiType.CHAR.getCanonicalText() : "String");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.single.character.string.to.char.literal.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myCall.isValid() && myLiteral.isValid() && myCall.getManager().isInProject(myCall);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final Object value = myLiteral.getValue();
    if ((value != null) && (value.toString().length() == 1)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

      final PsiExpression newExpression = factory.createExpressionFromText(quote(convertedValue(), ! isString(myLiteral.getType())),
                                                                           myLiteral.getParent());
      myLiteral.replace(newExpression);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  private String quote(final String value, final boolean doubleQuotes) {
    final char quote = doubleQuotes ? '"' : '\'';
    return quote + value + quote;
  }

  private String convertedValue() {
    String value = String.valueOf(myLiteral.getValue());
    return ("\"".equals(value) || "'".equals(value)) ? "\\" + value : value;
  }

  public static void createHighLighting(@NotNull final PsiMethod[] candidates, @NotNull final PsiConstructorCall call,
                                        @NotNull final List<HighlightInfo> out) {
    final Set<PsiLiteralExpression> literals = new HashSet<PsiLiteralExpression>();
    if (call.getArgumentList() == null) {
      return;
    }
    boolean exactMatch = false;
    for (PsiMethod method : candidates) {
      exactMatch |= findMatchingExpressions(call.getArgumentList().getExpressions(), method, literals);
    }
    if (! exactMatch) {
      processLiterals(literals, call, out);
    }
  }

  public static void createHighLighting(@NotNull final CandidateInfo[] candidates, @NotNull final PsiMethodCallExpression methodCall,
                                        @NotNull final List<HighlightInfo> out) {
    final Set<PsiLiteralExpression> literals = new HashSet<PsiLiteralExpression>();
    boolean exactMatch = false;
    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo) candidate).getElement();
        exactMatch |= findMatchingExpressions(methodCall.getArgumentList().getExpressions(), method, literals);
      }
    }
    if (! exactMatch) {
      processLiterals(literals, methodCall, out);
    }
  }

  private static void processLiterals(@NotNull final Set<PsiLiteralExpression> literals, @NotNull final PsiCall call,
                                        @NotNull final List<HighlightInfo> out) {
    for (PsiLiteralExpression literal : literals) {
      final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, literal, null);
      final ChangeStringLiteralToCharInMethodCallFix fix = new ChangeStringLiteralToCharInMethodCallFix(literal, call);
      QuickFixAction.registerQuickFixAction(info, fix);
      out.add(info);
    }
  }

  /**
   * @return <code>true</code> if exact TYPEs match
   */
  private static boolean findMatchingExpressions(final PsiExpression[] arguments, final PsiMethod existingMethod,
                                                                   final Set<PsiLiteralExpression> result) {
    final PsiParameterList parameterList = existingMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();

    if (arguments.length != parameters.length) {
      return false;
    }

    boolean typeMatch = true;
    for (int i = 0; (i < parameters.length) && (i < arguments.length); i++) {
      final PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      final PsiType argumentType = arguments[i].getType();

      typeMatch &= Comparing.equal(parameterType, argumentType);

      if ((arguments[i] instanceof PsiLiteralExpression) &&
          (! result.contains(arguments[i])) &&
          (charToString(parameterType, argumentType) || charToString(argumentType, parameterType))) {

        final String value = String.valueOf(((PsiLiteralExpression) arguments[i]).getValue());
        if ((value != null) && (value.length() == 1)) {
          result.add((PsiLiteralExpression) arguments[i]);
        }
      }
    }
    return typeMatch;
  }

  private static boolean charToString(final PsiType firstType, final PsiType secondType) {
    return Comparing.equal(PsiType.CHAR, firstType) && isString(secondType);
  }

  private static boolean isString(final PsiType type) {
    return (type != null) && "java.lang.String".equals((type.getCanonicalText()));
  }
}
