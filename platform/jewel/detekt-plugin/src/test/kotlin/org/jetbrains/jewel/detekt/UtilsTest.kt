// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule
import org.junit.jupiter.api.Test

class UtilsTest {
    @Test
    fun `isJewelSymbol should return true for exact org_jetbrains_jewel package`() {
        val rule = EqualityMembersRule(TestConfig())
        val code =
            """
            |package org.jetbrains.jewel
            |
            |annotation class GenerateDataFunctions
            |
            |@GenerateDataFunctions
            |class TestClass(val prop: String)
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing methods, which means the rule ran
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `isJewelSymbol should return true for org_jetbrains_jewel subpackages`() {
        val rule = EqualityMembersRule(TestConfig())
        val code =
            """
            |package org.jetbrains.jewel.foundation
            |
            |annotation class GenerateDataFunctions
            |
            |@GenerateDataFunctions
            |class TestClass(val prop: String)
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing methods, which means the rule ran
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `isJewelSymbol should return true for deeply nested org_jetbrains_jewel subpackages`() {
        val rule = EqualityMembersRule(TestConfig())
        val code =
            """
            |package org.jetbrains.jewel.ui.component
            |
            |annotation class GenerateDataFunctions
            |
            |@GenerateDataFunctions
            |class TestClass(val prop: String)
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing methods, which means the rule ran
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `isJewelSymbol should return true for no package declaration`() {
        val rule = EqualityMembersRule(TestConfig())
        val code =
            """
            |annotation class GenerateDataFunctions
            |
            |@GenerateDataFunctions
            |class TestClass(val prop: String)
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing methods, which means the rule ran
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `isJewelSymbol should return false for non-jewel packages`() {
        val rule = EqualityMembersRule(TestConfig())
        val code =
            """
            |package com.example.other
            |
            |annotation class GenerateDataFunctions
            |
            |@GenerateDataFunctions
            |class TestClass(val prop: String)
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should NOT find anything, which means the rule didn't run
        assertThat(findings).isEmpty()
    }

    @Test
    fun `MissingApiStatusAnnotationRule should work with exact org_jetbrains_jewel package`() {
        val rule = MissingApiStatusAnnotationRule(TestConfig())
        val code =
            """
            |package org.jetbrains.jewel
            |
            |import org.jetbrains.jewel.foundation.InternalJewelApi
            |
            |@InternalJewelApi
            |class TestClass
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing @ApiStatus.Internal annotation
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `MissingApiStatusAnnotationRule should work with Jewel subpackages`() {
        val rule = MissingApiStatusAnnotationRule(TestConfig())
        val code =
            """
            |package org.jetbrains.jewel.foundation
            |
            |import org.jetbrains.jewel.foundation.InternalJewelApi
            |
            |@InternalJewelApi
            |class TestClass
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing @ApiStatus.Internal annotation
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `MissingApiStatusAnnotationRule should work with root package`() {
        val rule = MissingApiStatusAnnotationRule(TestConfig())
        val code =
            """
            |import org.jetbrains.jewel.foundation.InternalJewelApi
            |
            |@InternalJewelApi
            |class TestClass
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should find the missing @ApiStatus.Internal annotation
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `MissingApiStatusAnnotationRule should not run for non-jewel packages`() {
        val rule = MissingApiStatusAnnotationRule(TestConfig())
        val code =
            """
            |package com.example.other
            |
            |import org.jetbrains.jewel.foundation.InternalJewelApi
            |
            |@InternalJewelApi
            |class TestClass
            """
                .trimMargin()

        val findings = rule.compileAndLint(code)
        // Should NOT find anything because the rule shouldn't run
        assertThat(findings).isEmpty()
    }
}
