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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
* User: anna
* Date: 2/22/12
*/
public class ConvertSwitchToIfIntention implements IntentionAction {
  private final PsiSwitchStatement mySwitchExpression;

  public ConvertSwitchToIfIntention(@NotNull PsiSwitchStatement switchStatement) {
    mySwitchExpression = switchStatement;
  }

  @NotNull
  @Override
  public String getText() {
    return "Replace 'switch' with 'if'";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiCodeBlock body = mySwitchExpression.getBody();
    return body != null && body.getStatements().length > 0;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doProcessIntention(mySwitchExpression);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void doProcessIntention(@NotNull PsiSwitchStatement switchStatement) {
    final PsiExpression switchExpression = switchStatement.getExpression();
    if (switchExpression == null) {
      return;
    }
    final PsiType switchExpressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(switchExpression);
    if (switchExpressionType == null) {
      return;
    }
    final boolean isSwitchOnString =
      switchExpressionType.equalsToText(JAVA_LANG_STRING);
    boolean useEquals = isSwitchOnString;
    if (!useEquals) {
      final PsiClass aClass = PsiUtil.resolveClassInType(switchExpressionType);
      useEquals = aClass != null && !aClass.isEnum();
    }
    final String declarationString;
    final boolean hadSideEffects;
    final String expressionText;
    final Project project = switchStatement.getProject();
    if (RemoveUnusedVariableUtil.checkSideEffects(switchExpression, null, new ArrayList<>())) {
      hadSideEffects = true;

      final JavaCodeStyleManager javaCodeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      final String variableName;
      if (isSwitchOnString) {
        variableName = javaCodeStyleManager.suggestUniqueVariableName(
          "s", switchExpression, true);
      }
      else {
        variableName = javaCodeStyleManager.suggestUniqueVariableName(
          "i", switchExpression, true);
      }
      expressionText = variableName;
      declarationString = switchExpressionType.getCanonicalText() + ' ' + variableName + " = " + switchExpression.getText() + ';';
    }
    else {
      hadSideEffects = false;
      declarationString = null;
      expressionText = ParenthesesUtils.getPrecedence(switchExpression) > ParenthesesUtils.EQUALITY_PRECEDENCE
                       ? '(' + switchExpression.getText() + ')'
                       : switchExpression.getText();
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return;
    }
    final List<SwitchStatementBranch> openBranches =
      new ArrayList<>();
    final Set<PsiLocalVariable> declaredVariables =
      new HashSet<>();
    final List<SwitchStatementBranch> allBranches =
      new ArrayList<>();
    SwitchStatementBranch currentBranch = null;
    final PsiElement[] children = body.getChildren();
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement statement = children[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        final PsiSwitchLabelStatement label =
          (PsiSwitchLabelStatement)statement;
        if (currentBranch == null) {
          openBranches.clear();
          currentBranch = new SwitchStatementBranch();
          currentBranch.addPendingVariableDeclarations(declaredVariables);
          allBranches.add(currentBranch);
          openBranches.add(currentBranch);
        }
        else if (currentBranch.hasStatements()) {
          currentBranch = new SwitchStatementBranch();
          allBranches.add(currentBranch);
          openBranches.add(currentBranch);
        }
        if (label.isDefaultCase()) {
          currentBranch.setDefault();
        }
        else {
          final PsiExpression value = label.getCaseValue();
          final String valueText = getCaseValueText(value);
          currentBranch.addCaseValue(valueText);
        }
      }
      else {
        if (statement instanceof PsiStatement) {
          if (statement instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement =
              (PsiDeclarationStatement)statement;
            final PsiElement[] elements =
              declarationStatement.getDeclaredElements();
            for (PsiElement varElement : elements) {
              final PsiLocalVariable variable =
                (PsiLocalVariable)varElement;
              declaredVariables.add(variable);
            }
          }
          for (SwitchStatementBranch branch : openBranches) {
            branch.addStatement(statement);
          }
          try {
            ControlFlow controlFlow = ControlFlowFactory
                         .getInstance(project).getControlFlow(statement, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
            int startOffset = controlFlow.getStartOffset(statement);
            int endOffset = controlFlow.getEndOffset(statement);
            if (startOffset != -1 && endOffset != -1 && !ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset)) {
              currentBranch = null;
            }
          }
          catch (AnalysisCanceledException e) {
            currentBranch = null;
          }
        }
        else {
          for (SwitchStatementBranch branch : openBranches) {
            if (statement instanceof PsiWhiteSpace) {
              branch.addWhiteSpace(statement);
            }
            else {
              branch.addComment(statement);
            }
          }
        }
      }
    }
    final StringBuilder ifStatementText = new StringBuilder();
    boolean firstBranch = true;
    SwitchStatementBranch defaultBranch = null;
    for (SwitchStatementBranch branch : allBranches) {
      if (branch.isDefault()) {
        defaultBranch = branch;
      }
      else {
        final List<String> caseValues = branch.getCaseValues();
        final List<PsiElement> bodyElements = branch.getBodyElements();
        final Set<PsiLocalVariable> pendingVariableDeclarations =
          branch.getPendingVariableDeclarations();
        dumpBranch(expressionText, caseValues, bodyElements,
                   pendingVariableDeclarations, firstBranch,
                   useEquals, ifStatementText);
        firstBranch = false;
      }
    }
    if (defaultBranch != null) {
      final List<PsiElement> bodyElements =
        defaultBranch.getBodyElements();
      final Set<PsiLocalVariable> pendingVariableDeclarations =
        defaultBranch.getPendingVariableDeclarations();
      dumpDefaultBranch(bodyElements, pendingVariableDeclarations,
                        firstBranch, ifStatementText);
    }
    if (ifStatementText.length() == 0) return;
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    if (hadSideEffects) {
      final PsiStatement declarationStatement =
        factory.createStatementFromText(declarationString,
                                        switchStatement);
      final PsiStatement ifStatement =
        factory.createStatementFromText(ifStatementText.toString(),
                                        switchStatement);
      final PsiElement parent = switchStatement.getParent();
      parent.addBefore(declarationStatement, switchStatement);
      switchStatement.replace(ifStatement);
    }
    else {
      final PsiStatement newStatement =
        factory.createStatementFromText(ifStatementText.toString(),
                                        switchStatement);
      switchStatement.replace(newStatement);
    }
  }

  private static String getCaseValueText(PsiExpression value) {
    if (value == null) {
      return "";
    }
    if (value instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)value;
      final PsiExpression expression =
        parenthesizedExpression.getExpression();
      return getCaseValueText(expression);
    }
    if (!(value instanceof PsiReferenceExpression)) {
      return value.getText();
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)value;
    final PsiElement target = referenceExpression.resolve();
    final String text = referenceExpression.getText();
    if (!(target instanceof PsiEnumConstant)) {
      return value.getText();
    }
    final PsiEnumConstant enumConstant = (PsiEnumConstant)target;
    final PsiClass aClass = enumConstant.getContainingClass();
    if (aClass == null) {
      return value.getText();
    }
    final String name = aClass.getQualifiedName();
    return name + '.' + text;
  }

  private static void dumpBranch(
    String expressionText, List<String> caseValues,
    List<PsiElement> bodyStatements,
    Set<PsiLocalVariable> variables, boolean firstBranch,
    boolean useEquals,
    @NonNls StringBuilder ifStatementString) {
    if (!firstBranch) {
      ifStatementString.append("else ");
    }
    dumpCaseValues(expressionText, caseValues, useEquals,
                   ifStatementString);
    dumpBody(bodyStatements, variables, ifStatementString);
  }

  private static void dumpDefaultBranch(
    List<PsiElement> bodyStatements,
    Set<PsiLocalVariable> variables, boolean firstBranch,
    @NonNls StringBuilder ifStatementString) {
    if (!firstBranch) {
      ifStatementString.append("else ");
    }
    dumpBody(bodyStatements, variables, ifStatementString);
  }

  private static void dumpCaseValues(
    String expressionText, List<String> caseValues, boolean useEquals,
    @NonNls StringBuilder ifStatementString) {
    ifStatementString.append("if(");
    boolean firstCaseValue = true;
    for (String caseValue : caseValues) {
      if (!firstCaseValue) {
        ifStatementString.append("||");
      }
      firstCaseValue = false;
      ifStatementString.append(expressionText);
      if (useEquals) {
        ifStatementString.append(".equals(");
        ifStatementString.append(caseValue);
        ifStatementString.append(')');
      }
      else {
        ifStatementString.append("==");
        ifStatementString.append(caseValue);
      }
    }
    ifStatementString.append(')');
  }

  private static void dumpBody(List<PsiElement> bodyStatements,
                               Set<PsiLocalVariable> variables,
                               @NonNls StringBuilder ifStatementString) {
    ifStatementString.append('{');
    for (PsiLocalVariable variable : variables) {
      if (ReferencesSearch.search(variable, new LocalSearchScope(bodyStatements.toArray(new PsiElement[bodyStatements.size()]))).findFirst() != null) {
        final PsiType varType = variable.getType();
        ifStatementString.append(varType.getCanonicalText());
        ifStatementString.append(' ');
        ifStatementString.append(variable.getName());
        ifStatementString.append(';');
      }
    }
    for (PsiElement bodyStatement : bodyStatements) {
      if (bodyStatement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)bodyStatement;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
          appendElement(statement, ifStatementString);
        }
      }
      else {
        appendElement(bodyStatement, ifStatementString);
      }
    }
    ifStatementString.append("\n}");
  }

  private static void appendElement(
    PsiElement element, @NonNls StringBuilder ifStatementString) {
    if (element instanceof PsiBreakStatement) {
      final PsiBreakStatement breakStatement =
        (PsiBreakStatement)element;
      final PsiIdentifier identifier =
        breakStatement.getLabelIdentifier();
      if (identifier == null) {
        return;
      }
    }
    final String text = element.getText();
    ifStatementString.append(text);
  }
}
