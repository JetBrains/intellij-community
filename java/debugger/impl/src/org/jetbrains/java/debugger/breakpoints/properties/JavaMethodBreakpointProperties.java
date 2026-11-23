// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaMethodBreakpointProperties extends JavaBreakpointProperties<JavaMethodBreakpointProperties> {
  @Attribute("class")
  public String myClassPattern;

  @Attribute("method")
  public String myMethodName;

  public boolean EMULATED;
  public boolean WATCH_ENTRY;
  public boolean WATCH_EXIT;

  @SuppressWarnings("unused") // used by serialization
  private JavaMethodBreakpointProperties() {
    // default values are kept for compatibility reasons
    EMULATED = false;
    WATCH_ENTRY = true;
    WATCH_EXIT = true;
  }

  private JavaMethodBreakpointProperties(@Nullable String classPattern, @Nullable String methodName,
                                         boolean emulated, boolean watchEntry, boolean watchExit) {
    myClassPattern = classPattern;
    myMethodName = methodName;
    EMULATED = emulated;
    WATCH_ENTRY = watchEntry;
    WATCH_EXIT = watchExit;
  }

  public static JavaMethodBreakpointProperties create() {
    return create(null, null);
  }

  public static JavaMethodBreakpointProperties create(@Nullable String classPattern, @Nullable String methodName) {
    return new JavaMethodBreakpointProperties(classPattern, methodName,
         Registry.is("debugger.emulate.method.breakpoints"),
         true,
         !Registry.is("debugger.method.breakpoints.entry.default"));
  }

  public static JavaMethodBreakpointProperties createEmulatedWatchOnlyEntry(@NotNull String classPattern, @NotNull String methodName) {
    return new JavaMethodBreakpointProperties(classPattern, methodName, true, true, false);
  }

  public static JavaMethodBreakpointProperties createEmulatedWatchOnlyExit(@NotNull String classPattern, @NotNull String methodName) {
    return new JavaMethodBreakpointProperties(classPattern, methodName, true, false, true);
  }

  public static JavaMethodBreakpointProperties createEmulatedWatchEntryAndExit(@NotNull String classPattern, @NotNull String methodName) {
    return new JavaMethodBreakpointProperties(classPattern, methodName, true, true, true);
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
