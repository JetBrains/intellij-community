// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.collaboration.ui.jbColorFromHex
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor

object CodeReviewColorUtil {
  object Review {
    val stateForeground: JBColor = jbColorFromHex("Review.State.Foreground", "#6C707E", "#868A91")
    val stateBackground: JBColor = jbColorFromHex("Review.State.Background", "#DFE1E5", "#43454A")

    object Chat {
      val hover: JBColor = jbColorFromHex("Review.ChatItem.Hover", "#DFE1E533", "#5A5D6333")
    }
  }

  object Branch {
    val background: JBColor = JBColor.namedColor("Review.Branch.Background", ColorUtil.fromHex("#EBECF0"))
    val backgroundHovered: JBColor = JBColor.namedColor("Review.Branch.Background.Hover", ColorUtil.fromHex("#DFE1E5"))
  }
}