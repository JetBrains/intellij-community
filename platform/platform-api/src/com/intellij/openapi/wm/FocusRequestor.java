// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Basic interface for requesting sending focus commands to {@code IdeFocusManager}
 */
public interface FocusRequestor extends Disposable {

  /**
   * Requests focus on a component
   * @param c - component to request focus to
   * @param forced - if true - focus request is explicit, must be fulfilled, if false - can be dropped
   * @return action callback that either notifies when the focus was obtained or focus request was dropped
   */
  @NotNull
  ActionCallback requestFocus(@NotNull Component c, boolean forced);
}
