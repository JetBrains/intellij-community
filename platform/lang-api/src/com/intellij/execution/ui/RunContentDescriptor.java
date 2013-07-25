/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpIdProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RunContentDescriptor implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ui.RunContentDescriptor");

  private ExecutionConsole myExecutionConsole;
  private ProcessHandler myProcessHandler;
  private JComponent myComponent;
  private final String myDisplayName;
  private final Icon myIcon;
  private final String myHelpId;

  private boolean myActivateToolWindowWhenAdded = true;
  private long myExecutionId = 0;
  private Computable<JComponent> myFocusComputable = null;
  private boolean myAutoFocusContent = false;

  /**
   * Used to hack {@link com.intellij.execution.runners.RestartAction}
   */
  private Content myContent;
  private Runnable myRestarter;

  public RunContentDescriptor(final ExecutionConsole executionConsole,
                              final ProcessHandler processHandler, final JComponent component, final String displayName, final Icon icon) {
    myExecutionConsole = executionConsole;
    myProcessHandler = processHandler;
    myComponent = component;
    myDisplayName = displayName;
    myIcon = icon;
    myHelpId = myExecutionConsole instanceof HelpIdProvider ? ((HelpIdProvider)myExecutionConsole).getHelpId() : null;
    DataManager.registerDataProvider(myComponent, new DataProvider() {

      @Override
      public Object getData(@NonNls final String dataId) {
        if (RunContentManager.RUN_CONTENT_DESCRIPTOR.is(dataId)) {
          return RunContentDescriptor.this;
        }
        return null;
      }
    });
  }

  public RunContentDescriptor(final ExecutionConsole executionConsole,
                              final ProcessHandler processHandler, final JComponent component, final String displayName) {
    this(executionConsole, processHandler, component, displayName, null);
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
    if (myComponent != null) {
      DataManager.removeDataProvider(myComponent);
      myComponent = null;
    }
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

  /**
   * @see #myContent
   */
  public void setAttachedContent(final Content content) {
    myContent = content;
  }

  /**
   * @see #myContent
   */
  public Content getAttachedContent() {
    return myContent;
  }

  public void setRestarter(Runnable runnable) {
    myRestarter = runnable;
  }

  public Runnable getRestarter() {
    return myRestarter;
  }

  public boolean isActivateToolWindowWhenAdded() {
    return myActivateToolWindowWhenAdded;
  }

  public void setActivateToolWindowWhenAdded(boolean activateToolWindowWhenAdded) {
    myActivateToolWindowWhenAdded = activateToolWindowWhenAdded;
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
