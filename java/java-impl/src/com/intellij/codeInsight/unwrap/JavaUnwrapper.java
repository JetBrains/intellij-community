// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

public abstract class JavaUnwrapper extends AbstractUnwrapper<JavaUnwrapper.Context> {

  public JavaUnwrapper(@Nls String description) {
    super(description);
  }

  @Override
  protected Context createContext() {
    return new Context();
  }

  protected static class Context extends AbstractUnwrapper.AbstractContext {

    public void extractFromBlockOrSingleStatement(PsiStatement block, PsiElement from) throws IncorrectOperationException {
      if (block instanceof PsiBlockStatement) {
        extractFromCodeBlock(((PsiBlockStatement)block).getCodeBlock(), from);
      }
      else if (block != null && !(block instanceof PsiEmptyStatement)) {
        extract(block, block, from);
      }
    }

    public void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
      if (block == null) return;
      extract(block.getFirstBodyElement(), block.getLastBodyElement(), from);
    }

    public void setElseBranch(PsiIfStatement ifStatement, PsiStatement elseBranch) throws IncorrectOperationException {
      PsiStatement toExtract = elseBranch;
      if (myIsEffective) {
        ifStatement.setElseBranch(copyElement(elseBranch));
        toExtract = ifStatement.getElseBranch();
      }
      addElementToExtract(toExtract);
    }

    @Override
    protected boolean isWhiteSpace(PsiElement element) {
      return element instanceof PsiWhiteSpace;
    }

    private static PsiStatement copyElement(PsiStatement e) throws IncorrectOperationException {
      // We cannot call el.copy() for 'else' since it sets context to parent 'if'.
      // This causes copy to be invalidated after parent 'if' is removed by setElseBranch method.
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(e.getProject());
      return factory.createStatementFromText(e.getText(), null);
    }

    public void setInitializer(PsiVariable variable, PsiExpression returnValue) {
      PsiExpression toExtract = returnValue;
      if (myIsEffective) {
        final PsiExpression initializer = copyExpression(returnValue);
        variable.setInitializer(initializer);
        toExtract = variable.getInitializer();
      }
      addElementToExtract(toExtract);
    }

    private static PsiExpression copyExpression(PsiExpression returnValue) {
      return JavaPsiFacade.getElementFactory(returnValue.getProject()).createExpressionFromText(returnValue.getText(), null);
    }
  }
}
