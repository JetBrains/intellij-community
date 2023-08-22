/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

/**
 * @author Bas Leijdekkers
 */
class NestedTryStatementsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken javaToken)) {
      return false;
    }
    final IElementType tokenType = javaToken.getTokenType();
    if (!JavaTokenType.TRY_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!isSimpleTry(parent)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    final PsiCodeBlock block = tryStatement.getTryBlock();
    if (block == null) {
      return false;
    }
    final PsiStatement[] statements = block.getStatements();
    if (statements.length != 1) {
      return false;
    }
    final PsiStatement statement = statements[0];
    return isSimpleTry(statement);
  }

  private static boolean isSimpleTry(PsiElement element) {
    if (!(element instanceof PsiTryStatement tryStatement)) {
      return false;
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      return false;
    }
    final PsiCodeBlock block = tryStatement.getTryBlock();
    return block != null;
  }
}
