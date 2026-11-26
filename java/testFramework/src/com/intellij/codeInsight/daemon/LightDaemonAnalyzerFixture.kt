// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.injected.editor.EditorWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.FileTreeAccessFilter
import com.intellij.testFramework.HighlightTestInfo
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.runAllSuspend
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function
import java.util.function.Supplier

/**
 * The options of a [LightDaemonAnalyzerFixture]. Start from [DEFAULT] and add an option with a `with` method.
 */
@TestOnly
class LightDaemonAnalyzerOptions private constructor(
  /**
   * The test data path relative to the community path, as the `basePath` of [lightJavaCodeInsightFixture].
   * The default is the test data of `intellij.java.tests`, as `LightJavaCodeInsightTestCase.getTestDataPath()` gives it under Bazel.
   */
  val basePath: String,
  /**
   * When `true`, [LightDaemonAnalyzerFixture.doHighlighting] runs the folding pass. It is `doFolding()` of [LightDaemonAnalyzerTestCase].
   */
  val folding: Boolean,
  /**
   * When `false`, [LightDaemonAnalyzerFixture.doHighlighting] skips the local inspections pass.
   * It is `doInspections()` of [LightDaemonAnalyzerTestCase].
   */
  val inspections: Boolean,
) {
  fun withBasePath(basePath: String): LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions(basePath, folding, inspections)

  fun withFolding(): LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions(basePath, true, inspections)

  fun withoutInspections(): LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions(basePath, folding, false)

  companion object {
    /**
     * The options of [LightDaemonAnalyzerTestCase] without overrides.
     */
    @JvmField
    val DEFAULT: LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions("/java/java-tests/testData", false, true)
  }
}

/**
 * The JUnit 5 form of [LightDaemonAnalyzerTestCase]: a highlighting test in the light Java project. Create it with [lightDaemonAnalyzerFixture].
 *
 * For each test, the fixture prepares the daemon with [TestDaemonCodeAnalyzerImpl.prepareForTest] and switches the import hint off.
 * After the test, it switches the import hint on and cleans the daemon.
 * The highlighting runs with the tree access filter: it fails when it loads the tree of a file other than the configured file.
 *
 * [configureByFile] and [configureFromFileText] work as in [LightDaemonAnalyzerTestCase].
 * They create the file in the source root with its name only, and they delete the file of the last configure call first.
 * `configureByFile` of [fixture] keeps the directories of the path, so the package of the file can change.
 *
 * Call the members off the EDT. They run the calls that need the EDT on the EDT.
 * The test body runs off the EDT and without a command, so put an editor action or a refactoring in [LightJavaCodeInsightTestFixture.runInCommand].
 */
@TestOnly
class LightDaemonAnalyzerFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val sourceRoot: VirtualFile,
  val options: LightDaemonAnalyzerOptions,
  /**
   * The test helper of the daemon. It is one instance for the test, as `myTestDaemonCodeAnalyzer` of [LightDaemonAnalyzerTestCase].
   */
  val testDaemonCodeAnalyzer: TestDaemonCodeAnalyzerImpl,
  /**
   * When `true`, the highlighting can change the document. The test method or its class sets it with [CanChangeDocumentDuringHighlighting].
   */
  val isCanChangeDocumentDuringHighlighting: Boolean,
) {
  private val javaFilesFilter = FileTreeAccessFilter()
  private var configuredFile: VirtualFile? = null

  /**
   * The daemon of the project.
   */
  val daemonCodeAnalyzer: DaemonCodeAnalyzerImpl
    get() = DaemonCodeAnalyzer.getInstance(fixture.project) as DaemonCodeAnalyzerImpl

  /**
   * Configures the file of the test data path and [relativePath]. The path has no separator between the two parts, as in JUnit 3.
   * The fixture creates the file in the source root with the file name of [relativePath], and opens it in an editor.
   * See [configureFromFileText].
   */
  fun configureByFile(relativePath: String): PsiFile {
    val fullPath = fixture.testDataPath + relativePath
    val path = Path.of(fullPath)
    PlatformTestUtil.checkCaseSensitiveFS(fullPath, path.toFile())
    return configureFromFileText(path.fileName.toString(), StringUtilRt.convertLineSeparators(Files.readString(path)))
  }

  /**
   * Deletes the file of the last configure call, creates the file [fileName] with [text] in the source root, and opens it in an editor.
   * The text can have the caret and selection markers. When the caret is in an injected fragment, [fixture] gives the injected editor and file.
   */
  fun configureFromFileText(fileName: String, text: String): PsiFile {
    deleteConfiguredFile()
    val psiFile = fixture.configureByText(fileName, text)
    configuredFile = runReadActionBlocking {
      val virtualFile = psiFile.virtualFile
      if (virtualFile is VirtualFileWindow) virtualFile.delegate else virtualFile
    }
    return psiFile
  }

  private fun deleteConfiguredFile() {
    val file = configuredFile ?: return
    configuredFile = null
    runInEdtAndWait {
      if (file.isValid && file.isWritable && !file.fileSystem.isReadOnly) {
        WriteAction.run<Throwable> {
          // avoid messing with invalid files, in case someone calls configureXXX() several times
          PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
          FileEditorManager.getInstance(fixture.project).closeFile(file)
          file.delete(this)
        }
      }
    }
  }

  /**
   * Creates the file [relativePath] with [text] in the source root and opens it in an editor, as `createSaveAndOpenFile` of
   * [com.intellij.testFramework.LightPlatformCodeInsightTestCase]. It does not change [fixture]. The tree access filter does not allow the file.
   */
  fun createSaveAndOpenFile(relativePath: String, text: String): Editor = runInEdtAndGet {
    val project = fixture.project
    val file = VfsTestUtil.createFile(sourceRoot, relativePath, text)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    @Suppress("DEPRECATION")
    val editor = checkNotNull(FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, 0), false)) {
      "No editor for $file"
    }
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart("LightDaemonAnalyzerFixture.createSaveAndOpenFile")
    (editor as EditorImpl).setCaretActive()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    editor
  }

  /**
   * Sets the language level of the module until the end of the test.
   *
   * [LightDaemonAnalyzerTestCase] had `setLanguageLevel`, which set the level of the project. Its module had no level of its own.
   * The module of [lightDaemonAnalyzerFixture] has an explicit level, so a change of the project level does nothing.
   * Use this function also for `IdeaTestUtil.setProjectLanguageLevel` of a JUnit 3 test.
   */
  fun setLanguageLevel(level: LanguageLevel) {
    IdeaTestUtil.setModuleLanguageLevel(fixture.module, level, fixture.testRootDisposable)
  }

  /**
   * Configures [filePath] with [configureByFile] and checks its highlighting with the markup of its text.
   */
  fun doTest(filePath: String, checkWarnings: Boolean, checkInfos: Boolean) {
    configureByFile(filePath)
    doTestConfiguredFile(checkWarnings, checkInfos, filePath)
  }

  /**
   * Configures [filePath] with [configureByFile] and checks its highlighting with the markup of its text.
   */
  fun doTest(filePath: String, checkWarnings: Boolean, checkWeakWarnings: Boolean, checkInfos: Boolean) {
    configureByFile(filePath)
    doTestConfiguredFile(checkWarnings, checkWeakWarnings, checkInfos, filePath)
  }

  /**
   * Checks the highlighting of the configured file with the markup of its text. [filePath] only names the file in a failure message.
   */
  fun doTestConfiguredFile(checkWarnings: Boolean, checkInfos: Boolean, filePath: String?) {
    doTestConfiguredFile(checkWarnings, false, checkInfos, filePath)
  }

  /**
   * Checks the highlighting of the configured file with the markup of its text. [filePath] only names the file in a failure message.
   */
  fun doTestConfiguredFile(checkWarnings: Boolean, checkWeakWarnings: Boolean, checkInfos: Boolean, filePath: String?) {
    val data = runReadActionBlocking {
      ExpectedHighlightingData(fixture.editor.document, checkWarnings, checkWeakWarnings, checkInfos)
    }
    checkHighlighting(data, filePath)
  }

  /**
   * Highlights the configured file with the tree access filter and checks the result with [data].
   * Use it for the data that a `getExpectedHighlightingData` override of [LightDaemonAnalyzerTestCase] gave.
   * [filePath] only names the file in a failure message.
   */
  fun checkHighlighting(data: ExpectedHighlightingData, filePath: String?) {
    val project = fixture.project
    data.init()

    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    val psiFile = fixture.file
    runReadActionBlocking { psiFile.text } // to load text
    javaFilesFilter.allowTreeAccessForFile(psiFile.virtualFile)
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(javaFilesFilter, fixture.testRootDisposable)
    try {
      val infos = doHighlighting()
      runInEdtAndWait {
        data.checkResult(psiFile, infos, fixture.editor.document.text, filePath?.let { fixture.testDataPath + "/" + it })
      }
    }
    finally {
      PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, fixture.testRootDisposable)
    }
  }

  /**
   * Returns a highlighting test of [filePath], as `doTestFile` of [LightDaemonAnalyzerTestCase].
   * Its `test()` configures the file with [configureByFile] and checks its highlighting with [JavaExpectedHighlightingData].
   */
  fun doTestFile(filePath: String): HighlightTestInfo =
    object : HighlightTestInfo(fixture.testRootDisposable, filePath) {
      override fun doTest(): HighlightTestInfo {
        val path = filePaths.single()
        configureByFile(path)
        val data = runReadActionBlocking {
          JavaExpectedHighlightingData(fixture.editor.document, checkWarnings, checkWeakWarnings, checkInfos)
        }
        if (checkSymbolNames) data.checkSymbolNames()
        checkHighlighting(data, path)
        return this
      }
    }

  fun highlightErrors(): List<HighlightInfo> = doHighlighting(HighlightSeverity.ERROR)

  fun doHighlighting(minSeverity: HighlightSeverity): List<HighlightInfo> = DaemonAnalyzerFixture.filter(doHighlighting(), minSeverity)

  /**
   * Runs the passes of the daemon on the configured file and returns the highlighting.
   * The passes skip the folding and the local inspections as [options] say.
   * In an injected editor, the passes run on the top-level editor and file.
   */
  fun doHighlighting(): List<HighlightInfo> {
    val project = fixture.project
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }

    val toIgnore = ArrayList<Int>()
    if (!options.folding) {
      toIgnore.add(Pass.UPDATE_FOLDING)
    }
    if (!options.inspections) {
      toIgnore.add(Pass.LOCAL_INSPECTIONS)
    }
    var editor = fixture.editor
    var psiFile = fixture.file
    if (editor is EditorWindow) {
      editor = editor.delegate
      psiFile = runReadActionBlocking { InjectedLanguageManager.getInstance(project).getTopLevelFile(psiFile) }
    }
    return CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, toIgnore.toIntArray(), isCanChangeDocumentDuringHighlighting)
  }

  companion object {
    /**
     * Creates a [LightDaemonAnalyzerFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
     *
     * [localInspectionTools] gives the inspections of the test profile, as `configureLocalInspectionTools()` of [LightDaemonAnalyzerTestCase].
     * In Java, give it as a lambda, for example `() -> List.of(new FooInspection())`.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun lightDaemonAnalyzerFixture(
      projectFixture: TestFixture<LightJavaProject>,
      localInspectionTools: Supplier<out List<InspectionProfileEntry>> = Supplier { emptyList() },
      options: LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions.DEFAULT,
    ): TestFixture<LightDaemonAnalyzerFixture> = lightDaemonAnalyzerFixture(projectFixture, null, localInspectionTools, options)

    /**
     * Creates a [LightDaemonAnalyzerFixture] as the other overload does, for a class that picks the descriptor for each test.
     * [descriptor] works as in [lightJavaCodeInsightFixture]. For the JUnit 3 rule of a `JavaXYZ` test name,
     * give [com.intellij.testFramework.fixtures.lightJavaDescriptorOfTestName].
     * A `getProjectJDK()` override that reads the test name also becomes a [descriptor]. Keep each descriptor in a constant.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun lightDaemonAnalyzerFixture(
      projectFixture: TestFixture<LightJavaProject>,
      descriptor: Function<String, LightProjectDescriptor>?,
      localInspectionTools: Supplier<out List<InspectionProfileEntry>> = Supplier { emptyList() },
      options: LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions.DEFAULT,
    ): TestFixture<LightDaemonAnalyzerFixture> = testFixture("lightDaemonAnalyzerFixture") { context ->
      val lightProject = projectFixture.init()
      val fixture = lightJavaCodeInsightFixture(projectFixture, descriptor, options.basePath).init()
      val tools = localInspectionTools.get()
      if (tools.isNotEmpty()) {
        fixture.enableInspections(*tools.toTypedArray())
      }
      val testDaemonCodeAnalyzer = TestDaemonCodeAnalyzerImpl(fixture.project)
      withContext(Dispatchers.EDT) {
        testDaemonCodeAnalyzer.prepareForTest()
      }
      DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = false

      val testMethod = context.extensionContext.requiredTestMethod
      val testClass = context.extensionContext.requiredTestClass
      fun annotatedWith(annotationClass: Class<out Annotation>): Boolean =
        testMethod.getAnnotation(annotationClass) != null ||
        generateSequence(testClass) { it.superclass }.any { it.getAnnotation(annotationClass) != null }

      val canChangeDocument = annotatedWith(CanChangeDocumentDuringHighlighting::class.java) ||
                              @Suppress("DEPRECATION") annotatedWith(DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting::class.java)
      val daemonFixture = LightDaemonAnalyzerFixture(fixture, lightProject.sourceRoot, options, testDaemonCodeAnalyzer, canChangeDocument)
      initialized(daemonFixture) {
        runAllSuspend(
          // return default value to avoid unnecessary save
          { DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = true },
          { withContext(Dispatchers.EDT) { testDaemonCodeAnalyzer.cleanupAfterTest() } },
        )
      }
    }
  }
}
