// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.components.RatingComponent
import com.intellij.platform.feedback.dialog.createBoldJBLabel
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

class RatingBlock(@NlsContexts.Label private val myLabel: String,
                  private val myJsonElementName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myProperty: Int = 0

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        rating()
          .label(createBoldJBLabel(myLabel), LabelPosition.TOP)
          .apply {
            onApply {
              myProperty = this.component.myRating
            }
          }
          .errorOnApply(CommonFeedbackBundle.message("dialog.feedback.rating.required")) {
            it.myRating == 0
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
}

internal fun Row.rating(): Cell<RatingComponent> {
  val ratingComponent = RatingComponent()
  return cell(ratingComponent)
}


