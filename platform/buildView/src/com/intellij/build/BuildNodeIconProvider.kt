// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.BuildEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface BuildNodeIconProvider {

  fun getIcon(event: BuildEvent, status: BuildNodeStatus): Icon?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BuildNodeIconProvider> = ExtensionPointName("com.intellij.buildNodeIconProvider")
  }

  enum class BuildNodeStatus {
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    SKIPPED,
  }
}
