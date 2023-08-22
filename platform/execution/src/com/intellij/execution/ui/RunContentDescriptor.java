// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.HelpIdProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RunContentDescriptor implements Disposable {
  // Should be used in com.intellij.ui.content.Content
  public static final Key<RunContentDescriptor> DESCRIPTOR_KEY = Key.create("Descriptor");
  public static final Key<String> CONTENT_TOOL_WINDOW_ID_KEY = Key.create("ContentToolWindowId");
  private ExecutionConsole myExecutionConsole;
  private ProcessHandler myProcessHandler;
  private JComponent myComponent;
  private final @TabTitle String myDisplayName;
  private final Icon myIcon;
  private final String myHelpId;
  private RunnerLayoutUi myRunnerLayoutUi = null;
  private RunContentDescriptorReusePolicy myReusePolicy = RunContentDescriptorReusePolicy.DEFAULT;

  private boolean myActivateToolWindowWhenAdded = true;
  private boolean myReuseToolWindowActivation = false;
  private boolean mySelectContentWhenAdded = true;
  private long myExecutionId = 0;
  private Computable<JComponent> myFocusComputable = null;
  private boolean myAutoFocusContent = false;

  private Content myContent;
  private String myContentToolWindowId;
  private final AnAction @NotNull [] myRestartActions;

  @Nullable
  private final Runnable myActivationCallback;

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              @TabTitle String displayName,
                              @Nullable Icon icon,
                              @Nullable Runnable activationCallback) {
    this(executionConsole, processHandler, component, displayName, icon, activationCallback, null);
  }

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              @TabTitle String displayName,
                              @Nullable Icon icon,
                              @Nullable Runnable activationCallback,
                              AnAction @Nullable [] restartActions) {
    myExecutionConsole = executionConsole;
    myProcessHandler = processHandler;
    myComponent = component;
    myDisplayName = displayName;
    myIcon = icon;
    myHelpId = myExecutionConsole instanceof HelpIdProvider ? ((HelpIdProvider)myExecutionConsole).getHelpId() : null;
    myActivationCallback = activationCallback;
    if (myExecutionConsole != null) {
      Disposer.register(this, myExecutionConsole);
    }

    myRestartActions = restartActions == null ? AnAction.EMPTY_ARRAY : restartActions;
    if (processHandler != null) {
      setContentToolWindowId(processHandler.getUserData(CONTENT_TOOL_WINDOW_ID_KEY));
    }
  }

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              @TabTitle String displayName,
                              @Nullable Icon icon) {
    this(executionConsole, processHandler, component, displayName, icon, null, null);
  }

  public RunContentDescriptor(@Nullable ExecutionConsole executionConsole,
                              @Nullable ProcessHandler processHandler,
                              @NotNull JComponent component,
                              @TabTitle String displayName) {
    this(executionConsole, processHandler, component, displayName, null, null, null);
  }

  public RunContentDescriptor(@NotNull RunProfile profile, @NotNull ExecutionResult executionResult, @NotNull RunnerLayoutUi ui) {
    this(executionResult.getExecutionConsole(),
         executionResult.getProcessHandler(),
         ui.getComponent(),
         profile.getName(),
         profile.getIcon(),
         null,
         executionResult instanceof DefaultExecutionResult ? ((DefaultExecutionResult)executionResult).getRestartActions() : null);
    myRunnerLayoutUi = ui;
  }

  public @Nullable Runnable getActivationCallback() {
    return myActivationCallback;
  }

  /**
   * @return actions to restart or rerun
   */
  public AnAction @NotNull [] getRestartActions() {
    return myRestartActions.length == 0 ? AnAction.EMPTY_ARRAY : myRestartActions.clone();
  }

  public ExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  @Override
  public void dispose() {
    myExecutionConsole = null;
    myComponent = null;
    myProcessHandler = null;
    myContent = null;
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

  @BuildEventsNls.Title
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

  /**
   * @return Tool window id where content should be shown. Null if content tool window is determined by executor.
   */
  @Nullable
  public String getContentToolWindowId() {
    return myContentToolWindowId;
  }

  public void setContentToolWindowId(@Nullable String contentToolWindowId) {
    myContentToolWindowId = contentToolWindowId;
  }

  public boolean isActivateToolWindowWhenAdded() {
    return myActivateToolWindowWhenAdded;
  }

  public void setActivateToolWindowWhenAdded(boolean activateToolWindowWhenAdded) {
    myActivateToolWindowWhenAdded = activateToolWindowWhenAdded;
  }

  public boolean isSelectContentWhenAdded() {
    return mySelectContentWhenAdded;
  }

  public void setSelectContentWhenAdded(boolean selectContentWhenAdded) {
    mySelectContentWhenAdded = selectContentWhenAdded;
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
    return getClass().getSimpleName() + "#" + hashCode() + "(" + getDisplayName() + ", " + getProcessHandler() + ")";
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

  /**
   * Returns the runner layout UI interface that can be used to manage the sub-tabs in this run/debug tab, if available.
   * (The runner layout UI is used, for example, by debugger tabs which have multiple sub-tabs, but is not used by other tabs
   * which only display a single piece of content.
   * @return the RunnerLayoutUi instance or null if this tab does not use RunnerLayoutUi for managing its contents.
   */
  @Nullable
  public RunnerLayoutUi getRunnerLayoutUi() {
    return myRunnerLayoutUi;
  }

  /**
   * Sets the RunnerLayoutUi instance used for managing tab contents.
   *
   * @param runnerLayoutUi the RunnerLayoutUi instance used for managing tab contents.
   * @see #getRunnerLayoutUi()
   */
  public void setRunnerLayoutUi(@Nullable RunnerLayoutUi runnerLayoutUi) {
    myRunnerLayoutUi = runnerLayoutUi;
  }

  /**
   * return true if the content should not be shown by the {@link RunContentManager}
   */
  @ApiStatus.Experimental
  public boolean isHiddenContent() {
    return false;
  }

  @NotNull
  public RunContentDescriptorReusePolicy getReusePolicy() {
    return myReusePolicy;
  }

  public void setReusePolicy(@NotNull RunContentDescriptorReusePolicy reusePolicy) {
    myReusePolicy = reusePolicy;
  }
}
