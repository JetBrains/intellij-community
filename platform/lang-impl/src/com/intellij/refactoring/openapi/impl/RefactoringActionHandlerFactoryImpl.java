/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;

/**
 * @author dsl
 */
public class RefactoringActionHandlerFactoryImpl extends RefactoringActionHandlerFactory {

  public RefactoringActionHandler createSafeDeleteHandler() {
    return new SafeDeleteHandler();
  }

  public RefactoringActionHandler createMoveHandler() {
    return new MoveHandler();
  }

  public RefactoringActionHandler createRenameHandler() {
    return new PsiElementRenameHandler();
  }
}
