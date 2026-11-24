// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.setToolTipText
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JList

private const val DESCRIPTION_WIDTH = 340

/**
 * Shows the [SearchEngine.hint] in gray under the name of an engine in the popup, and the [SearchEngine.tooltip] as the tooltip.
 * The collapsed combo box shows only the name of the selected engine.
 */
internal class SearchEngineRenderer : GroupedComboBoxRenderer<SearchEngine?>() {
  private lateinit var texts: NonOpaquePanel
  private lateinit var description: JBLabel
  private var descriptionText: @Nls String? = null

  override fun getText(item: SearchEngine?): String = item?.displayName ?: ""

  override fun separatorFor(value: SearchEngine?): ListSeparator? = null

  override fun layout() {
    // no fixed row height, because an engine with a hint takes two rows
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH)
    myRendererComponent.add(NonOpaquePanel(itemComponent), BorderLayout.CENTER)
  }

  override fun layoutComponent(component: JComponent): JComponent {
    description = JBLabel()
    texts = NonOpaquePanel(BorderLayout())
    texts.add(component, BorderLayout.CENTER)
    texts.add(description, BorderLayout.SOUTH)
    return super.layoutComponent(texts)
  }

  override fun getListCellRendererComponent(
    list: JList<out SearchEngine?>?,
    value: SearchEngine?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    myRendererComponent.setToolTipText(if (index >= 0) value?.tooltip?.let(HtmlChunk::text) else null)
    return component
  }

  override fun customize(
    item: SimpleColoredComponent,
    value: SearchEngine?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ) {
    super.customize(item, value, index, isSelected, cellHasFocus)
    val text = value?.hint
    descriptionText = text
    description.isVisible = index >= 0 && text != null
    if (index < 0 || text == null) {
      texts.border = JBUI.Borders.empty()
      return
    }

    texts.border = JBUI.Borders.empty(4, 0)
    description.border = BorderFactory.createEmptyBorder(JBUI.scale(2), item.insets.left + item.ipad.left, 0, 0)
    description.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    val maxWidth = JBUI.scale(DESCRIPTION_WIDTH)
    val body = if (description.getFontMetrics(description.font).stringWidth(text) <= maxWidth) {
      HtmlChunk.text(text)
    }
    else {
      HtmlChunk.text(text).wrapWith(HtmlChunk.body().style("width: ${maxWidth}px"))
    }
    description.text = body.wrapWith(HtmlChunk.html()).toString()
  }

  override fun getDelegateAccessibleDescription(): String? = descriptionText
}
