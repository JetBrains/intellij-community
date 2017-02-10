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
package com.intellij.refactoring.util.occurrences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.RefactoringUtil;

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
  protected PsiExpression[] defaultOccurrences() {
    return new PsiExpression[]{myMainOccurence};
  }

  public PsiExpression getMainOccurence() {
    return myMainOccurence;
  }

  protected PsiExpression[] findOccurrences() {
    if("null".equals(myMainOccurence.getText())) {
      return defaultOccurrences();
    }
    if(myFilter != null && !myFilter.isOK(myMainOccurence)) {
      return defaultOccurrences();
    }
    final PsiExpression[] expressionOccurrences = findExpressionOccurrences();
    final PsiClass scopeClass = PsiTreeUtil.getNonStrictParentOfType(myScope, PsiClass.class);
    if (myMaintainStaticContext && expressionOccurrences.length > 1 && !RefactoringUtil.isInStaticContext(myMainOccurence, scopeClass)) {
      final ArrayList<PsiExpression> expressions = new ArrayList<>(Arrays.asList(expressionOccurrences));
      for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext();) {
        final PsiExpression expression = iterator.next();
        if(RefactoringUtil.isInStaticContext(expression, scopeClass)) {
          iterator.remove();
        }
      }
      return expressions.toArray(new PsiExpression[expressions.size()]);
    }
    else {
      return expressionOccurrences;
    }
  }

  public PsiElement getScope() {
    return myScope;
  }

  public PsiExpression[] findExpressionOccurrences() {
    if (myMainOccurence instanceof PsiLiteralExpression && !myMainOccurence.isPhysical()) {
      final FindManager findManager = FindManager.getInstance(getScope().getProject());
      final FindModel findModel = (FindModel)findManager.getFindInFileModel().clone();
      findModel.setCaseSensitive(true);
      findModel.setRegularExpressions(false);
      String value = StringUtil.stripQuotesAroundValue(myMainOccurence.getText());
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
              IntroduceVariableBase.getSelectedExpression(file.getProject(), file, startOffset, offset + endOffset);
            if (expression != null && IntroduceVariableBase.getErrorMessage(expression) == null) {
              results.add(expression);
              literals.add(literalExpression);
            }
          }
          result = findManager.findString(text, endOffset, findModel);
        }
        return results.toArray(new PsiExpression[results.size()]);
      }
    }
    return CodeInsightUtil.findExpressionOccurrences(myScope, myMainOccurence);
  }
}
