// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.components.Service
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Merge with WelcomeRightTabContentProvider?
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class WelcomeScreenPreventWelcomeTabFocusService {
  private val isFocusAllowed = AtomicBoolean(true)

  fun preventFocusOnWelcomeTab() {
    // Works only in monolith, probably not needed in Remove Development,
    // because the local welcome screen tab is opened faster than
    // other files from the backend, even without network delays
    isFocusAllowed.getAndSet(false)
  }

  fun isAllowedFocusOnWelcomeTab(): Boolean {
    return isFocusAllowed.get()
  }
}
