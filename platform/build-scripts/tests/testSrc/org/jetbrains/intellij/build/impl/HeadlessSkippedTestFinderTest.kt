// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.idea.IJIgnore
import com.intellij.testFramework.SkipInHeadlessEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

class HeadlessSkippedTestFinderTest {
  private val finder = HeadlessSkippedTestFinder(HeadlessSkippedTestFinderTest::class.java.classLoader)

  @Test
  fun `the finder reports the same classes as the reflection it replaces`() {
    val fixtures = listOf(
      MarkedCase::class.java,
      MarkedBaseCase::class.java,
      InheritingCase::class.java,
      GrandchildCase::class.java,
      MarkedInterface::class.java,
      IgnoredMarkedCase::class.java,
      IgnoredPlainCase::class.java,
      PlainCase::class.java,
      PlainBaseCase::class.java,
      PlainChildCase::class.java,
      JdkDerivedCase::class.java,
    )

    for (fixture in fixtures) {
      assertThat(finder.isSkippedInHeadlessEnvironment(fixture.name))
        .describedAs(fixture.name)
        .isEqualTo(isSkippedByReflection(fixture))
    }
  }

  @Test
  fun `the finder sees an annotation of a superclass and skips an abstract class`() {
    // The expected values are spelled out, so a change of `isSkippedByReflection` cannot make the test vacuous.
    assertThat(finder.isSkippedInHeadlessEnvironment(MarkedCase::class.java.name)).isTrue()
    assertThat(finder.isSkippedInHeadlessEnvironment(InheritingCase::class.java.name)).isTrue()
    assertThat(finder.isSkippedInHeadlessEnvironment(GrandchildCase::class.java.name)).isTrue()
    assertThat(finder.isSkippedInHeadlessEnvironment(MarkedBaseCase::class.java.name)).isFalse()
    assertThat(finder.isSkippedInHeadlessEnvironment(MarkedInterface::class.java.name)).isFalse()
    assertThat(finder.isSkippedInHeadlessEnvironment(IgnoredMarkedCase::class.java.name)).isFalse()
    assertThat(finder.isSkippedInHeadlessEnvironment(PlainCase::class.java.name)).isFalse()
    assertThat(finder.isSkippedInHeadlessEnvironment(PlainChildCase::class.java.name)).isFalse()
    assertThat(finder.isSkippedInHeadlessEnvironment(JdkDerivedCase::class.java.name)).isFalse()
  }

  @Test
  fun `the finder runs no class initializer`() {
    // One test method holds both checks, because the flag stays set after the first initializer runs.
    assertThat(EnumDefaultInitialization.ran).isFalse()

    assertThat(finder.isSkippedInHeadlessEnvironment(MarkedCaseWithAnEnumDefault::class.java.name)).isTrue()
    assertThat(EnumDefaultInitialization.ran)
      .describedAs("the finder must not run the initializer of an enum that an annotation uses as a default value")
      .isFalse()

    // The reflection that the finder replaces does run it. This is what used to deadlock the build.
    assertThat(isSkippedByReflection(MarkedCaseWithAnEnumDefault::class.java)).isTrue()
    assertThat(EnumDefaultInitialization.ran).isTrue()
  }

  @Test
  fun `an unknown class fails loudly`() {
    assertThatThrownBy { finder.isSkippedInHeadlessEnvironment("com.example.AbsentTest") }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("com/example/AbsentTest.class")
  }

  /** The predicate that `TestingTasksImpl#loadTestsSkippedInHeadlessEnvironment` used before the finder. */
  private fun isSkippedByReflection(testClass: Class<*>): Boolean {
    return !Modifier.isAbstract(testClass.modifiers) &&
           !testClass.isAnnotationPresent(IJIgnore::class.java) &&
           testClass.isAnnotationPresent(SkipInHeadlessEnvironment::class.java)
  }

  @SkipInHeadlessEnvironment
  @Suppress("JUnitTestCaseWithNoTests")
  class MarkedCase

  @SkipInHeadlessEnvironment
  abstract class MarkedBaseCase

  open class InheritingCase : MarkedBaseCase()

  class GrandchildCase : InheritingCase()

  @SkipInHeadlessEnvironment
  interface MarkedInterface

  @SkipInHeadlessEnvironment
  @IJIgnore(issue = "IJPL-000000")
  @Suppress("JUnitTestCaseWithNoTests")
  class IgnoredMarkedCase

  @IJIgnore(issue = "IJPL-000000")
  @Suppress("JUnitTestCaseWithNoTests")
  class IgnoredPlainCase

  class PlainCase

  abstract class PlainBaseCase

  class PlainChildCase : PlainBaseCase()

  /** The walk must stop at a JDK superclass, whose class file the test class path does not hold. */
  class JdkDerivedCase : Thread()

}

/**
 * The fixtures below stay at the top level on purpose.
 * JUnit searches the nested classes of a test class for `@Nested`, and that search reads their annotations.
 * A nested fixture would therefore run the initializer of [EnvironmentFixture] before the test observes it.
 */
@SkipInHeadlessEnvironment
@WithAnEnumDefault
@Suppress("JUnitTestCaseWithNoTests")
internal class MarkedCaseWithAnEnumDefault

/** Records whether the JVM has run the initializer of [EnvironmentFixture]. */
internal object EnumDefaultInitialization {
  private val initialized = AtomicBoolean()

  val ran: Boolean get() = initialized.get()

  fun record() {
    initialized.set(true)
  }
}

internal enum class EnvironmentFixture {
  DEFAULT;

  init {
    EnumDefaultInitialization.record()
  }
}

/**
 * Repeats the shape that hung the build. `PyEnvTestCase` uses an enum constant as a default value, and the JVM runs
 * the initializer of that enum when it reads any annotation of the class.
 */
internal annotation class WithAnEnumDefault(val environment: EnvironmentFixture = EnvironmentFixture.DEFAULT)
