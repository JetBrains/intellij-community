// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.ui

import com.intellij.importSettings.data.ChildSetting
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ChildSettingsList(val settings: List<SettingItem>, val configurable: Boolean) : JBList<SettingItem>(createDefaultListModel(settings)) {

  init {
    cellRenderer = CBRenderer(configurable)

    if(configurable) {
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          val index = locationToIndex(e.point)
          if (settings.size > index) {
            val settingItem = settings[index]
            settingItem.choosed = !settingItem.choosed
            repaint()
          }
        }
      })
    }

  }
}

private class CBRenderer(val configurable: Boolean) : ListCellRenderer<SettingItem> {
  private lateinit var ch: JBCheckBox
  private lateinit var txt: JEditorPane
  private lateinit var addTxt: JEditorPane
  private lateinit var rightTxt: JEditorPane

  private val separator = SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null)


  val line = panel {
    row {
      checkBox("").applyToComponent {
        ch = this
      }
      text("").applyToComponent {
        txt = this
      }
      comment("").applyToComponent {
        addTxt = this
      }.resizableColumn()

      comment("").applyToComponent {
        rightTxt = this
      }
    }
  }

  val pane = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(separator)
    add(line)
  }

  override fun getListCellRendererComponent(list: JList<out SettingItem>,
                                            value: SettingItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    separator.isVisible = value.separatorNeeded
    val child = value.child

    ch.isVisible = configurable
    ch.isSelected = value.choosed

    txt.text = child.name
    addTxt.text = child.additionText ?: ""

    rightTxt.isVisible = child.value?.let {
      rightTxt.text = it
      true
    } ?: false

    return pane
  }
}

class SettingItem(val child: ChildSetting, var separatorNeeded: Boolean = false, var choosed: Boolean = true )
