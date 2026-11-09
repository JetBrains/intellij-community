// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightTestDataPath
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.function.Function
import java.util.function.Supplier

/**
 * The JUnit 5 form of [LightJavaInspectionTestCase]. Create it with [lightJavaInspectionFixture].
 *
 * The members are the helpers of [LightJavaInspectionTestCase] with the same names and the same checks.
 * Call them from any thread. A helper that runs an intention runs it on the EDT.
 */
@TestOnly
class LightJavaInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val inspection: InspectionProfileEntry?,
  private val inspectionClass: Class<out InspectionProfileEntry>?,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  fun addEnvironmentClass(@Language("JAVA") classText: String) {
    fixture.addClass(classText)
  }

  fun doStatementTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\") class X { void m() {", suffix = "}}") statementText: String) {
    doTest("class X { void m() {$statementText}}")
  }

  fun doMemberTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\") class X {", suffix = "}") memberText: String) {
    doTest("class X {$memberText}")
  }

  /**
   * Configures `X.java` with [classText] and checks the highlighting.
   * The comments in [classText] mark the expected highlighting, as in [LightJavaInspectionTestCase.doTest].
   */
  fun doTest(@Language("JAVA") classText: String) {
    doTest(classText, "X.java")
  }

  fun doTest(@Language("JAVA") classText: String, fileName: String) {
    fixture.configureByText(fileName, convertMarkers(classText))
    fixture.testHighlighting(true, false, false)
  }

  /**
   * Configures the file `<test name>.java` of the test data and checks the highlighting.
   */
  fun doTest() {
    doNamedTest(getTestName(false))
  }

  fun doNamedTest(name: String) {
    fixture.configureByFile("$name.java")
    fixture.testHighlighting(true, false, false)
  }

  fun assertQuickFixNotAvailable(name: String) {
    val intentions = fixture.filterAvailableIntentions(name)
    assertTrue(intentions.isEmpty()) { "Expected no intentions starting with \"$name\", got: $intentions" }
  }

  fun checkQuickFix(name: String, @Language("JAVA") result: String) {
    runInEdtAndWait {
      invokeIntention(name)
      fixture.checkResult(result)
    }
  }

  /**
   * Runs the intention [name] and compares the file with `<test name>.after.java` of the test data.
   */
  fun checkQuickFix(name: String) {
    runInEdtAndWait {
      invokeIntention(name)
      fixture.checkResultByFile(getTestName(false) + ".after.java")
    }
  }

  /**
   * Runs the "Fix all" intention of the inspection and compares the file with `<test name>.after.java` of the test data.
   */
  fun checkQuickFixAll() {
    val tool = InspectionTestUtil.instantiateTool(requireInspectionClass())
    checkNotNull(tool) { "The fixture needs an inspection for checkQuickFixAll() to work" }
    checkQuickFix(InspectionsBundle.message("fix.all.inspection.problems.in.file", tool.displayName))
  }

  private fun invokeIntention(name: String) {
    assertFalse(fixture.caretOffset == 0) { "No <caret> in " + fixture.file.name }
    val intention = fixture.getAvailableIntention(name)
    assertNotNull(intention) { "Intention \"$name\" not found\navailable intentions: " + fixture.availableIntentions }
    fixture.launchAction(intention!!)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
  }

  private fun requireInspectionClass(): Class<out InspectionProfileEntry> =
    inspectionClass ?: inspection?.javaClass ?: error("File-based tests need an inspection or an inspection class")

  companion object {
    const val INSPECTION_GADGETS_TEST_DATA_PATH: String = "/java/java-tests/testData/ig/"

    /**
     * Returns the test data path of [inspectionClass] relative to the community root, as [LightJavaInspectionTestCase.getBasePath] does.
     * For example, `com.siyeh.ig.bugs.ReturnNullInspection` gives `/java/java-tests/testData/ig/com/siyeh/igtest/bugs/return_null`.
     */
    @JvmStatic
    fun inspectionBasePath(inspectionClass: Class<*>): String {
      val words = inspectionClass.name.split('.')
      val basePath = StringBuilder(INSPECTION_GADGETS_TEST_DATA_PATH)
      for (i in 0 until words.size - 1) {
        basePath.append(if (words[i] == "ig") "igtest" else words[i]).append('/')
      }
      val lastWord = words.last().removeSuffix("Inspection")
      var upperCase = false
      for (i in lastWord.indices) {
        val ch = lastWord[i]
        if (Character.isUpperCase(ch)) {
          if (!upperCase) {
            upperCase = true
            if (i != 0) {
              basePath.append('_')
            }
          }
          basePath.append(Character.toLowerCase(ch))
        }
        else {
          upperCase = false
          basePath.append(ch)
        }
      }
      return basePath.toString()
    }

    /**
     * Returns the absolute test data path of [basePath], as [LightJavaInspectionTestCase.getTestDataPath] does.
     * Under Bazel, a path in [INSPECTION_GADGETS_TEST_DATA_PATH] resolves in the test data of the Java tests.
     */
    @JvmStatic
    fun inspectionTestDataPath(basePath: String): String {
      if (BazelTestUtil.isUnderBazelTest && basePath.startsWith(INSPECTION_GADGETS_TEST_DATA_PATH)) {
        return JavaTestUtil.getJavaTestDataPath() + "/ig/" + basePath.substring(INSPECTION_GADGETS_TEST_DATA_PATH.length)
      }
      return lightTestDataPath(basePath)
    }

    private fun convertMarkers(classText: String): String {
      val newText = StringBuilder()
      var start = 0
      var end = classText.indexOf("/*")
      while (end >= 0) {
        newText.append(classText, start, end)
        start = end + 2
        end = classText.indexOf("*/", end)
        require(end >= 0) { "invalid class text" }
        val text = classText.substring(start, end)
        when {
          text.isEmpty() -> newText.append("</warning>")
          text == "!" -> newText.append("</error>")
          text == "_" -> newText.append("<caret>")
          text.startsWith("!") -> newText.append("<error descr=\"").append(text.substring(1)).append("\">")
          text.startsWith(" ") -> newText.append("/*").append(text).append("*/")
          else -> newText.append("<warning descr=\"").append(text).append("\">")
        }
        start = end + 2
        end = classText.indexOf("/*", end + 1)
      }
      newText.append(classText, start, classText.length)
      return newText.toString()
    }
  }
}

/**
 * Creates a [LightJavaInspectionFixture] for one test, as [LightJavaInspectionTestCase.setUp] does. Declare it as an instance field.
 *
 * Before the test, the fixture adds [environmentClasses] and enables the inspection that [inspection] creates.
 * It raises the level of the inspection to a warning when the profile does not show it or shows it as a weak warning.
 * In Java, give [inspection] as a lambda, for example `() -> new FooInspection()`.
 * [inspection] can return `null` for a test that enables its inspections itself. Then give [inspectionClass] for the file-based helpers.
 *
 * [basePath] is the test data path relative to the community root.
 * By default, it comes from the inspection class, as [LightJavaInspectionTestCase.getBasePath] computes it.
 * [testDataPath] is an absolute test data path, as an override of [LightJavaInspectionTestCase.getTestDataPath] gives it.
 * When it is set, the fixture ignores [basePath].
 */
@TestOnly
@JvmOverloads
fun lightJavaInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  inspection: Supplier<out InspectionProfileEntry?>,
  @Language("JAVA") environmentClasses: Array<String> = emptyArray(),
  basePath: String? = null,
  inspectionClass: Class<out InspectionProfileEntry>? = null,
  testDataPath: String? = null,
): TestFixture<LightJavaInspectionFixture> =
  lightJavaInspectionFixtureByTestName(projectFixture, { inspection.get() }, environmentClasses, basePath, inspectionClass, testDataPath)

/**
 * Creates a [LightJavaInspectionFixture] as [lightJavaInspectionFixture] does, for a class that sets up the inspection for each test.
 * [inspection] gets the name of the test method without the `test` prefix, as `getTestName(false)` of JUnit 3 returns it.
 */
@TestOnly
@JvmOverloads
fun lightJavaInspectionFixtureByTestName(
  projectFixture: TestFixture<LightJavaProject>,
  inspection: Function<String, out InspectionProfileEntry?>,
  @Language("JAVA") environmentClasses: Array<String> = emptyArray(),
  basePath: String? = null,
  inspectionClass: Class<out InspectionProfileEntry>? = null,
  testDataPath: String? = null,
): TestFixture<LightJavaInspectionFixture> = testFixture("lightJavaInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  val tool = inspection.apply(fixture.getTestName(false))
  fixture.testDataPath = testDataPath ?: LightJavaInspectionFixture.inspectionTestDataPath(
    basePath ?: LightJavaInspectionFixture.inspectionBasePath(
      inspectionClass ?: tool?.javaClass ?: error("Give an inspection, a base path, an inspection class, or a test data path")))

  for (environmentClass in environmentClasses) {
    fixture.addClass(environmentClass)
  }
  if (tool != null) {
    fixture.enableInspections(tool)
    val project = fixture.project
    val displayKey = checkNotNull(HighlightDisplayKey.find(tool.shortName)) { "No display key for ${tool.shortName}" }
    val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    val errorLevel = currentProfile.getErrorLevel(displayKey, null)
    if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW || errorLevel == HighlightDisplayLevel.WEAK_WARNING) {
      currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project)
    }
  }
  checkMockJdk17(fixture)
  initialized(LightJavaInspectionFixture(fixture, tool, inspectionClass)) {}
}

// LightJavaInspectionTestCase.setUp checks that the mock JDK 1.7 has the classes that many tests use.
private suspend fun checkMockJdk17(fixture: LightJavaCodeInsightTestFixture) {
  val mockJdk17 = LightJavaCodeInsightFixtureTestCase.JAVA_1_7 as LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
  readAction {
    if (mockJdk17.sdkName == ModuleRootManager.getInstance(fixture.module).sdk?.name) {
      val scope = GlobalSearchScope.allScope(fixture.project)
      val facade = JavaPsiFacade.getInstance(fixture.project)
      checkNotNull(facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, scope)) { "No java.lang.Object in the mock JDK 1.7" }
      checkNotNull(facade.findClass("java.awt.Component", scope)) { "No java.awt.Component in the mock JDK 1.7" }
    }
  }
}
