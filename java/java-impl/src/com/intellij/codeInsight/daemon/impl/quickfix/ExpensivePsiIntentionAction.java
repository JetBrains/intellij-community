// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

/**
 * IntentionAction which is related to some very expensive PSI computations, e.g., import java class fix.
 * Usually this action instantiated in the background and performs very expensive computations in its constructor and invalidate their results on any PSI modification.
 */
public abstract class ExpensivePsiIntentionAction implements IntentionAction {
  protected final Project myProject;
  private final long myPsiModificationCount;

  protected ExpensivePsiIntentionAction(@NotNull Project project) {
    if (ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed()) {
      // there is a lot of PSI computations and resolve going on here, ensure no freezes are reported
      throw new IllegalStateException("Must be created in a background thread under the read action");
    }
    myProject = project;
    myPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
  }

  protected boolean isPsiModificationStampChanged() {
    long currentPsiModificationCount = PsiModificationTracker.getInstance(myProject).getModificationCount();
    return currentPsiModificationCount != myPsiModificationCount;
  }
}
