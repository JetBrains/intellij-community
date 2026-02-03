// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.core.theme

import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.bundle.DynamicBundle
import org.junit.jupiter.api.Test

private val MNEMONIC = 0x1B.toChar().toString()

internal class DynamicBundleTest {
    private val testBundle = DynamicBundle(DynamicBundle::class.java, "messages.TestBundle")

    @Test
    fun `plain text should load`() {
        val msg = testBundle.getMessage("plain.hello")
        assertEquals("Hello", msg)
    }

    @Test
    fun `parameterized text should load and formatted`() {
        val msg = testBundle.getMessage("hello.param", "World")
        assertEquals("Hello World", msg)
    }

    @Test
    fun `the ampersand should be rendered as the mnemonic char key`() {
        val msg = testBundle.getMessage("menu.open")
        // Current implementation inserts the mnemonic marker for single '&' on all OSes
        assertEquals(MNEMONIC + "Open", msg)
    }

    @Test
    fun `double mnemonic are handled differently based on platform`() {
        val msg = testBundle.getMessage("menu.copy")
        // Accept both outcomes depending on the underlying OS
        val macExpectation = "Copy $MNEMONIC Paste"
        val nonMacExpectation = "Copy  Paste"
        assertTrue(msg == macExpectation || msg == nonMacExpectation, "Unexpected processed value: '$msg'")
    }

    @Test
    fun `escaped ampersand should render normally`() {
        val msg = testBundle.getMessage("menu.copy.paste")
        assertEquals("Copy & Paste", msg)
    }

    @Test
    fun `concurrent access should not cause problems`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..12).map { pool.submit<String> { testBundle.getMessage("plain.hello") } }
            tasks.forEach { assertEquals("Hello", it.get()) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `english ordinals should be formated correctly`() {
        assertEquals("Value: 1st", testBundle.getMessage("number.ordinal", 1))
        assertEquals("Value: 2nd", testBundle.getMessage("number.ordinal", 2))
        assertEquals("Value: 3rd", testBundle.getMessage("number.ordinal", 3))
        assertEquals("Value: 4th", testBundle.getMessage("number.ordinal", 4))
        assertEquals("Value: 11th", testBundle.getMessage("number.ordinal", 11))
        assertEquals("Value: 12th", testBundle.getMessage("number.ordinal", 12))
        assertEquals("Value: 13th", testBundle.getMessage("number.ordinal", 13))
        assertEquals("Value: 14th", testBundle.getMessage("number.ordinal", 14))
        assertEquals("Value: 21st", testBundle.getMessage("number.ordinal", 21))
        assertEquals("Value: 22nd", testBundle.getMessage("number.ordinal", 22))
        assertEquals("Value: 23rd", testBundle.getMessage("number.ordinal", 23))
        assertEquals("Value: 24th", testBundle.getMessage("number.ordinal", 24))
        assertEquals("Value: 101st", testBundle.getMessage("number.ordinal", 101))
        assertEquals("Value: 404th", testBundle.getMessage("number.ordinal", 404))
    }
}
