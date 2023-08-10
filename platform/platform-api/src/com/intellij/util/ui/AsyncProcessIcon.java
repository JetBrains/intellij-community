// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AsyncProcessIcon extends AnimatedIcon {
  private static final Icon[] SMALL_ICONS = com.intellij.ui.AnimatedIcon.Default.ICONS.toArray(new Icon[0]);

  public static final int COUNT = SMALL_ICONS.length;
  public static final int CYCLE_LENGTH = com.intellij.ui.AnimatedIcon.Default.DELAY * SMALL_ICONS.length;

  public AsyncProcessIcon(@NonNls String name) {
    this(name, SMALL_ICONS, AllIcons.Process.Step_passive);
  }

  public AsyncProcessIcon(@NotNull CoroutineScope coroutineScope) {
    this(null, SMALL_ICONS, AllIcons.Process.Step_passive, coroutineScope);
  }

  public AsyncProcessIcon(@NonNls String name, Icon[] icons, Icon passive) {
    super(name, icons, passive, CYCLE_LENGTH);
  }

  public AsyncProcessIcon(@NonNls String name, Icon[] icons, Icon passive, @NotNull CoroutineScope coroutineScope) {
    super(name, icons, passive, CYCLE_LENGTH, coroutineScope);
  }

  @Override
  protected Dimension calcPreferredSize() {
    return new Dimension(passiveIcon.getIconWidth(), passiveIcon.getIconHeight());
  }

  public void updateLocation(@NotNull JComponent container) {
    Rectangle newBounds = calculateBounds(container);
    if (!newBounds.equals(getBounds())) {
      setBounds(newBounds);
      // painting problems with scrollpane
      // repaint shouldn't be called from paint method
      SwingUtilities.invokeLater(() -> container.repaint());
    }
  }

  protected @NotNull Rectangle calculateBounds(@NotNull JComponent container) {
    Rectangle rec = container.getVisibleRect();
    Dimension iconSize = getPreferredSize();
    return new Rectangle(rec.x + rec.width - iconSize.width, rec.y, iconSize.width, iconSize.height);
  }

  public static @NotNull AnimatedIcon createBig(@NonNls String name) {
    return new AsyncProcessIcon(name, com.intellij.ui.AnimatedIcon.Big.ICONS, AllIcons.Process.Big.Step_passive);
  }

  public static @NotNull AnimatedIcon createBig(@NotNull CoroutineScope coroutineScope) {
    return new AsyncProcessIcon(null, com.intellij.ui.AnimatedIcon.Big.ICONS, AllIcons.Process.Big.Step_passive, coroutineScope);
  }

  public static class Big extends AsyncProcessIcon {
    public Big(@NonNls String name) {
      super(name, com.intellij.ui.AnimatedIcon.Big.ICONS, AllIcons.Process.Big.Step_passive);
    }
  }

  public static class BigCentered extends Big {
    public BigCentered(@NonNls String name) {
      super(name);
    }

    @Override
    protected @NotNull Rectangle calculateBounds(@NotNull JComponent container) {
      Dimension size = container.getSize();
      Dimension iconSize = getPreferredSize();
      return new Rectangle((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height);
    }
  }


  public boolean isDisposed() {
    return animator.isDisposed();
  }
}
