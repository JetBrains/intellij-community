// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.components.RatingComponent
import com.intellij.platform.feedback.dialog.createBoldJBLabel
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import org.jetbrains.annotations.Nls

class RatingBlock(
  @NlsContexts.Label private val myLabel: String,
  private val myJsonElementName: String,
) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myProperty: Int = 0
  private var requireAnswer: Boolean = true
  private var myHint: @Nls String? = null

  override fun addToPanel(panel: Panel) {
    val hint = myHint
    panel.apply {
      // With a hint, show the label and the hint above the rating (like RatingGroupBlock);
      // otherwise keep the label attached on top of the rating for backward compatibility.
      if (hint != null) {
        row {
          label(myLabel).bold()
          rowComment(hint)
        }.bottomGap(BottomGap.NONE)
      }
      row {
        rating()
          .apply {
            if (hint == null) {
              label(createBoldJBLabel(myLabel), LabelPosition.TOP)
            }
          }
          .apply {
            onApply {
              myProperty = this.component.myRating
            }

            if (requireAnswer) {
              errorOnApply(CommonFeedbackBundle.message("dialog.feedback.rating.required")) {
                it.myRating == 0
              }
            }
          }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myLabel)
      appendLine(myProperty)
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonElementName, myProperty)
    }
  }

  fun doNotRequireAnswer(): RatingBlock {
    requireAnswer = false
    return this
  }

  fun setHint(@Nls hint: String): RatingBlock {
    myHint = hint
    return this
  }
}

internal fun Row.rating(): Cell<RatingComponent> {
  val ratingComponent = RatingComponent()
  return cell(ratingComponent)
}


