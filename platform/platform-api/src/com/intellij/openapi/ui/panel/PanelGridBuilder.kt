// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.panel

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel

@Deprecated(
  """Provides incorrect spacing between components and out-dated. Fully covered by Kotlin UI DSL, which should be used instead.
  PanelGridBuilder will be removed after moving Kotlin UI DSL into platform API package"""
)
@ApiStatus.ScheduledForRemoval
open class PanelGridBuilder : PanelBuilder {

  private var expand = false
  private var splitColumns = false
  private val builders = mutableListOf<GridBagPanelBuilder>()

  /**
   * Adds a single panel builder to grid.
   * @param builder single row panel builder
   * @return `this`
   */
  @Deprecated("Use Kotlin UI DSL")
  @ApiStatus.ScheduledForRemoval
  open fun add(builder: PanelBuilder): PanelGridBuilder {
    builders.add(builder as GridBagPanelBuilder)
    return this
  }

  /**
   * Allow resizing vertically all panel grid. By default all rows take only preferred height being
   * anchored to the top of the panel and don't resize vertically. All free space is filled with a
   * blank area.
   * This setting is useful when one or more rows are resizable also.
   *
   * @return `this`
   */
  @Deprecated("Use Kotlin UI DSL")
  @ApiStatus.ScheduledForRemoval
  open fun resize(): PanelGridBuilder {
    this.expand = true
    return this
  }

  /**
   * Splits components and their inline comments into different columns in the resulting grid.
   * This method is effective only when you build a grid of panels containing components with
   * comment text resided on the right of the component. By default component and the comment
   * text are placed in a row and different alignment rules apply to different rows.
   *
   * @return `this`
   */
  @Deprecated("Use Kotlin UI DSL")
  @ApiStatus.ScheduledForRemoval
  open fun splitColumns(): PanelGridBuilder {
    this.splitColumns = true
    return this
  }

  override fun createPanel(): JPanel {
    val panel = JPanel(GridBagLayout())
    val gc = GridBagConstraints(
      0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
      null, 0, 0
    )

    addToPanel(panel, gc)
    UIUtil.applyDeprecatedBackground(panel)
    return panel
  }

  override fun constrainsValid(): Boolean {
    return builders.all { it.constrainsValid() }
  }

  private fun gridWidth(): Int {
    return builders.maxOfOrNull { it.gridWidth() } ?: 0
  }

  private fun addToPanel(panel: JPanel, gc: GridBagConstraints) {
    builders
      .filter { it.constrainsValid() }
      .forEach { it.addToPanel(panel, gc, splitColumns) }

    if (!expand) {
      gc.gridx = 0
      gc.anchor = GridBagConstraints.PAGE_END
      gc.fill = GridBagConstraints.BOTH
      gc.weighty = 1.0
      gc.insets = JBUI.emptyInsets()
      gc.gridwidth = gridWidth()
      panel.add(JPanel(), gc)
    }
  }
}
