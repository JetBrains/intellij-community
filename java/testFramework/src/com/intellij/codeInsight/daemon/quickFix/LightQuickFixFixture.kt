// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The options of a [LightQuickFixFixture]. Start from [DEFAULT] and add an option with a `with` method.
 */
@TestOnly
class LightQuickFixOptions private constructor(
  /**
   * When true, a `before` file named `...JavaXYZ.java` sets the language level of the module for its test.
   * See [lightQuickFixLanguageLevel].
   */
  val languageLevelFromFileName: Boolean,
  /**
   * When false, the hint text is a prefix of the action text, as in [ActionHint.parse] with `exactMatch = false`.
   */
  val exactActionHintMatch: Boolean,
  /**
   * When true, the action can stay available at the caret after its launch.
   */
  val actionAvailableAfterExecution: Boolean,
) {
  fun withLanguageLevelFromFileName(): LightQuickFixOptions =
    LightQuickFixOptions(true, exactActionHintMatch, actionAvailableAfterExecution)

  fun withPrefixActionHintMatch(): LightQuickFixOptions =
    LightQuickFixOptions(languageLevelFromFileName, false, actionAvailableAfterExecution)

  fun withActionAvailableAfterExecution(): LightQuickFixOptions =
    LightQuickFixOptions(languageLevelFromFileName, exactActionHintMatch, true)

  companion object {
    /**
     * The options of [LightQuickFixParameterizedTestCase] without overrides.
     */
    @JvmField
    val DEFAULT: LightQuickFixOptions = LightQuickFixOptions(false, true, false)
  }
}

/**
 * The JUnit 5 form of [LightQuickFixParameterizedTestCase]. Create it with [lightQuickFixFixture].
 *
 * A test is one `before` file of [testDataPath] with an action hint in it, an `after` file, and an optional `preview` file.
 * [doTest] runs the whole test. A test that runs its own code between the steps calls [configure], [findAction] and [checkAction].
 * [lightQuickFixTestFiles] lists the tests of a directory.
 *
 * When the caret is in an injected fragment, the hint, the action context and the preview use the host editor and the host file.
 * Call the members off the EDT. They run the action and the checks that need the EDT on the EDT.
 */
@TestOnly
class LightQuickFixFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: JavaCodeInsightTestFixture,
  /**
   * The directory of the `before`, `after` and `preview` files.
   */
  val testDataPath: String,
  val options: LightQuickFixOptions,
) {
  /**
   * Returns a fixture with the same code insight fixture and test data path, and with [options].
   * Use it for a test whose options depend on the file name.
   */
  fun withOptions(options: LightQuickFixOptions): LightQuickFixFixture = LightQuickFixFixture(fixture, testDataPath, options)

  /**
   * Runs the test of the `before` file with the name suffix [fileName]: [configure], then [checkAction].
   */
  fun doTest(fileName: String) {
    val hint = configure(fileName)
    checkAction(fileName, hint)
  }

  /**
   * Sets the language level of the file name when the options ask for it, configures the `before` file [fileName], and parses its hint.
   * The language level stays until the end of the test.
   */
  fun configure(fileName: String): ActionHint {
    if (options.languageLevelFromFileName) {
      val level = lightQuickFixLanguageLevel(fileName)
      if (level != null) {
        IdeaTestUtil.setModuleLanguageLevel(fixture.module, level, fixture.testRootDisposable)
      }
    }
    fixture.configureByFile(LightQuickFixTestCase.BEFORE_PREFIX + fileName)
    return runReadActionBlocking {
      val file = hostFile()
      ActionHint.parse(file, file.text, options.exactActionHintMatch)
    }
  }

  /**
   * Finds the action of [hint] among the available intentions, and checks that its presence agrees with the hint.
   * It returns the action, or null when the hint expects no action.
   * The function reads [fileName] only for the failure message.
   */
  fun findAction(fileName: String, hint: ActionHint): IntentionAction? {
    val (editor, file) = runReadActionBlocking { hostEditor() to hostFile() }
    val intentions = fixture.availableIntentions
    return runInEdtAndGet {
      hint.findAndCheck(intentions, ActionContext.from(editor, file)) {
        """
             Test: $testDataPath/${LightQuickFixTestCase.BEFORE_PREFIX}$fileName
             Language level: ${PsiUtil.getLanguageLevel(fixture.project)}
             SDK: ${ModuleRootManager.getInstance(ModuleUtilCore.findModuleForFile(file)!!).sdk}
             Infos: ${LightQuickFixTestCase.getCurrentHighlightingInfo(fixture.doHighlighting())}
             """.trimIndent()
      }
    }
  }

  /**
   * Finds the action of [hint] with [findAction], launches it, and compares the result with the `after` file of [fileName].
   * For a `-preview` hint, it also compares the intention preview with the `preview` file, or with the result when no `preview` file exists.
   * The action must not be available at the caret after the launch, unless the options allow it.
   * The action must have a family name that is not blank.
   */
  fun checkAction(fileName: String, hint: ActionHint) {
    val action = findAction(fileName, hint) ?: return
    val (editor, file) = runReadActionBlocking { hostEditor() to hostFile() }
    val text = action.text
    val element = runReadActionBlocking { PsiUtilBase.getElementAtCaret(editor) }
    val previewContent = if (hint.shouldCheckPreview()) {
      // Run in a background thread to catch accidental write actions during the preview generation
      ReadAction.nonBlocking<String> {
        IntentionPreviewPopupUpdateProcessor.getPreviewContent(fixture.project, action, file, editor)
      }.submit(AppExecutorUtil.getAppExecutorService()).get()
    }
    else null

    fixture.launchAction(action)
    runInEdtAndWait {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    if (previewContent != null) {
      checkPreview(text, fileName, previewContent)
    }

    if (!options.actionAvailableAfterExecution) {
      val afterAction = CodeInsightTestUtil.findIntentionByText(fixture.availableIntentions, text)
      if (afterAction != null && Comparing.equal(element, runReadActionBlocking { PsiUtilBase.getElementAtCaret(editor) })) {
        fail("Action '$text' is still available after its invocation in test ${LightQuickFixTestCase.BEFORE_PREFIX}$fileName")
      }
    }
    runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
    fixture.checkResultByFile(LightQuickFixTestCase.AFTER_PREFIX + fileName)
    if (action.familyName.isBlank()) {
      fail("Action '$text' provides empty family name which means that user would see action with empty presentable text in Inspection Results")
    }
  }

  private fun checkPreview(actionText: String, fileName: String, previewContent: String) {
    val path = Path.of(testDataPath, LightQuickFixTestCase.PREVIEW_PREFIX + fileName)
    if (Files.exists(path)) {
      PlatformTestUtil.assertSameLinesWithFile(path.toString(), previewContent)
    }
    else {
      if (previewContent.isEmpty()) {
        fail("No preview was generated for '$actionText'")
      }
      assertEquals(runReadActionBlocking { hostFile().text }, previewContent)
    }
  }

  private fun hostEditor(): Editor = InjectedLanguageEditorUtil.getTopLevelEditor(fixture.editor)

  private fun hostFile(): PsiFile = InjectedLanguageManager.getInstance(fixture.project).getTopLevelFile(fixture.file)
}

/**
 * Creates a [LightQuickFixFixture] for one test. Declare it as an instance field.
 *
 * The fixture creates the code insight fixture on [projectFixture], sets [testDataPath] as its test data path,
 * and enables the inspections that [inspections] creates.
 * In Java, give [inspections] as a lambda, for example `() -> List.of(new FooInspection())`.
 */
@TestOnly
@JvmOverloads
fun lightQuickFixFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
  inspections: Supplier<out List<InspectionProfileEntry>> = Supplier { emptyList() },
  options: LightQuickFixOptions = LightQuickFixOptions.DEFAULT,
): TestFixture<LightQuickFixFixture> = testFixture("lightQuickFixFixture") {
  val fixture: LightJavaCodeInsightTestFixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = testDataPath
  val tools = inspections.get()
  if (tools.isNotEmpty()) {
    fixture.enableInspections(*tools.toTypedArray())
  }
  initialized(LightQuickFixFixture(fixture, testDataPath, options)) {}
}

/**
 * Returns the name suffixes of the `before` files in [testDataPath], sorted, for a `@MethodSource` of a parameterized test.
 * A name suffix is the file name without the `before` prefix, for example `Simple.java`.
 * It fails when the directory has no `before` file, or when an `after` file has no `before` file.
 */
@TestOnly
fun lightQuickFixTestFiles(testDataPath: String): List<String> {
  val dir = Path.of(testDataPath)
  if (!dir.isDirectory()) fail("Test files not found in $testDataPath")
  val names = dir.listDirectoryEntries().map { it.name }
  val before = names.filter { it.startsWith(LightQuickFixTestCase.BEFORE_PREFIX) }.map { it.removePrefix(LightQuickFixTestCase.BEFORE_PREFIX) }
  val after = names.filter { it.startsWith(LightQuickFixTestCase.AFTER_PREFIX) }.map { it.removePrefix(LightQuickFixTestCase.AFTER_PREFIX) }
  val orphans = after.toSet() - before.toSet()
  if (orphans.isNotEmpty()) fail("'After' file has no corresponding 'before' file: ${orphans.sorted().joinToString()}")
  if (before.isEmpty()) fail("Test files not found in $testDataPath")
  return before.sorted()
}

private val JDK_SELECT_PATTERN = Regex("Java([\\d.]+)(Preview)?(\\.java)?$")

/**
 * Returns the language level that the name suffix [fileName] of a `before` file selects, or null.
 * A name that ends with `JavaXYZ` or `JavaXYZ.java` selects the Java version XYZ, for example `Java1.4.java` or `Java9.java`.
 * A name that ends with `JavaXYZPreview.java` selects the preview level of XYZ.
 * It is the rule of [com.intellij.testFramework.LightJavaCodeInsightTestCase.getLanguageLevel].
 */
@TestOnly
fun lightQuickFixLanguageLevel(fileName: String): LanguageLevel? {
  val match = JDK_SELECT_PATTERN.find(fileName) ?: return null
  val level = LanguageLevel.parse(match.groupValues[1]) ?: return null
  return if (match.groups[2] == null) level else LanguageLevel.valueOf("${level}_PREVIEW")
}
