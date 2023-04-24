// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.ui.JBColor

object CodeReviewColorUtil {
  object Review {
    val stateForeground: JBColor = JBColor.namedColor("Review.State.Foreground", 0x797979)
    val stateBackground: JBColor = JBColor.namedColor("Review.State.Background", 0xDFE1E5)
  }
}