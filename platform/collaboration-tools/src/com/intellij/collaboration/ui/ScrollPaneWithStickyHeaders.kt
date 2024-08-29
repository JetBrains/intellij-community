// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

@Internal
object ScrollPaneWithStickyHeaders {
  /**
   * @param components List of child components along with sticky flags
   */
  fun create(components: List<Pair<JComponent, Boolean>>): JComponent {
    val stickyLayer = NonOpaquePanel(BorderLayout())

    val topStickedPane = OpaquePanel(VerticalFlowLayout(0, 0)).apply {
      border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM)
    }

    val bottomStickedPane = OpaquePanel(VerticalFlowLayout(0, 0)).apply {
      border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM)
    }

    val scrolledBody = NonOpaquePanel(VerticalFlowLayout(0, 0))

    val scrollPane = JBScrollPane(scrolledBody, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER)

    val stickyElements = mutableMapOf<Component, StickyElement>()

    components.forEach { (component, isSticky) ->

      if (isSticky) {
        val (wrapper, element) = addStickySection(scrollPane, scrolledBody, topStickedPane, bottomStickedPane, component, stickyElements.values)
        stickyElements[wrapper] = element
      }
      else scrolledBody.add(component)
    }

    return JBLayeredPane().apply {
      name = "scrollable-sticked-pane"
      isFullOverlayLayout = true
      scrollPane.border = JBUI.Borders.empty()

      scrollPane.viewport.addChangeListener {
        val position = scrollPane.viewport.viewPosition
        val topMargin = topStickedPane.height
        val bottomMargin = bottomStickedPane.height

        scrolledBody.components.mapNotNull { stickyElements[it] }
          .forEach { elem ->
            var topLimit = position.y + topMargin
            if (elem.isInTop) topLimit -= elem.wrapperBody.height

            var bottomLimit = position.y + scrollPane.height - bottomMargin
            if (elem.isInBottom) bottomLimit += elem.wrapperBody.height

            elem.underTopLimit = elem.wrapperBody.y > topLimit
            elem.aboveBottomLimit = elem.wrapperBody.y + elem.wrapperBody.height < bottomLimit

            elem.move()

            revalidate()
            repaint()
          }
      }

      stickyLayer.add(topStickedPane, BorderLayout.NORTH)
      stickyLayer.add(bottomStickedPane, BorderLayout.SOUTH)

      add(stickyLayer, 1 as Any)
      add(scrollPane, 0 as Any)
    }
  }

  private fun addStickySection(
    scrollPane: JBScrollPane, scrolledBody: JPanel, topStickedPane: JPanel, bottomStickedPane: JPanel,
    comp: JComponent, stickyElems: Iterable<StickyElement>,
  ): Pair<JComponent, StickyElement> {

    val wrapperInBody = createWrapper(false)
    wrapperInBody.add(comp)
    scrolledBody.add(wrapperInBody)

    val wrapperInTop = createWrapper()
    topStickedPane.add(wrapperInTop)

    val wrapperInBottom = createWrapper()
    bottomStickedPane.add(wrapperInBottom)

    return wrapperInBody to StickyElement(
      comp,
      wrapperInBody,
      wrapperInTop,
      wrapperInBottom,
      scrollPane,
      scrolledBody,
      stickyElems
    )
  }

  private fun createWrapper(hiding: Boolean = true): JPanel {
    val panel = NonOpaquePanel()

    panel.name = "wrapper"
    panel.border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.TOP)
    if (hiding) {
      panel.addContainerListener(object : ContainerListener {
        override fun componentAdded(e: ContainerEvent?) {
          panel.isVisible = e?.child?.isVisible ?: false
        }

        override fun componentRemoved(e: ContainerEvent?) {
          panel.isVisible = false
        }
      })
    }
    return panel
  }

  private class StickyElement(
    val component: Component,
    val wrapperBody: JPanel,
    val wrapperTop: JPanel,
    val wrapperBottom: JPanel,
    scrollPane: JBScrollPane,
    scrolledBody: JPanel,
    private val stickyElems: Iterable<StickyElement>,
  ) {
    var underTopLimit = true
    var aboveBottomLimit = true
    var beforeElems: List<StickyElement>? = null

    val isInTop get() = wrapperTop.componentCount > 0
    val isInBottom get() = wrapperBottom.componentCount > 0
    val isInBody get() = !isInTop && !isInBottom

    /** Used to reserve space in the scrollable body when the component is not there */
    private val dummy = NonOpaquePanel()

    init {
      wrapperTop.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          scrolledBody.scrollRectToVisible(wrapperBody.bounds)
        }
      })
      wrapperBottom.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (beforeElems == null) {
            beforeElems = stickyElems.takeWhile { it != this@StickyElement }
          }

          scrollPane.viewport.viewPosition = Point(0, wrapperBody.y - beforeElems!!.sumOf { it.component.height })
        }
      })
    }

    fun move() {
      when {
        !underTopLimit && !isInTop -> {
          wrapperTop.add(component)
          wrapperTop.isVisible = true
          dummy.preferredSize = component.preferredSize
          wrapperBody.add(dummy)
        }
        underTopLimit && aboveBottomLimit && !isInBody -> {
          wrapperTop.isVisible = false
          wrapperBottom.isVisible = false
          wrapperBody.remove(dummy)
          wrapperBody.add(component)
        }
        !aboveBottomLimit && !isInBottom -> {
          wrapperBottom.isVisible = true
          wrapperBottom.add(component)
          dummy.preferredSize = component.preferredSize
          wrapperBody.add(dummy)
        }
      }
    }
  }
}