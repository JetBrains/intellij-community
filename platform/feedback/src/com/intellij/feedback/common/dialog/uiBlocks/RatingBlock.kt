// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class RatingBlock(private val myProperty: ObservableMutableProperty<Int>,
                  @NlsContexts.Label private val myLabel: String) : BaseFeedbackBlock() {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        rating()
          .label(myLabel, LabelPosition.TOP)
          .bind({ it.myRating }, { _, _ -> }, MutableProperty(myProperty::get, myProperty::set))
          .errorOnApply(CommonFeedbackBundle.message("dialog.feedback.rating.required")) {
            it.myRating == 0
          }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myLabel)
      appendLine(myProperty.get())
      appendLine()
    }
  }

  private fun Row.rating(): Cell<RatingComponent> {
    val ratingComponent = RatingComponent()
    return cell(ratingComponent)
  }
}

