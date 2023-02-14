// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.shared.ui

import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.shared.LibraryInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.*
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.util.ui.UIUtil.DEFAULT_VGAP
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.math.max

@ApiStatus.Internal
class LibraryDescriptionPanel : ScrollablePanel(VerticalLayout(DEFAULT_VGAP)) {
  private val descriptionHeader: JBLabel = JBLabel()
  private val descriptionText: JTextArea = JTextArea()
  private val descriptionVersion: MultiLineLabel = MultiLineLabel()
  private val linksPanel: JPanel = JPanel(WrappedFlowLayout())

  private val emptyState: StatusText = object : StatusText(this) {
    override fun isStatusVisible(): Boolean {
      return UIUtil.uiChildren(this@LibraryDescriptionPanel)
        .filter { obj: Component -> obj.isVisible }
        .isEmpty
    }
  }

  init {
    this.border = JBUI.Borders.empty(DEFAULT_VGAP)

    val headerPanel = JPanel(BorderLayout())
    descriptionHeader.font = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD)
    descriptionHeader.border = JBUI.Borders.empty(DEFAULT_VGAP, 0)

    descriptionVersion.icon = AllIcons.General.BalloonWarning
    descriptionVersion.border = JBUI.Borders.empty(DEFAULT_VGAP, 0, DEFAULT_VGAP * 2, 0)
    descriptionVersion.isVisible = false
    headerPanel.add(descriptionHeader, BorderLayout.NORTH)
    headerPanel.add(descriptionVersion, BorderLayout.CENTER)
    add(headerPanel)

    descriptionText.background = JBColor.PanelBackground
    descriptionText.isFocusable = false
    descriptionText.lineWrap = true
    descriptionText.wrapStyleWord = true
    descriptionText.isEditable = false
    descriptionText.font = JBUI.Fonts.label()
    add(descriptionText)

    linksPanel.border = JBUI.Borders.emptyTop(DEFAULT_VGAP * 2)
    add(linksPanel)

    emptyState.text = JavaStartersBundle.message("hint.no.library.selected")

    showEmptyState()
  }

  fun update(library: LibraryInfo, @NlsSafe versionConstraint: String?) {
    descriptionHeader.text = library.title
    descriptionVersion.text = versionConstraint ?: ""
    descriptionVersion.isVisible = versionConstraint != null
    descriptionText.text = library.description

    addDescriptionLinks(linksPanel, library)

    showDescriptionUi()
  }

  fun update(@NlsSafe title: String, description: String?) {
    descriptionHeader.text = title
    descriptionVersion.text = ""
    descriptionVersion.isVisible = false
    descriptionText.text = description

    linksPanel.removeAll()

    showDescriptionUi()
  }

  fun reset() {
    descriptionHeader.text = ""
    descriptionVersion.text = ""
    descriptionText.text = ""
    descriptionVersion.isVisible = false

    linksPanel.removeAll()

    showEmptyState()
  }

  private fun showEmptyState() {
    for (component in this.components) {
      component.isVisible = false
    }
    revalidate()
    repaint()
  }

  private fun showDescriptionUi() {
    for (component in this.components) {
      component.isVisible = true
    }
    revalidate()
    repaint()
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    emptyState.paint(this, g)
  }

  override fun getComponentGraphics(graphics: Graphics?): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
  }

  private fun addDescriptionLinks(linksPanel: JPanel, item: LibraryInfo) {
    linksPanel.removeAll()
    for (link in item.links) {
      if (link.url.contains('{')) continue // URL templates are not supported

      val linkLabel = HyperlinkLabel(link.title ?: link.type.getTitle())
      linkLabel.font = JBUI.Fonts.smallFont()
      linkLabel.setHyperlinkTarget(link.url)
      linkLabel.toolTipText = link.url

      linksPanel.add(BorderLayoutPanel().apply {
        addToCenter(linkLabel)
        border = JBUI.Borders.empty(0, 0, 0, DEFAULT_HGAP / 2)
      })
    }
    linksPanel.revalidate()
    linksPanel.repaint()
  }

  // do not add horizontal gap - it is inserted before the first component
  private class WrappedFlowLayout : FlowLayout(LEADING, 0, DEFAULT_VGAP) {
    override fun preferredLayoutSize(target: Container): Dimension {
      val baseSize = super.preferredLayoutSize(target)
      if (alignOnBaseline) return baseSize
      return getWrappedSize(target)
    }

    private fun getWrappedSize(target: Container): Dimension {
      val parent = SwingUtilities.getUnwrappedParent(target)
      val maxWidth = parent.width - (parent.insets.left + parent.insets.right)
      return getDimension(target, maxWidth)
    }

    private fun getDimension(target: Container, maxWidth: Int): Dimension {
      val insets = target.insets
      var height = insets.top + insets.bottom
      var width = insets.left + insets.right
      var rowHeight = 0
      var rowWidth = insets.left + insets.right
      var isVisible = false
      var start = true

      synchronized(target.treeLock) {
        for (i in 0 until target.componentCount) {
          val component = target.getComponent(i)
          if (component.isVisible) {
            isVisible = true
            val size = component.preferredSize
            if (rowWidth + hgap + size.width > maxWidth && !start) {
              height += vgap + rowHeight
              width = max(width, rowWidth)
              rowWidth = insets.left + insets.right
              rowHeight = 0
            }
            rowWidth += hgap + size.width
            rowHeight = max(rowHeight, size.height)
            start = false
          }
        }

        height += vgap + rowHeight
        width = max(width, rowWidth)
        if (!isVisible) {
          return super.preferredLayoutSize(target)
        }

        return Dimension(width, height)
      }
    }

    override fun minimumLayoutSize(target: Container): Dimension {
      return if (alignOnBaseline) super.minimumLayoutSize(target) else getWrappedSize(target)
    }
  }
}