// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class TargetEnvironmentWizardStep extends AbstractWizardStepEx {

  public TargetEnvironmentWizardStep(@Nullable @NlsContexts.DialogTitle String title) {
    super(title);
  }

  /**
   * This method is abstract in super class and can't be overridden in Kotlin since it exposes protected member
   * {@link AbstractWizardStepEx.CommitType}
   */
  @Override
  @RequiresEdt
  public final void commit(CommitType commitType) throws CommitStepException {
    doCommit(commitType);
  }

  @RequiresEdt
  protected abstract void doCommit(CommitType commitType) throws CommitStepException;
}
