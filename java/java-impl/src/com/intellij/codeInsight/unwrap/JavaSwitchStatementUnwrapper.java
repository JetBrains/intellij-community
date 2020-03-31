// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class JavaSwitchStatementUnwrapper extends JavaUnwrapper {

  public JavaSwitchStatementUnwrapper() {
    super("Unwrap 'switch' statement");
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchLabeledRuleStatement switchLabeledRuleStatement = (PsiSwitchLabeledRuleStatement)e;
      return switchLabeledRuleStatement.getEnclosingSwitchBlock() instanceof PsiSwitchStatement;
    }
    if (e instanceof PsiStatement || e instanceof PsiWhiteSpace) {
      if (e.getNextSibling() instanceof PsiSwitchLabeledRuleStatement || e.getPrevSibling() instanceof PsiSwitchLabeledRuleStatement) {
        return false;
      }
      return e.getParent() instanceof PsiCodeBlock && e.getParent().getParent() instanceof PsiSwitchStatement;
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) {
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)element.getParent().getParent();
    if (element instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchLabeledRuleStatement switchLabeledRuleStatement = (PsiSwitchLabeledRuleStatement)element;
      PsiStatement body = switchLabeledRuleStatement.getBody();
      if (body instanceof PsiBlockStatement) {
        context.extractFromCodeBlock(((PsiBlockStatement)body).getCodeBlock(), switchStatement);
      }
      else {
        context.extractElement(body, switchStatement);
      }
    }
    else {
      while (!(element instanceof PsiSwitchLabelStatement) && element != null) {
        element = PsiTreeUtil.getPrevSiblingOfType(element, PsiStatement.class);
      }
      while (element instanceof PsiSwitchLabelStatement) {
        element = PsiTreeUtil.getNextSiblingOfType(element, PsiStatement.class);
      }
      if (!(element instanceof PsiBreakStatement) && element != null) {
        outer: while (true) {
          if (!(element instanceof PsiSwitchLabelStatement)) {
            if (element instanceof PsiBlockStatement) {
              final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
              final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
              PsiElement start = codeBlock.getFirstBodyElement();
              final PsiElement end = codeBlock.getLastBodyElement();
              while (start != end && start != null) {
                if (start instanceof PsiBreakStatement && ((PsiBreakStatement)start).getLabelIdentifier() == null) {
                  break outer;
                }
                removeBreakStatements(start, switchStatement);
                context.extractElement(start, switchStatement);
                start = start.getNextSibling();
              }
            }
            else {
              removeBreakStatements(element, switchStatement);
              context.extractElement(element, switchStatement);
            }
          }
          if (element instanceof PsiBreakStatement
              || element instanceof PsiContinueStatement
              || element instanceof PsiThrowStatement
              || element instanceof PsiReturnStatement) {
            break;
          }
          element = element.getNextSibling();
          if (element == null || element instanceof PsiJavaToken ||
              element instanceof PsiBreakStatement && ((PsiBreakStatement)element).getLabelIdentifier() == null) {
            break;
          }
        }
      }
    }
    context.delete(switchStatement);
  }

  private static void removeBreakStatements(PsiElement element, PsiSwitchStatement switchStatement) {
    for (PsiBreakStatement breakStatement : PsiTreeUtil.findChildrenOfType(element, PsiBreakStatement.class)) {
      if (breakStatement.getLabelIdentifier() == null && breakStatement.findExitedStatement() == switchStatement) {
        breakStatement.delete();
      }
    }
  }
}
