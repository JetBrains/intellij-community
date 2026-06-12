// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.diff.impl.ui.DiffHeaderToolbarPanel.Companion.NEED_BOTTOM_BORDER_KEY
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.util.asSafely
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.plaf.BorderUIResource
import javax.swing.plaf.PanelUI
import javax.swing.plaf.UIResource
import kotlin.properties.Delegates.observable

private const val UI_CLASS_ID = "Diff.HeaderToolbarPanelUI"

/**
 * A special panel to hold the diff header toolbar.
 * Look like a usual panel in classic and new UI, but takes the form of a separate island in Island UI
 */
@ApiStatus.Internal
class DiffHeaderToolbarPanel(layoutManager: LayoutManager? = null) : JComponent() {
  private var _heightReferent: WeakReference<JComponent>? = null

  /**
   * Only used without Island UI
   */
  var heightReferent: JComponent?
    get() = _heightReferent?.get()
    set(value) {
      _heightReferent = WeakReference(value)
      repaint()
    }

  var needBottomSeparatorBorder: Boolean by observable(false) { _, old, new ->
    firePropertyChange(NEED_BOTTOM_BORDER_KEY.toString(), old, new)
  }

  init {
    layout = layoutManager
    isDoubleBuffered = true
    updateUI()
  }

  override fun getUIClassID(): String = UI_CLASS_ID

  override fun updateUI() {
    setUI(UIManager.getUI(this))
  }

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = AccessiblePanel()
    }
    return accessibleContext
  }

  @Suppress("RedundantInnerClassModifier")
  private inner class AccessiblePanel : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL
  }

  companion object {
    internal val NEED_BOTTOM_BORDER_KEY = Key.create<Boolean>("DiffHeaderToolbarPanel.needBottomSeparatorBorder")
  }
}

internal abstract class DiffHeaderToolbarPanelUI : PanelUI()

@Suppress("unused")
internal class DiffToolbarIslandPanelUI : DiffHeaderToolbarPanelUI() {
  override fun installUI(c: JComponent) {
    if (c.background == null || c.background is UIResource) {
      c.background = UIManager.getColor("Editor.SearchField.background")
    }
    if (c.border == null || c.border is UIResource) {
      c.border = BorderUIResource(Borders.empty(2, 6, 0, 6))
    }
    LookAndFeel.installProperty(c, "opaque", false)
    ClientProperty.put(c, FileEditorManager.SEPARATOR_DISABLED, true)
  }

  override fun uninstallUI(c: JComponent) {
    ClientProperty.put(c, FileEditorManager.SEPARATOR_DISABLED, null)
  }

  override fun paint(g: Graphics, c: JComponent) {
    val g2 = g.create() as Graphics2D
    try {
      val rect = Rectangle(0, 0, c.width, c.height)
      JBInsets.removeFrom(rect, c.insets)
      g2.color = c.background
      val arcDiameter = ARC_RADIUS * 2.0
      RectanglePainter2D.FILL.paint(g2,
                                    rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble(),
                                    arcDiameter,
                                    LinePainter2D.StrokeType.CENTERED, 1.0,
                                    RenderingHints.VALUE_ANTIALIAS_ON)

      val borderColor = UIManager.getColor("Editor.SearchField.borderColor")
      g2.color = borderColor
      RectanglePainter2D.DRAW.paint(g2,
                                    rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble(),
                                    arcDiameter,
                                    LinePainter2D.StrokeType.CENTERED, 1.0,
                                    RenderingHints.VALUE_ANTIALIAS_ON)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getPreferredSize(c: JComponent?): Dimension? {
    val layout = c?.layout ?: return super.getPreferredSize(c)
    return layout.preferredLayoutSize(c)?.apply {
      height = fixedHeight + c.insets.top + c.insets.bottom
    }
  }

  companion object {
    private const val ARC_RADIUS = 6
    private val fixedHeight: Int get() = JBUI.scale(40)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun createUI(c: JComponent?): DiffToolbarIslandPanelUI = DiffToolbarIslandPanelUI()
  }
}

@Suppress("unused")
internal class DiffToolbarPlainPanelUI : DiffHeaderToolbarPanelUI() {
  private lateinit var propertyListener: PropertyChangeListener

  override fun installUI(c: JComponent) {
    LookAndFeel.installColorsAndFont(c,
                                     "Panel.background",
                                     "Panel.foreground",
                                     "Panel.font")
    LookAndFeel.installProperty(c, "opaque", true)

    propertyListener = PropertyChangeListener {
      if (it.propertyName == NEED_BOTTOM_BORDER_KEY.toString()) {
        updateBorder(c)
        c.repaint()
      }
    }
    c.addPropertyChangeListener(propertyListener)
    updateBorder(c)
  }

  private fun updateBorder(c: JComponent) {
    val panel = c.asSafely<DiffHeaderToolbarPanel>() ?: return
    if (panel.border == null || panel.border is UIResource) {
      panel.border = if (panel.needBottomSeparatorBorder) {
        BorderUIResource(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
      }
      else {
        UIManager.getBorder("Panel.border")
      }
    }
  }

  override fun uninstallUI(c: JComponent) {
    LookAndFeel.uninstallBorder(c)
    c.removePropertyChangeListener(propertyListener)
  }

  override fun getPreferredSize(c: JComponent?): Dimension? =
    c.asSafely<DiffHeaderToolbarPanel>()?.heightReferent?.preferredSize
    ?: super.getPreferredSize(c)

  companion object {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun createUI(c: JComponent?): DiffToolbarPlainPanelUI = DiffToolbarPlainPanelUI()
  }
}
