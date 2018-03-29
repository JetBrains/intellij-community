// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.panel;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PanelGridBuilder implements PanelBuilder {
  private boolean expand;
  private final List<GridBagPanelBuilder> builders = new ArrayList<>();

  /**
   * Adds a single panel builder to grid.
   * @param builder single row panel builder
   * @return <code>this</code>
   */
  public PanelGridBuilder add(@NotNull PanelBuilder builder) {
    builders.add((GridBagPanelBuilder)builder);
    return this;
  }

  /**
   * Allow resizing vertically all panel grid. By default all rows take only preferred height being
   * anchored to the top of the panel and don't resize vertically. All free space is filled with a
   * blank area.
   * This setting is useful when one or more rows are resizable also. To turn on row vertical
   * resizing use {@link ComponentPanelBuilder#resizeX(boolean)}.
   *
   * @return <code>this</code>
   */
  public PanelGridBuilder resize() {
    this.expand = true;
    return this;
  }


  @NotNull
  public JPanel createPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);

    addToPanel(panel, gc);
    return panel;
  }

  public boolean constrainsValid() {
    return builders.stream().allMatch(b -> b.constrainsValid());
  }

  private int gridWidth() {
    return builders.stream().map(b -> b.gridWidth()).max(Integer::compareTo).orElse(0);
  }

  private void addToPanel(JPanel panel, GridBagConstraints gc) {
    builders.stream().filter(b -> b.constrainsValid()).forEach(b -> b.addToPanel(panel, gc));

    if (!expand) {
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.PAGE_END;
      gc.fill = GridBagConstraints.BOTH;
      gc.weighty = 1.0;
      gc.insets = JBUI.insets(0);
      gc.gridwidth = gridWidth();
      panel.add(new JPanel(), gc);
    }
  }
}
