/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

  @Nullable
  private @NlsContexts.DialogTitle String myTitle;

  public interface Listener extends StepListener {
    void doNextAction();
  }

  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  public AbstractWizardStepEx(@Nullable final @NlsContexts.DialogTitle String title) {
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

  protected void setTitle(@Nullable final @NlsContexts.DialogTitle String title) {
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

  @NotNull
  public abstract Object getStepId();

  @Nullable
  public abstract Object getNextStepId();

  @Nullable
  public abstract Object getPreviousStepId();

  public abstract boolean isComplete();

  public abstract void commit(CommitType commitType) throws CommitStepException;

  @Nullable
  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  @Override
  public void dispose() {
  }

  @Override
  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  @NonNls
  public String getHelpId() {
    return null;
  }

}
