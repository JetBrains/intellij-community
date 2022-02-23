// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class JavaCollectionBreakpointProperties extends JavaBreakpointProperties<JavaCollectionBreakpointProperties> {
  @Attribute("field")
  @NlsSafe
  public String myFieldName;

  @Attribute("class")
  @NlsSafe
  public String myClassName;

  public JavaCollectionBreakpointProperties(String fieldName, String className) {
    myFieldName = fieldName;
    myClassName = className;
  }

  public JavaCollectionBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaCollectionBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaCollectionBreakpointProperties state) {
    super.loadState(state);
    myFieldName = state.myFieldName;
    myClassName = state.myClassName;
  }
}
