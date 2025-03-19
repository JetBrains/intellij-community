// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualityMembersRuleSpec {
    private val subject = EqualityMembersRule(Config.empty)

    @Test
    fun `should find missing functions`() {
        val findings = subject.lint(code)
        assertEquals(findings.isNotEmpty(), true)
    }
}

private val code: String =
    """
    annotation class GenerateDataFunctions

    @GenerateDataFunctions
    class DataFuncTest(
        val a: String,
        val b: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DataFuncTest

            if (a != other.a) return false

            return true
        }
    }
"""
        .trimIndent()
