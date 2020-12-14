// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

/**
 * Component that wants to process {@link KeyEvent} instead of {@link IdeKeyEventDispatcher} when focused.
 * Can be used by components in inlays that have actions conflicting with editor actions
 */
public interface KeyboardAwareFocusOwner {
  /**
   * Skip event processing. Component wants to process event by itself
   */
  boolean skipKeyEventDispatcher(@NotNull KeyEvent event);
}
