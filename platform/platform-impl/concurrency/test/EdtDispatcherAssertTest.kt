// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.impl

import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EdtDispatcherAssertTest {
  @Test
  fun test() {
    var warn: String? = null
    val coroutineSupport = object : AssertingPlatformCoroutineSupport(warnAboutUsingLegacyEdt = true) {
      override fun isLegacyEdtWarningEnabled() = true

      override fun logWarning(className: String) {
        warn = className
      }
    }

    coroutineSupport.uiDispatcher(UiDispatcherKind.STRICT, false)
    assertThat(warn).isNull()

    coroutineSupport.uiDispatcher(UiDispatcherKind.RELAX, false)
    assertThat(warn).isNull()

    coroutineSupport.uiDispatcher(UiDispatcherKind.LEGACY, false)
    assertThat(warn).isEqualTo("com.intellij.application.impl.EdtDispatcherAssertTest")
  }
}