// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Normally ActionButton (a button in toolbar) has tooltip with action text and shortcut (if assigned).
 * AnAction that implements this interface also has the second line in tooltip, a link to call some activity
 * (e.g. to show help article or wide description related to the action)
 */
public interface TooltipLinkProvider {
  /**
   *
   * @param owner may be used in activity (Runnable) to proper popup positioning etc.
   * @return text for a link and its activity
   */
  @Nullable Pair<@NotNull String, @NotNull Runnable> getTooltipLink(@Nullable JComponent owner);
}
