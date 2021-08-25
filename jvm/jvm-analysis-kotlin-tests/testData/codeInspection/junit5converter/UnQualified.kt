// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Test
import org.junit.Assert.*

class UnQual<caret>ified {
    @Test
    fun testMethodCall() {
        assertArrayEquals(arrayOf<Any>(), null)
        assertArrayEquals("message", arrayOf<Any>(), null)
        assertEquals("Expected", "actual")
        assertEquals("message", "Expected", "actual")
        fail()
        fail("")
    }

    @Test
    fun testMethodRef() {
        fun foo(param: (Boolean) -> Unit) = param(false)
        foo(::assertTrue)
    }
}