// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class UnQualified {
    @Test
    fun testMethodCall() {
        Assertions.assertArrayEquals(arrayOf<Any>(), null)
        Assertions.assertArrayEquals(arrayOf<Any>(), null, "message")
        Assertions.assertEquals("Expected", "actual")
        Assertions.assertEquals("Expected", "actual", "message")
        Assertions.fail()
        Assertions.fail("")
    }

    @Test
    fun testMethodRef() {
        fun foo(param: (Boolean) -> Unit) = param(false)
        foo(Assertions::assertTrue)
    }
}