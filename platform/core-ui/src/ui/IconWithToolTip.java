// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Icon which supports providing a tooltip.
 */
public interface IconWithToolTip extends Icon {
  /**
   * Returns the tooltip for the icon.
   * @param composite if true, this tooltip will be combined with other tooltips (from other layers of a layered icon or parts of a row icon).
   *                  For some icons, it only makes sense to show a tooltip if the icon is composite.
   * @return
   */
  @NlsContexts.Tooltip @Nullable String getToolTip(boolean composite);
}
