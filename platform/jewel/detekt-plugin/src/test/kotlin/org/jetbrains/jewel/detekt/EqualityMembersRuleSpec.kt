// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.github.detekt.test.utils.compileContentForTest
import io.github.detekt.test.utils.readResourceContent
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Suppress("ClassName")
class EqualityMembersRuleSpec {
    private val subject = EqualityMembersRule(TestConfig(Config.AUTO_CORRECT_KEY to true))

    @Nested
    inner class `classes without the annotation are ignored` {
        @Test
        fun `should not report when no annotation is present`() {
            val code =
                """
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |)
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).isEmpty()
        }
    }

    @Nested
    inner class `missing members` {
        @Test
        fun `should report missing equals, hashCode, and toString`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |)
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0])
                .hasMessage("DataFuncTest is missing required functions: equals, hashCode, toString.")
        }

        @Test
        fun `should report missing hashCode and toString`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    override fun equals(other: Any?): Boolean {
                |        if (this === other) return true
                |        if (javaClass != other?.javaClass) return false
                |        
                |        other as DataFuncTest
                |        
                |        if (a != other.a) return false
                |        if (b != other.b) return false
                |        return true
                |    }
                |}
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage("DataFuncTest is missing required functions: hashCode, toString.")
        }
    }

    @Nested
    inner class `incomplete members` {
        @Test
        fun `should report incomplete equals method`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    override fun equals(other: Any?): Boolean {
                |        if (this === other) return true
                |        if (javaClass != other?.javaClass) return false
                |
                |        other as DataFuncTest
                |
                |        if (a != other.a) return false
                |
                |        return true
                |    }
                |}
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).hasSize(2) // Missing hashCode/toString + incomplete equals
            assertThat(findings[0]).hasMessage("DataFuncTest is missing required functions: hashCode, toString.")
            assertThat(findings[1]).hasMessage("Function equals is missing property b.")
        }

        @Test
        fun `should report incomplete hashCode method`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    override fun hashCode(): Int {
                |        return a.hashCode()
                |    }
                |}
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).hasSize(2) // Missing equals/toString + incomplete hashCode
            assertThat(findings[0]).hasMessage("DataFuncTest is missing required functions: equals, toString.")
            assertThat(findings[1]).hasMessage("Function hashCode is missing property b.")
        }

        @Test
        fun `should report incomplete toString method`() {
            val code =
                $$"""
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    override fun toString(): String {
                |        return "DataFuncTest(a=$a)"
                |    }
                |}
                """
                    .trimMargin()
            val findings = subject.lint(code)
            assertThat(findings).hasSize(2) // Missing equals/hashCode + incomplete toString
            assertThat(findings[0]).hasMessage("DataFuncTest is missing required functions: equals, hashCode.")
            assertThat(findings[1]).hasMessage("Function toString is missing property b.")
        }
    }

    @Nested
    inner class `auto-correction` {
        @Test
        fun `should generate missing equals, hashCode, and toString`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |)
                """
                    .trimMargin()

            val (findings, result) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(result)
                .isEqualTo(
                    $$"""
                    |annotation class GenerateDataFunctions
                    |
                    |@GenerateDataFunctions
                    |class DataFuncTest(
                    |    val a: String,
                    |    val b: String
                    |){
                    |
                    |override fun equals(other: Any?): Boolean {
                    |    if (this === other) return true
                    |    if (javaClass != other?.javaClass) return false
                    |
                    |    other as DataFuncTest
                    |
                    |    if (a != other.a) return false
                    |    if (b != other.b) return false
                    |
                    |    return true
                    |}
                    |
                    |override fun hashCode(): Int {
                    |    var result = a.hashCode()
                    |    result = 31 * result + b.hashCode()
                    |    return result
                    |}
                    |
                    |override fun toString(): String {
                    |    return "DataFuncTest(a=$a, b=$b)"
                    |}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should fix incomplete equals method`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(val a: String, val b: String) {
                |    override fun equals(other: Any?): Boolean {
                |        if (this === other) return true
                |        if (javaClass != other?.javaClass) return false
                |
                |        other as DataFuncTest
                |
                |        if (a != other.a) return false
                |
                |        return true
                |    }
                |}
                """
                    .trimMargin()

            val (findings, result) = subject.lintAndFix(code)

            assertThat(findings).hasSize(2)

            assertThat(result)
                .isEqualTo(
                    $$"""
                    |annotation class GenerateDataFunctions
                    |
                    |@GenerateDataFunctions
                    |class DataFuncTest(val a: String, val b: String) {
                    |
                    |override fun equals(other: Any?): Boolean {
                    |    if (this === other) return true
                    |    if (javaClass != other?.javaClass) return false
                    |
                    |    other as DataFuncTest
                    |
                    |    if (a != other.a) return false
                    |    if (b != other.b) return false
                    |
                    |    return true
                    |}
                    |
                    |override fun hashCode(): Int {
                    |    var result = a.hashCode()
                    |    result = 31 * result + b.hashCode()
                    |    return result
                    |}
                    |
                    |override fun toString(): String {
                    |    return "DataFuncTest(a=$a, b=$b)"
                    |}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should not touch unrelated functions`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    fun unrelated() {
                |        println("hello")
                |    }
                |}
                """
                    .trimMargin()

            val (findings, result) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(result)
                .isEqualTo(
                    $$"""
                    |annotation class GenerateDataFunctions
                    |
                    |@GenerateDataFunctions
                    |class DataFuncTest(
                    |    val a: String,
                    |    val b: String
                    |) {
                    |    fun unrelated() {
                    |        println("hello")
                    |    }
                    |
                    |override fun equals(other: Any?): Boolean {
                    |    if (this === other) return true
                    |    if (javaClass != other?.javaClass) return false
                    |
                    |    other as DataFuncTest
                    |
                    |    if (a != other.a) return false
                    |    if (b != other.b) return false
                    |
                    |    return true
                    |}
                    |
                    |override fun hashCode(): Int {
                    |    var result = a.hashCode()
                    |    result = 31 * result + b.hashCode()
                    |    return result
                    |}
                    |
                    |override fun toString(): String {
                    |    return "DataFuncTest(a=$a, b=$b)"
                    |}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should not reorder unrelated functions`() {
            val code =
                """
                |annotation class GenerateDataFunctions
                |
                |@GenerateDataFunctions
                |class DataFuncTest(
                |    val a: String,
                |    val b: String
                |) {
                |    fun unrelated() {
                |        println("hello")
                |    }
                |
                |    fun unrelated2() {
                |        println("hello2")
                |    }
                |}
                """
                    .trimMargin()

            val (findings, result) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(result)
                .isEqualTo(
                    $$"""
                    |annotation class GenerateDataFunctions
                    |
                    |@GenerateDataFunctions
                    |class DataFuncTest(
                    |    val a: String,
                    |    val b: String
                    |) {
                    |    fun unrelated() {
                    |        println("hello")
                    |    }
                    |
                    |    fun unrelated2() {
                    |        println("hello2")
                    |    }
                    |
                    |override fun equals(other: Any?): Boolean {
                    |    if (this === other) return true
                    |    if (javaClass != other?.javaClass) return false
                    |
                    |    other as DataFuncTest
                    |
                    |    if (a != other.a) return false
                    |    if (b != other.b) return false
                    |
                    |    return true
                    |}
                    |
                    |override fun hashCode(): Int {
                    |    var result = a.hashCode()
                    |    result = 31 * result + b.hashCode()
                    |    return result
                    |}
                    |
                    |override fun toString(): String {
                    |    return "DataFuncTest(a=$a, b=$b)"
                    |}
                    |}
                    """
                        .trimMargin()
                )
        }
    }

    @Nested
    inner class `real-life scenarios` {
        @Test
        fun `should not crash on TitleBarStyling`() {
            val code = readResourceContent("TitleBarStyling-forTest.kt")
            val (findings, result) = subject.lintAndFix(code)

            assertThat(findings).isEmpty()
            assertThat(result).isEqualTo(code)
        }
    }

    private fun EqualityMembersRule.lintAndFix(@Language("kotlin") code: String): Pair<List<Finding>, String> {
        val ktFile = compileContentForTest(code)
        visit(ktFile)
        return findings to ktFile.text
    }
}
