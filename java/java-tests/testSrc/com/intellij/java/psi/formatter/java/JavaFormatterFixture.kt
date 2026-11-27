// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.JavaTestUtil
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.formatting.FormatterTestUtils.ACTIONS
import com.intellij.formatting.FormatterTestUtils.Action
import com.intellij.formatting.FormatterTestUtils.Action.REFORMAT
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.IncorrectOperationException
import com.intellij.util.LocalTimeCounter
import com.intellij.util.text.LineReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * A Java formatter test: it formats a Java file of a text and compares the result with the expected text.
 * Create it with [javaFormatterFixture].
 *
 * Each test gets its own temporary code style settings from [fixture], so a test can change [settings], [javaSettings]
 * and [indentOptions]. The project language level is [LanguageLevel.HIGHEST] for each test.
 * The fixture creates the file `A.java` of [doTextTest] outside the source root, with [LanguageLevel.HIGHEST] in
 * [PsiUtil.FILE_LANGUAGE_LEVEL_KEY].
 *
 * Call the members off the EDT. They run the command of the formatter and the document commit on the EDT.
 */
@TestOnly
class JavaFormatterFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * The range that [doTextTest] formats. When it is null, [doTextTest] formats the whole file.
   */
  var textRange: TextRange? = null

  /**
   * A range of line numbers. When it is not null, [doTextTest] sets [textRange] to these lines of the text before the format.
   */
  var lineRange: TextRange? = null

  val project: Project
    get() = fixture.project

  /**
   * The common Java code style settings of the test.
   */
  val settings: CommonCodeStyleSettings
    get() = CodeStyle.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE)

  val javaSettings: JavaCodeStyleSettings
    get() = settings.rootSettings.getCustomSettings(JavaCodeStyleSettings::class.java)

  val indentOptions: CommonCodeStyleSettings.IndentOptions
    get() = settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE)

  /**
   * See [LightJavaCodeInsightTestFixture.getTestName].
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Creates a non-physical file of [fileName] with [text], outside the source root.
   * The file type comes from the extension of [fileName].
   */
  fun createFile(fileName: String, text: String): PsiFile {
    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
    return runReadActionBlocking {
      PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false)
    }
  }

  /**
   * Formats the test data file `<test name>.java` and compares the result with `<test name>_after.java`.
   */
  fun doTest() {
    val testName = getTestName(false)
    doTest("$testName.java", "${testName}_after.java")
  }

  /**
   * Formats the test data file [fileNameBefore] and compares the result with the test data file [fileNameAfter].
   */
  fun doTest(fileNameBefore: String, fileNameAfter: String) {
    doTextTest(REFORMAT, loadFile(fileNameBefore), loadFile(fileNameAfter))
  }

  /**
   * Runs [doTextTest] with the detectable indent options switched on.
   */
  fun doTestWithDetectableIndentOptions(text: String, textAfter: String) {
    val provider = DetectableIndentOptionsProvider.getInstance()
    assertNotNull(provider, "DetectableIndentOptionsProvider not found")
    provider!!.setEnabledInTest(true)
    try {
      doTextTest(text, textAfter)
    }
    finally {
      provider.setEnabledInTest(false)
    }
  }

  fun doTextTest(text: String, textAfter: String) {
    doTextTest(REFORMAT, text, textAfter)
  }

  /**
   * Runs [action] on the file `A.java` with [text], in [textRange] or on the whole file.
   * Then it compares the document and the file with [textAfter].
   */
  fun doTextTest(action: Action, text: String, textAfter: String) {
    val file = createFile("A.java", text)
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST)
    val manager = PsiDocumentManager.getInstance(project)
    val document = runReadActionBlocking { manager.getDocument(file) } ?: fail("Document is null")
    replaceAndProcessDocument(action, text, file, document)
    assertEquals(textAfter, document.text)
    runInEdtAndWait {
      manager.commitDocument(document)
    }
    assertEquals(textAfter, runReadActionBlocking { file.text })
  }

  /**
   * Formats each text of [before] in one file, and checks that each result is equal to the result of the first text.
   */
  fun formatEveryoneAndCheckIfResultEqual(vararg before: String) {
    assert(before.size > 1)
    val file = createFile("A.java", "")
    val document = runReadActionBlocking { PsiDocumentManager.getInstance(project).getDocument(file) }
    val afterFirst = replaceAndProcessDocument(REFORMAT, before[0], file, document)
    for (nextBefore in before) {
      assertEquals(afterFirst, replaceAndProcessDocument(REFORMAT, nextBefore, file, document))
    }
  }

  private fun replaceAndProcessDocument(action: Action, text: String, file: PsiFile, document: Document?): String {
    if (document == null) {
      fail<Nothing>("Don't expect the document to be null")
    }
    lineRange?.let { lines ->
      val doc = DocumentImpl(text)
      textRange = TextRange(doc.getLineStartOffset(lines.startOffset), doc.getLineEndOffset(lines.endOffset))
    }
    val manager = PsiDocumentManager.getInstance(project)
    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, {
        ApplicationManager.getApplication().runWriteAction {
          document.replaceString(0, document.textLength, text)
          manager.commitDocument(document)
          try {
            val rangeToUse = textRange ?: file.textRange
            ACTIONS[action]!!.run(file, rangeToUse.startOffset, rangeToUse.endOffset)
          }
          catch (e: IncorrectOperationException) {
            fail(e.localizedMessage)
          }
        }
      }, if (action == REFORMAT) ReformatCodeProcessor.getCommandName() else "", "")
    }
    return document.text
  }

  /**
   * Formats [before] as the body of a method in a class, and compares the body with [after].
   */
  fun doMethodTest(before: String, after: String) {
    doTextTest(
      REFORMAT,
      "class Foo{\n    void foo() {\n$before\n    }\n}",
      "class Foo {\n    void foo() {\n${shiftIndentInside(after, 8, false)}\n    }\n}"
    )
  }

  /**
   * Formats [before] as the body of a class, and compares the body with [after].
   */
  fun doClassTest(before: String, after: String) {
    doTextTest(
      REFORMAT,
      "class Foo{\n$before\n}",
      "class Foo {\n${shiftIndentInside(after, 4, false)}\n}"
    )
  }

  companion object {
    private val BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/java"

    /**
     * Indents each line of [initial] with [i] spaces. An empty line gets the spaces only with [shiftEmptyLines].
     */
    @JvmStatic
    fun shiftIndentInside(initial: String, i: Int, shiftEmptyLines: Boolean): String {
      val result = StringBuilder(initial.length)
      val lines = try {
        LineReader(ByteArrayInputStream(initial.toByteArray(StandardCharsets.UTF_8))).readLines()
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }

      var first = true
      for (line in lines) {
        if (!first) result.append('\n')
        if (line.isNotEmpty() || shiftEmptyLines) {
          StringUtil.repeatSymbol(result, ' ', i)
        }
        result.append(String(line, StandardCharsets.UTF_8))
        first = false
      }
      return result.toString()
    }

    /**
     * Loads the formatter test data file [name] with the line separators `\n`.
     */
    @JvmStatic
    fun loadFile(name: String): String {
      val fullName = BASE_PATH + File.separatorChar + name
      try {
        return StringUtil.convertLineSeparators(FileUtil.loadFile(File(fullName)))
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }
  }
}

/**
 * Creates the light project of a [javaFormatterFixture] class: a Java module with the mock JDK 1.7.
 * Declare it in a static field or in a companion object.
 */
@TestOnly
fun javaFormatterProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(DefaultLightProjectDescriptor())

/**
 * Creates a [JavaFormatterFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
 * It sets the project language level to [LanguageLevel.HIGHEST]. [lightJavaCodeInsightFixture] sets the level back after the test.
 */
@TestOnly
fun javaFormatterFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<JavaFormatterFixture> {
  val codeInsightFixture = lightJavaCodeInsightFixture(projectFixture)
  return testFixture("javaFormatterFixture") {
    val fixture = codeInsightFixture.init()
    withContext(Dispatchers.EDT) {
      IdeaTestUtil.setProjectLanguageLevel(fixture.project, LanguageLevel.HIGHEST)
    }
    initialized(JavaFormatterFixture(fixture)) {}
  }
}
