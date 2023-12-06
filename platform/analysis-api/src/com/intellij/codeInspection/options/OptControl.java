// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Basic interface for all the controls
 */
public sealed interface OptControl extends OptComponent
  permits OptCheckbox, OptDropdown, OptExpandableString, OptNumber, OptString, OptStringList, OptTableColumn {
  /**
   * @return identifier of control, and at the same time the name of the binding variable used by inspection 
   * acceptable by {@link InspectionProfileEntry#getOptionController()}
   * (name of the field by default). The bindId must be unique within single option pane. 
   * For {@link OptCustom} control, it's not required to have a corresponding field,
   * but the bindId still must unique identify the control. 
   */
  @NotNull String bindId();

  /**
   * @param tool inspection tool to read the option from
   * @return value of the option bound to this control
   */
  default Object getValue(@NotNull InspectionProfileEntry tool) {
    return tool.getOptionController().getOption(bindId());
  }

  /**
   * @param tool inspection tool to write the option to
   * @param value value to write. It's the caller responsibility to provide the value of compatible type. 
   */
  default void setValue(@NotNull InspectionProfileEntry tool, @Nullable Object value) {
    tool.getOptionController().setOption(bindId(), value);
  }
}
