// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.ui.panel.ProgressPanelBuilder;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UI extends JBUI {

  public enum Anchor {
    Top,
    Center,
    Bottom
  }

  /**
   * <p>Factory class for creating panels of components. There are two different types of panels.</p>
   *
   * <p>1) Component panel. It's essentially a <code>JComponent</code> that can be decorated with:
   * <ul>
   *   <li>&nbsp;a label that's located on the right of the component</li>
   *   <li>&nbsp;a help context label that's located on the left or below of the component</li>
   *   <li>&nbsp;an icon with a question mark that show a popup with more information which is show on the left</li>
   * </ul>
   * For more information see {@link ComponentPanelBuilder}</p>
   *
   * <p>2) ProgressBar panel. A panel containing <code>JProgressBar</code> that has specific layout
   * which is different from an arbitrary component panel. A progress bar panel can contain following items:
   * <ul>
   *   <li>&nbsp;a label that' located above or on the left of the progress bar</li>
   *   <li>&nbsp;a comment label that's located below the progress bar</li>
   *   <li>&nbsp;Cancel, Play, Pause buttons with assignable actions</li>
   * </ul>
   * For more information see {@link ProgressPanelBuilder}</p>
   *
   * <p>Either of the mentioned panels can be grouped together in a grid. I.e. it's possible to create a grid of
   * panels see {@link #grid()}.
   * When using grid internal implementation makes sure all labels (if the are placed on the left) are located
   * in the leftmost column and components/progress bars are in the second column expanding horizontally.
   * </p>
   *
   * <p>{@link PanelGridBuilder} has convenient {@link PanelGridBuilder#resize()} method that allows rows to expand
   * vertically when the grid is resized. By default all rows stick to the top of the grid and all available empty space is
   * expanded below rows.
   * </p>
   *
   * <p>For concrete examples look <code>ComponentPanelTestAction</code> test action and class.</p>
   */
  public static class PanelFactory {

    /**
     * Creates a panel builder for arbitrary <code>JComponent</code>.
     *
     * @param component is the central component
     * @return a newly created instance of {@link ComponentPanelBuilder} for configuring the panel before
     * creation.
     */
    public static ComponentPanelBuilder panel(JComponent component) {
      return new ComponentPanelBuilder(component);
    }

    /**
     * Creates a panel builder for arbitrary <code>JProgressBar</code>.
     *
     * @param progressBar is the central progressBar
     * @return a newly created instance of {@link ProgressPanelBuilder} for configuring the panel before
     * creation.
     */
    public static ProgressPanelBuilder panel(JProgressBar progressBar) {
      return new ProgressPanelBuilder(progressBar);
    }

    /**
     * Creates a panel grid. Each grid should contain panels of the same type.
     *
     * @return a newly created {@link PanelGridBuilder}
     */
    public static PanelGridBuilder grid() {
      return new PanelGridBuilder();
    }
  }
}
