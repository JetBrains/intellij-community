// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.stateStore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

/**
 * A heavy Java test project, as [JavaCodeInsightTestCase] gives it: a new opened project on disk for each test,
 * with one main module, the mock JDK 1.7 and the language level 1.6.
 *
 * The helpers copy the test data files from [testDataPath] and open them in real editors.
 * The fixture keeps the editor and the file of the last `configureBy*` call.
 *
 * Call a member from the test thread, not from the EDT. A member moves to the EDT only for a call that needs it.
 * A PSI read of the test needs a read action.
 *
 * Create it with [heavyJavaCodeInsightFixture]. A sub-fixture overrides the `protected open` hooks.
 */
@TestOnly
open class HeavyJavaCodeInsightFixture(context: Context, val testDataPath: String) {
  /**
   * The test data of a new fixture. [heavyJavaCodeInsightFixture] creates it.
   */
  class Context internal constructor(
    val project: Project,
    internal val tempRoot: Path,
    internal val testClass: Class<*>,
    internal val testMethod: Method,
    internal val isTestDataProject: Boolean,
  )

  val project: Project = context.project
  private val tempRoot: Path = context.tempRoot
  private val testClass: Class<*> = context.testClass
  private val testMethod: Method = context.testMethod
  private val isTestDataProject: Boolean = context.isTestDataProject

  /**
   * The disposable of the test. The fixture disposes it after the test, before the project closes.
   */
  val testRootDisposable: Disposable = Disposer.newDisposable("HeavyJavaCodeInsightFixture")

  /**
   * The main module. It is set before the first member of a test runs.
   */
  lateinit var module: Module
    private set

  private val modulesToDispose = ArrayList<Module>()
  private var tempDirCounter = 0

  /**
   * The editor of the last `configureBy*` call. It is the editor of the first file of the call.
   */
  var editor: Editor? = null

  /**
   * The PSI file of [editor].
   */
  var file: PsiFile? = null

  /**
   * When `true`, `configureByFiles` clears the roots of [module] before it adds the new content root.
   */
  var isClearModelBeforeConfiguring: Boolean = false

  /**
   * When `true`, `configureByFiles` adds the directory of the copied files as a content root of [module].
   */
  var isAddDirToContentRoot: Boolean = true

  /**
   * When `true`, `configureByFiles` also adds the directory of the copied files as a source root.
   */
  var isAddDirToSource: Boolean = true

  /**
   * The directory where `configureByFiles` copies the files.
   * When it is `null`, each call copies the files to a new temporary directory.
   */
  var contentDirectory: Supplier<VirtualFile>? = null

  /**
   * Wraps each `configureByFiles` call. The interceptor gets the project root, the files and the call that copies and opens the files.
   */
  var configureInterceptor: ConfigureInterceptor? = null

  private val psiManager: PsiManagerEx
    get() = PsiManagerEx.getInstanceEx(project)

  val javaFacade: JavaPsiFacadeEx
    get() = JavaPsiFacadeEx.getInstanceEx(project)

  /**
   * Wraps a `configureByFiles` call, for example to change the module before or after the files are copied.
   */
  fun interface ConfigureInterceptor {
    fun configure(projectRoot: File?, files: Array<out VirtualFile>, proceed: BiFunction<File?, Array<out VirtualFile>, VirtualFile>): VirtualFile
  }

  /**
   * Creates the main module of the project. By default, it is a Java module in the project directory.
   * For a project from the test data, it is the first module of the project.
   */
  protected open fun createMainModule(): Module {
    if (isTestDataProject) {
      return ModuleManager.getInstance(project).modules.first()
    }
    val moduleFile = project.stateStore.projectBasePath.resolve(project.name + ".iml")
    val module = WriteAction.computeAndWait<Module, RuntimeException> {
      ModuleManager.getInstance(project).newModule(moduleFile, JavaModuleType.getModuleType().id)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return module
  }

  /**
   * Sets up [module] after [createMainModule] and before the fixture sets the JDK, as `setUpModule` of a JUnit 3 test.
   */
  protected open fun setUpModule(module: Module) {
  }

  /**
   * The JDK of each module. It is `getTestProjectJdk()` of [com.intellij.testFramework.JavaProjectTestCase].
   */
  protected open fun getTestProjectJdk(): Sdk = IdeaTestUtil.getMockJdk17()

  /**
   * The language level of the project. It is `getProjectLanguageLevel()` of [com.intellij.testFramework.JavaProjectTestCase].
   */
  protected open fun getProjectLanguageLevel(): LanguageLevel = LanguageLevel.JDK_1_6

  /**
   * Sets up the project before the test. An override calls `super.setUp()` first.
   */
  protected open fun setUp() {
    // the global data context of an action gets the project and its editor
    TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project), testRootDisposable)
    module = createMainModule()
    setUpModule(module)
    setUpJdk(getTestProjectJdk())
    IdeaTestUtil.setProjectLanguageLevel(project, getProjectLanguageLevel())
    runInEdtAndWait {
      CodeStyle.setTemporarySettings(project, CodeStyle.createTestSettings())
    }
    InjectedLanguageManagerImpl.pushInjectors(project)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun setUpJdk(jdk: Sdk) {
    val jdkTable = ProjectJdkTable.getInstance()
    if (jdkTable.allJdks.none { it === jdk }) {
      WriteAction.runAndWait<RuntimeException> { jdkTable.addJdk(jdk, project) }
    }
    for (module in ModuleManager.getInstance(project).modules) {
      ModuleRootModificationUtil.setModuleSdk(module, jdk)
    }
  }

  /**
   * Cleans up the project after the test. An override cleans up its own state first and then calls `super.tearDown()`.
   * The fixture runs each step also when a step before it fails.
   */
  protected open fun tearDown() {
    runAll(
      {
        runInEdtAndWait {
          FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
          editor = null
          file = null
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
        }
      },
      { disposeLoadedModules() },
      { runInEdtAndWait { Disposer.dispose(testRootDisposable) } },
      { InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project) },
    )
  }

  private fun disposeLoadedModules() {
    if (modulesToDispose.isEmpty()) {
      return
    }
    WriteAction.runAndWait<RuntimeException> {
      val moduleManager = ModuleManager.getInstance(project)
      for (module in modulesToDispose) {
        if (moduleManager.findModuleByName(module.name) != null) {
          moduleManager.disposeModule(module)
        }
      }
    }
    modulesToDispose.clear()
  }

  /**
   * Returns the test method name without the `test` prefix, as `UsefulTestCase.getTestName` does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethod.name, lowercaseFirstLetter)

  /**
   * Returns `true` when the test method, its class or a superclass has [annotationClass].
   */
  protected fun annotatedWith(annotationClass: Class<out Annotation>): Boolean {
    if (testMethod.getAnnotation(annotationClass) != null) return true
    var aClass: Class<*>? = testClass
    while (aClass != null && aClass != Any::class.java) {
      if (aClass.getAnnotation(annotationClass) != null) return true
      aClass = aClass.superclass
    }
    return false
  }

  /**
   * Returns [testDataPath] with `/` separators.
   */
  fun getTestRoot(): String = FileUtil.toSystemIndependentName(testDataPath)

  /**
   * Creates a new directory in the temporary directory of the test.
   */
  fun createTempVirtualDir(): VirtualFile {
    val root = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempRoot)) { tempRoot.toString() }
    return WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildDirectory(HeavyJavaCodeInsightFixture::class.java, "dir" + tempDirCounter++)
    }
  }

  /**
   * Copies the directory [rootPath] to a new directory in the temporary directory of the test.
   * The new directory becomes a content root and a source root of [module].
   */
  fun createTestProjectStructure(rootPath: String?): VirtualFile {
    val dir = Files.createTempDirectory(tempRoot, "structure")
    val result = HeavyTestHelper.createTestProjectStructure(module, rootPath, dir, true)
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    return result
  }

  /**
   * Runs [runnable] on the EDT in a command, as a heavy JUnit 3 test with `@WrapInCommand` ran.
   */
  fun runInCommand(runnable: ThrowableRunnable<*>) {
    runInEdtAndWait {
      var error: Throwable? = null
      CommandProcessor.getInstance().executeCommand(project, {
        try {
          runnable.run()
        }
        catch (e: Throwable) {
          error = e
        }
      }, null, null)
      error?.let { throw it }
    }
  }

  /**
   * Loads the module file [modulePath] into the project. The fixture disposes the module after the test.
   */
  fun loadModule(modulePath: Path): Module {
    val module = WriteAction.computeAndWait<Module, Exception> { ModuleManager.getInstance(project).loadModule(modulePath) }
    modulesToDispose.add(module)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return module
  }

  fun findVirtualFile(filePath: String): VirtualFile {
    val absolutePath = testDataPath + filePath
    VfsRootAccess.allowRootAccess(testRootDisposable, absolutePath)
    return VfsTestUtil.findFileByCaseSensitivePath(absolutePath)
  }

  /**
   * Copies the file [filePath] of the test data and opens it in an editor.
   */
  open fun configureByFile(filePath: String): VirtualFile = configureByFile(filePath, null)

  fun configureByFile(filePath: String, projectRoot: String?): VirtualFile {
    val vFile = findVirtualFile(filePath)
    val projectFile = if (projectRoot == null) null else File(testDataPath + projectRoot)
    return configureByFiles(projectFile, vFile)
  }

  fun configureByFile(vFile: VirtualFile): VirtualFile = configureByFiles(null, vFile)

  /**
   * Copies [files] and opens each of them in an editor. The first file stays in [editor].
   */
  fun configureByFiles(projectRoot: String?, vararg files: String): VirtualFile? {
    if (files.isEmpty()) {
      return null
    }
    val vFiles = files.map { path -> findVirtualFile(path).also { VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(it, path) } }
    val projectFile = if (projectRoot == null) null else File(testDataPath + projectRoot)
    return configureByFiles(projectFile, *vFiles.toTypedArray())
  }

  /**
   * Copies [vFiles] to [contentDirectory] or to a new temporary directory and opens each of them in an editor.
   * When [projectRoot] is set, the fixture copies the whole directory and keeps the paths of [vFiles] relative to it.
   * The first file stays in [editor].
   */
  fun configureByFiles(projectRoot: File?, vararg vFiles: VirtualFile): VirtualFile {
    val interceptor = configureInterceptor ?: return doConfigureByFiles(projectRoot, vFiles)
    return interceptor.configure(projectRoot, vFiles) { root, files -> doConfigureByFiles(root, files) }
  }

  private fun doConfigureByFiles(rawProjectRoot: File?, vFiles: Array<out VirtualFile>): VirtualFile {
    file = null
    editor = null

    val toDir = contentDirectory?.get() ?: createTempVirtualDir()

    val editorInfos = WriteAction.computeAndWait<Map<VirtualFile, EditorInfo?>, IOException> {
      val rootModel = ModuleRootManager.getInstance(module).modifiableModel
      if (isClearModelBeforeConfiguring) {
        rootModel.clear()
      }

      // auxiliary files should be copied first
      val reversed = vFiles.reversed()
      val infos: Map<VirtualFile, EditorInfo?>
      if (rawProjectRoot != null) {
        FileUtil.copyDir(rawProjectRoot, toDir.toNioPath().toFile())
        val projectRoot = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(
          FileUtil.toSystemIndependentName(rawProjectRoot.absolutePath)))
        infos = copyFilesFillingEditorInfos(projectRoot, toDir, reversed.map { file ->
          checkNotNull(VfsUtilCore.getRelativePath(file, projectRoot)) { file.path }
        })
        toDir.refresh(false, true)
      }
      else {
        infos = LinkedHashMap()
        for (vFile in reversed) {
          val parent = vFile.parent
          check(parent.isDirectory) { parent }
          infos.putAll(copyFilesFillingEditorInfos(parent, toDir, listOf(vFile.name)))
        }
      }

      var sourceRootAdded = false
      if (isAddDirToContentRoot) {
        val contentEntry = rootModel.addContentEntry(toDir)
        if (isAddDirToSource) {
          sourceRootAdded = true
          contentEntry.addSourceFolder(toDir, false)
        }
      }
      rootModel.commit()
      IndexingTestUtil.waitUntilIndexesAreReady(project)
      if (sourceRootAdded) {
        sourceRootAdded(toDir)
      }
      infos
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    runInEdtAndWait {
      val editors = editorInfos.keys.map { newVFile ->
        val psiFile = psiManager.findFile(newVFile)
        if (file == null) file = psiFile
        val newEditor = createEditor(newVFile)
        if (editor == null) editor = newEditor
        editorInfos[newVFile]?.applyToEditor(newEditor)
        newEditor
      }
      setActiveEditor(editors.last())
    }
    return toDir
  }

  /**
   * Runs in the write action of `configureByFiles` after [dir] becomes a source root of [module].
   */
  protected open fun sourceRootAdded(dir: VirtualFile) {
  }

  private fun copyFilesFillingEditorInfos(fromDir: VirtualFile, toDir: VirtualFile, relativePaths: List<String>): Map<VirtualFile, EditorInfo?> {
    val editorInfos = LinkedHashMap<VirtualFile, EditorInfo?>()
    for (path in relativePaths) {
      val relativePath = path.removePrefix("/")
      val fromFile = checkNotNull(fromDir.findFileByRelativePath(relativePath)) { fromDir.path + "/" + relativePath }
      var toFile = toDir.findFileByRelativePath(relativePath)
      if (toFile == null) {
        val ioFile = File(toDir.path, relativePath)
        FileUtil.createIfDoesntExist(ioFile)
        toFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(ioFile.absolutePath)) { ioFile.canonicalPath }
      }
      toFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, FileUtil.toSystemDependentName(fromFile.path))
      if (fromFile.fileType.isBinary) {
        toFile.setBinaryContent(fromFile.contentsToByteArray())
        editorInfos[toFile] = null
      }
      else {
        val editorInfo = EditorInfo(StringUtil.convertLineSeparators(VfsUtilCore.loadText(fromFile)))
        LoadTextUtil.write(null, toFile, toFile, editorInfo.newFileText, -1)
        editorInfos[toFile] = editorInfo
      }
    }
    return editorInfos
  }

  /**
   * Opens [virtualFile] in an editor, removes the caret markers from its text and makes it the current file.
   */
  fun configureByExistingFile(virtualFile: VirtualFile) {
    file = null
    editor = null
    runInEdtAndWait {
      val newEditor = createEditor(virtualFile)
      val document = newEditor.document
      val editorInfo = EditorInfo(document.text)
      val newFileText = editorInfo.newFileText
      WriteAction.run<RuntimeException> {
        if (document.text != newFileText) {
          document.setText(newFileText)
        }
        file = psiManager.findFile(virtualFile)
        editor = newEditor
        editorInfo.applyToEditor(newEditor)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  /**
   * Writes [text] to a new file in a new source root of [module], and opens it.
   */
  @JvmOverloads
  fun configureByText(fileType: FileType, text: String, extension: String? = null): PsiFile {
    val ext = extension ?: fileType.defaultExtension
    val dir = createTempVirtualDir()
    CodeInsightTestFixtureImpl.associateExtensionTemporarily(fileType, ext, testRootDisposable)
    val vFile = WriteAction.computeAndWait<VirtualFile, IOException> {
      val created = dir.createChildData(HeavyJavaCodeInsightFixture::class.java, "tempFile.$ext")
      created.charset = StandardCharsets.UTF_8
      VfsUtil.saveText(created, text)
      created
    }
    PsiTestUtil.addSourceRoot(module, dir)
    configureByExistingFile(vFile)
    val psiFile = checkNotNull(file)
    check(psiFile.virtualFile.fileType == fileType) { "Expected $fileType, actual ${psiFile.virtualFile.fileType}" }
    return psiFile
  }

  /**
   * Opens [file] in a real editor and restarts the daemon. Runs on the EDT.
   */
  protected open fun createEditor(file: VirtualFile): Editor {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val newEditor = checkNotNull(FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, 0), false)) { file.path }
    (newEditor as EditorImpl).setCaretActive()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart("HeavyJavaCodeInsightFixture.createEditor $file")
    return newEditor
  }

  /**
   * Makes [editor] and its file current. Call it on the EDT.
   */
  fun setActiveEditor(editor: Editor) {
    this.editor = editor
    this.file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
  }

  /**
   * Types [s] in [editor], one character after another, as [type] does.
   */
  fun type(s: String) {
    for (c in s) {
      type(c)
    }
  }

  /**
   * Types [c] in [editor]. For `'\n'`, it runs the enter action in a command.
   */
  fun type(c: Char) {
    if (c == '\n') {
      executeAction(IdeActions.ACTION_EDITOR_ENTER)
    }
    else {
      runInEdtAndWait {
        TypedAction.getInstance().actionPerformed(editor!!, c, DataManager.getInstance().dataContext)
      }
    }
  }

  fun backspace() {
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE)
  }

  fun deleteLine() {
    executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE)
  }

  fun caretUp() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
  }

  /**
   * Runs the editor action [actionId] in [editor] in a command.
   */
  fun executeAction(actionId: String) {
    runInEdtAndWait {
      val currentEditor = editor!!
      CommandProcessor.getInstance()
        .executeCommand(project, { EditorTestUtil.executeAction(currentEditor, actionId, true) }, "", null, currentEditor.document)
      awaitPendingNavigationAndEditorOpenIfEnabled(project)
    }
  }

  fun getDocument(file: PsiFile): Document? = ReadAction.computeBlocking<Document?, RuntimeException> {
    PsiDocumentManager.getInstance(project).getDocument(file)
  }

  /**
   * Checks the text and the caret of [editor] with the file [filePath] of the test data.
   */
  fun checkResultByFile(filePath: String) {
    val vFile = findVirtualFile(filePath)
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      PsiDocumentManager.getInstance(project).commitAllDocuments()

      VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(vFile, filePath)
      val expectedText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile))
      val document = EditorFactory.getInstance().createDocument(expectedText)
      val caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document)

      val topEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor!!)
      editor = topEditor
      file = PsiDocumentManager.getInstance(project).getPsiFile(topEditor.document)

      val actualText = StringUtil.convertLineSeparators(file!!.text)
      if (document.text != actualText) {
        throw FileComparisonFailedError("Text mismatch in file $filePath", document.text, actualText, vFile.path)
      }
      EditorTestUtil.verifyCaretAndSelectionState(topEditor, caretState)
    }
  }

  /**
   * Writes the file [filePath] of the test data without its [REFERENCE_MARKER] to a new source root of [module].
   * Returns the reference at the marker. The new file becomes [file].
   * It is `configureByFile` of [com.intellij.testFramework.JavaResolveTestCase].
   */
  fun configureReferenceByFile(filePath: String): PsiReference {
    val vFile = VfsTestUtil.findFileByCaseSensitivePath(testDataPath + filePath)
    val fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile))
    val offset = fileText.indexOf(REFERENCE_MARKER)
    check(offset >= 0) { "Expected to find $REFERENCE_MARKER marker in file but was none" }
    val text = fileText.substring(0, offset) + fileText.substring(offset + REFERENCE_MARKER.length)
    val psiFile = createFile(vFile.name, text)
    file = psiFile
    return checkNotNull(ReadAction.computeBlocking<PsiReference?, RuntimeException> { psiFile.findReferenceAt(offset) }) {
      "No reference at $offset in $filePath"
    }
  }

  fun findClass(name: String): PsiClass {
    val aClass = ReadAction.computeBlocking<PsiClass?, RuntimeException> { javaFacade.findClass(name, ProjectScope.getProjectScope(project)) }
    return checkNotNull(aClass) { "Class $name not found" }
  }

  /**
   * Adds [vDir] as a content root and a source root of [module].
   */
  open fun addSourceContentToRoots(module: Module, vDir: VirtualFile) {
    PsiTestUtil.addSourceContentToRoots(module, vDir)
  }

  /**
   * Writes [text] to a new file [fileName] in [vDir]. When [vDir] is not in the sources, it becomes a source root of [module].
   */
  fun createFile(module: Module, vDir: VirtualFile, fileName: String, text: String): PsiFile {
    if (!ReadAction.computeBlocking<Boolean, RuntimeException> { ModuleRootManager.getInstance(module).fileIndex.isInSourceContent(vDir) }) {
      addSourceContentToRoots(module, vDir)
    }
    val virtualFile = WriteAction.computeAndWait<VirtualFile, IOException> {
      val vFile = vDir.createChildData(vDir, fileName)
      VfsUtil.saveText(vFile, text)
      vFile
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return checkNotNull(ReadAction.computeBlocking<PsiFile?, RuntimeException> { PsiManager.getInstance(project).findFile(virtualFile) })
  }

  /**
   * Writes [text] to a new file [fileName] in a new temporary directory, which becomes a source root of [module].
   */
  fun createFile(fileName: String, text: String): PsiFile = createFile(module, createTempVirtualDir(), fileName, text)

  companion object {
    /**
     * The marker of the reference in a test data file of [configureReferenceByFile].
     */
    const val REFERENCE_MARKER: String = "<ref>"

    /**
     * Creates a [HeavyJavaCodeInsightFixture] with a new project for each test. Declare it as an instance field.
     * [testDataPath] is the prefix of each test data path of the helpers.
     * See [heavyJavaCodeInsightSubFixture] for [testDataProjectDir].
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun heavyJavaCodeInsightFixture(
      testDataPath: String,
      testDataProjectDir: Function<String, String>? = null,
    ): TestFixture<HeavyJavaCodeInsightFixture> =
      heavyJavaCodeInsightSubFixture("heavyJavaCodeInsightFixture", testDataProjectDir) { context ->
        HeavyJavaCodeInsightFixture(context, testDataPath)
      }

    /**
     * Creates a sub-fixture of [HeavyJavaCodeInsightFixture] with [create], with a new project for each test.
     * The fixture calls `setUp` before the test and `tearDown` after it.
     *
     * By default, the project is new and empty. When [testDataProjectDir] is set, it gets the test name
     * without the `test` prefix and with a lowercase first letter, and returns a directory with an `.ipr` project.
     * The fixture opens a copy of this project, as a JUnit 3 test that overrides `setUpProject` with `loadAndOpenProject` does.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun <T : HeavyJavaCodeInsightFixture> heavyJavaCodeInsightSubFixture(
      debugName: String,
      testDataProjectDir: Function<String, String>? = null,
      create: (Context) -> T,
    ): TestFixture<T> =
      testFixture(debugName) { testContext ->
        val tempRoot = tempPathFixture().init()
        val extensionContext = testContext.extensionContext
        val testMethod = extensionContext.requiredTestMethod
        val projectDisposable = Disposer.newDisposable(debugName)
        val project = if (testDataProjectDir == null) {
          projectFixture(tempPathFixture(), openAfterCreation = true).init()
        }
        else {
          val projectDir = testDataProjectDir.apply(PlatformTestUtil.getTestName(testMethod.name, true))
          openTestDataProject(projectDir, tempPathFixture().init(), projectDisposable)
        }
        val context = Context(project, tempRoot, extensionContext.requiredTestClass, testMethod, testDataProjectDir != null)
        val fixture = create(context)
        fixture.setUp()
        initialized(fixture) {
          try {
            fixture.tearDown()
          }
          finally {
            runInEdtAndWait { Disposer.dispose(projectDisposable) }
          }
        }
      }

    private fun openTestDataProject(projectDir: String, dir: Path, disposable: Disposable): Project {
      val copyDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)) { dir.toString() }
      WriteAction.runAndWait<IOException> {
        val source = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(projectDir))) {
          "$projectDir not found"
        }
        source.refresh(false, true)
        VfsUtil.copyDirectory(null, source, copyDir, null)
      }
      val projectFile = checkNotNull(copyDir.children.find { FileTypeRegistry.getInstance().isFileOfType(it, ProjectFileType.INSTANCE) }) {
        "Can't find project file among " + copyDir.children.toList()
      }
      val project = PlatformTestUtil.loadAndOpenProject(projectFile.toNioPath(), disposable)
      // startup activities
      runInEdtAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
      check(project.isInitialized)
      return project
    }
  }
}
