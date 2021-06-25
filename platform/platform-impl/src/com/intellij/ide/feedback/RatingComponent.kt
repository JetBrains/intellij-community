// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.intellij.icons.AllIcons
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent

class RatingComponent : JComponent() {
  private val myIconSize = 32
  private val myIconGap = 4
  private val myIconCount = 5
  private val myActiveIcon = AllIcons.Ide.FeedbackRatingOn
  private val myInactiveIcon = AllIcons.Ide.FeedbackRating
  private var myHoverRating = 0
  private var myMouseInside = false

  var rating = 0

  init {
    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        myMouseInside = true
      }

      override fun mouseExited(e: MouseEvent) {
        myMouseInside = false
        myHoverRating = 0
        repaint()
      }

      override fun mouseClicked(e: MouseEvent) {
        val oldRating = rating
        rating = ratingFromPoint(e)
        if (rating != oldRating) {
          firePropertyChange(RATING_PROPERTY, oldRating, rating)
        }
      }
    })

    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val newHoverRating = ratingFromPoint(e)
        if (newHoverRating != myHoverRating) {
          myHoverRating = newHoverRating
          repaint()
        }
      }
    })
  }

  private fun ratingFromPoint(e: MouseEvent) = (e.x / (myIconSize + myIconGap) + 1).coerceAtMost(myIconCount)

  override fun getPreferredSize(): Dimension {
    return Dimension(myIconCount * myIconSize + myIconGap * (myIconSize - 1), myIconSize)
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }

  override fun paintComponent(g: Graphics) {
    val ratingToShow = if (myMouseInside) myHoverRating else rating
    for (i in 0 until myIconCount) {
      val icon = if (i < ratingToShow) myActiveIcon else myInactiveIcon
      icon.paintIcon(this, g, i * myIconSize + (i - 1) * myIconGap, 0)
    }
  }

  companion object {
    const val RATING_PROPERTY = "rating"
  }
}
