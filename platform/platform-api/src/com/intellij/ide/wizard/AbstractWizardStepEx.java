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
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractWizardStepEx implements Step, Disposable {

  protected enum CommitType {
    Prev, Next, Finish
  }

  private String myTitle;

  public interface Listener extends StepListener {
    void doNextAction();
  }

  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  public AbstractWizardStepEx(final String title) {
    myTitle = title;
  }

  public void _init() {
  }

  public final void _commitPrev() throws CommitStepException {
    commit(CommitType.Prev);
  }

  public final void _commit(boolean finishChosen) throws CommitStepException {
    commit(finishChosen ? CommitType.Finish : CommitType.Next);
  }

  public void addStepListener(Listener listener) {
    myEventDispatcher.addListener(listener);
  }

  protected void setTitle(final String title) {
    myTitle = title;
  }

  protected void fireStateChanged() {
    myEventDispatcher.getMulticaster().stateChanged();
  }

  protected void fireGoNext() {
    myEventDispatcher.getMulticaster().doNextAction();
  }

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

  public String getTitle() {
    return myTitle;
  }

  public void dispose() {
  }

  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  public String getHelpId() {
    return null;
  }

}
