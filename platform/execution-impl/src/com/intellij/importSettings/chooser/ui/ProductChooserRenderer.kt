// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.importSettings.chooser.actions.ConfigAction
import com.intellij.importSettings.chooser.actions.SettingChooserItemAction
import com.intellij.importSettings.data.ActionsDataProvider
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.*

class ProductChooserRenderer : ListCellRenderer<PopupFactoryImpl.ActionItem> {
  private val twoLineComponent = ProductChooserTwoLineComponent()
  private val oneLineComponent = ProductChooserOneLineComponent()
  private val twoLineComponentWithSeparator = ComponentWithSeparator(ProductChooserTwoLineComponent())
  private val oneLineComponentWithSeparator = ComponentWithSeparator(ProductChooserOneLineComponent())

  override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>?,
                                            value: PopupFactoryImpl.ActionItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val model = list?.model as? ListPopupModel<*>
    val hasSeparator = model?.isSeparatorAboveOf(value) ?: false

    if(value.action is ConfigAction) {
      val config = value.action as ConfigAction

      return if(hasSeparator) {
        oneLineComponentWithSeparator.rendererComponent.update(ConfigAction.name, ConfigAction.icon, isSelected)
        oneLineComponentWithSeparator.component
      } else {
        oneLineComponent.update(ConfigAction.name, ConfigAction.icon, isSelected)
        oneLineComponent.component
      }
    }

    if(value.action is SettingChooserItemAction) {
      val action = value.action as SettingChooserItemAction
      val product = action.product
      val provider = action.provider

      return if (hasSeparator) {
        twoLineComponentWithSeparator.rendererComponent.update(product.name, provider.getProductIcon(product.id), product.lastUsage.toString(), isSelected)
        twoLineComponentWithSeparator.component
      }
      else {
        twoLineComponent.update(product.name, provider.getProductIcon(product.id), product.lastUsage.toString(), isSelected)
        twoLineComponent.component
      }
    }

    return JLabel("Nothing to show")
  }
}

class ProductChooserOneLineComponent : RendererComponent {
  private lateinit var icon: JLabel
  private lateinit var mainText: JLabel
  private lateinit var selectablePanel: SelectablePanel

  private var isSelected = false

  init {
    val content = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          icon = icon(AllIcons.Chooser.Bottom).component
          panel {
            row {
              mainText = label("")
                .customize(UnscaledGaps(bottom = 4))
                .applyToComponent {
                  foreground = getForegraund()
                }.component
            }
          }
        }
      }
    }.apply {
      border = JBUI.Borders.empty(8, 0)
      isOpaque = false
    }

    selectablePanel = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
    PopupUtil.configListRendererFlexibleHeight(selectablePanel)
    if (isSelected) {
      selectablePanel.selectionColor = ListPluginComponent.SELECTION_COLOR
    }

    content.size = JBDimension(UiUtils.DEFAULT_BUTTON_WIDTH, content.size.height)
  }

  fun update(txt: String, icn: Icon, selected: Boolean) {
    icon.icon = icn
    mainText.text = txt
    isSelected = selected

    mainText.foreground = getForegraund()
  }

  override val component: JComponent
    get() = selectablePanel

  fun getForegraund(): Color {
    return if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
  }

}

class ProductChooserTwoLineComponent : RendererComponent {
  private lateinit var icon: JLabel
  private lateinit var mainText: JLabel
  private lateinit var additionalText: JLabel
  private lateinit var selectablePanel: SelectablePanel

  private var isSelected = false

  init {
    val content = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          icon = icon(AllIcons.Chooser.Bottom).component
          panel {
            row {
              mainText = label("")
                .customize(UnscaledGaps(bottom = 4))
                .applyToComponent {
                  foreground = getForegraund()
                }.component
            }
            row {
              additionalText = label("")
                .applyToComponent {
                  font = JBFont.smallOrNewUiMedium()
                  foreground = UIUtil.getLabelInfoForeground()
                }.component
            }
          }
        }
      }
    }.apply {
      border = JBUI.Borders.empty(8, 0)
      isOpaque = false
    }

    selectablePanel = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
    PopupUtil.configListRendererFlexibleHeight(selectablePanel)
    if (isSelected) {
      selectablePanel.selectionColor = ListPluginComponent.SELECTION_COLOR
    }

    AccessibleContextUtil.setCombinedName(selectablePanel, mainText, " - ", additionalText)
    AccessibleContextUtil.setCombinedDescription(selectablePanel, mainText, " - ", additionalText)

    content.size = JBDimension(UiUtils.DEFAULT_BUTTON_WIDTH, content.size.height)

  }

  fun update(txt: String, icn: Icon?, addTxt: String, selected: Boolean) {
    icon.icon = icn
    mainText.text = txt
    additionalText.text = addTxt
    isSelected = selected

    mainText.foreground = getForegraund()
  }

  override val component: JComponent
    get() = selectablePanel

  fun getForegraund(): Color {
    return if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
  }

}

interface RendererComponent {
  val component: JComponent
}

class ComponentWithSeparator<T : RendererComponent>(public val rendererComponent: T) : RendererComponent {
  private val separator = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
  private val panel = NonOpaquePanel(BorderLayout())

  init {
    panel.border = JBUI.Borders.empty()
    separator.setHideLine(false)

    val panel = JPanel(BorderLayout())
    panel.border = JBUI.Borders.empty()
    panel.isOpaque = true
    panel.background = JBUI.CurrentTheme.Popup.BACKGROUND
    panel.add(separator)

    panel.add(separator, BorderLayout.NORTH)
    panel.add(rendererComponent.component, BorderLayout.CENTER)
  }

  override val component: JComponent
    get() = panel

}
