// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TestInspectionFixtures")

package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.codeInspection.test.AssertBetweenInconvertibleTypesInspection
import com.intellij.codeInspection.test.TestCaseWithConstructorInspection
import com.intellij.codeInspection.test.TestCaseWithoutTestsInspection
import com.intellij.codeInspection.test.TestFailedLineInspection
import com.intellij.codeInspection.test.TestMethodWithoutAssertionInspection
import com.intellij.codeInspection.test.TestOnlyInspection
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.Date

@Suppress("LightProjectDescriptorEqualsHashCode")
private class TestLibrariesDescriptor(private val addLibraries: ModifiableRootModel.() -> Unit) : ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    model.addLibraries()
  }
}

/**
 * Creates the project of an [AssertBetweenInconvertibleTypesInspection] test class. It has JUnit 3, the JUnit 5 API and AssertJ,
 * as [AssertEqualsBetweenInconvertibleTypesInspectionTestBase] has. [moreLibraries] adds more libraries to the module.
 */
@TestOnly
@JvmOverloads
fun assertEqualsBetweenInconvertibleTypesProjectFixture(moreLibraries: ModifiableRootModel.() -> Unit = {}): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(TestLibrariesDescriptor {
    addJUnit3Library()
    addJUnit5Library()
    addAssertJLibrary()
    moreLibraries()
  })

/**
 * Creates the fixture of an [AssertBetweenInconvertibleTypesInspection] test. It replaces [AssertEqualsBetweenInconvertibleTypesInspectionTestBase].
 * Give it the project of [assertEqualsBetweenInconvertibleTypesProjectFixture].
 */
@TestOnly
fun assertEqualsBetweenInconvertibleTypesInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<AssertBetweenInconvertibleTypesInspection>> =
  jvmInspectionFixture(projectFixture) { AssertBetweenInconvertibleTypesInspection() }

/**
 * Creates the project of a [TestCaseWithConstructorInspection] test class. It has JUnit 3 and JUnit 4,
 * as [TestCaseWithConstructorInspectionTestBase] has. [moreLibraries] adds more libraries to the module.
 */
@TestOnly
@JvmOverloads
fun testCaseWithConstructorProjectFixture(moreLibraries: ModifiableRootModel.() -> Unit = {}): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(TestLibrariesDescriptor {
    addJUnit3Library()
    addJUnit4Library()
    moreLibraries()
  })

/**
 * Creates the fixture of a [TestCaseWithConstructorInspection] test. It replaces [TestCaseWithConstructorInspectionTestBase].
 * Give it the project of [testCaseWithConstructorProjectFixture].
 */
@TestOnly
fun testCaseWithConstructorInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<TestCaseWithConstructorInspection>> =
  jvmInspectionFixture(projectFixture) { TestCaseWithConstructorInspection() }

/**
 * Creates the project of a [TestCaseWithoutTestsInspection] test class. It has JUnit 3, JUnit 4, the JUnit 5 API and TestNG,
 * as [TestCaseWithoutTestsInspectionTestBase] has.
 */
@TestOnly
fun testCaseWithoutTestsProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(TestLibrariesDescriptor {
  addJUnit3Library()
  addJUnit4Library()
  addJUnit5Library()
  addTestNGLibrary()
})

/**
 * Creates the fixture of a [TestCaseWithoutTestsInspection] test. It replaces [TestCaseWithoutTestsInspectionTestBase].
 * Give it the project of [testCaseWithoutTestsProjectFixture].
 */
@TestOnly
fun testCaseWithoutTestsInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<TestCaseWithoutTestsInspection>> =
  jvmInspectionFixture(projectFixture) { TestCaseWithoutTestsInspection() }

/**
 * The fixture of a [TestFailedLineInspection] test. It is the JUnit 5 form of [TestFailedLineInspectionTestBase].
 * Create it with [testFailedLineInspectionFixture].
 */
@TestOnly
class TestFailedLineInspectionFixture internal constructor(
  base: JvmInspectionFixture<TestFailedLineInspection>,
) : JvmInspectionFixture<TestFailedLineInspection>(base.fixture, base.inspection) {
  /**
   * Stores a failed state of the test [url] with [stackTrace] and [errorMessage], checks the highlighting of [text],
   * and checks that the failed line has a bold dotted line.
   */
  fun doTest(
    lang: JvmLanguage,
    text: String,
    fileName: String = generateFileName(),
    url: String,
    stackTrace: String,
    errorMessage: String,
  ) {
    val pair = runReadActionBlocking { TestStackTraceParser(url, stackTrace, errorMessage, JavaTestLocator.INSTANCE, project) }
    val record = TestStateStorage.Record(
      TestStateInfo.Magnitude.FAILED_INDEX.value, Date(), 0,
      pair.failedLine, pair.failedMethodName, pair.errorMessage, pair.topLocationLine
    )
    TestStateStorage.getInstance(project).writeState(url, record)
    testHighlighting(lang, text, fileName)
    val infos = doHighlighting(HighlightSeverity.WARNING)
    assertEquals(1, infos.size)
    val attributes = infos.first().forcedTextAttributes
    assertNotNull(attributes)
    assertEquals(EffectType.BOLD_DOTTED_LINE, attributes.effectType)
  }
}

/**
 * Creates the project of a [TestFailedLineInspection] test class. It has JUnit 3, Hamcrest and the JUnit 5 API,
 * as [TestFailedLineInspectionTestBase] has.
 */
@TestOnly
fun testFailedLineProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(TestLibrariesDescriptor {
  addJUnit3Library()
  addHamcrestLibrary()
  addJUnit5Library()
})

/**
 * Creates a [TestFailedLineInspectionFixture] for one test. Give it the project of [testFailedLineProjectFixture].
 */
@TestOnly
fun testFailedLineInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<TestFailedLineInspectionFixture> = testFixture("testFailedLineInspectionFixture") {
  initialized(TestFailedLineInspectionFixture(jvmInspectionFixture(projectFixture) { TestFailedLineInspection() }.init())) {}
}

/**
 * Creates the project of a [TestMethodWithoutAssertionInspection] test class.
 * It has JUnit 3, JUnit 4, the JUnit 5 API, the JUnit suite API, MockK and AssertJ, as [TestMethodWithoutAssertionInspectionTestBase] has.
 * [moreLibraries] adds more libraries to the module.
 */
@TestOnly
@JvmOverloads
fun testMethodWithoutAssertionProjectFixture(moreLibraries: ModifiableRootModel.() -> Unit = {}): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(TestLibrariesDescriptor {
    addJUnit3Library()
    addJUnit4Library()
    addJUnit5Library()
    addJUnitSuiteLibrary()
    addMockKLibrary()
    addAssertJLibrary()
    moreLibraries()
  })

/**
 * Creates the fixture of a [TestMethodWithoutAssertionInspection] test. It replaces [TestMethodWithoutAssertionInspectionTestBase].
 * The inspection takes `assert` as an assertion and ignores a test that throws. The fixture adds the JMockit stubs of the old base.
 * Give it the project of [testMethodWithoutAssertionProjectFixture].
 */
@TestOnly
fun testMethodWithoutAssertionInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<TestMethodWithoutAssertionInspection>> = testFixture("testMethodWithoutAssertionInspectionFixture") {
  val test = jvmInspectionFixture(projectFixture) {
    TestMethodWithoutAssertionInspection().apply {
      assertKeywordIsAssertion = true
      ignoreIfExceptionThrown = true
    }
  }.init()
  test.addClass("""
      package mockit;
      public abstract class Verifications {
        protected Verifications() { }
      }
    """.trimIndent())
  test.addClass("""
      package mockit;
      import java.lang.annotation.*;

      @Retention(value=RUNTIME)
      @Target({FIELD,PARAMETER})
      public @interface Mocked { }
    """.trimIndent())
  initialized(test) {}
}

/**
 * Creates the fixture of a [TestOnlyInspection] test. It replaces [TestOnlyInspectionTestBase].
 * The fixture adds the annotations `VisibleForTesting` and `TestOnly` of `org.jetbrains.annotations`.
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
fun testOnlyInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<TestOnlyInspection>> = testFixture("testOnlyInspectionFixture") {
  val testOnly = jvmInspectionFixture(projectFixture) { TestOnlyInspection() }.init()
  testOnly.addClass("""
      package org.jetbrains.annotations;

      import java.lang.annotation.*;

      @Documented
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
      public @interface VisibleForTesting { }
  """.trimIndent())
  testOnly.addClass("""
      package org.jetbrains.annotations;

      import java.lang.annotation.*;

      @Documented
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
      public @interface TestOnly { }
  """.trimIndent())
  initialized(testOnly) {}
}
