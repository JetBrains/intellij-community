/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

/**
 * @author egor
 */
public class SyntheticLineBreakpoint extends LineBreakpoint<JavaLineBreakpointProperties> {
  private String mySuspendPolicy;
  private final JavaLineBreakpointProperties myProperties = new JavaLineBreakpointProperties();

  public SyntheticLineBreakpoint(@NotNull Project project) {
    super(project, null);
  }

  @Override
  public String getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(String policy) {
    mySuspendPolicy = policy;
  }

  protected boolean isLogEnabled() {
    return false;
  }

  protected boolean isLogStack() {
    return false;
  }

  @Override
  protected boolean isLogExpressionEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  public boolean isCountFilterEnabled() {
    return false;
  }

  public boolean isClassFiltersEnabled() {
    return false;
  }

  @Override
  public boolean isConditionEnabled() {
    return false;
  }

  @Override
  public void reload() {
  }

  @Override
  protected boolean isVisible() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @NotNull
  @Override
  protected JavaLineBreakpointProperties getProperties() {
    return myProperties;
  }

  @Override
  protected void fireBreakpointChanged() {
  }

  @Nullable
  @Override
  protected JavaLineBreakpointType getXBreakpointType() {
    return XDebuggerUtil.getInstance().findBreakpointType(JavaLineBreakpointType.class);
  }

  @Override
  protected boolean isMuted(@NotNull final DebugProcessImpl debugProcess) {
    return false;  // always enabled
  }
}
