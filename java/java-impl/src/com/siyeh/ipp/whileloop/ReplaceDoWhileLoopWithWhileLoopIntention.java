// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.whileloop;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceDoWhileLoopWithWhileLoopIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.do.while.loop.with.while.loop.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.do.while.loop.with.while.loop.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new DoWhileLoopPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)element.getParent();
    if (doWhileStatement == null) {
      return;
    }
    final PsiStatement body = doWhileStatement.getBody();
    final PsiElement parent = doWhileStatement.getParent();
    final PsiExpression condition = doWhileStatement.getCondition();
    @NonNls final StringBuilder replacementText = new StringBuilder();
    CommentTracker commentTracker = new CommentTracker();
    if (BoolUtils.isTrue(condition)) {
      // no trickery needed
      replacementText.append("while(").append(commentTracker.text(condition)).append(')');
      if (body != null) {
        replacementText.append(commentTracker.text(body));
      }
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString(), commentTracker);
      return;
    }
    final boolean noBraces = !(parent instanceof PsiCodeBlock);
    if (noBraces) {
      final PsiElement[] parentChildren = parent.getChildren();
      for (PsiElement child : parentChildren) {
        if (child == doWhileStatement) {
          break;
        }
        replacementText.append(commentTracker.text(child));
      }
      replacementText.append('{');
    }
    if (body instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        for (int i = 1, length = children.length - 1; i < length; i++) {
          final PsiElement child = children[i];
          if (child instanceof PsiDeclarationStatement declarationStatement) {
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
              if (declaredElement instanceof PsiVariable variable) {
                final PsiModifierList modifierList = variable.getModifierList();
                if (modifierList != null) {
                  modifierList.setModifierProperty(PsiModifier.FINAL, false);
                }
              }
            }
          }
          if (noBraces) {
            replacementText.append(commentTracker.text(child));
          }
          else {
            parent.addBefore(child, doWhileStatement);
          }
        }
      }
    }
    else if (body != null) {
      if (noBraces) {
        replacementText.append(commentTracker.text(body)).append("\n");
      }
      else {
        parent.addBefore(body, doWhileStatement);
      }
    }
    replacementText.append("while(");
    if (condition != null) {
      replacementText.append(commentTracker.text(condition));
    }
    replacementText.append(')');
    if (body instanceof PsiBlockStatement) {
      replacementText.append('{');
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        for (int i = 1; i < children.length - 1; i++) {
          final PsiElement child = children[i];
          if (child instanceof PsiDeclarationStatement declarationStatement) {
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
              if (declaredElement instanceof PsiVariable variable) {
                // prevent duplicate variable declarations.
                final PsiExpression initializer = variable.getInitializer();
                if (initializer != null) {
                  replacementText.append(variable.getName()).append(" = ").append(commentTracker.text(initializer)).append(';');
                }
              }
            }
          }
          else {
            replacementText.append(commentTracker.text(child));
          }
        }
      }
      replacementText.append('}');
    }
    else if (body != null) {
      replacementText.append(commentTracker.text(body)).append("\n");
    }
    if (noBraces) {
      replacementText.append('}');
    }
    if (noBraces) {
      PsiReplacementUtil.replaceStatement((PsiStatement)parent, replacementText.toString(), commentTracker);
    }
    else {
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString(), commentTracker);
    }
  }
}
