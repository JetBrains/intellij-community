// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;


public final class GotoBreakContinueHandler extends GotoDeclarationHandlerBase {
  private static final Logger LOG = Logger.getInstance(GotoBreakContinueHandler.class);

  @Override
  public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiKeyword) {
      IElementType type = ((PsiKeyword)elementAt).getTokenType();
      if (type == JavaTokenType.CONTINUE_KEYWORD) {
        if (elementAt.getParent() instanceof PsiContinueStatement) {
          return ((PsiContinueStatement)elementAt.getParent()).findContinuedStatement();
        }
      }
      else if (type == JavaTokenType.BREAK_KEYWORD) {
        if (elementAt.getParent() instanceof PsiBreakStatement) {
          PsiStatement statement = ((PsiBreakStatement)elementAt.getParent()).findExitedStatement();
          if (statement == null) return null;
          if (statement.getParent() instanceof PsiLabeledStatement) {
            statement = (PsiStatement)statement.getParent();
          }
          PsiElement nextSibling = statement.getNextSibling();
          while (!(nextSibling instanceof PsiStatement) && nextSibling != null) nextSibling = nextSibling.getNextSibling();
          //return nextSibling != null ? nextSibling : statement.getNextSibling();
          if (nextSibling != null) return nextSibling;
          nextSibling = statement.getNextSibling();
          if (nextSibling != null) return nextSibling;
          return statement.getLastChild();
        }
      }
    }
    else if (elementAt instanceof PsiIdentifier) {
      PsiElement parent = elementAt.getParent();
      PsiStatement statement = null;
      if (parent instanceof PsiContinueStatement) {
        statement = ((PsiContinueStatement)parent).findContinuedStatement();
      }
      else if (parent instanceof PsiBreakStatement) {
        statement = ((PsiBreakStatement)parent).findExitedStatement();
      }
      if (statement == null) return null;

      LOG.assertTrue(statement.getParent() instanceof PsiLabeledStatement);
      return ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier();
    }
    return null;
  }
}
