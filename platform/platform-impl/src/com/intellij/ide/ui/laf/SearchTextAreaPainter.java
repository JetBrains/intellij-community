// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import org.jetbrains.annotations.NotNull;
import javax.swing.border.Border;
import java.awt.*;

/**
 * <code>SearchTextArea</code> paint helper
 *
 */
@Deprecated
public interface SearchTextAreaPainter {
  @NotNull
  Border getBorder();

  @NotNull
  String getLayoutConstraints();

  @NotNull
  String getHistoryButtonConstraints();

  @NotNull
  String getIconsPanelConstraints();

  @NotNull
  Border getIconsPanelBorder(int rows);

  void paint(@NotNull Graphics2D g);
}
