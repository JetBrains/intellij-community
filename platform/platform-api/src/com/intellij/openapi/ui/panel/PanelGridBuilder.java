// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.panel;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Provides incorrect spacing between components and out-dated. Fully covered by Kotlin UI DSL, which should be used instead.
 * PanelGridBuilder will be removed after moving Kotlin UI DSL into platform API package
 */
@Deprecated
public class PanelGridBuilder implements PanelBuilder {
  private boolean expand;
  private boolean splitColumns;
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

  /**
   * Splits components and their inline comments into different columns in the resulting grid.
   * This method is effective only when you build a grid of panels containing components with
   * comment text resided on the right of the component. By default component and the comment
   * text are placed in a row and different alignment rules apply to different rows.
   *
   * @return <code>this</code>
   */
  public PanelGridBuilder splitColumns() {
    this.splitColumns = true;
    return this;
  }

  @Override
  public @NotNull JPanel createPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);

    addToPanel(panel, gc);
    UIUtil.applyDeprecatedBackground(panel);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return builders.stream().allMatch(b -> b.constrainsValid());
  }

  private int gridWidth() {
    return builders.stream().map(b -> b.gridWidth()).max(Integer::compareTo).orElse(0);
  }

  private void addToPanel(JPanel panel, GridBagConstraints gc) {
    builders.stream().filter(b -> b.constrainsValid()).forEach(b -> b.addToPanel(panel, gc, splitColumns));

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
