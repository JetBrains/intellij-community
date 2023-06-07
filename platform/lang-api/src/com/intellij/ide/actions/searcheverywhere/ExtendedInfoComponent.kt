// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.labelFont
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.JPanel

open class ExtendedInfoComponent {
  @JvmField
  val myComponent: JPanel =
    JPanel(BorderLayout(0, 0))
      .apply {
        isOpaque = true
        background = JBUI.CurrentTheme.Advertiser.background()
        border = JBUI.CurrentTheme.Advertiser.border()
      }

  @JvmField
  protected var myForeground: Color = JBUI.CurrentTheme.Advertiser.foreground()

  fun createLabel(): JBLabel = JBLabel()
    .apply {
      font = adFont()
      foreground = myForeground
    }

  protected open fun adFont(): Font = RelativeFont.NORMAL.scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get()).derive(labelFont)
}

open class ExtendedInfo {
  var leftText: (Any) -> String?
  var rightAction: (Any) -> AnAction?

  constructor(leftText: (Any) -> String?, rightAction: (Any) -> AnAction?) {
    this.leftText = leftText
    this.rightAction = rightAction
  }

  internal constructor() {
    leftText = fun(_: Any) = null
    rightAction = fun(_: Any?): AnAction? = null
  }
}

class ExtendedInfoImpl(val contributors: List<SearchEverywhereContributor<*>>) : ExtendedInfo() {
  private val list = contributors.mapNotNull { it.createExtendedInfo() }

  init {
    leftText = fun(element: Any) = list.firstNotNullOfOrNull { it.leftText.invoke(element) }
    rightAction = fun(element: Any) = list.firstNotNullOfOrNull { it.rightAction.invoke(element) }
  }
}
