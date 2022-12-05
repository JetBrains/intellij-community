// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Basic interface for all the controls
 */
public sealed interface OptControl extends OptComponent permits OptCheckbox, OptCustom, OptDropdown, OptMap, OptNumber, OptSet, OptString {
  /**
   * @return identifier of control, and at the same time the name of the binding variable used by inspection (name of the field); 
   * must be unique within single option pane. For {@link OptCustom} control, it's not required to have a corresponding field,
   * but the bindId still must unique identify the control. 
   */
  @NotNull String bindId();

  /**
   * @param tool inspection tool to read the field from
   * @return value of the field bound to this; null if field not found, or its value is null
   */
  default Object getValue(@NotNull InspectionProfileEntry tool) {
    return ReflectionUtil.getField(tool.getClass(), tool, null, bindId());
  }

  /**
   * @param tool inspection tool to write the field to
   * @param value value to write to the field. It's the caller responsibility to provide the value of compatible type. 
   */
  default void setValue(@NotNull InspectionProfileEntry tool, @Nullable Object value) {
    ReflectionUtil.setField(tool.getClass(), tool, null, bindId(), value);
  }
}
