// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.wizard;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractWizardStepEx implements Step, Disposable {

  public enum CommitType {
    Prev, Next, Finish
  }

  private @Nullable @NlsContexts.DialogTitle String myTitle;

  public interface Listener extends StepListener {
    void doNextAction();
  }

  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  public AbstractWizardStepEx(final @Nullable @NlsContexts.DialogTitle String title) {
    myTitle = title;
  }

  @Override
  public void _init() {
  }

  public final void _commitPrev() throws CommitStepException {
    commit(CommitType.Prev);
  }

  @Override
  public final void _commit(boolean finishChosen) throws CommitStepException {
    commit(finishChosen ? CommitType.Finish : CommitType.Next);
  }

  public void addStepListener(Listener listener) {
    myEventDispatcher.addListener(listener);
  }

  protected void setTitle(final @Nullable @NlsContexts.DialogTitle String title) {
    myTitle = title;
  }

  protected void fireStateChanged() {
    myEventDispatcher.getMulticaster().stateChanged();
  }

  protected void fireGoNext() {
    myEventDispatcher.getMulticaster().doNextAction();
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  public abstract @NotNull Object getStepId();

  public abstract @Nullable Object getNextStepId();

  public abstract @Nullable Object getPreviousStepId();

  public abstract boolean isComplete();

  public abstract void commit(CommitType commitType) throws CommitStepException;

  public @Nullable @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  @Override
  public void dispose() {
  }

  @Override
  public abstract @Nullable JComponent getPreferredFocusedComponent();

  public @NonNls String getHelpId() {
    return null;
  }

}
