// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.ui.panel;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

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
 * panels see {@link JBPanelFactory#grid()}.
 * When using grid internal implementation makes sure all labels (if the are placed on the left) are located
 * in the leftmost column and components/progress bars are in the second column expanding horizontally.
 * </p>
 *
 * <p>{@link PanelGridBuilder} has convenient {@link PanelGridBuilder#expandVertically()} method that allows rows to expand
 * vertically when the grid is resized. By default all rows stick to the top of the grid and all available empty space is
 * expanded below rows.
 * </p>
 *
 * <p>For concrete examples look <code>ComponentPanelTestAction</code> test action and class.</p>
 */

public abstract class JBPanelFactory {
  /**
   * @return the popup factory instance.
   */
  public static JBPanelFactory getInstance() {
    return ServiceManager.getService(JBPanelFactory.class);
  }

  /**
   * Creates a panel builder for arbitrary <code>JComponent</code>.
   * @param component is the central component
   *
   * @return a newly created instance of {@link ComponentPanelBuilder} for configuring the panel before
   * creation.
   */
  public static ComponentPanelBuilder panel(JComponent component) {
    return getInstance().createComponentPanelBuilder(component);
  }

  /**
   * Creates a panel builder for arbitrary <code>JProgressBar</code>.
   * @param progressBar is the central progressBar
   *
   * @return a newly created instance of {@link ProgressPanelBuilder} for configuring the panel before
   * creation.
   */
  public static ProgressPanelBuilder panel(JProgressBar progressBar) {
    return getInstance().createProgressPanelBuilder(progressBar);
  }

  /**
   * Creates a panel grid. Each grid should contain panels of the same type.
   *
   * @return a newly created {@link PanelGridBuilder}
   */
  public static PanelGridBuilder grid() {
    return getInstance().createPanelGridBuilder();
  }

  public abstract ComponentPanelBuilder createComponentPanelBuilder(JComponent component);

  public abstract ProgressPanelBuilder createProgressPanelBuilder(JProgressBar progressBar);

  public abstract PanelGridBuilder createPanelGridBuilder();
}
