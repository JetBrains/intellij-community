// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Test
import org.junit.Assert

class Qual<caret>ified {
    @Test
    fun testMethodCall() {
        Assert.assertArrayEquals(arrayOf<Any>(), null)
        Assert.assertArrayEquals("message", arrayOf<Any>(), null)
        Assert.assertEquals("Expected", "actual")
        Assert.assertEquals("message", "Expected", "actual")
        Assert.fail()
        Assert.fail("")
    }

    @Test
    fun testMethodRef() {
        fun foo(param: (Boolean) -> Unit) = param(false)
        foo(Assert::assertTrue)
    }
}