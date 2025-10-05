// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Suppress("ClassName")
internal class MissingApiStatusAnnotationRuleSpec {
    private val subject = MissingApiStatusAnnotationRule(TestConfig(Config.AUTO_CORRECT_KEY to true))

    @Nested
    inner class `violations for missing ApiStatus annotation` {
        @Test
        fun `should report for class with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |class MyClass
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |class MyClass
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for class with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |class MyClass
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |class MyClass
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for function with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |fun myFunction() {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |fun myFunction() {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for function with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |fun myFunction() {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |fun myFunction() {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |val myVal = 0
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |val myVal = 0
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |val myVal = 0
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |val myVal = 0
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for typealias with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |typealias MyAlias = String
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |typealias MyAlias = String
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for typealias with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |typealias MyAlias = String
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |typealias MyAlias = String
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for object with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |object MyObject
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |object MyObject
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for primary constructor with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass @ExperimentalJewelApi constructor()
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass @ApiStatus.Experimental
                    |@ExperimentalJewelApi constructor()
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for secondary constructor with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |  @InternalJewelApi
                |  constructor()
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass {
                    |  @ApiStatus.Internal
                    |@InternalJewelApi
                    |  constructor()
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for class initializer with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass {
                |  @ExperimentalJewelApi
                |  init { println("") }
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass {
                    |  @ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |  init { println("") }
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property getter with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |  val myVal: Int
                |    @InternalJewelApi
                |    get() = 1
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass {
                    |  val myVal: Int
                    |    @ApiStatus.Internal
                    |@InternalJewelApi
                    |    get() = 1
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property setter with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass {
                |  var myVal: Int = 1
                |    @ExperimentalJewelApi
                |    set(value) {}
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass {
                    |  var myVal: Int = 1
                    |    @ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |    set(value) {}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for parameter with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |fun myFunction(@InternalJewelApi param: Int) {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |fun myFunction(@ApiStatus.Internal
                    |@InternalJewelApi param: Int) {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for interface with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |interface MyInterface
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |interface MyInterface
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for enum with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |enum class MyEnum
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |@ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |enum class MyEnum
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for companion object with InternalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |  @InternalJewelApi
                |  companion object
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass {
                    |  @ApiStatus.Internal
                    |@InternalJewelApi
                    |  companion object
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property in primary constructor with ExperimentalJewelApi`() {
            val code =
                """
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass(
                |  @ExperimentalJewelApi
                |  val myVal: Int
                |)
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.annotations.ApiStatus
                    |class MyClass(
                    |  @ApiStatus.Experimental
                    |@ExperimentalJewelApi
                    |  val myVal: Int
                    |)
                    """
                        .trimMargin()
                )
        }
    }

    @Nested
    inner class `violations for missing Jewel annotation` {
        @Test
        fun `should report for class with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |class MyClass
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |class MyClass
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for class with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Experimental
                |class MyClass
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |class MyClass
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for function with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |fun myFunction() {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |fun myFunction() {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for function with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Experimental
                |fun myFunction() {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |fun myFunction() {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |val myVal = 0
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |val myVal = 0
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Experimental
                |val myVal = 0
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |val myVal = 0
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for typealias with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |typealias MyAlias = String
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |typealias MyAlias = String
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for typealias with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Experimental
                |typealias MyAlias = String
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |typealias MyAlias = String
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for object with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |object MyObject
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |object MyObject
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for primary constructor with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass @ApiStatus.Experimental constructor()
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |class MyClass @ExperimentalJewelApi
                    |@ApiStatus.Experimental constructor()
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for secondary constructor with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass {
                |  @ApiStatus.Internal
                |  constructor()
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |class MyClass {
                    |  @InternalJewelApi
                    |@ApiStatus.Internal
                    |  constructor()
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for class initializer with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass {
                |  @ApiStatus.Experimental
                |  init {}
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |class MyClass {
                    |  @ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |  init {}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property getter with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass {
                |  val myVal: Int
                |    @ApiStatus.Internal
                |    get() = 1
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |class MyClass {
                    |  val myVal: Int
                    |    @InternalJewelApi
                    |@ApiStatus.Internal
                    |    get() = 1
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property setter with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass {
                |  var myVal: Int = 1
                |    @ApiStatus.Experimental
                |    set(value) {}
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |class MyClass {
                    |  var myVal: Int = 1
                    |    @ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |    set(value) {}
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for parameter with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |fun myFunction(@ApiStatus.Internal param: Int) {}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |fun myFunction(@InternalJewelApi
                    |@ApiStatus.Internal param: Int) {}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for interface with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Internal
                |interface MyInterface
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@InternalJewelApi
                    |@ApiStatus.Internal
                    |interface MyInterface
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for enum with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |@ApiStatus.Experimental
                |enum class MyEnum
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |enum class MyEnum
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for companion object with ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass {
                |  @ApiStatus.Internal
                |  companion object
                |}
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |class MyClass {
                    |  @InternalJewelApi
                    |@ApiStatus.Internal
                    |  companion object
                    |}
                    """
                        .trimMargin()
                )
        }

        @Test
        fun `should report for property in primary constructor with ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |class MyClass(
                |  @ApiStatus.Experimental
                |  val myVal: Int
                |)
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(1)
            assertThat(findings.first().message)
                .isEqualTo(
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing."
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |class MyClass(
                    |  @ExperimentalJewelApi
                    |@ApiStatus.Experimental
                    |  val myVal: Int
                    |)
                    """
                        .trimMargin()
                )
        }
    }

    @Nested
    inner class `violations for mismatched annotations` {
        @Test
        fun `should report for class with InternalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Experimental
                |class MismatchedAnnotation
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(2)
            assertThat(findings.map(Finding::message))
                .containsExactlyInAnyOrder(
                    "The annotation `@InternalJewelApi` is present, but the required annotation `@ApiStatus.Internal` is missing.",
                    "The annotation `@ApiStatus.Experimental` is present, but the required annotation `@ExperimentalJewelApi` is missing.",
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Internal
                    |@InternalJewelApi
                    |@ApiStatus.Experimental
                    |class MismatchedAnnotation
                    """
                        .trimMargin()
                )
            assertThat(fixedCode).contains("@ExperimentalJewelApi")
            assertThat(fixedCode).contains("import org.jetbrains.jewel.foundation.ExperimentalJewelApi")
        }

        @Test
        fun `should report for class with ExperimentalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Internal
                |class MismatchedAnnotation
                """
                    .trimMargin()
            val (findings, fixedCode) = subject.lintAndFix(code)

            assertThat(findings).hasSize(2)
            assertThat(findings.map(Finding::message))
                .containsExactlyInAnyOrder(
                    "The annotation `@ExperimentalJewelApi` is present, but the required annotation `@ApiStatus.Experimental` is missing.",
                    "The annotation `@ApiStatus.Internal` is present, but the required annotation `@InternalJewelApi` is missing.",
                )

            assertThat(fixedCode)
                .isEqualTo(
                    """
                    |import org.jetbrains.annotations.ApiStatus
                    |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                    |import org.jetbrains.jewel.foundation.InternalJewelApi
                    |@ApiStatus.Experimental
                    |@InternalJewelApi
                    |@ExperimentalJewelApi
                    |@ApiStatus.Internal
                    |class MismatchedAnnotation
                    """
                        .trimMargin()
                )
            assertThat(fixedCode).contains("@InternalJewelApi")
            assertThat(fixedCode).contains("import org.jetbrains.jewel.foundation.InternalJewelApi")
        }
    }

    @Nested
    inner class `correctly annotated code` {
        @Test
        fun `should not report for class with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |class MyClass
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for class with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Experimental
                |class MyClass
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for function with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |fun myFunction() {}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for function with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Experimental
                |fun myFunction() {}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for property with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |val myVal = 0
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for property with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Experimental
                |val myVal = 0
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for typealias with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |typealias MyAlias = String
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for typealias with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Experimental
                |typealias MyAlias = String
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for object with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |object MyObject
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for primary constructor with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass @ExperimentalJewelApi @ApiStatus.Experimental constructor()
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for secondary constructor with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |  @InternalJewelApi
                |  @ApiStatus.Internal
                |  constructor()
                |}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for class initializer with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass {
                |  @ExperimentalJewelApi
                |  @ApiStatus.Experimental
                |  init { println("") }
                |}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for property getter with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |  val myVal: Int
                |    @InternalJewelApi
                |    @ApiStatus.Internal
                |    get() = 1
                |}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for property setter with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass {
                |  var myVal: Int = 1
                |    @ExperimentalJewelApi
                |    @ApiStatus.Experimental
                |    set(value) {}
                |}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for parameter with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |fun myFunction(@InternalJewelApi @ApiStatus.Internal param: Int) {}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for interface with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |@InternalJewelApi
                |@ApiStatus.Internal
                |interface MyInterface
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for enum with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |@ExperimentalJewelApi
                |@ApiStatus.Experimental
                |enum class MyEnum
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for companion object with InternalJewelApi and ApiStatus Internal`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.InternalJewelApi
                |class MyClass {
                |    @InternalJewelApi
                |    @ApiStatus.Internal
                |    companion object {}
                |}
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }

        @Test
        fun `should not report for property in primary constructor with ExperimentalJewelApi and ApiStatus Experimental`() {
            val code =
                """
                |import org.jetbrains.annotations.ApiStatus
                |import org.jetbrains.jewel.foundation.ExperimentalJewelApi
                |class MyClass(
                |  @ExperimentalJewelApi
                |  @ApiStatus.Experimental
                |  val myVal: Int
                |)
                """
                    .trimMargin()
            val findings = subject.compileAndLint(code)

            assertThat(findings).isEmpty()
        }
    }
}
