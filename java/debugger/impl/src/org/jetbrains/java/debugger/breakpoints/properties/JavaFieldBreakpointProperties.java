// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFieldBreakpointProperties extends JavaBreakpointProperties<JavaFieldBreakpointProperties> {
  public boolean WATCH_MODIFICATION = true;
  public boolean WATCH_ACCESS       = false;

  @Attribute("field")
  @NlsSafe
  public String myFieldName;

  @Attribute("class")
  @NlsSafe
  public String myClassName;

  public JavaFieldBreakpointProperties(String fieldName, String className) {
    myFieldName = fieldName;
    myClassName = className;
  }

  public JavaFieldBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaFieldBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaFieldBreakpointProperties state) {
    super.loadState(state);

    WATCH_MODIFICATION = state.WATCH_MODIFICATION;
    WATCH_ACCESS = state.WATCH_ACCESS;
    myFieldName = state.myFieldName;
    myClassName = state.myClassName;
  }
}
