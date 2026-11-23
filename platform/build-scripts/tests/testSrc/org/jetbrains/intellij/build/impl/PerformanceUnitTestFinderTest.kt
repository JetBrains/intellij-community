// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestFrameworkUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * The fixtures carry no `@Test` method.
 * A performance build runs every marked test, and a marked fixture with a test method would join it.
 */
class PerformanceUnitTestFinderTest {
  private val finder = PerformanceUnitTestFinder(PerformanceUnitTestFinderTest::class.java.classLoader)

  @Test
  fun `the finder reports the same classes as the test process`() {
    val fixtures = listOf(
      MarkedCase::class.java,
      MarkedMethodCase::class.java,
      InheritedMarkedMethodCase::class.java,
      MarkedInterfaceMethodCase::class.java,
      MarkedBaseChildCase::class.java,
      PrivateMarkedMethodCase::class.java,
      AbstractMarkedMethodCase::class.java,
      PlainCase::class.java,
      JdkDerivedCase::class.java,
    )

    for (fixture in fixtures) {
      assertThat(finder.hasPerformanceTest(fixture.name))
        .describedAs(fixture.name)
        .isEqualTo(hasPerformanceTestByReflection(fixture))
    }
  }

  @Test
  fun `the finder sees a marked method of a supertype and ignores a mark on a superclass`() {
    // The expected values are spelled out, so a change of `hasPerformanceTestByReflection` cannot make the test vacuous.
    assertThat(finder.hasPerformanceTest(MarkedCase::class.java.name)).isTrue()
    assertThat(finder.hasPerformanceTest(MarkedMethodCase::class.java.name)).isTrue()
    assertThat(finder.hasPerformanceTest(InheritedMarkedMethodCase::class.java.name)).isTrue()
    assertThat(finder.hasPerformanceTest(MarkedInterfaceMethodCase::class.java.name)).isTrue()
    assertThat(finder.hasPerformanceTest(MarkedBaseChildCase::class.java.name)).isFalse()
    assertThat(finder.hasPerformanceTest(PrivateMarkedMethodCase::class.java.name)).isFalse()
    assertThat(finder.hasPerformanceTest(AbstractMarkedMethodCase::class.java.name)).isFalse()
    assertThat(finder.hasPerformanceTest(PlainCase::class.java.name)).isFalse()
    assertThat(finder.hasPerformanceTest(JdkDerivedCase::class.java.name)).isFalse()
  }

  @Test
  fun `an unknown class counts as a performance test`() {
    assertThat(finder.hasPerformanceTest("com.example.AbsentTest")).isTrue()
  }

  /** The decision of the test process: it runs a method that `TestFrameworkUtil.isPerformanceTest` accepts. */
  private fun hasPerformanceTestByReflection(testClass: Class<*>): Boolean {
    return !Modifier.isAbstract(testClass.modifiers) &&
           (TestFrameworkUtil.isPerformanceTest(null, testClass) ||
            testClass.methods.any { TestFrameworkUtil.isPerformanceTest(it.name, testClass) })
  }

  @PerformanceUnitTest
  class MarkedCase {
    fun measure() = Unit
  }

  class MarkedMethodCase {
    @PerformanceUnitTest
    fun measure() = Unit
  }

  open class MarkedMethodBaseCase {
    @PerformanceUnitTest
    fun measure() = Unit
  }

  class InheritedMarkedMethodCase : MarkedMethodBaseCase()

  interface MarkedMethodInterface {
    @PerformanceUnitTest
    fun measure() = Unit
  }

  class MarkedInterfaceMethodCase : MarkedMethodInterface

  @PerformanceUnitTest
  open class MarkedBaseCase

  class MarkedBaseChildCase : MarkedBaseCase()

  class PrivateMarkedMethodCase {
    @PerformanceUnitTest
    private fun measure() = Unit

    fun run() = measure()
  }

  abstract class AbstractMarkedMethodCase {
    @PerformanceUnitTest
    fun measure() = Unit
  }

  class PlainCase {
    fun measure() = Unit
  }

  /** The walk must stop at a JDK superclass, whose class file the test class path does not hold. */
  class JdkDerivedCase : Thread()
}
