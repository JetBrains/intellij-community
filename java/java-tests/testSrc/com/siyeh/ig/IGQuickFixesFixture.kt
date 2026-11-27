// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The fixture of the inspection gadgets quick-fix tests. Create it with [igQuickFixesFixture].
 *
 * The members configure a file of the test data or a text, run a quick-fix and check the result.
 * The file-based helpers read `<relative path>/<test name>.java` of the test data `ig/com/siyeh/igfixes`.
 * A helper that runs a quick-fix runs the sequence from the intention lookup to the last check on the EDT.
 */
@TestOnly
class IGQuickFixesFixture internal constructor(
  /**
   * The code insight fixture of the test. Each test gets a new heavy project.
   */
  val fixture: JavaCodeInsightTestFixture,
  private val testMethodName: String,
  private val testClassName: String,
  private val relativePath: String?,
  /**
   * The quick-fix of the helpers without a hint.
   */
  val defaultHint: String?,
) {
  /**
   * Returns the name of the test method without the `test` prefix.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)

  /**
   * Returns the test data directory of the file-based helpers, relative to the test data path.
   */
  fun getRelativePath(): String = checkNotNull(relativePath) { "Give the relative path to igQuickFixesFixture" }

  fun assertQuickfixNotAvailable() {
    assertQuickfixNotAvailable(defaultHint)
  }

  /**
   * Configures `<test name>.java` and checks that no intention starts with [quickfixName].
   */
  fun assertQuickfixNotAvailable(quickfixName: String?) {
    fixture.configureByFile(getRelativePath() + "/" + getTestName(false) + ".java")
    assertNotAvailable(quickfixName, fixture.filterAvailableIntentions(quickfixName!!))
  }

  /**
   * Configures [text] and checks that no intention starts with [quickfixName]. The `/**/` marker is the caret.
   */
  fun assertQuickfixNotAvailable(quickfixName: String, @Language("JAVA") text: String) {
    fixture.configureByText(JavaFileType.INSTANCE, text.replace("/**/", "<caret>"))
    assertNotAvailable(quickfixName, fixture.filterAvailableIntentions(quickfixName))
  }

  fun doTest() {
    doTest(getTestName(false), checkNotNull(defaultHint) { "Give the default hint to igQuickFixesFixture" })
  }

  fun doTest(hint: String) {
    doTest(getTestName(false), hint)
  }

  /**
   * Runs the quick-fix [hint] on `<testName>.java` and compares the result with `<testName>.after.java`.
   * When `<testName>.preview.java` exists, the preview must have its text. Otherwise, the preview must give the result.
   */
  fun doTest(testName: String, hint: String) {
    fixture.configureByFile(getRelativePath() + "/" + testName + ".java")
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    runInEdtAndWait {
      val action = fixture.getAvailableIntention(hint)
                   ?: fail("No action '" + hint + "' found among " + fixture.availableIntentions.joinToString(", ") { it.text })
      val previewPath = Path.of(fixture.testDataPath, getRelativePath(), "$testName.preview.java")
      if (Files.exists(previewPath)) {
        val previewText = fixture.getIntentionPreviewText(action)
        PlatformTestUtil.assertSameLinesWithFile(previewPath.toString(), previewText!!)
        fixture.launchAction(action)
      }
      else {
        fixture.checkPreviewAndLaunchAction(action)
      }
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      fixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java")
    }
  }

  fun doExpressionTest(
    hint: String,
    @Language(value = "JAVA", prefix = $$"/** @noinspection ALL*/class $X$ {static {System.out.print(", suffix = ");}}") before: String,
    @Language(value = "JAVA", prefix = $$"class $X$ {static {System.out.print(", suffix = ");}}") after: String,
  ) {
    doTest(hint, $$"class $X$ {static {System.out.print($$before);}}", $$"class $X$ {static {System.out.print($$after);}}")
  }

  fun doMemberTest(
    hint: String,
    @Language(value = "JAVA", prefix = $$"/** @noinspection ALL*/class $X$ {", suffix = "}") before: String,
    @Language(value = "JAVA", prefix = $$"class $X$ {", suffix = "}") after: String,
  ) {
    doTest(hint, $$"class $X$ {$$before}", $$"class $X$ {$$after}")
  }

  /**
   * Configures [before] in the file `<test class>_<test name>.java`, runs the quick-fix [hint] and compares the result with [after].
   */
  fun doTest(hint: String, @Language("JAVA") before: String, @Language("JAVA") after: String) {
    doTest(hint, before, after, testClassName + "_" + getTestName(false) + ".java")
  }

  /**
   * Configures [before] in [fileName], runs the quick-fix [hint] and compares the result with [after]. The `/**/` marker is the caret.
   */
  fun doTest(hint: String, @Language("JAVA") before: String, @Language("JAVA") after: String, fileName: String) {
    fixture.configureByText(fileName, before.replace("/**/", "<caret>"))
    runInEdtAndWait {
      val intention: IntentionAction? = fixture.getAvailableIntention(hint)
      checkNotNull(intention) { "No action '$hint'" }
      fixture.checkPreviewAndLaunchAction(intention)
      fixture.checkResult(after)
    }
  }

  private fun assertNotAvailable(quickfixName: String?, intentions: List<IntentionAction>) {
    assertTrue(intentions.isEmpty()) { "Quickfix '$quickfixName' is available but should not: $intentions" }
  }
}

/**
 * Creates an [IGQuickFixesFixture] for one test. Declare it as an instance field.
 *
 * The project is a new heavy project with one Java module, as in `JavaCodeInsightFixtureTestCase`.
 * The module language level is 1.8. [tune] gets the module builder after that, before the project opens.
 * The test data path is `ig/com/siyeh/igfixes` in the Java test data.
 * Before the test, the fixture adds [environmentClasses] and enables the inspections that [inspections] creates.
 * In Java, give [inspections] as a lambda, for example `() -> List.of(new FooInspection())`.
 * [relativePath] is the test data directory of the file-based helpers. [defaultHint] is the quick-fix of the helpers without a hint.
 */
@TestOnly
@JvmOverloads
fun igQuickFixesFixture(
  inspections: Supplier<out List<InspectionProfileEntry>>,
  relativePath: String? = null,
  defaultHint: String? = null,
  tune: Consumer<JavaModuleFixtureBuilder<*>>? = null,
  @Language("JAVA") environmentClasses: Array<String> = emptyArray(),
): TestFixture<IGQuickFixesFixture> = testFixture("igQuickFixesFixture") { context ->
  val fixture = javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igfixes/", true) { builder ->
    builder.setLanguageLevel(LanguageLevel.JDK_1_8)
    tune?.accept(builder)
  }.init()
  for (environmentClass in environmentClasses) {
    fixture.addClass(environmentClass)
  }
  fixture.enableInspections(*inspections.get().toTypedArray())
  val extensionContext = context.extensionContext
  initialized(IGQuickFixesFixture(fixture, extensionContext.requiredTestMethod.name, extensionContext.requiredTestClass.name,
                                  relativePath, defaultHint)) {}
}
