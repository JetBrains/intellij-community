// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * Basic interface for all the controls
 */
public sealed interface OptControl extends OptComponent permits OptCheckbox, OptCustom, OptDropdown, OptMap, OptNumber, OptSet, OptString {
  /**
   * @return identifier of binding variable used by inspection (name of the field); must be unique within single options pane
   */
  @NotNull String bindId();
}
