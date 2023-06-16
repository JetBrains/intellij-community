// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import javax.swing.SwingConstants

class SegmentedButtonBlock(@NlsContexts.Label private val myMainLabel: String?,
                           private val myItems: List<String>,
                           private val myJsonElementName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myProperty: String = ""
  private var myLeftBottomLabel: String? = null
  private var myMiddleBottomLabel: String? = null
  private var myRightBottomLabel: String? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      if (myMainLabel != null) {
        row {
          label(myMainLabel)
            .customize(UnscaledGaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
        }.bottomGap(BottomGap.SMALL)
      }
      row {
        segmentedButton(myItems) { it }
          .apply {
            maxButtonsCount(myItems.size)
          }.customize(UnscaledGaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .whenItemSelected { myProperty = it }
          .align(Align.FILL)
          .validation {
            addApplyRule(CommonFeedbackBundle.message("dialog.feedback.segmentedButton.required")) { it.selectedItem == null }
          }
      }.apply {
        if (myLeftBottomLabel == null && myMiddleBottomLabel == null && myRightBottomLabel == null) {
          this.bottomGap(BottomGap.MEDIUM)
          return
        }
      }

      row {
        if (myLeftBottomLabel != null) {
          label(myLeftBottomLabel!!)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            .widthGroup("Group")
            .apply {
              if (myMiddleBottomLabel == null) {
                resizableColumn()
              }
            }
        }
        if (myMiddleBottomLabel != null) {
          label(myMiddleBottomLabel!!)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            .align(AlignX.CENTER)
            .resizableColumn()
        }
        if (myRightBottomLabel != null) {
          label(myRightBottomLabel!!)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
              horizontalAlignment = SwingConstants.RIGHT
            }
            .widthGroup("Group")
        }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myMainLabel)
      appendLine(myProperty)
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonElementName, myProperty)
    }
  }

  fun addLeftBottomLabel(@NlsContexts.Label leftBottomLabel: String): SegmentedButtonBlock {
    myLeftBottomLabel = leftBottomLabel
    return this
  }

  fun addMiddleBottomLabel(@NlsContexts.Label middleBottomLabel: String): SegmentedButtonBlock {
    myMiddleBottomLabel = middleBottomLabel
    return this
  }

  fun addRightBottomLabel(@NlsContexts.Label rightBottomLabel: String): SegmentedButtonBlock {
    myRightBottomLabel = rightBottomLabel
    return this
  }
}