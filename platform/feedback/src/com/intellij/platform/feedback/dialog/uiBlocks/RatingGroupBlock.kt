// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.platform.feedback.dialog.components.RatingComponent
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import org.jetbrains.annotations.Nls

class RatingGroupBlock(@Nls private val topLabel: String,
                       private val ratingItems: List<RatingItem>) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myHint: @Nls String? = null
  private val myRatingValues: HashMap<String, Int> = HashMap(ratingItems.associate { Pair(it.jsonElementName, 0) })
  private var myRandomOrder: Boolean = false

  override fun addToPanel(panel: Panel) {
    val allRatings: ArrayList<RatingComponent> = arrayListOf()

    panel.apply {
      row {
        label(topLabel).bold().errorOnApply(CommonFeedbackBundle.message("dialog.feedback.block.required")) {
          allRatings.any { it.myRating == 0 }
        }.validationRequestor { validate -> validate() }

        myHint?.let { rowComment(it) }
      }.bottomGap(BottomGap.NONE)
      val positions = getPositionsList()

      for (position in positions) {
        val ratingItem = ratingItems[position]
        val label = JBLabel(ratingItem.label)
        row(label) {
          rating()
            .apply {
              allRatings.add(this.component)
              onApply {
                myRatingValues[ratingItem.jsonElementName] = this.component.myRating
              }
            }

          if (positions.last() == position) {
            bottomGap(BottomGap.MEDIUM)
          }
        }.topGap(TopGap.NONE)
      }
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      ratingItems.forEach {
        val jsonElementName = it.jsonElementName
        put(jsonElementName, myRatingValues[jsonElementName])
      }
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(topLabel)
      ratingItems.forEach {
        appendLine(it.label + ": " + myRatingValues[it.jsonElementName])
      }
      appendLine()
    }
  }

  fun setHint(@Nls hint: String): RatingGroupBlock {
    myHint = hint
    return this
  }

  fun setRandomOrder(randomOrder: Boolean): RatingGroupBlock {
    myRandomOrder = randomOrder
    return this
  }

  private fun getPositionsList(): List<Int> {
    var positions = List(ratingItems.size) { it }
    if (myRandomOrder) {
      positions = positions.shuffled()
    }
    return positions
  }
}

data class RatingItem(@Nls val label: String, val jsonElementName: String)