// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmModifier;
import org.jetbrains.annotations.NotNull;

public interface ChangeModifierRequest extends ActionRequest {

  /**
   * @return the modifier which should be changed
   */
  @NotNull
  JvmModifier getModifier();

  /**
   * @return true if the modifier should be added, false if it should be removed
   */
  boolean shouldBePresent();

  /**
   * @return true if it's desired to process hierarchy when applicable (e.g., update overriding methods correspondingly)
   */
  default boolean processHierarchy() {
    return true;
  }
}
