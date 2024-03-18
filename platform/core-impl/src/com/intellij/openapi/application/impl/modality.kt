// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun <T> inModalContext(modalEntity: Any, action: (ModalityState) -> T): T {
  val newModalityState = LaterInvocator.getCurrentModalityState().appendEntity(modalEntity)
  LaterInvocator.enterModal(modalEntity, newModalityState)
  try {
    return action(newModalityState)
  }
  finally {
    LaterInvocator.leaveModal(modalEntity)
  }
}
