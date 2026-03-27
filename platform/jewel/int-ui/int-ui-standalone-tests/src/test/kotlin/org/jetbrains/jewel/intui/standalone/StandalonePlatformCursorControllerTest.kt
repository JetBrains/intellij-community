// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import kotlin.test.assertEquals
import org.jetbrains.jewel.intui.standalone.fakes.FakeMacPlatformServices
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

internal class StandalonePlatformCursorControllerTest {
    private lateinit var fakePlatformServices: FakeMacPlatformServices
    private lateinit var controller: StandalonePlatformCursorController

    @BeforeEach
    fun setup() {
        fakePlatformServices = FakeMacPlatformServices()
        controller = StandalonePlatformCursorController(fakePlatformServices)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `verify hideCursorUntilMoved was called on macOS`() {
        controller.hideCursor()

        assertEquals(1, fakePlatformServices.hideCursorCalls, "Should have called native hide exactly once")
    }

    @Test
    @DisabledOnOs(OS.MAC)
    fun `verify hideCursorUntilMoved was not called on non-macOS`() {
        controller.hideCursor()

        assertEquals(0, fakePlatformServices.hideCursorCalls, "Should not call native hide on non-macOS")
    }

    @Test
    fun `hideCursor should not throw even if native service fails`() {
        val explodingFake =
            object : FakeMacPlatformServices() {
                override fun hideCursorUntilMoved() {
                    throw RuntimeException("Native library missing!")
                }
            }

        val controller = StandalonePlatformCursorController(explodingFake)

        assertDoesNotThrow { controller.hideCursor() }
    }
}
