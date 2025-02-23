// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class InspectionWrapperUtil {
  public static @NotNull InspectionToolWrapper<?,?> wrapTool(@NotNull InspectionProfileEntry profileEntry) {
    if (profileEntry instanceof LocalInspectionTool local) {
      LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(local);
      if (local.getNameProvider() == null) {
        local.setNameProvider(wrapper.getExtension());
      }
      return wrapper;
    }
    else if (profileEntry instanceof GlobalInspectionTool global) {
      GlobalInspectionToolWrapper wrapper = new GlobalInspectionToolWrapper(global);
      if (global.getNameProvider() == null) {
        global.setNameProvider(wrapper.getExtension());
      }
      return wrapper;
    }
    else throw new RuntimeException("unknown inspection class: " + profileEntry + "; " + profileEntry.getClass());
  }
}
