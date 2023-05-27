// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class RatingBlock(myProperty: ObservableMutableProperty<Int>,
                  @NlsContexts.Label val label: String) : SingleInputFeedbackBlock<Int>(myProperty) {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        rating()
          .label(label, LabelPosition.TOP)
          .bind({ it.myRating }, { _, _ -> }, MutableProperty(myProperty::get, myProperty::set))
          .errorOnApply(CommonFeedbackBundle.message("dialog.feedback.rating.required")) {
            it.myRating == 0
          }
      }
    }
  }
}

fun Row.rating(): Cell<RatingComponent> {
  val ratingComponent = RatingComponent()
  return cell(ratingComponent)
}