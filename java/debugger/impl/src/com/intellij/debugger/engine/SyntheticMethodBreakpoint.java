// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.breakpoints.WildcardMethodBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.sun.jdi.Method;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

public class SyntheticMethodBreakpoint extends WildcardMethodBreakpoint {
  private final JavaMethodBreakpointProperties myProperties = new JavaMethodBreakpointProperties();
  private final String mySignature;
  private String mySuspendPolicy;

  public SyntheticMethodBreakpoint(String className, String methodName, @Nullable String signature, Project project) {
    super(project, null);
    myProperties.EMULATED = true;
    myProperties.WATCH_EXIT = false;
    myProperties.myClassPattern = className;
    myProperties.myMethodName = methodName;
    mySignature = signature;
  }

  @Override
  public StreamEx<Method> matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess) {
    String methodName = getMethodName();
    return methods
      .filter(m -> Comparing.equal(methodName, m.name()) && (mySignature == null || Comparing.equal(mySignature, m.signature())))
      .limit(1);
  }

  @NotNull
  @Override
  protected JavaMethodBreakpointProperties getProperties() {
    return myProperties;
  }

  @Override
  public boolean isCountFilterEnabled() {
    return false;
  }

  @Override
  public boolean isClassFiltersEnabled() {
    return false;
  }

  @Override
  public boolean isConditionEnabled() {
    return false;
  }

  @Override
  public String getSuspendPolicy() {
    return mySuspendPolicy;
  }

  @Override
  public void setSuspendPolicy(String policy) {
    mySuspendPolicy = policy;
  }

  @Override
  protected void fireBreakpointChanged() {
  }

  @Override
  protected boolean isLogEnabled() {
    return false;
  }

  @Override
  protected boolean isLogExpressionEnabled() {
    return false;
  }

  @Override
  protected boolean isLogStack() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
  }
}
