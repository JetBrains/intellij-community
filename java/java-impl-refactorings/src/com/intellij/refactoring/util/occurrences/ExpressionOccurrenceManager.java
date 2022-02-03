// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.occurrences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dsl
 */
public class ExpressionOccurrenceManager extends BaseOccurrenceManager {
  private final PsiExpression myMainOccurence;
  private final PsiElement myScope;
  private final boolean myMaintainStaticContext;

  public ExpressionOccurrenceManager(PsiExpression mainOccurence, PsiElement scope, OccurrenceFilter filter) {
    this(mainOccurence, scope, filter, false);
  }

  public ExpressionOccurrenceManager(PsiExpression mainOccurence, PsiElement scope, OccurrenceFilter filter, boolean maintainStaticContext) {
    super(filter);
    myMainOccurence = mainOccurence;
    myScope = scope;
    myMaintainStaticContext = maintainStaticContext;
  }
  @Override
  protected PsiExpression @NotNull [] defaultOccurrences() {
    return new PsiExpression[]{myMainOccurence};
  }

  public PsiExpression getMainOccurence() {
    return myMainOccurence;
  }

  @Override
  protected PsiExpression @NotNull [] findOccurrences() {
    if("null".equals(myMainOccurence.getText())) {
      return defaultOccurrences();
    }
    if(myFilter != null && !myFilter.isOK(myMainOccurence)) {
      return defaultOccurrences();
    }
    final PsiExpression[] expressionOccurrences = findExpressionOccurrences();
    final PsiClass scopeClass = PsiTreeUtil.getNonStrictParentOfType(myScope, PsiClass.class);
    if (myMaintainStaticContext && expressionOccurrences.length > 1 && !CommonJavaRefactoringUtil.isInStaticContext(myMainOccurence, scopeClass)) {
      final ArrayList<PsiExpression> expressions = new ArrayList<>(Arrays.asList(expressionOccurrences));
      expressions.removeIf(expression -> CommonJavaRefactoringUtil.isInStaticContext(expression, scopeClass));
      return expressions.toArray(PsiExpression.EMPTY_ARRAY);
    }
    else {
      return expressionOccurrences;
    }
  }

  public PsiElement getScope() {
    return myScope;
  }

  public PsiExpression @NotNull [] findExpressionOccurrences() {
    if (myMainOccurence instanceof PsiLiteralExpression && !myMainOccurence.isPhysical()) {
      final FindManager findManager = FindManager.getInstance(getScope().getProject());
      final FindModel findModel = findManager.getFindInFileModel().clone();
      findModel.setCaseSensitive(true);
      findModel.setRegularExpressions(false);
      String value = StringUtil.unquoteString(myMainOccurence.getText());
      if (value.length() > 0) {
        findModel.setStringToFind(value);
        final List<PsiExpression> results = new ArrayList<>();
        final PsiFile file = getScope().getContainingFile();
        final String text = getScope().getText();
        final int offset = getScope().getTextRange().getStartOffset();
        FindResult result = findManager.findString(text, 0, findModel);
        final Set<PsiLiteralExpression> literals = new HashSet<>();
        while (result.isStringFound()) {
          final int startOffset = offset + result.getStartOffset();
          final int endOffset = result.getEndOffset();
          final PsiLiteralExpression literalExpression =
            PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), PsiLiteralExpression.class);
          if (literalExpression != null && !literals.contains(literalExpression)) { //enum. occurrences inside string literals
            final PsiExpression expression =
              IntroduceVariableUtil.getSelectedExpression(file.getProject(), file, startOffset, offset + endOffset);
            if (expression != null && IntroduceVariableUtil.getErrorMessage(expression) == null) {
              results.add(expression);
              literals.add(literalExpression);
            }
          }
          result = findManager.findString(text, endOffset, findModel);
        }
        return results.toArray(PsiExpression.EMPTY_ARRAY);
      }
    }
    return CodeInsightUtil.findExpressionOccurrences(myScope, myMainOccurence);
  }
}
