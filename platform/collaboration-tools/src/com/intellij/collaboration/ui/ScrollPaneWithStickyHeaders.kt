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

    val topStuckPane = OpaquePanel(VerticalFlowLayout(0, 0)).apply {
      border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM)
    }

    val bottomStuckPane = OpaquePanel(VerticalFlowLayout(0, 0)).apply {
      border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM)
    }

    val scrolledBody = NonOpaquePanel(VerticalFlowLayout(0, 0))

    val scrollPane = JBScrollPane(scrolledBody, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER)

    val stickyElements = mutableMapOf<Component, StickyElement>()

    components.forEach { (component, isSticky) ->
      if (isSticky) {
        val (wrapper, element) = addStickySection(scrollPane, scrolledBody, topStuckPane, bottomStuckPane, component, stickyElements.values)
        stickyElements[wrapper] = element
      }
      else scrolledBody.add(component)
    }

    return JBLayeredPane().apply {
      name = "scrollable-sticked-pane"
      isFullOverlayLayout = true
      scrollPane.border = JBUI.Borders.empty()

      scrollPane.viewport.addChangeListener {
        val scrollPosition = scrollPane.viewport.viewPosition.y
        var topLimit = 0
        var bottomLimit = scrollPane.height

        val elements = scrolledBody.components.mapNotNull { stickyElements[it] }.filter { it.component.isVisible }
        elements.forEach { element ->
          val elemPosition = element.wrapperBody.y - scrollPosition
          if (elemPosition < topLimit) {
            element.underTopLimit = false
            topLimit += element.wrapperBody.height
          }
          else {
            element.underTopLimit = true
          }
        }

        elements.reversed().forEach { element ->
          val elemPosition = element.wrapperBody.y + element.wrapperBody.height - scrollPosition
          if (elemPosition > bottomLimit) {
            element.aboveBottomLimit = false
            bottomLimit -= element.wrapperBody.height
          }
          else {
            element.aboveBottomLimit = true
          }

          element.move()
        }

        revalidate()
        repaint()
      }

      stickyLayer.add(topStuckPane, BorderLayout.NORTH)
      stickyLayer.add(bottomStuckPane, BorderLayout.SOUTH)

      add(stickyLayer, 1 as Any)
      add(scrollPane, 0 as Any)
    }
  }

  private fun addStickySection(
    scrollPane: JBScrollPane, scrolledBody: JPanel, topStuckPane: JPanel, bottomStuckPane: JPanel,
    comp: JComponent, stickyElems: Iterable<StickyElement>,
  ): Pair<JComponent, StickyElement> {

    val wrapperInBody = createWrapper()
    wrapperInBody.add(comp)
    scrolledBody.add(wrapperInBody)

    val wrapperInTop = createWrapper().apply { isVisible = false }
    topStuckPane.add(wrapperInTop)

    val wrapperInBottom = createWrapper().apply { isVisible = false }
    bottomStuckPane.add(wrapperInBottom)

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

  private fun createWrapper(): JPanel {
    val panel = NonOpaquePanel()

    panel.name = "wrapper"
    panel.border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.TOP)
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
      val listener = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (beforeElems == null) {
            beforeElems = stickyElems.takeWhile { it != this@StickyElement }
          }

          scrollPane.viewport.viewPosition = Point(0, wrapperBody.y - beforeElems!!.sumOf { it.component.height })
        }
      }
      wrapperTop.addMouseListener(listener)
      wrapperBottom.addMouseListener(listener)
    }

    fun move() {
      when {
        !underTopLimit && !isInTop -> {
          wrapperTop.add(component)
          wrapperTop.isVisible = true
          wrapperBottom.isVisible = false
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
          wrapperTop.isVisible = false
          wrapperBottom.add(component)
          dummy.preferredSize = component.preferredSize
          wrapperBody.add(dummy)
        }
      }
    }
  }
}