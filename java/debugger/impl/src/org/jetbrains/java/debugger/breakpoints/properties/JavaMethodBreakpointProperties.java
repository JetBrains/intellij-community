// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaMethodBreakpointProperties extends JavaBreakpointProperties<JavaMethodBreakpointProperties> {
  @Attribute("class")
  public String myClassPattern;

  @Attribute("method")
  public String myMethodName;

  public boolean EMULATED = false;
  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT  = true;

  public JavaMethodBreakpointProperties(String classPattern, String methodName) {
    myClassPattern = classPattern;
    myMethodName = methodName;
  }

  public JavaMethodBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaMethodBreakpointProperties getState() {
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
