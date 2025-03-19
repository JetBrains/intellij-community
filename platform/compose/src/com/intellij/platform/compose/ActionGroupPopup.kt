// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component

@Experimental
internal interface JBPopupPlacer {
  fun showPopup(
    anchorComponent: Component,
    density: Density,
    anchorBounds: IntRect,
    popupToShow: JBPopup,
  )
}

@Suppress("ForbiddenInSuspectContextMethod", "unused")
@Experimental
@Composable
internal fun ActionGroupPopup(
  actionGroupId: String,
  placer: JBPopupPlacer,
  place: String = ActionPlaces.UNKNOWN,
  dataContext: DataContext? = null,
  onClose: (isOk: Boolean) -> Unit
) {
  val component = LocalComponent.current
  val density = LocalDensity.current

  var layoutParentBoundsInWindow: IntRect? by remember { mutableStateOf(null) }
  Layout(
    content = {},
    modifier = Modifier.onGloballyPositioned { childCoordinates ->
      childCoordinates.parentCoordinates?.let {
        val layoutPosition = it.positionInWindow().round()
        val layoutSize = it.size
        layoutParentBoundsInWindow = IntRect(layoutPosition, layoutSize)
      }
    },
    measurePolicy = { _, _ ->
      layout(0, 0) {}
    }
  )

  LaunchedEffect(Unit) {
    if (layoutParentBoundsInWindow == null) {
      return@LaunchedEffect
    }
    val manager = ActionManager.getInstance()
    val actionGroup = manager.getAction(actionGroupId) as ActionGroup
    val popup = JBPopupFactory
      .getInstance()
      .createActionGroupPopup(
        null, actionGroup, dataContext ?: DataManager.getInstance().getDataContext(component),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true, {},
        Int.MAX_VALUE,
        null,
        place
      )
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        onClose(event.isOk)
      }
    })
    placer.showPopup(component, density, layoutParentBoundsInWindow!!, popup)
  }
}