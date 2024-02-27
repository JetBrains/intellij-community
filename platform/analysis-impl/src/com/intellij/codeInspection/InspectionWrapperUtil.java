// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class InspectionWrapperUtil {
  @NotNull
  public static InspectionToolWrapper<?,?> wrapTool(@NotNull InspectionProfileEntry profileEntry) {
    if (profileEntry instanceof LocalInspectionTool local) {
      LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(local);
      if (local.myNameProvider == null) {
        local.myNameProvider = wrapper.getExtension();
      }
      return wrapper;
    }
    else if (profileEntry instanceof GlobalInspectionTool global) {
      GlobalInspectionToolWrapper wrapper = new GlobalInspectionToolWrapper(global);
      if (global.myNameProvider == null) {
        global.myNameProvider = wrapper.getExtension();
      }
      return wrapper;
    }
    else throw new RuntimeException("unknown inspection class: " + profileEntry + "; " + profileEntry.getClass());
  }
}
