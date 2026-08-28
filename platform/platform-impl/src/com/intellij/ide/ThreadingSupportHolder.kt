// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.platform.locking.impl.NestedLocksThreadingSupport
import com.intellij.platform.locking.impl.newLockingSupport
import org.jetbrains.annotations.ApiStatus

// do not use this without caution, it is not friendly to analyzer
@ApiStatus.Internal
class ThreadingSupportHolder {
  companion object {
    @JvmStatic
    val threadingSupport: NestedLocksThreadingSupport = newLockingSupport() as NestedLocksThreadingSupport
  }
}
