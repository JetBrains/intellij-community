// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.components.service
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@Internal
@TestOnly
object JbProtocolTestSupport {
  @JvmStatic
  fun fire(url: String) {
    val query = url.trim()
      .removePrefix("jetbrains://")
      .removePrefix("jetbrains:/")
    ApplicationManager.getApplication().service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      JBProtocolCommand.execute(query)
    }
  }
}
