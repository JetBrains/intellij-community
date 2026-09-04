// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.windowsProcessApi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the ABI of the `CreateProcessW` structures on every OS: the layouts are plain data and touch no Windows library.
 * The sizes and the offsets match the 64-bit Windows SDK headers.
 */
class Kernel32LayoutTest {
  @Test
  fun securityAttributesLayout() {
    assertEquals(24, Kernel32.SECURITY_ATTRIBUTES.byteSize())
    assertEquals(0, Kernel32.SECURITY_ATTRIBUTES_LENGTH_OFFSET)
    assertEquals(16, Kernel32.SECURITY_ATTRIBUTES_INHERIT_HANDLE_OFFSET)
  }

  @Test
  fun processInformationLayout() {
    assertEquals(24, Kernel32.PROCESS_INFORMATION.byteSize())
    assertEquals(0, Kernel32.PROCESS_INFORMATION_PROCESS_OFFSET)
    assertEquals(8, Kernel32.PROCESS_INFORMATION_THREAD_OFFSET)
    assertEquals(16, Kernel32.PROCESS_INFORMATION_PROCESS_ID_OFFSET)
  }

  @Test
  fun startupInfoLayout() {
    assertEquals(104, Kernel32.STARTUPINFOW.byteSize())
    assertEquals(0, Kernel32.STARTUPINFOW_CB_OFFSET)
    assertEquals(60, Kernel32.STARTUPINFOW_FLAGS_OFFSET)
    assertEquals(80, Kernel32.STARTUPINFOW_STD_INPUT_OFFSET)
    assertEquals(88, Kernel32.STARTUPINFOW_STD_OUTPUT_OFFSET)
    assertEquals(96, Kernel32.STARTUPINFOW_STD_ERROR_OFFSET)
  }
}
