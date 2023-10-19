// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
class ActionContextElement(val actionId: String,
                           val place: String,
                           val inputEventId: Int,
                           val parent: ActionContextElement?):
  AbstractCoroutineContextElement(ActionContextElement), CoroutineContext.Element {

  companion object: CoroutineContext.Key<ActionContextElement>
}