// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Normally, an ActionButton (a button in the toolbar) has a tooltip with action text and shortcut (if assigned).
 * AnAction that implements this interface also has the second line in the tooltip, a link to call some activity
 * (e.g. to show help article or wide description related to the action)
 */
public interface TooltipLinkProvider {
  /**
   *
   * @param owner may be used in activity (Runnable) to proper popup positioning etc.
   * @return text for a link and its activity
   */
  @Nullable TooltipLink getTooltipLink(@Nullable JComponent owner);

  class TooltipLink {
    public final @NlsContexts.LinkLabel String tooltip;
    public final Runnable action;

    public TooltipLink(@NlsContexts.LinkLabel String tooltip, Runnable action) {
      this.tooltip = tooltip;
      this.action = action;
    }
  }
}
