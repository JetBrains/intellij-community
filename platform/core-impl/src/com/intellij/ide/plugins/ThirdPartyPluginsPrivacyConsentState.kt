// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@IntellijInternalApi
@ApiStatus.Internal
object ThirdPartyPluginsPrivacyConsentState {
  private val thirdPartyPluginsNoteAccepted: AtomicReference<Boolean?> = AtomicReference(null)

  fun setState(isNoteAccepted: Boolean) {
    thirdPartyPluginsNoteAccepted.set(isNoteAccepted)
  }

  fun consumeState(): Boolean? {
    val result = thirdPartyPluginsNoteAccepted.getAndSet(null)
    return result
  }
}