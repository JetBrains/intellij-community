// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.importer

import com.intellij.importSettings.chooser.ui.MultipleSettingPane
import com.intellij.importSettings.data.*
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.*
import javax.swing.border.LineBorder

class SettingSyncDialog(val service: ActionsDataProvider, val product: ImportItem) : DialogWrapper(null) {

  private val pane = JPanel(HorizontalLayout(0)).apply {
    add( panel {
      row {
        text("Import<br>Settings From").apply {
          this.component.font = JBFont.h1()
        }.align(AlignY.TOP).customize(UnscaledGaps(20, 0, 17, 0))
      }
      panel {
        service.getProductIcon(product.id, IconProductSize.MIDDLE)?.let { icn ->
          row {
            icon(icn).align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 8))
            panel {
              row {
                text(service.getText(product)).customize(UnscaledGaps(0, 0, 2,0))
              }

              service.getComment(product)?.let { addTxt ->
                row {
                  comment(addTxt).customize(UnscaledGaps(0))
                }
              }
            }
          }
        }
      }.align(AlignY.TOP)
    }.apply {
      preferredSize = JBDimension(200, 110)
      border = JBUI.Borders.emptyLeft(5)
    })

    add(
      panel {
        row {
          cell(MultipleSettingPane(TestJbService.testChildConfig, false).component())
        }

      }.apply {
        isOpaque = false
        preferredSize = JBDimension(420, 374)
        background = Color.RED
        border = LineBorder(Color.BLACK)
      }
    )
  }.apply {
   // preferredSize = JBDimension(640, 410)
  }



  init {
    init()
  }


  override fun createCenterPanel(): JComponent {
    return pane
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }

  override fun getCancelAction(): Action {
    return super.getCancelAction().apply {
      putValue(Action.NAME, "Back")
    }
  }
}