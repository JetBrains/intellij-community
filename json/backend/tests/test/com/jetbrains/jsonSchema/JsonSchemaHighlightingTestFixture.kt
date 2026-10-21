// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.Language
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubTextInconsistencyException
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.widget.JsonSchemaStatusWidgetFactory
import org.intellij.lang.annotations.Language as InjectedLanguage
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Creates a [JsonSchemaHighlightingTestFixture] on [codeInsightFixture].
 * It is the JUnit 5 replacement of the JUnit 3 bases `JsonSchemaHighlightingTestBase` and `JsonSchemaQuickFixTestBase`.
 *
 * Declare the result as an instance property, so that each test gets its own fixture.
 *
 * @param testFileName the name of the file that [JsonSchemaHighlightingTestFixture.configureInitially] creates
 * @param inspection creates the inspection that [JsonSchemaHighlightingTestFixture.configureInitially] enables
 * @param language the schema applies to the files of this language and of its dialects
 */
@TestOnly
fun jsonSchemaHighlightingFixture(
  codeInsightFixture: TestFixture<CodeInsightTestFixture>,
  testFileName: String,
  inspection: Supplier<out InspectionProfileEntry>,
  language: Language,
): TestFixture<JsonSchemaHighlightingTestFixture> = testFixture("jsonSchemaHighlightingFixture") {
  val fixture = codeInsightFixture.init()
  (fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  initialized(JsonSchemaHighlightingTestFixture(fixture, testFileName, inspection, language)) {}
}

/**
 * JSON schema helpers for a JUnit 5 highlighting test: the schema registration, the highlighting, and the quick fixes.
 *
 * Call a helper off the EDT.
 */
@TestOnly
class JsonSchemaHighlightingTestFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  private val testFileName: String,
  private val inspection: Supplier<out InspectionProfileEntry>,
  language: Language,
) {
  /**
   * Accepts the files of the language of the fixture and of its dialects.
   */
  val availabilityPredicate: Predicate<VirtualFile> = Predicate { file ->
    val fileType = file.fileType
    fileType is LanguageFileType && fileType.language.isKindOf(language)
  }

  /**
   * Registers the JSON [schema], configures the test file with [text], and checks the warnings in [text].
   */
  fun doTest(@InjectedLanguage("JSON") schema: String, text: String) {
    configureInitially(schema, text, "json")
    fixture.checkHighlighting(true, false, false)
  }

  /**
   * Checks the highlighting, invokes the intention [fixName] at the caret, and checks the file against [afterFix].
   */
  fun doTest(@InjectedLanguage("JSON") schema: String, text: String, fixName: String, afterFix: String) {
    val psiFile = configureInitially(schema, text, "json")
    fixture.checkHighlighting()
    val intentions = fixture.availableIntentions
    val action = intentions.find { it.text == fixName }
    if (action == null) {
      val intentionsList = intentions.map { it.text }.distinct().sorted().joinToString("\n")
      throw IllegalStateException("No available intention found with name \"$fixName\". Available intentions:\n$intentionsList")
    }
    runInEdtAndWait {
      try {
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, fixture.editor, action, action.text)
      }
      catch (e: StubTextInconsistencyException) {
        PsiTestUtil.compareStubTexts(e)
      }
    }
    fixture.checkResult(afterFix)
  }

  /**
   * Enables the inspection, registers [schema] with the extension [schemaExt], and configures the test file with [text].
   * The schema of the first call stays for the rest of the test.
   */
  fun configureInitially(schema: String, text: String, schemaExt: String): PsiFile {
    fixture.enableInspections(inspection.get())
    registerJsonSchema(fixture, schema, schemaExt, availabilityPredicate)
    return fixture.configureByText(testFileName, text)
  }

  companion object {
    /**
     * Adds the schema file to the project, and makes it the schema of the files that [predicate] accepts.
     * It does nothing when the test already has a schema with the extension [schemaExt].
     */
    @JvmStatic
    fun registerJsonSchema(fixture: CodeInsightTestFixture, schemaText: String, schemaExt: String, predicate: Predicate<VirtualFile>) {
      val path = "json_schema_test/schema.$schemaExt"
      if (fixture.tempDirFixture.getFile(path) != null) return
      val file = fixture.addFileToProject(path, schemaText)
      JsonSchemaTestServiceImpl.setProvider(JsonSchemaTestProvider(file.virtualFile, predicate))
      val testRootDisposable = fixture.testRootDisposable
      Disposer.register(testRootDisposable) { JsonSchemaTestServiceImpl.setProvider(null) }
      val project = fixture.project
      project.replaceService(JsonSchemaService::class.java, JsonSchemaTestServiceImpl(project), testRootDisposable)
    }

    @JvmStatic
    fun createJsonSchemaStatusWidget(project: Project): EditorBasedStatusBarPopup {
      val widgetFactory = checkNotNull(StatusBarWidgetFactory.EP_NAME.findExtension(JsonSchemaStatusWidgetFactory::class.java))
      return widgetFactory.createWidget(project, (project as ComponentManagerEx).getCoroutineScope()) as EditorBasedStatusBarPopup
    }
  }
}
