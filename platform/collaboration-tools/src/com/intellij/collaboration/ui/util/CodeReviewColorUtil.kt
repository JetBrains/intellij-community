// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor

object CodeReviewColorUtil {
  object Review {
    val stateForeground: JBColor = JBColor.namedColor("Review.State.Foreground", ColorUtil.fromHex("#6C707E"))
    val stateBackground: JBColor = JBColor.namedColor("Review.State.Background", ColorUtil.fromHex("#DFE1E5"))
  }

  object Branch {
    val background: JBColor = JBColor.namedColor("Review.Branch.Background", ColorUtil.fromHex("#EBECF0"))
    val backgroundHovered: JBColor = JBColor.namedColor("Review.Branch.Background.Hover", ColorUtil.fromHex("#DFE1E5"))
  }
}