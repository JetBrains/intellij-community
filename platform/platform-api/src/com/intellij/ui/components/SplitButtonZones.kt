// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle

/** Which half of a split button a caller means. See [SplitButtonZones]. */
@ApiStatus.Internal
enum class SplitButtonHalf {
  /** The half a press runs the button's main action from. */
  ACTION,

  /** The chevron half a press opens the options from. */
  EXPAND,
}

/**
 * The two halves of a split button, told apart.
 *
 * The zones live on the button rather than inside its UI because they are the button's contract with
 * whatever presses it: hit testing, hover painting and anything driving the control from outside must
 * agree on where the two halves are, and a second copy of that arithmetic is a bug waiting for a theme
 * change. Three unrelated families answer the same question — an option button lays its halves out as
 * child buttons, a toolbar split button and an action-system split button each paint both halves
 * themselves — so a caller that wants "the action half of this thing" asks here instead of switching on
 * the class.
 *
 * What an implementation promises, once the component has been laid out:
 * - a returned zone is non-empty and lies inside the component's bounds;
 * - the centre of each zone lies inside that zone and inside no other;
 * - coordinates are the implementing component's own, so a zone applies to that component directly and
 *   needs conversion only to reach another one;
 * - `null` means the half is absent — a simple option button with no options has no expand half — or that
 *   no UI is installed to answer from.
 *
 * Two things it deliberately does not promise. The halves need not be disjoint: the Windows 10 option
 * button overlaps them by two pixels. They need not tile the component either: a toolbar split button
 * keeps its separator out of both halves, so a point can belong to neither. Both are why a caller aims at
 * the centre of a zone rather than at an arbitrary point in it.
 */
@ApiStatus.Internal
interface SplitButtonZones {
  /** The bounds of [half] in this component's own coordinates, or `null` when that half is absent. */
  fun splitButtonZone(half: SplitButtonHalf): Rectangle?

  /**
   * The component that owns that half's listeners; may be this component itself.
   *
   * An awaited gesture does not need this: events handed to the window are routed by AWT, which is what
   * finds the child a zone belongs to. A gesture posted at a named recipient does need it, because the
   * recipient is not always the split button — an option button lays its halves out as child buttons and
   * the listener that arms and fires belongs to the child, so events posted to the outer component reach
   * nothing, silently.
   */
  fun splitButtonHalfComponent(half: SplitButtonHalf): Component?
}
