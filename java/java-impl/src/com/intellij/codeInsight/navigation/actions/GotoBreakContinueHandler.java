/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class GotoBreakContinueHandler extends GotoDeclarationHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.navigation.actions.GotoBreakContinueHandler");

  @Override
  @Nullable
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
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
