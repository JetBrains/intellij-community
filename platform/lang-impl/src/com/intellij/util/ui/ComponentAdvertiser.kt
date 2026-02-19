// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Internal
open class ComponentAdvertiser {
  val component: JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
  protected val multiPanel: MyPanel = MyPanel()
  protected var currentIndex: AtomicInteger = AtomicInteger(0)

  protected val nextLabel: ActionLink = ActionLink(CodeInsightBundle.message("label.next.tip")) {
    val i = currentIndex.incrementAndGet()
    multiPanel.select(currentIndex.updateAndGet { i % multiPanel.list.size }, true).isDone
  }

  init {
    nextLabel.font = adFont()

    multiPanel.background = JBUI.CurrentTheme.Advertiser.background()
    multiPanel.border = JBUI.Borders.empty()

    component.add(multiPanel)
    component.add(nextLabel)

    component.isOpaque = true
    component.background = JBUI.CurrentTheme.Advertiser.background()
    component.border = JBUI.CurrentTheme.Advertiser.border()
  }

  fun addAdvertisement(@NlsContexts.PopupAdvertisement text: String?): Boolean {
    if (text == null) return false

    val label = JLabel()
      .apply {
        font = adFont()
        this.text = text
        foreground = JBUI.CurrentTheme.Advertiser.foreground()
        background = JBUI.CurrentTheme.Advertiser.background()
        border = JBUI.Borders.empty()
      }

    addComponentAdvertiser(label)
    return true
  }

  fun addComponentAdvertiser(component: JComponent) {
    multiPanel.list.add(component)
    multiPanel.select(0, true)

    nextLabel.isVisible = multiPanel.list.size > 1
  }

  protected class MyPanel : MultiPanel() {
    var list: MutableList<JComponent> = ArrayList()
    override fun create(key: Int): JComponent {
      return list[key]
    }
  }

  companion object {
    fun adFont(): Font {
      val font = StartupUiUtil.labelFont
      val relativeFont = RelativeFont.NORMAL.scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get())
      return relativeFont.derive(font)
    }
  }
}