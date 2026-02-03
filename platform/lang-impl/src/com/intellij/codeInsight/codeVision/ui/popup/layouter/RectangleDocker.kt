package com.intellij.codeInsight.codeVision.ui.popup.layouter

import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * Algorithm to dock a rectangle to another (copy-paste from R# platform)
 */
@ApiStatus.Internal
class RectangleDocker(anchor: Rectangle, size: Dimension,
                      private val myAllowedDispositions: List<Anchoring2D>,
                      private val myScreen: Rectangle,
                      private val myPadding: Int) {
  private val myAnchorRect: Rectangle = anchor.smartClip(myScreen)
  private val mySize = Dimension(myScreen.width.coerceAtMost(size.width), myScreen.height.coerceAtMost(size.height))
  private var myCandidateRect: Rectangle = Rectangle()
  private var myCandidateRoom: Rectangle = Rectangle()
  private var myCandidateDisposition: Anchoring2D? = null
  private var myCandidateRatio: Int = Int.MAX_VALUE

  fun layout(): LayoutResult? {
    for (disposition in myAllowedDispositions) {
      if (attachRect(myAnchorRect, disposition))
        break
    }

    return if (myCandidateDisposition == null) null
    else LayoutResult(myCandidateRoom, myCandidateRect, myAnchorRect, myCandidateDisposition!!)
  }

  private fun attachRect(anchorRect: Rectangle, disposition: Anchoring2D): Boolean {
    if (disposition.isInside) {
      if (disposition == Anchoring2D(Anchoring.MiddleInside, Anchoring.MiddleInside))
        return attachRectMiddle(disposition, anchorRect)
      val a = anchorRect.inflate(Dimension(-myPadding, -myPadding))
      val visibleAnchorRect = a.intersection(myScreen)

      return attachRectInside(visibleAnchorRect, disposition)
    }

    val a = anchorRect.inflate(Dimension(myPadding, myPadding))
    val visibleAnchorRect = a.intersection(myScreen)

    return attachRectOutside(visibleAnchorRect, disposition, disposition)
  }

  private fun attachRectOutside(anchorRectPadded: Rectangle, mangledDisposition: Anchoring2D, originalDisposition: Anchoring2D): Boolean {
    var rectCandidate = Rectangle(mySize)
    val rectRoom = Rectangle(myScreen)

    when (mangledDisposition.horizontal) {
      Anchoring.NearOutside -> {
        rectCandidate.x = anchorRectPadded.left - mySize.width
        rectRoom.setRight(anchorRectPadded.left)
      }
      Anchoring.NearInside -> {
        rectCandidate.x = anchorRectPadded.left
        rectRoom.setLeft(anchorRectPadded.left)
      }
      Anchoring.MiddleInside -> {
        centerHorizontally(anchorRectPadded.getCenter(), rectCandidate)
      }
      Anchoring.FarInside -> {
        rectCandidate.x = anchorRectPadded.right - mySize.width
        rectRoom.setRight(anchorRectPadded.right)
      }
      Anchoring.FarOutside -> {
        rectCandidate.x = anchorRectPadded.right
        rectRoom.setLeft(anchorRectPadded.right)
      }
    }

    when (mangledDisposition.vertical) {
      Anchoring.NearOutside -> {
        rectCandidate.y = anchorRectPadded.top - mySize.height
        rectRoom.setBottom(anchorRectPadded.top)
      }
      Anchoring.NearInside -> {
        rectCandidate.y = anchorRectPadded.top
        rectRoom.setTop(anchorRectPadded.top)
      }
      Anchoring.MiddleInside -> {
        centerVertically(anchorRectPadded.getCenter(), rectCandidate)
      }
      Anchoring.FarInside -> {
        rectCandidate.y = anchorRectPadded.bottom - mySize.height
        rectRoom.setBottom(anchorRectPadded.bottom)
      }
      Anchoring.FarOutside -> {
        rectCandidate.y = anchorRectPadded.bottom
        rectCandidate.setTop(anchorRectPadded.bottom)
      }
    }

    rectCandidate = rectCandidate.intersection(myScreen)
    return CheckCandidate(rectCandidate, rectRoom, originalDisposition)
  }

  /**
   * Attaches inside the anchoring rect: flips the anchor so that to solve the “attach-outside” problem afterwards.
   */
  private fun attachRectInside(anchorRectPadded: Rectangle, disposition: Anchoring2D): Boolean {
    var (h, v) = disposition

    // Adjust the rect and disposition to handle it as an outside case
    when (disposition.horizontal) {
      Anchoring.NearInside -> {
        h = Anchoring.FarOutside
        anchorRectPadded.x -= anchorRectPadded.width
      }
      Anchoring.MiddleInside -> Unit // One middle is allowed for an outside mode
      Anchoring.FarInside -> {
        h = Anchoring.NearOutside
        anchorRectPadded.x += anchorRectPadded.width
      }
      else -> throw IllegalArgumentException("Expect only *Inside disposition here: disposition = ${disposition.horizontal}.")
    }
    when (disposition.vertical) {
      Anchoring.NearInside -> {
        v = Anchoring.FarOutside
        anchorRectPadded.y -= anchorRectPadded.height
      }
      Anchoring.MiddleInside -> Unit // One middle is allowed for an outside mode
      Anchoring.FarInside -> {
        v = Anchoring.NearOutside
        anchorRectPadded.y += anchorRectPadded.height
      }
      else -> throw IllegalArgumentException("Expect only *Inside disposition here: disposition = ${disposition.vertical}.")
    }

    return attachRectOutside(anchorRectPadded, Anchoring2D(h, v), disposition)
  }

  private fun attachRectMiddle(disposition: Anchoring2D, anchorRect: Rectangle): Boolean {
    val rectCandidate = Rectangle(mySize)
    val anchorRectCenter = anchorRect.getCenter()

    centerHorizontally(anchorRectCenter, rectCandidate)
    centerVertically(anchorRectCenter, rectCandidate)
    return CheckCandidate(rectCandidate, myScreen, disposition)
  }

  private fun CheckCandidate(rectCandidate: Rectangle, rectRoom: Rectangle, disposition: Anchoring2D): Boolean {
    if (rectCandidate.width > mySize.width)
      throw IllegalStateException("The candidate is wider than needed.")
    if (rectCandidate.height > mySize.height)
      throw IllegalStateException("The candidate is higher than needed.")

    // Satisfied completely?
    if ((rectCandidate.width == mySize.width) && (rectCandidate.height == mySize.height)) {
      myCandidateRect = rectCandidate
      myCandidateRoom = rectRoom
      myCandidateDisposition = disposition
      myCandidateRatio = 0
      return true
    }

    // Rate this candidate
    val nRatioHorz = mySize.width - rectCandidate.width
    val nRatioVert = mySize.height - rectCandidate.height
    val nRatio = nRatioHorz * nRatioHorz + nRatioVert * nRatioVert
    if (nRatio < myCandidateRatio) {
      // This candidate is better
      myCandidateRect = rectCandidate
      myCandidateRoom = rectRoom
      myCandidateDisposition = disposition
      myCandidateRatio = nRatio
    }

    return false // Not quite satisfied yet
  }

  private fun centerHorizontally(center: Point, rect: Rectangle) {
    rect.x = center.x - rect.width / 2
    if (rect.left < myScreen.left)
      rect.x += myScreen.left - rect.left
    if (rect.right > myScreen.right)
      rect.x -= rect.right - myScreen.right
  }

  private fun centerVertically(center: Point, rect: Rectangle) {
    rect.y = center.y - rect.height / 2
    if (rect.top < myScreen.top)
      rect.y += myScreen.top - rect.top
    if (rect.bottom > myScreen.bottom)
      rect.y -= rect.bottom - myScreen.bottom
  }
}