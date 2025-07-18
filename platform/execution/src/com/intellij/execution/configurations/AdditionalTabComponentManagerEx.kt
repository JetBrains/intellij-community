// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import com.intellij.diagnostic.logging.AdditionalTabComponent
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface AdditionalTabComponentManagerEx : AdditionalTabComponentManager {
  fun addAdditionalTabComponent(
    tabComponent: AdditionalTabComponent,
    id: String,
    icon: Icon?,
    closeable: Boolean,
  ): Content? {
    addAdditionalTabComponent(tabComponent, id)
    return null
  }
}