// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class TransferSettingsLeftPanelItemRenderer : ListCellRenderer<BaseIdeVersion> {
  override fun getListCellRendererComponent(
    list: JList<out BaseIdeVersion>?,
    item: BaseIdeVersion,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val fg = UIUtil.getListForeground(isSelected, cellHasFocus)
    val bg = UIUtil.getListBackground(isSelected, cellHasFocus)
    return panel {
      // todo: there's a bug with this label. i tried to fix it but nothing helped. anyway, it's not a supported scenario now in rider
      /*if (item is FailedIdeVersion
          && (index == 0 || (list?.model?.getElementAt(index-1) !is FailedIdeVersion))) {
          row {
              val ts = TitledSeparator(RiderIdeaInteropBundle.message("IdeVersionListItemRenderer.separator.failed.to.open"))
              ts.apply {
                  background = UIUtil.getListBackground(false, false)
                  isOpaque = true
              }
              cell(ts)
                  .align(AlignX.FILL)
                  .customize(Gaps(bottom = 4))
          }
      }*/

      row {
        icon(IconUtil.scale(item.icon, null, 35 / item.icon.iconWidth.toFloat()))
          .align(AlignY.CENTER)
          .customize(Gaps(right = 10, left = 10)) // TODO: create a customizer and pass inset here
        panel {
          row {
            if (item is FailedIdeVersion) {
              icon(AllIcons.General.Warning).gap(RightGap.SMALL).customize(Gaps(right = 3))
            }
            label(item.name).bold().align(AlignY.TOP).applyToComponent {
              font = font.deriveFont(13f)
              foreground = fg
            }.customize(Gaps())
          }
          item.subName?.let {
            row {
              label(it).applyToComponent {
                font = font.deriveFont(12f)
                foreground = fg
              }.customize(Gaps())
            }.bottomGap(BottomGap.NONE)
          }

        }
      }.topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    }.apply {
      background = bg
      border = JBUI.Borders.empty()
    }
  }

}