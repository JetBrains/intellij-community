// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.items
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal class LcrListPanel : UISandboxPanel {

  override val title: String = "List"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox
      row {
        enabled = checkBox("Enabled")
          .selected(true)
          .component
      }

      indent {
        row {
          jbList(null, items(99), textListCellRenderer { it })
          jbList("Icons", (1..99).toList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text("Item $value")
          })
          jbList("Switch", (1..99).toList(), listCellRenderer {
            switch(index % 2 == 0)
            text("Item $value")
          })

          val aligns = listOf(LcrInitParams.Align.LEFT, LcrInitParams.Align.CENTER, LcrInitParams.Align.RIGHT)
          jbList("Align", (1..99).toList(), listCellRenderer {
            val customAlign = aligns.getOrNull(index % (aligns.size + 1))
            text("$value: $customAlign") {
              align = customAlign
            }
          }).align(Align.FILL)
        }

        row {
          val colors = listOf(UIUtil.getLabelForeground(),
                              JBColor.GREEN,
                              JBColor.MAGENTA)
          val styles = listOf(SimpleTextAttributes.STYLE_PLAIN,
                              SimpleTextAttributes.STYLE_BOLD,
                              SimpleTextAttributes.STYLE_ITALIC)

          jbList("Foreground", (1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              if (i > 0) {
                foreground = colors[i]
              }
            }
          })

          jbList("Attributes", (1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              attributes = SimpleTextAttributes(styles[i], colors[i])
            }
          })

          jbList("Background", (1..99).toList(), listCellRenderer {
            val i = index % colors.size
            if (i > 0) {
              background = colors[i]
            }
            text("Item $value")
          })
        }

        row {
          @Suppress("UNCHECKED_CAST")
          jbList("Speed search", (1..99).toList(), listCellRenderer {
            text("Item $value") {
              speedSearch { }
              attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_ITALIC, JBColor.BLUE)
            }
            text("Item $value") {
              speedSearch { }
              align = LcrInitParams.Align.LEFT
            }
            text("Not searchable text") {
              foreground = greyForeground
            }
          }, patchList = {
            TreeUIHelper.getInstance().installListSpeedSearch(it) { item ->
              "Item $item"
            }
          }).applyToComponent {
            minimumSize = JBDimension(100, 300)
          }
            .component.viewport.view as JBList<Int>

          jbList("Mixed, tooltips", listOf("Text", "With Icon", "Italic", "Commented", "With Switch", "With Icon And Switch"), listCellRenderer {
            toolTipText = value

            when (index) {
              0 -> text(value)
              1 -> {
                icon(AllIcons.General.Information)
                text(value)
              }
              2 -> text(value) {
                attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getLabelForeground())
              }
              3 -> {
                text(value) {
                  align = LcrInitParams.Align.LEFT
                }
                text("Comment") {
                  foreground = greyForeground
                }
              }
              4 -> {
                switch(false)
                text(value)
              }
              5 -> {
                icon(AllIcons.General.Information)
                text(value)
                switch(true)
              }
            }
          })
          jbList("FixedCellHeight", (1..99).toList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text("Item $value")
          }, patchList = { it.fixedCellHeight = JBUIScale.scale(30) })
          jbList("Big font", (1..99).toList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text("Item ($value)") {
              font = JBFont.h1()
            }
            switch(index % 2 == 0)
            text("small comment") {
              font = JBFont.small()
              foreground = greyForeground
            }
          }, patchList = { it.fixedCellHeight = JBUIScale.scale(30) })
        }
      }.enabledIf(enabled.selected)
    }
  }
}

internal fun <T> Row.jbList(label: @NlsContexts.Label String?, items: List<T>, renderer: ListCellRenderer<T>,
                            patchList: Consumer<JBList<T>>? = null): Cell<JBScrollPane> {
  val list = JBList(items)
  list.setCellRenderer(renderer)
  patchList?.accept(list)
  val scroll = JBScrollPane(list)
  scroll.minimumSize = JBDimension(100, 200)
  scroll.isOverlappingScrollBar = true

  val result = cell(scroll)
    .align(AlignY.TOP)
  label?.let {
    result.label(it, LabelPosition.TOP)
  }
  return result
}
