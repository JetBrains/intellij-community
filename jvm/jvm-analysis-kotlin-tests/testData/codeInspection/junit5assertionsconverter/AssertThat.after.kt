// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Assert.*
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test

internal class Test1 {
    @Test
    fun testFirst(matcher: Matcher<String>) {
        MatcherAssert.assertThat("reason", "null", matcher)
    }
}