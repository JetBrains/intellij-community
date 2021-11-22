// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Assume.*
import org.junit.jupiter.api.Test

class Test1 {
    @Test
    fun testFirst() {
        assume<caret>True ("message", false)
    }
}