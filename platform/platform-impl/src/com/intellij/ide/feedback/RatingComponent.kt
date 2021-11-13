// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.intellij.icons.AllIcons
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.*
import javax.swing.*


class RatingComponent : JComponent() {
  private val myIconSize = 32
  private val myIconGap = 4
  private val myLeftGap = 1
  private val myIconCount = 5
  private val myActiveIcon = AllIcons.Ide.FeedbackRatingOn
  private val myInactiveIcon = AllIcons.Ide.FeedbackRating
  private val myFocusActiveIcon = AllIcons.Ide.FeedbackRatingOnFocused
  private val myFocusInactiveIcon = AllIcons.Ide.FeedbackRatingFocused
  private var myHoverRating = 0
  private var myMouseInside = false

  private val myMaxRating = 5
  private val myMinRating = 1
  var myRating = 0
    private set

  init {
    isFocusable = true
    createKeyBindings()

    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        repaint()
      }

      override fun focusLost(e: FocusEvent?) {
        repaint()
      }
    })

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
        this@RatingComponent.requestFocusInWindow()
        val oldRating = myRating
        myRating = ratingFromPoint(e)
        if (myRating != oldRating) {
          firePropertyChange(RATING_PROPERTY, oldRating, myRating)
        }
        else {
          myRating = 0
          firePropertyChange(RATING_PROPERTY, oldRating, 0)
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

  private fun createKeyBindings() {
    val leftActionMapKey = "left"
    val rightActionMapKey = "right"
    val spaceActionMapKey = "space"
    val oneStarActionMapKey = "1"
    val twoStarActionMapKey = "2"
    val threeStarActionMapKey = "3"
    val fourStarActionMapKey = "4"
    val fiveStarActionMapKey = "5"

    inputMap.put(KeyStroke.getKeyStroke("LEFT"), leftActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke("RIGHT"), rightActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke("SPACE"), spaceActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), oneStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), twoStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), threeStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0), fourStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0), fiveStarActionMapKey)

    val leftAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myRating != myMinRating) {
          myRating -= 1
          repaint()
        }
      }
    }
    actionMap.put(leftActionMapKey, leftAction)
    val rightAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myRating != myMaxRating) {
          myRating += 1
          repaint()
        }
      }
    }
    actionMap.put(rightActionMapKey, rightAction)
    val spaceAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myRating == myMaxRating) {
          myRating = myMinRating
        }
        else {
          myRating += 1
        }
        repaint()
      }
    }
    actionMap.put(spaceActionMapKey, spaceAction)
    val oneStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myRating = 1
        repaint()
      }
    }
    actionMap.put(oneStarActionMapKey, oneStarAction)
    val twoStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myRating = 2
        repaint()
      }
    }
    actionMap.put(twoStarActionMapKey, twoStarAction)
    val threeStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myRating = 3
        repaint()
      }
    }
    actionMap.put(threeStarActionMapKey, threeStarAction)
    val fourStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myRating = 4
        repaint()
      }
    }
    actionMap.put(fourStarActionMapKey, fourStarAction)
    val fiveStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myRating = 5
        repaint()
      }
    }
    actionMap.put(fiveStarActionMapKey, fiveStarAction)
  }

  private fun ratingFromPoint(e: MouseEvent) = (e.x / (myIconSize + myIconGap) + 1).coerceAtMost(myIconCount)

  override fun getPreferredSize(): Dimension {
    return Dimension(myIconSize * myIconCount + myIconGap * (myIconCount - 2), myIconSize)
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }

  override fun paintComponent(g: Graphics) {
    if (isFocusOwner) {
      val ratingToShow = if (myMouseInside) myHoverRating else myRating
      for (i in 0 until myIconCount) {
        val icon = if (ratingToShow == 0) {
          myFocusInactiveIcon
        }
        else if (i < ratingToShow) {
          myFocusActiveIcon
        }
        else {
          myInactiveIcon
        }
        icon.paintRatingIcon(i, g)
      }
    }
    else {
      for (i in 0 until myIconCount) {
        val icon = if (i < myRating) myActiveIcon else myInactiveIcon
        icon.paintRatingIcon(i, g)
      }
    }
  }

  private fun Icon.paintRatingIcon(position: Int, g: Graphics) {
    this.paintIcon(this@RatingComponent, g, position * myIconSize + (position - 1) * myIconGap + myLeftGap, 0)
  }

  companion object {
    const val RATING_PROPERTY = "rating"
  }
}
