// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

/**
 * {@link IntentionAction} which performs some very expensive PSI computations, e.g., imports java class.
 * Usually this action instantiated in the background and performs very expensive computations in its constructor
 * and invalidates their results on any PSI modification.
 */
public abstract class ExpensivePsiIntentionAction implements IntentionAction {
  @SuppressWarnings("ActionIsNotPreviewFriendly") protected final Project myProject;
  private final long myPsiModificationCount;

  protected ExpensivePsiIntentionAction(@NotNull Project project) {
    // there are a lot of PSI computations and resolve going on here, ensure no freezes are reported
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = project;
    myPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
  }

  protected boolean isPsiModificationStampChanged() {
    long currentPsiModificationCount = PsiModificationTracker.getInstance(myProject).getModificationCount();
    return currentPsiModificationCount != myPsiModificationCount;
  }
}
