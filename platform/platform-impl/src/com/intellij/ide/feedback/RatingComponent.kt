// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.intellij.icons.AllIcons
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.*
import javax.swing.*


class RatingComponent : JComponent() {
  private val myIconSize = 32
  private val myIconGap = 4
  private val myLeftGap = 5
  private val myIconCount = 5
  private val myActiveIcon = AllIcons.Ide.FeedbackRatingOn
  private val myInactiveIcon = AllIcons.Ide.FeedbackRating
  private val myFocusActiveIcon = AllIcons.Ide.FeedbackRatingFocusedOn
  private val myFocusInactiveIcon = AllIcons.Ide.FeedbackRatingFocused
  private var myHoverRating = 0
  private var myMouseInside = false

  private val myMaxRating = 5
  private val myMinRating = 1
  private var myFocusRating = 1
  var myRating = 0
    private set


  init {
    isFocusable = true
    focusTraversalKeysEnabled = false
    createKeyBindings()

    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        if (myRating != 0) {
          myFocusRating = myRating
        }
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
        if (!isFocusOwner) {
          requestFocusInWindow()
        }
        val oldRating = myRating
        myRating = ratingFromPoint(e)
        myFocusRating = myRating
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
    val oneStarActionMapKey = "1"
    val twoStarActionMapKey = "2"
    val threeStarActionMapKey = "3"
    val fourStarActionMapKey = "4"
    val fiveStarActionMapKey = "5"
    val tabStarActionMapKey = "tab"
    val shiftTabStarActionMapKey = "shiftTab"
    val enterStarActionMapKey = "enter"

    inputMap.put(KeyStroke.getKeyStroke("LEFT"), leftActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke("RIGHT"), rightActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), oneStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), twoStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), threeStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0), fourStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0), fiveStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), tabStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK), shiftTabStarActionMapKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), enterStarActionMapKey)

    val leftAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myFocusRating != myMinRating) {
          myFocusRating -= 1
        }
        repaint()
      }
    }
    actionMap.put(leftActionMapKey, leftAction)

    val rightAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myFocusRating != myMaxRating) {
          myFocusRating += 1
        }
        repaint()
      }
    }
    actionMap.put(rightActionMapKey, rightAction)

    val oneStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myFocusRating = 1
        repaint()
      }
    }
    actionMap.put(oneStarActionMapKey, oneStarAction)

    val twoStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myFocusRating = 2
        repaint()
      }
    }
    actionMap.put(twoStarActionMapKey, twoStarAction)

    val threeStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myFocusRating = 3
        repaint()
      }
    }
    actionMap.put(threeStarActionMapKey, threeStarAction)

    val fourStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myFocusRating = 4
        repaint()
      }
    }
    actionMap.put(fourStarActionMapKey, fourStarAction)

    val fiveStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        myFocusRating = 5
        repaint()
      }
    }
    actionMap.put(fiveStarActionMapKey, fiveStarAction)

    val tabStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myFocusRating == myMaxRating) {
          transferFocus()
        }
        else {
          myFocusRating += 1
        }
        repaint()
      }
    }
    actionMap.put(tabStarActionMapKey, tabStarAction)

    val shiftTabStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (myFocusRating == myMinRating) {
          transferFocusBackward()
        }
        else {
          myFocusRating -= 1
        }
        repaint()
      }
    }
    actionMap.put(shiftTabStarActionMapKey, shiftTabStarAction)

    val enterTabStarAction: Action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        val oldRating = myRating
        if (myFocusRating == myRating) {
          myRating = 0
          firePropertyChange(RATING_PROPERTY, oldRating, myRating)
        }
        else {
          myRating = myFocusRating
          firePropertyChange(RATING_PROPERTY, oldRating, myRating)
        }
        repaint()
      }
    }
    actionMap.put(enterStarActionMapKey, enterTabStarAction)
  }

  private fun ratingFromPoint(e: MouseEvent) = (e.x / (myIconSize + myIconGap) + 1).coerceAtMost(myIconCount)

  override fun getPreferredSize(): Dimension {
    return Dimension(myIconSize * myIconCount + myIconGap * (myIconCount - 2) + myLeftGap, myIconSize)
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }

  override fun paintComponent(g: Graphics) {
    val ratingToShow = if (myMouseInside) myHoverRating else myRating
    val isFocusOwner = isFocusOwner
    for (i in 1 until myIconCount + 1) {
      if (isFocusOwner) {
        val icon = if (i <= ratingToShow) {
          if (i == myFocusRating) {
            myFocusActiveIcon
          }
          else {
            myActiveIcon
          }
        }
        else {
          if (i == myFocusRating) {
            myFocusInactiveIcon
          }
          else {
            myInactiveIcon
          }
        }
        icon.paintRatingIcon(i, g)
      }
      else {
        val icon = if (i <= ratingToShow) myActiveIcon else myInactiveIcon
        icon.paintRatingIcon(i, g)
      }
    }
  }

  private fun Icon.paintRatingIcon(position: Int, g: Graphics) {
    this.paintIcon(this@RatingComponent, g, (position - 1) * myIconSize + (position - 2) * myIconGap + myLeftGap, 0)
  }

  companion object {
    @Nls
    const val RATING_PROPERTY = "rating" //NON-NLS
  }
}
