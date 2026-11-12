// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The fixture of the Intention Power Pack tests. Create it with [ippFixture].
 *
 * The members run an intention of the test data and check the result.
 * A member that runs an intention runs the whole sequence on the EDT.
 */
@TestOnly
class IPPFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The intention of the members without an intention name.
   */
  val intentionName: String?,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Runs the intention of the fixture on `<test name>.java` and compares the result with `<test name>_after.java`.
   */
  fun doTest() {
    doTest(requireIntentionName())
  }

  fun doTest(intentionName: String) {
    val testName = getTestName(false)
    runInEdtAndWait {
      CodeInsightTestUtil.doIntentionTest(fixture, intentionName, "$testName.java", "${testName}_after.java")
    }
  }

  /**
   * Configures [before] and runs the intention at the `/*_name*/` marker. An empty name means the intention of the fixture.
   */
  fun doTest(@Language("JAVA") before: String, @Language("JAVA") after: String) {
    runInEdtAndWait {
      val name = configureAtMarker(before)
      fixture.launchAction(fixture.findSingleIntention(name))
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      fixture.checkResult(after)
    }
  }


  fun doTestIntentionNotAvailable(@Language("JAVA") source: String) {
    val name = configureAtMarker(source)
    assertNotAvailable(name, fixture.filterAvailableIntentions(name))
  }

  fun assertIntentionNotAvailable() {
    assertIntentionNotAvailable(requireIntentionName())
  }

  fun assertIntentionNotAvailable(intentionName: String) {
    fixture.configureByFile(getTestName(false) + ".java")
    assertNotAvailable(intentionName, fixture.filterAvailableIntentions(intentionName))
  }

  fun assertIntentionNotAvailable(intentionClass: Class<out CommonIntentionAction>) {
    fixture.configureByFile(getTestName(false) + ".java")
    val result = fixture.availableIntentions.filter {
      intentionClass.isInstance(IntentionActionDelegate.unwrap(it)) || intentionClass.isInstance(it.asModCommandAction())
    }
    assertTrue(result.isEmpty()) { "Intention of class '$intentionClass' is available but should not: $result" }
  }

  private fun configureAtMarker(@Language("JAVA") source: String): String {
    val matcher = PATTERN.matcher(source)
    assertTrue(matcher.find(), "No caret and intention name specified")
    val text = matcher.replaceFirst("<caret>")
    val group = matcher.group(1)
    fixture.configureByText("a.java", text)
    return group.ifEmpty { requireIntentionName() }
  }

  private fun requireIntentionName(): String = checkNotNull(intentionName) { "Give the intention name to ippFixture" }

  private fun assertNotAvailable(intentionName: String, intentions: List<IntentionAction>) {
    assertTrue(intentions.isEmpty()) { "Intention '$intentionName' is available but should not: $intentions" }
  }

  private companion object {
    private val PATTERN = Regex("/\\*_(.*)\\*/").toPattern()
  }
}

/**
 * Creates an [IPPFixture] for one test. Declare it as an instance field.
 *
 * The test data path is `ipp/com/siyeh/ipp/<relativePath>` in the Java test data.
 * [intentionName] is the intention of [IPPFixture.doTest] and [IPPFixture.assertIntentionNotAvailable] without a name.
 */
@TestOnly
@JvmOverloads
fun ippFixture(
  projectFixture: TestFixture<LightJavaProject>,
  relativePath: String = "",
  intentionName: String? = null,
): TestFixture<IPPFixture> = testFixture("ippFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/ipp/com/siyeh/ipp/" + relativePath
  initialized(IPPFixture(fixture, intentionName)) {}
}
