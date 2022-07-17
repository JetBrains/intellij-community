// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.preview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 * @deprecated see {@link PreviewHintProvider} deprecation notice
 */
@Deprecated
public class ColorPreviewComponent extends JComponent implements PreviewHintComponent {
  @NotNull
  private final Color myColor;

  public ColorPreviewComponent(@NotNull final Color color) {
    myColor = color;
    setOpaque(true);
  }

  @Override
  public void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;

    final Rectangle r = getBounds();

    g2.setPaint(myColor);
    g2.fillRect(1, 1, r.width - 2, r.height - 2);

    g2.setPaint(Color.BLACK);
    g2.drawRect(0, 0, r.width - 1, r.height - 1);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(70, 25);
  }

  @Override
  public boolean isEqualTo(@Nullable PreviewHintComponent other) {
    return other instanceof ColorPreviewComponent && myColor.equals(((ColorPreviewComponent)other).myColor);
  }
}
