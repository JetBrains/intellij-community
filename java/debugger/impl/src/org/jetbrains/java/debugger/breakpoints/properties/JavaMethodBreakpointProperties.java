// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaMethodBreakpointProperties extends JavaBreakpointProperties<JavaMethodBreakpointProperties> {
  @Attribute("class")
  public String myClassPattern;

  @Attribute("method")
  public String myMethodName;

  public boolean EMULATED = false;
  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT = true;

  public JavaMethodBreakpointProperties(String classPattern, String methodName) {
    myClassPattern = classPattern;
    myMethodName = methodName;
  }

  public JavaMethodBreakpointProperties() {
  }

  @Override
  public @Nullable JavaMethodBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaMethodBreakpointProperties state) {
    super.loadState(state);

    myClassPattern = state.myClassPattern;
    myMethodName = state.myMethodName;

    EMULATED = state.EMULATED;
    WATCH_ENTRY = state.WATCH_ENTRY;
    WATCH_EXIT = state.WATCH_EXIT;
  }
}
