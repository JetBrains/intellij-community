// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;

final class RefactoringActionHandlerFactoryImpl extends RefactoringActionHandlerFactory {
  @Override
  public RefactoringActionHandler createSafeDeleteHandler() {
    return new SafeDeleteHandler();
  }

  @Override
  public RefactoringActionHandler createMoveHandler() {
    return new MoveHandler();
  }

  @Override
  public RefactoringActionHandler createRenameHandler() {
    return new PsiElementRenameHandler();
  }
}
