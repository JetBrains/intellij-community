/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.HelpIdProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RunContentDescriptor implements Disposable {
  private ExecutionConsole myExecutionConsole;
  private ProcessHandler myProcessHandler;
  private JComponent myComponent;
  private final String myDisplayName;
  private final Icon myIcon;
  private final String myHelpId;

  private boolean myActivateToolWindowWhenAdded = true;
  private boolean myReuseToolWindowActivation = false;
  private long myExecutionId = 0;
  private Computable<JComponent> myFocusComputable = null;
  private boolean myAutoFocusContent = false;

  private Content myContent;
  private Runnable myRestarter;

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              String displayName,
                              @Nullable Icon icon) {
    myExecutionConsole = executionConsole;
    myProcessHandler = processHandler;
    myComponent = component;
    myDisplayName = displayName;
    myIcon = icon;
    myHelpId = myExecutionConsole instanceof HelpIdProvider ? ((HelpIdProvider)myExecutionConsole).getHelpId() : null;
  }

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              String displayName) {
    this(executionConsole, processHandler, component, displayName, null);
  }

  public RunContentDescriptor(@NotNull RunProfile profile, @NotNull ExecutionResult executionResult, @NotNull RunnerLayoutUi ui) {
    this(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), ui.getComponent(), profile.getName(), profile.getIcon());
  }

  public ExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  @Override
  public void dispose() {
    if (myExecutionConsole != null) {
      Disposer.dispose(myExecutionConsole);
      myExecutionConsole = null;
    }
    myComponent = null;
    myRestarter = null;
  }

  /**
   * Returns the icon to show in the Run or Debug toolwindow tab corresponding to this content.
   *
   * @return the icon to show, or null if the executor icon should be used.
   */
  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  public boolean isContentReuseProhibited() {
    return false;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public String getHelpId() {
    return myHelpId;
  }

  @Nullable
  public Content getAttachedContent() {
    return myContent;
  }

  public void setAttachedContent(@NotNull Content content) {
    myContent = content;
  }

  @Nullable
  @Deprecated
  /**
   * @deprecated Use {@link com.intellij.execution.runners.ExecutionUtil#restart(RunContentDescriptor)} instead
   * to remove in IDEA 15
   */
  public Runnable getRestarter() {
    return myRestarter;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated to remove in IDEA 15
   */
  public void setRestarter(@Nullable Runnable runnable) {
    myRestarter = runnable;
  }

  public boolean isActivateToolWindowWhenAdded() {
    return myActivateToolWindowWhenAdded;
  }

  public void setActivateToolWindowWhenAdded(boolean activateToolWindowWhenAdded) {
    myActivateToolWindowWhenAdded = activateToolWindowWhenAdded;
  }

  public boolean isReuseToolWindowActivation() {
    return myReuseToolWindowActivation;
  }

  public void setReuseToolWindowActivation(boolean reuseToolWindowActivation) {
    myReuseToolWindowActivation = reuseToolWindowActivation;
  }

  public long getExecutionId() {
    return myExecutionId;
  }

  public void setExecutionId(long executionId) {
    myExecutionId = executionId;
  }

  @Override
  public String toString() {
    return getClass().getName() + "#" + hashCode() + "(" + getDisplayName() + ")";
  }

  public Computable<JComponent> getPreferredFocusComputable() {
    return myFocusComputable;
  }

  public void setFocusComputable(Computable<JComponent> focusComputable) {
    myFocusComputable = focusComputable;
  }

  public boolean isAutoFocusContent() {
    return myAutoFocusContent;
  }

  public void setAutoFocusContent(boolean autoFocusContent) {
    myAutoFocusContent = autoFocusContent;
  }
}
