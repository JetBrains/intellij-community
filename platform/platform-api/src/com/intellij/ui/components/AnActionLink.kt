// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities

open class AnActionLink(@Nls text: String, anAction: AnAction, @NonNls place: String)
  : DataProvider, ActionLink(text, { invokeAction(anAction, it.source as AnActionLink, place, null, null) }) {

  constructor(@Nls text: String, anAction: AnAction) : this(text, anAction, UNKNOWN)
  constructor(anAction: AnAction, @NonNls place: String) : this(anAction.templateText ?: "", anAction, place)
  constructor(@NonNls actionId: String, @NonNls place: String) : this(ActionManager.getInstance().getAction(actionId), place)

  var dataProvider: DataProvider? = anAction as? DataProvider

  override fun getData(dataId: String) = when {
    PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.`is`(dataId) -> {
      val point = SwingUtilities.convertPoint(this, 0, 0, UIUtil.getRootPane(this))
      val ps = preferredSize
      Rectangle(point.x, point.y, width.coerceAtMost(ps.width), height.coerceAtMost(ps.height))
    }
    PlatformDataKeys.CONTEXT_MENU_POINT.`is`(dataId) -> {
      Point(0, height.coerceAtMost(preferredSize.height))
    }
    else -> dataProvider?.getData(dataId)
  }
}
