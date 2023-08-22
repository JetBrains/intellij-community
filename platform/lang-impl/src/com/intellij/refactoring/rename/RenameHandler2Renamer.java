// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

final class RenameHandler2Renamer implements Renamer {

  private final @NotNull Project myProject;
  private final @NotNull DataContext myDataContext; // This is wrong. Don't do drugs kids, and don't store DataContext.
  private final @NotNull RenameHandler myHandler;
  private final int myEventCount;

  RenameHandler2Renamer(@NotNull Project project,
                        @NotNull DataContext context,
                        @NotNull RenameHandler handler,
                        int count) {
    myProject = project;
    myDataContext = context;
    myHandler = handler;
    myEventCount = count;
  }

  @Override
  public @NotNull String getPresentableText() {
    return UIUtil.removeMnemonic(RenameHandlerRegistry.getHandlerTitle(myHandler));
  }

  @Override
  public void performRename() {
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      IdeEventQueue.getInstance().setEventCount(myEventCount); // Make DataContext valid again.
      BaseRefactoringAction.performRefactoringAction(myProject, myDataContext, myHandler);
    }
  }
}
