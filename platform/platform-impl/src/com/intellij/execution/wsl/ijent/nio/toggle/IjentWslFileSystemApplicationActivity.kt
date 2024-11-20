// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.ide.ApplicationActivity

/**
 * This activity registers IJent file systems as early as possible.
 *
 * It's important to register them before opening projects, because:
 * * The project itself may be located on an IJent filesystem.
 * * There could have been initialization races during loading the project.
 *
 * See also [IjentInProjectStarter].
 */
class IjentWslFileSystemApplicationActivity : ApplicationActivity {
  override suspend fun execute() {
    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return
    }

    val ijentWslNioFsToggler = IjentWslNioFsToggler.instanceAsync()
    if (!ijentWslNioFsToggler.isAvailable) {
      return
    }

    ijentWslNioFsToggler.enableForAllWslDistributions()
  }
}