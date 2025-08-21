// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.codeInsight.BlockUtils.expandSingleStatementToBlockStatement;

/**
 * This class provides a quick fix to lift a throw expression out of a switch expression
 * when all execution branches of the switch expression end with a throw statement.
 */
@NotNullByDefault
public class LiftThrowOutOfSwitchExpressionFix extends PsiUpdateModCommandAction<PsiSwitchExpression> {
  public static @Nullable LiftThrowOutOfSwitchExpressionFix create(PsiSwitchExpression switchExpression) {
    return isAvailable(switchExpression) ? new LiftThrowOutOfSwitchExpressionFix(switchExpression) : null;
  }

  private static boolean isAvailable(PsiSwitchExpression switchExpression) {
    PsiStatement enclosingStatement = enclosingStatementOf(switchExpression);
    if (enclosingStatement == null) return false;
    // Fix is not available when switch is inside for's initializer OR condition OR update.
    if (enclosingStatement.getParent() instanceof PsiForStatement) return false;
    if (enclosingStatement instanceof PsiDeclarationStatement declarationStatement) {
      PsiElement[] elements = declarationStatement.getDeclaredElements();
      return 0 < elements.length && elements[0] instanceof PsiLocalVariable;
    }
    if (isInsideConditionalExpression(switchExpression)) return false;
    return (topmostExpressionOf(enclosingStatement) != null);
  }

  private static boolean isInsideConditionalExpression(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiConditionalExpression.class) instanceof PsiConditionalExpression;
  }

  private LiftThrowOutOfSwitchExpressionFix(PsiSwitchExpression expression) {
    super(expression);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getFamilyName() {
    return QuickFixBundle.message("lift.throw.out.of.switch.expression.fix.name");
  }

  @Override
  protected void invoke(ActionContext context, PsiSwitchExpression switchExpression, ModPsiUpdater updater) {
    PsiCodeBlock body = switchExpression.getBody();
    if (body == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    replaceThrowStatementsInsideSwitchBodyScope(body, factory);

    PsiStatement enclosingStatement = enclosingStatementOf(switchExpression);
    if (enclosingStatement == null) return;

    if (enclosingStatement instanceof PsiDeclarationStatement declarationStatement) {
      replaceDeclarationStatementWithThrowStatement(declarationStatement, switchExpression, factory, updater);
    }
    else {
      PsiExpression mainExpression = topmostExpressionOf(enclosingStatement);
      if (mainExpression != null) {
        replaceStatementWithThrowStatement(enclosingStatement, mainExpression, switchExpression, factory, updater);
      }
    }
  }

  private static @Nullable PsiStatement enclosingStatementOf(PsiSwitchExpression switchExpression) {
    return PsiTreeUtil.getParentOfType(switchExpression, PsiStatement.class);
  }

  private static void replaceThrowStatementsInsideSwitchBodyScope(PsiCodeBlock body, PsiElementFactory factory) {
    for (PsiStatement statement : body.getStatements()) {
      if (statement instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
        PsiStatement ruleBody = ruleStatement.getBody();
        if (ruleBody instanceof PsiThrowStatement throwStatement) {
          replaceThrowStatementWithExceptionExpression(throwStatement, factory);
        }
        else if (ruleBody != null) {
          replaceThrowStatementsWithYieldStatements(ruleBody, factory);
        }
      }
      else {
        replaceThrowStatementsWithYieldStatements(statement, factory);
      }
    }
  }

  private static void replaceThrowStatementWithExceptionExpression(PsiThrowStatement throwStatement, PsiElementFactory factory) {
    PsiExpression exception = throwStatement.getException();
    CommentTracker commentTracker = new CommentTracker();
    String exceptionText = exception != null ? commentTracker.text(exception) : "";
    commentTracker.replaceAndRestoreComments(throwStatement, factory.createStatementFromText(exceptionText + ";", throwStatement));
  }

  private static void replaceThrowStatementsWithYieldStatements(PsiElement element, PsiElementFactory factory) {
    for (PsiThrowStatement throwStatement : collectThrowStatements(element)) {
      replaceThrowStatementWithYieldStatement(throwStatement, factory);
    }
  }

  /**
   * Returns all throw statements that are contained in the scope of the psiElement
   * excluding those from scopes of nested classes, lambda expressions, switch expressions.
   */
  private static Set<PsiThrowStatement> collectThrowStatements(PsiElement psiElement) {
    SmartHashSet<PsiThrowStatement> throwStatements = new SmartHashSet<>();
    if (psiElement instanceof PsiThrowStatement throwStatement) {
      throwStatements.add(throwStatement);
      return throwStatements;
    }
    var visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitThrowStatement(PsiThrowStatement throwStatement) {
        throwStatements.add(throwStatement);
      }

      @Override
      public void visitClass(PsiClass aClass) { }

      @Override
      public void visitSwitchExpression(PsiSwitchExpression expression) { }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) { }
    };
    visitor.visitElement(psiElement);
    return throwStatements;
  }

  private static void replaceThrowStatementWithYieldStatement(PsiThrowStatement throwStatement, PsiElementFactory factory) {
    PsiExpression exception = throwStatement.getException();
    var commentTracker = new CommentTracker();
    var exceptionText = exception != null ? commentTracker.text(exception) : "";
    var yieldText = "yield " + exceptionText + ";";
    var yieldStatement = factory.createStatementFromText(yieldText, throwStatement);
    var newYield = commentTracker.replaceAndRestoreComments(throwStatement, yieldStatement);
    if (newYield.getParent() instanceof PsiSwitchLabeledRuleStatement) {
      expandSingleStatementToBlockStatement((PsiStatement)newYield);
    }
  }

  /**
   * Replaces the enclosingStatement with (in order)
   * 1) all side effects of the enclosingStatement that precede switchExpression within the enclosingStatement
   * 2) a throw statement with the switchExpression
   */
  private static void replaceDeclarationStatementWithThrowStatement(PsiDeclarationStatement enclosingStatement,
                                                                    PsiSwitchExpression switchExpression,
                                                                    PsiElementFactory factory,
                                                                    ModPsiUpdater updater) {
    var enclosingVariable = PsiTreeUtil.getParentOfType(switchExpression, PsiLocalVariable.class);
    for (PsiElement element : enclosingStatement.getDeclaredElements()) {
      if (element == enclosingVariable) {
        PsiExpression initializer = enclosingVariable.getInitializer();
        if (initializer != null) {
          replaceStatementWithThrowStatement(enclosingStatement, initializer, switchExpression, factory, updater);
        }
        break;
      }
      else {
        if (element instanceof PsiLocalVariable variable) {
          var variableDeclaration =
            factory.createVariableDeclarationStatement(variable.getName(), variable.getType(), variable.getInitializer());
          enclosingStatement.getParent().addBefore(variableDeclaration, enclosingStatement);
        }
      }
    }
  }

  private static @Nullable PsiExpression topmostExpressionOf(PsiStatement statement) {
    if (statement instanceof PsiReturnStatement returnStatement) {
      return returnStatement.getReturnValue();
    }
    else if (statement instanceof PsiExpressionStatement expressionStatement) {
      return expressionStatement.getExpression();
    }
    else if (statement instanceof PsiThrowStatement throwStatement) {
      return throwStatement.getException();
    }
    else if (statement instanceof PsiIfStatement ifStatement) {
      return ifStatement.getCondition();
    }
    else if (statement instanceof PsiWhileStatement whileStatement) {
      return whileStatement.getCondition();
    }
    else if (statement instanceof PsiForeachStatementBase foreachStatementBase) {
      return foreachStatementBase.getIteratedValue();
    }
    else if (statement instanceof PsiYieldStatement yieldStatement) {
      return yieldStatement.getExpression();
    }
    return null;
  }

  /**
   * Replaces the enclosingStatement with (in order):
   * 1) all side effects of the enclosingStatement that precede switchExpression within the enclosingStatement
   * 2) a throw statement with the switchExpression
   */
  private static void replaceStatementWithThrowStatement(PsiStatement enclosingStatement,
                                                         PsiExpression enclosingExpression,
                                                         PsiSwitchExpression switchExpression,
                                                         PsiElementFactory factory, ModPsiUpdater updater) {
    var precedingExpressions = findAllPrecedingExpressionsInsideAncestor(switchExpression, enclosingExpression);
    var expressions = SideEffectChecker.extractSideEffectExpressions(enclosingExpression, e -> !precedingExpressions.contains(e));
    PsiStatement[] statements = StatementExtractor.generateStatements(expressions, enclosingExpression);

    var commentTracker = new CommentTracker();
    var throwText = "throw " + commentTracker.text(switchExpression) + ";";
    var throwStatement =
      commentTracker.replaceAndRestoreComments(enclosingStatement, factory.createStatementFromText(throwText, switchExpression));
    if (!(throwStatement.getParent() instanceof PsiCodeBlock)) {
      throwStatement = expandSingleStatementToBlockStatement((PsiStatement)throwStatement);
    }
    for (PsiStatement statement : statements) {
      throwStatement.getParent().addBefore(statement, throwStatement);
    }
    PsiExpression exception = ((PsiThrowStatement)throwStatement).getException();
    if (exception != null) {
      updater.moveCaretTo(exception.getTextOffset());
    }
  }

  /**
   * Returns all expressions of the ancestor that are executed in runtime before the descendant.
   */
  private static Set<PsiExpression> findAllPrecedingExpressionsInsideAncestor(PsiElement descendant,
                                                                              PsiElement ancestor) {
    Set<PsiExpression> set = new SmartHashSet<>();
    PsiElement current = descendant;
    while (true) {
      var preceding = current.getPrevSibling();
      while (preceding == null) {
        var parent = current.getParent();
        if (parent == null || parent == ancestor) return set;
        current = parent;
        preceding = parent.getPrevSibling();
      }
      if (preceding instanceof PsiExpression psiExpression) {
        set.add(psiExpression);
      }
      current = preceding;
    }
  }
}
