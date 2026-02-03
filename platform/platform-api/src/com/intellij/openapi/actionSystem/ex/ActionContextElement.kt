// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.InputEvent
import javax.swing.JComponent
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val ACTION_CONTEXT_ELEMENT_KEY = Key.create<ActionContextElement>("ACTION_CONTEXT_ELEMENT_KEY")

@ApiStatus.Internal
class ActionContextElement(val actionId: String,
                           val place: String,
                           val inputEventId: Int,
                           val parent: ActionContextElement?):
  AbstractCoroutineContextElement(ActionContextElement), CoroutineContext.Element, IntelliJContextElement {

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  companion object: CoroutineContext.Key<ActionContextElement> {

    @ApiStatus.Internal
    @JvmStatic
    fun reset(component: JComponent, element: ActionContextElement?) {
      ClientProperty.put(component, ACTION_CONTEXT_ELEMENT_KEY, element)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun create(actionId: String,
               place: String,
               event: InputEvent?,
               component: Component?): ActionContextElement {
      val parentActionElement = UIUtil.uiParents(component, false)
        .filterMap { ClientProperty.get(it, ACTION_CONTEXT_ELEMENT_KEY) }
        .first()
      return ActionContextElement(actionId, place, event?.id ?: -1, parentActionElement)
    }
  }

  override fun toString(): String = "ActionContextElement($actionId@$place)"
}