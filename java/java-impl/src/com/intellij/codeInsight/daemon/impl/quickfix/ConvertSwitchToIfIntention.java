/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    return body != null && !body.isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doProcessIntention(mySwitchExpression);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return mySwitchExpression;
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
    CommentTracker commentTracker = new CommentTracker();
    commentTracker.markUnchanged(switchExpression);
    final boolean isSwitchOnString = switchExpressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
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

      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String variableName;
      if (isSwitchOnString) {
        variableName = javaCodeStyleManager.suggestUniqueVariableName("s", switchExpression, true);
      }
      else {
        variableName = javaCodeStyleManager.suggestUniqueVariableName("i", switchExpression, true);
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
    final List<SwitchStatementBranch> openBranches = new ArrayList<>();
    final Set<PsiLocalVariable> declaredVariables = new HashSet<>();
    final List<SwitchStatementBranch> allBranches = new ArrayList<>();
    SwitchStatementBranch currentBranch = null;
    final PsiElement[] children = body.getChildren();
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement statement = children[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        final PsiSwitchLabelStatement label = (PsiSwitchLabelStatement)statement;
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
          final String valueText = getCaseValueText(value, commentTracker);
          currentBranch.addCaseValue(valueText);
        }
      }
      else {
        if (statement instanceof PsiStatement) {
          if (statement instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
            final PsiElement[] elements = declarationStatement.getDeclaredElements();
            for (PsiElement varElement : elements) {
              final PsiLocalVariable variable = (PsiLocalVariable)varElement;
              declaredVariables.add(variable);
            }
          }
          for (SwitchStatementBranch branch : openBranches) {
            branch.addStatement(statement);
          }
          try {
            ControlFlow controlFlow =
              ControlFlowFactory.getInstance(project).getControlFlow(statement, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
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
        dumpBranch(branch, expressionText, firstBranch, useEquals, ifStatementText, commentTracker);
        firstBranch = false;
      }
    }
    if (defaultBranch != null) {
      dumpDefaultBranch(defaultBranch, firstBranch, ifStatementText, commentTracker);
    }
    if (ifStatementText.length() == 0) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (hadSideEffects) {
      final PsiStatement declarationStatement = factory.createStatementFromText(declarationString, switchStatement);
      switchStatement.getParent().addBefore(declarationStatement, switchStatement);
    }
    final PsiStatement ifStatement = factory.createStatementFromText(ifStatementText.toString(), switchStatement);
    commentTracker.replaceAndRestoreComments(switchStatement, ifStatement);
  }

  private static String getCaseValueText(PsiExpression value, CommentTracker commentTracker) {
    value = PsiUtil.skipParenthesizedExprDown(value);
    if (value == null) {
      return "";
    }
    if (!(value instanceof PsiReferenceExpression)) {
      return commentTracker.text(value);
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
    final PsiElement target = referenceExpression.resolve();

    if (!(target instanceof PsiEnumConstant)) {
      return commentTracker.text(value);
    }
    final PsiEnumConstant enumConstant = (PsiEnumConstant)target;
    final PsiClass aClass = enumConstant.getContainingClass();
    if (aClass == null) {
      return commentTracker.text(value);
    }
    return aClass.getQualifiedName() + '.' + commentTracker.text(referenceExpression);
  }

  private static void dumpBranch(SwitchStatementBranch branch,
                                 String expressionText,
                                 boolean firstBranch,
                                 boolean useEquals,
                                 @NonNls StringBuilder out,
                                 CommentTracker commentTracker) {
    if (!firstBranch) {
      out.append("else ");
    }
    dumpCaseValues(expressionText, branch.getCaseValues(), useEquals, out);
    dumpBody(branch, out, commentTracker);
  }

  private static void dumpDefaultBranch(SwitchStatementBranch defaultBranch,
                                        boolean firstBranch,
                                        @NonNls StringBuilder out,
                                        CommentTracker commentTracker) {
    if (!firstBranch) {
      out.append("else ");
    }
    dumpBody(defaultBranch, out, commentTracker);
  }

  private static void dumpCaseValues(String expressionText,
                                     List<String> caseValues,
                                     boolean useEquals,
                                     @NonNls StringBuilder out) {
    out.append("if(");
    boolean firstCaseValue = true;
    for (String caseValue : caseValues) {
      if (!firstCaseValue) {
        out.append("||");
      }
      firstCaseValue = false;
      if (useEquals) {
        out.append(caseValue).append(".equals(").append(expressionText).append(')');
      }
      else {
        out.append(expressionText).append("==").append(caseValue);
      }
    }
    out.append(')');
  }

  private static void dumpBody(SwitchStatementBranch branch, @NonNls StringBuilder out, CommentTracker commentTracker) {
    final List<PsiElement> bodyStatements = branch.getBodyElements();
    out.append('{');
    for (PsiLocalVariable variable : branch.getPendingVariableDeclarations()) {
      if (ReferencesSearch.search(variable, new LocalSearchScope(bodyStatements.toArray(PsiElement.EMPTY_ARRAY))).findFirst() != null) {
        out.append(variable.getType().getCanonicalText()).append(' ').append(variable.getName()).append(';');
      }
    }
    for (PsiElement bodyStatement : bodyStatements) {
      if (bodyStatement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)bodyStatement;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        for (PsiStatement statement : codeBlock.getStatements()) {
          appendElement(statement, out, commentTracker);
        }
      }
      else {
        appendElement(bodyStatement, out, commentTracker);
      }
    }
    out.append("\n}");
  }

  private static void appendElement(PsiElement element, @NonNls StringBuilder out, CommentTracker commentTracker) {
    if (element instanceof PsiBreakStatement) {
      final PsiBreakStatement breakStatement = (PsiBreakStatement)element;
      final PsiIdentifier identifier = breakStatement.getLabelIdentifier();
      if (identifier == null) {
        return;
      }
    }
    out.append(commentTracker.text(element));
  }
}
