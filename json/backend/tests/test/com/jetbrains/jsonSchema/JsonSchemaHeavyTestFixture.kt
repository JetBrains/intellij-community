// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.json.JsonLanguage
import com.intellij.lang.LanguageDocumentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertInstanceOf
import java.io.File
import java.util.TreeMap

/**
 * Creates a [JsonSchemaHeavyTestFixture] on [codeInsightFixture].
 * It is the JUnit 5 replacement of the JUnit 3 bases `JsonSchemaHeavyAbstractTest`,
 * `JsonBySchemaHeavyCompletionTestBase`, and `JsonBySchemaDocumentationBaseTest`.
 *
 * Declare the result as an instance property, so that each test gets its own fixture.
 * The fixture clears the schema mappings of the project after the test.
 *
 * @param extensionWithoutDot the extension of the test files of the completion helpers
 */
@TestOnly
@JvmOverloads
fun jsonSchemaHeavyFixture(
  codeInsightFixture: TestFixture<CodeInsightTestFixture>,
  extensionWithoutDot: String = "json",
): TestFixture<JsonSchemaHeavyTestFixture> = testFixture("jsonSchemaHeavyFixture") { context ->
  val fixture = codeInsightFixture.init()
  (fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  val testMethodName = context.extensionContext.requiredTestMethod.name
  initialized(JsonSchemaHeavyTestFixture(fixture, testMethodName, extensionWithoutDot)) {
    JsonSchemaMappingsProjectConfiguration.getInstance(fixture.project).setState(TreeMap())
  }
}

/**
 * JSON schema helpers for a JUnit 5 test: the schema mappings, the completion, and the documentation.
 *
 * Call a helper off the EDT.
 * The files of the test are on disk, in the temporary directory of [fixture].
 */
@TestOnly
class JsonSchemaHeavyTestFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  private val testMethodName: String,
  private val extensionWithoutDot: String,
) {
  private val schemas = HashMap<String, UserDefinedJsonSchemaConfiguration>()

  /**
   * The variants of the last [complete] call.
   * It is `null` when the completion inserted the only variant.
   */
  var items: Array<LookupElement>? = null
    private set

  /**
   * When it is `true`, [skeleton] calls [complete] before [Callback.doCheck].
   */
  var doCompletion: Boolean = true

  val project: Project
    get() = fixture.project

  /**
   * Returns the name of the test method without the `test` prefix.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)

  /**
   * Configures the files, applies the schemas that [Callback.registerSchemes] adds, highlights the file, and runs [Callback.doCheck].
   */
  fun skeleton(callback: Callback) {
    callback.configureFiles()
    callback.registerSchemes()
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(schemas)
    JsonSchemaService.Impl.get(project).reset()
    runInEdtAndWait { PsiManager.getInstance(project).dropPsiCaches() }
    fixture.doHighlighting()
    if (doCompletion) {
      complete()
    }
    callback.doCheck()
  }

  fun complete() {
    items = fixture.complete(CompletionType.BASIC)
  }

  fun addSchema(schema: UserDefinedJsonSchemaConfiguration) {
    schemas[schema.name] = schema
  }

  fun assertStringItems(vararg strings: String) {
    val items = items
    assertNotNull(items)
    assertEquals(strings.toList(), items!!.map { it.lookupString })
  }

  /**
   * Selects [item] in the active lookup and finishes the lookup with [ch].
   */
  fun selectItem(item: LookupElement, ch: Char) {
    runInEdtAndWait {
      val lookup = assertInstanceOf<LookupImpl>(LookupManager.getInstance(project).activeLookup)
      lookup.currentItem = item
      lookup.finishLookup(ch)
    }
  }

  fun checkResultByFile(testDataFile: String) {
    val path = fixture.testDataPath.removeSuffix("/").removeSuffix("\\")
    fixture.checkResult(PlatformTestUtil.loadFileText(path + File.separator + testDataFile.removePrefix("/")), false)
  }

  /**
   * Returns the URL of [path] in the temporary directory of [fixture].
   */
  fun getUrlUnderTestRoot(path: String): String =
    VfsUtilCore.pathToUrl(fixture.tempDirPath + "/" + path.removePrefix("/"))

  fun locateFileUnderTestRoot(path: String): VirtualFile {
    return checkNotNull(fixture.tempDirFixture.getFile(path.removePrefix("/"))) { "$path is not found under the test root" }
  }

  fun baseCompletionTest(folder: String, testFile: String, vararg items: String) {
    baseTest(folder, testFile) {
      complete()
      assertStringItems(*items)
    }
  }

  /**
   * Invokes the completion, selects the first variant of the last [complete] call, and checks the file against `<testFile>_after`.
   */
  fun baseInsertTest(folder: String, testFile: String) {
    baseTest(folder, testFile) {
      runInEdtAndWait {
        CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, fixture.editor)
        items?.let { selectItem(it[0], '\u0000') }
      }
      checkResultByFile("/$folder/${testFile}_after.$extensionWithoutDot")
    }
  }

  fun baseReplaceTest(folder: String, testFile: String) {
    baseTest(folder, testFile) {
      items?.let { selectItem(it[0], '\t') }
      checkResultByFile("/$folder/${testFile}_after.$extensionWithoutDot")
    }
  }

  /**
   * Maps `<folder>/Schema.json` to `<folder>/<testFile>`, and runs [checker] after [skeleton] configures the files.
   */
  fun baseTest(folder: String, testFile: String, checker: Runnable) {
    skeleton(object : Callback {
      override fun registerSchemes() {
        val moduleDir = getUrlUnderTestRoot(folder)
        addSchema(UserDefinedJsonSchemaConfiguration(
          "base", JsonSchemaVersion.SCHEMA_4, "$moduleDir/Schema.json", false,
          listOf(UserDefinedJsonSchemaConfiguration.Item("$testFile.$extensionWithoutDot", true, false))))
      }

      override fun configureFiles() {
        fixture.configureByFiles("/$folder/$testFile.$extensionWithoutDot", "/$folder/Schema.json")
      }

      override fun doCheck() {
        checker.run()
      }
    })
  }

  fun baseTestNoSchema(folder: String, testFile: String, checker: Runnable) {
    skeleton(object : Callback {
      override fun registerSchemes() {
      }

      override fun configureFiles() {
        fixture.configureByFiles("/$folder/$testFile.$extensionWithoutDot")
      }

      override fun doCheck() {
        checker.run()
      }
    })
  }

  /**
   * Maps `<testName>Schema.json` to the `<testName>.<extension>` file, and checks the documentation at the caret.
   * The expected documentation is in `<testName>.html` and `<testName>_short.html`.
   */
  fun doDocumentationTest(hasDoc: Boolean, extension: String) {
    val testName = getTestName(true)
    val provider = JsonSchemaDocumentationProvider()
    LanguageDocumentation.INSTANCE.addExplicitExtension(JsonLanguage.INSTANCE, provider)
    try {
      skeleton(object : Callback {
        override fun registerSchemes() {
          val patterns = arrayListOf(UserDefinedJsonSchemaConfiguration.Item("$testName*", true, false))
          addSchema(UserDefinedJsonSchemaConfiguration("testDoc", JsonSchemaVersion.SCHEMA_4,
                                                       getUrlUnderTestRoot("${testName}Schema.json"), false, patterns))
        }

        override fun configureFiles() {
          fixture.configureByFiles("/$testName.$extension", "/${testName}Schema.json")
        }

        override fun doCheck() {
          val (inlineDoc, quickNavigate) = runReadActionBlocking {
            val psiElement = checkNotNull(PsiUtilBase.getElementAtCaret(fixture.editor))
            val documentationProvider = DocumentationManager.getProviderFromElement(psiElement)
            documentationProvider.generateDoc(psiElement, psiElement) to documentationProvider.getQuickNavigateInfo(psiElement, psiElement)
          }
          checkExpectedDoc(hasDoc, inlineDoc, "$testName.html")
          checkExpectedDoc(hasDoc, quickNavigate, "${testName}_short.html")
        }
      })
    }
    finally {
      LanguageDocumentation.INSTANCE.removeExplicitExtension(JsonLanguage.INSTANCE, provider)
      JsonSchemaTestServiceImpl.setProvider(null)
    }
  }

  private fun checkExpectedDoc(shouldHaveDoc: Boolean, doc: String?, expectedFileName: String) {
    if (shouldHaveDoc) {
      assertNotNull(doc, "inline help is null")
      PlatformTestUtil.assertSameLinesWithFile(fixture.testDataPath + "/" + expectedFileName, doc!!)
    }
    else {
      assertNull(doc, "inline help is not null")
    }
  }

  /**
   * The steps of [skeleton].
   */
  interface Callback {
    @Throws(Exception::class)
    fun registerSchemes()

    @Throws(Exception::class)
    fun configureFiles()

    @Throws(Exception::class)
    fun doCheck()
  }

  companion object {
    @JvmStatic
    fun getJsonSchemaTestDataFilePath(jsonSchemaRelativePath: String): String {
      val prefix = if (PathManagerEx.guessTestDataLookupStrategy() == PathManagerEx.TestDataLookupStrategy.COMMUNITY) {
        PathManager.getHomePath() + "/json/"
      }
      else {
        PathManager.getHomePath() + "/community/json/"
      }
      return prefix + "backend/tests/testData/jsonSchema/" + jsonSchemaRelativePath
    }
  }
}
