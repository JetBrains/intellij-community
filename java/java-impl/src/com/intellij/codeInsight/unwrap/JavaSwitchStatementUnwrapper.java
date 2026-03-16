// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class JavaSwitchStatementUnwrapper extends JavaUnwrapper {

  public JavaSwitchStatementUnwrapper() {
    super(JavaBundle.message("unwrap.switch.statement"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStatement) {
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
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) {
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)element.getParent().getParent();
    if (element instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStatement) {
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
            if (element instanceof PsiBlockStatement blockStatement) {
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
