// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.avatar

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
}