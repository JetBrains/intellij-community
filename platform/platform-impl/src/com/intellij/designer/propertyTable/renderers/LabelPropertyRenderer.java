// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.propertyTable.renderers;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This is convenient class for implementing property renderers which
 * are based on JLabel.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class LabelPropertyRenderer extends JLabel implements PropertyRenderer {
  @Nullable private final @Nls String myStaticText;

  public LabelPropertyRenderer(@Nullable @Nls String staticText) {
    myStaticText = staticText;
    setOpaque(true);
    putClientProperty("html.disable", true);
    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
  }

  @Override
  @NotNull
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 PropertyContext context,
                                 @Nullable Object value,
                                 boolean selected,
                                 boolean hasFocus) {
    // Reset text and icon
    setText(null);
    setIcon(null);

    // Background and foreground
    PropertyTable.updateRenderer(this, selected);

    if (value != null) {
      customize(value);
    }

    return this;
  }

  /**
   * Here all subclasses should customize their text, icon and other
   * attributes. Note, that background and foreground colors are already
   * set.
   */
  protected void customize(@NotNull Object value) {
    @NlsSafe String stringValue = value.toString();
    setText(myStaticText != null ? myStaticText : stringValue);
  }
}