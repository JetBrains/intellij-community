// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.panel.PanelGridBuilder
import com.intellij.openapi.ui.panel.ProgressPanelBuilder
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JProgressBar

/**
 * @author Konstantin Bulenkov
 */
@Deprecated("See details for every method/entity inside")
@ApiStatus.ScheduledForRemoval
object UI {

  @Deprecated("See details in ComponentPanelBuilder")
  @ApiStatus.ScheduledForRemoval
  enum class Anchor {
    Top,
    Center,
    Bottom
  }

  /**
   * Factory class for creating panels of components. There are two different types of panels.
   *
   * 1) Component panel. It's essentially a `JComponent` that can be decorated with:
   *
   *  * a label that's located on the right of the component
   *  * a help context label that's located on the left or below of the component
   *  * an icon with a question mark that show a popup with more information which is show on the left
   *
   * For more information see [ComponentPanelBuilder]
   *
   *
   * 2) ProgressBar panel. A panel containing `JProgressBar` that has specific layout
   * which is different from an arbitrary component panel. A progress bar panel can contain following items:
   *
   *  * a label that' located above or on the left of the progress bar
   *  * a comment label that's located below the progress bar
   *  * Cancel, Play, Pause buttons with assignable actions
   *
   * For more information see [ProgressPanelBuilder]
   *
   * Either of the mentioned panels can be grouped together in a grid. I.e. it's possible to create a grid of
   * panels see [UI.PanelFactory.grid].
   * When using grid internal implementation makes sure all labels (if the are placed on the left) are located
   * in the leftmost column and components/progress bars are in the second column expanding horizontally.
   *
   * For concrete examples look `ComponentPanelTestAction` test action and class.
   */
  @Deprecated("See details for every method/entity inside")
  @ApiStatus.ScheduledForRemoval
  object PanelFactory {
    /**
     * Creates a panel builder for arbitrary `JComponent`.
     *
     * @param component is the central component
     * @return a newly created instance of [ComponentPanelBuilder] for configuring the panel before
     * creation.
     */
    @Deprecated("See details in ComponentPanelBuilder", ReplaceWith("ComponentPanelBuilder(component)"))
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    fun panel(component: JComponent): ComponentPanelBuilder {
      return ComponentPanelBuilder(component)
    }

    /**
     * Creates a panel builder for arbitrary `JProgressBar`.
     *
     * @param progressBar is the central progressBar
     * @return a newly created instance of [ProgressPanelBuilder] for configuring the panel before
     * creation.
     */
    @Deprecated("Not needed", ReplaceWith("ProgressPanelBuilder(progressBar)"))
    @ApiStatus.Internal
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    fun panel(progressBar: JProgressBar): ProgressPanelBuilder {
      return ProgressPanelBuilder(progressBar)
    }

    /**
     * Creates a panel grid. Each grid should contain panels of the same type.
     *
     * @return a newly created [PanelGridBuilder]
     */
    @Deprecated("See details in PanelGridBuilder")
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    fun grid(): PanelGridBuilder {
      return PanelGridBuilder()
    }
  }
}
