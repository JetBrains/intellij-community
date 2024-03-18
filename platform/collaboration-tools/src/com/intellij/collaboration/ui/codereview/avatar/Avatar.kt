// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.avatar

import com.intellij.ui.JBColor

object Avatar {

  /**
   * Avatar sizes in different collaboration UIs
   */
  object Sizes {
    /**
     * Usages:
     * Mentions in comments
     */
    const val SMALL: Int = 15

    /**
     * Usages:
     * 1. Сode reviews list
     * 2. Reviewer's selector
     * 3. Details
     * 4. Replies
     */
    const val BASE: Int = 20

    /**
     * Usages:
     * Top level comment in timeline
     */
    const val TIMELINE: Int = 30

    /**
     * Usages:
     * Account representation in settings and popups
     */
    const val ACCOUNT: Int = 40
  }

  object Color {
    val ACCEPTED_BORDER: JBColor = JBColor.namedColor("Review.Avatar.Border.Status.Accepted", JBColor(0x5FB865, 0x57965C))
    val WAIT_FOR_UPDATES_BORDER: JBColor = JBColor.namedColor("Review.Avatar.Border.Status.WaitForUpdates", JBColor(0xEC8F4C, 0xE08855))
    val NEED_REVIEW_BORDER: JBColor = JBColor.namedColor("Review.Avatar.Border.Status.NeedReview", JBColor(0x818594, 0x6F737A))
  }
}