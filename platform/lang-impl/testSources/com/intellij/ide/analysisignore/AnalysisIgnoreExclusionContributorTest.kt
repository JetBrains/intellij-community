// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.ide.projectView.actions.UnmarkRootAction
import com.intellij.lang.LangBundle
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.ide.OptionalExclusionUtil
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.function.Consumer

private const val ENABLED = "ide.analysisignore.file.enabled"

private const val DEFAULTS = "ide.analysisignore.defaults.enabled"

/** The text that a new file at a root starts with. */
private val ROOT_DEFAULTS: String = AnalysisIgnoreDefaults.BUILT_IN_LINES.joinToString("\n", postfix = "\n")

/**
 * "Mark as Excluded" and "Cancel Exclusion" with a `.analysisignore` file as their store. The tests drive the real actions, and thus they
 * cover the exclusion contributor of the feature through [OptionalExclusionUtil], as the actions call it.
 */
@TestApplication
class AnalysisIgnoreExclusionContributorTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project get() = projectModel.project
  private val fileIndex get() = WorkspaceFileIndex.getInstance(project)
  private val service get() = AnalysisIgnoreService.getInstance(project)

  private lateinit var module: Module
  private lateinit var projectRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    module = projectModel.createModule()
    ModuleRootModificationUtil.addContentRoot(module, projectRoot)
    // Only a project root gets the defaults.
    val projectRootUrl = WorkspaceModel.getInstance(project).getVirtualFileUrlManager().storeAndGet(projectRoot.url)
    runBlocking { registerProjectRoot(project, projectRootUrl) }
    service.syncDefaultsBlocking()
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Mark as Excluded
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `marking a directory creates a file at the content root with a literal line`() = runBlocking {
    val buildDir = dir("projectRoot/build")

    invoke(MarkExcludeRootAction(), buildDir)

    assertEquals("/build/\n", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(buildDir))
    assertEquals(emptyList<String>(), excludeFolderUrls())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `marking a file writes a line without a trailing slash`() = runBlocking {
    val generated = file("projectRoot/gen/Foo.java")

    invoke(MarkExcludeRootAction(), generated)

    assertEquals("/gen/Foo.java\n", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(generated))
    assertTrue(isInContent(generated.parent))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the nearest file above the item takes the line`() = runBlocking {
    val rootFile = writeAnalysisIgnoreFile("projectRoot", "/other/")
    // No line break after the last line. The new line still gets a line of its own.
    val nearFile = writeAnalysisIgnoreFile("projectRoot/a", "/x/")
    discover(rootFile, nearFile)
    val cDir = dir("projectRoot/a/b/c")

    invoke(MarkExcludeRootAction(), cDir)

    assertEquals("/x/\n/b/c/\n", textOfIgnoreFile("projectRoot/a"))
    assertEquals("/other/", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(cDir))
    assertTrue(isInContent(cDir.parent))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a name with a wildcard character goes to the module exclude folder instead`() = runBlocking {
    assumeFalse(SystemInfoRt.isWindows, "The name is not a valid file name on Windows")
    val oddDir = dir("projectRoot/we?rd")

    invoke(MarkExcludeRootAction(), oddDir)

    assertNull(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME))
    assertEquals(listOf(oddDir.url), excludeFolderUrls())
    assertFalse(isInContent(oddDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a name that the format refuses goes to the module exclude folder instead`() = runBlocking {
    assumeFalse(SystemInfoRt.isWindows, "The name is not a valid file name on Windows")
    val oddDir = dir("projectRoot/x[1]")

    invoke(MarkExcludeRootAction(), oddDir)

    assertNull(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME))
    assertEquals(listOf(oddDir.url), excludeFolderUrls())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `nothing is written while the feature is switched off`() = runBlocking {
    val buildDir = dir("projectRoot/build")

    invoke(MarkExcludeRootAction(), buildDir)

    assertNull(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME))
    assertEquals(listOf(buildDir.url), excludeFolderUrls())
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Cancel Exclusion
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `cancel exclusion selects the line of the item and keeps the file as it is`(@TestDisposable disposable: Disposable) = runBlocking {
    val buildDir = dir("projectRoot/build")
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot", "/build/", "/out/")
    discover(ignoreFile)
    assertFalse(isInContent(buildDir))
    val notifications = collectNotifications(disposable)

    val presentation = presentationOf(UnmarkRootAction(), buildDir)
    assertTrue(presentation.isEnabledAndVisible)
    assertEquals(LangBundle.message("mark.as.unmark.excluded"), presentation.text)

    invoke(UnmarkRootAction(), buildDir)
    dispatchEvents()

    assertEquals("/build/\n/out/", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(buildDir))
    assertEquals("/build/", selectedTextIn(ignoreFile))
    assertEquals(emptyList<Pair<String, String>>(), notifications)
    closeEditorOf(ignoreFile)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `cancel exclusion after mark as excluded selects the new line`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    invoke(MarkExcludeRootAction(), buildDir)
    assertFalse(isInContent(buildDir))

    invoke(UnmarkRootAction(), buildDir)
    dispatchEvents()

    assertEquals("/build/\n", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(buildDir))
    val ignoreFile = projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME)!!
    assertEquals("/build/", selectedTextIn(ignoreFile))
    closeEditorOf(ignoreFile)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `cancel exclusion on an item inside an excluded directory keeps the line and opens it`() = runBlocking {
    val innerDir = dir("projectRoot/build/inner")
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot", "# comment", "/build/")
    discover(ignoreFile)
    assertFalse(isInContent(innerDir))

    assertTrue(presentationOf(UnmarkRootAction(), innerDir).isEnabledAndVisible)
    invoke(UnmarkRootAction(), innerDir)
    dispatchEvents()

    assertEquals("# comment\n/build/", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(innerDir))
    assertEquals("/build/", selectedTextIn(ignoreFile))
    closeEditorOf(ignoreFile)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `cancel exclusion is not offered while the feature is switched off`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    writeAnalysisIgnoreFile("projectRoot", "/build/")

    assertFalse(OptionalExclusionUtil.canCancelExclusion(project, buildDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Which line excludes a file
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the lookup names the literal line of the item`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    discover(writeAnalysisIgnoreFile("projectRoot", "/build/"))

    val exclusions = findAnalysisIgnoreExclusions(project, buildDir)

    assertEquals(listOf("/build/"), exclusions.map { it.pattern.source })
    assertEquals(buildDir, exclusions.single().excludedFile)
    assertEquals(projectRoot, exclusions.single().baseDir)
    assertEquals("build", exclusions.single().pattern.literalPath)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the lookup puts a line of the item before a line of a directory above it`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val note = file("projectRoot/build/note.txt")
    discover(writeAnalysisIgnoreFile("projectRoot", "/build/", "*.txt"))

    val exclusions = findAnalysisIgnoreExclusions(project, note)

    assertEquals(listOf("*.txt", "/build/"), exclusions.map { it.pattern.source })
    assertEquals(listOf(note, buildDir), exclusions.map { it.excludedFile })
    assertNull(exclusions[0].pattern.literalPath)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the lookup finds the lines of several files`() = runBlocking {
    val bDir = dir("projectRoot/a/b")
    discover(writeAnalysisIgnoreFile("projectRoot", "/a/b/"), writeAnalysisIgnoreFile("projectRoot/a", "/b/"))

    val exclusions = findAnalysisIgnoreExclusions(project, bDir)

    assertEquals(setOf(projectRoot.url to "/a/b/", bDir.parent.url to "/b/"), exclusions.mapTo(HashSet()) { it.baseDir.url to it.pattern.source })
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the lookup finds nothing for a file in content`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    discover(writeAnalysisIgnoreFile("projectRoot", "/build/"))

    assertEquals(emptyList<AnalysisIgnoreExclusion>(), findAnalysisIgnoreExclusions(project, srcDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The line and the file of a path
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `the line of a path is anchored and a directory ends with a slash`() {
    val buildDir = dir("projectRoot/build")
    val generated = file("projectRoot/gen/Foo.java")

    assertEquals("/build/", AnalysisIgnoreFileWriter.literalLineOf(buildDir, projectRoot))
    assertEquals("/gen/Foo.java", AnalysisIgnoreFileWriter.literalLineOf(generated, projectRoot))
    assertEquals("/Foo.java", AnalysisIgnoreFileWriter.literalLineOf(generated, generated.parent))
  }

  @Test
  fun `no line names the directory of the file itself or a name with a wildcard`() {
    assumeFalse(SystemInfoRt.isWindows, "The name is not a valid file name on Windows")
    val oddDir = dir("projectRoot/we?rd")
    val starDir = dir("projectRoot/a*b")
    val classDir = dir("projectRoot/x[1]")

    assertNull(AnalysisIgnoreFileWriter.literalLineOf(projectRoot, projectRoot))
    assertNull(AnalysisIgnoreFileWriter.literalLineOf(oddDir, projectRoot))
    assertNull(AnalysisIgnoreFileWriter.literalLineOf(starDir, projectRoot))
    assertNull(AnalysisIgnoreFileWriter.literalLineOf(classDir, projectRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the target directory is the nearest one with a file, or else the content root`() {
    val cDir = dir("projectRoot/a/b/c")
    val outside = dir("outside")
    assertEquals(projectRoot, AnalysisIgnoreFileWriter.targetBaseDirOf(project, cDir))

    writeAnalysisIgnoreFile("projectRoot/a")
    assertEquals(cDir.parent.parent, AnalysisIgnoreFileWriter.targetBaseDirOf(project, cDir))

    assertNull(AnalysisIgnoreFileWriter.targetBaseDirOf(project, outside))
  }

  @Test
  fun `the index of a line skips the comments and the blank lines`() {
    val text = "# comment\n\n/build/ \n*.log\n"

    assertEquals(2, AnalysisIgnoreFileWriter.lineIndexOf(text, "/build/"))
    assertEquals(3, AnalysisIgnoreFileWriter.lineIndexOf(text, "*.log"))
    assertEquals(-1, AnalysisIgnoreFileWriter.lineIndexOf(text, "# comment"))
    assertEquals(-1, AnalysisIgnoreFileWriter.lineIndexOf(text, "/out/"))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The default exclusions of a root
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  fun `every default line is a pattern that the format supports`() {
    for (line in AnalysisIgnoreDefaults.BUILT_IN_LINES) {
      assertEquals(AnalysisIgnoreValidated.Supported, AnalysisIgnorePattern.validate(line), line)
    }
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `marking a directory writes the default lines before the line of a new file and tells the user`(
    @TestDisposable disposable: Disposable,
  ) = runBlocking {
    val buildDir = dir("projectRoot/build")
    val notifications = collectNotifications(disposable)

    invoke(MarkExcludeRootAction(), buildDir)

    assertEquals("$ROOT_DEFAULTS/build/\n", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(buildDir))
    assertEquals(listOf("" to LangBundle.message("analysis.ignore.notification.defaults.copied", ANALYSIS_IGNORE_FILE_NAME)), notifications)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `the default lines of a new file exclude what they name`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val nodeModules = dir("projectRoot/sub/node_modules")
    val outDir = dir("projectRoot/out")
    val outBelow = dir("projectRoot/sub/out")

    invoke(MarkExcludeRootAction(), buildDir)

    assertFalse(isInContent(buildDir))
    assertFalse(isInContent(nodeModules))
    assertFalse(isInContent(outDir))
    assertTrue(isInContent(outBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a file that exists gets the line alone`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    discover(writeAnalysisIgnoreFile("projectRoot", "/other/"))

    invoke(MarkExcludeRootAction(), buildDir)

    assertEquals("/other/\n/build/\n", textOfIgnoreFile("projectRoot"))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a default line equal to the line is written once, as the line`() = runBlocking {
    // The defaults already exclude the directory, so the action offers no exclusion of it. The writer alone shows the case.
    withContext(Dispatchers.EDT) { AnalysisIgnoreFileWriter.appendLine(project, projectRoot, "/out/") }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val expected = buildList {
      addAll(AnalysisIgnoreDefaults.BUILT_IN_LINES)
      remove("/out/")
      add("/out/")
    }
    assertEquals(expected.joinToString("\n", postfix = "\n"), textOfIgnoreFile("projectRoot"))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new file below the root gets the line alone`() = runBlocking {
    val cDir = dir("projectRoot/a/b/c")
    val aDir = cDir.parent.parent
    val nodeModules = dir("projectRoot/node_modules")

    withContext(Dispatchers.EDT) { AnalysisIgnoreFileWriter.appendLine(project, aDir, "/b/c/") }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    // A file below the project root gets no default lines, and the defaults of the root stay in effect.
    assertEquals("/b/c/\n", textOfIgnoreFile("projectRoot/a"))
    assertFalse(isInContent(cDir))
    assertFalse(isInContent(nodeModules))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `cancel exclusion after mark as excluded selects the line below the default lines`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    invoke(MarkExcludeRootAction(), buildDir)

    invoke(UnmarkRootAction(), buildDir)
    dispatchEvents()

    assertEquals("$ROOT_DEFAULTS/build/\n", textOfIgnoreFile("projectRoot"))
    assertFalse(isInContent(buildDir))
    val ignoreFile = projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME)!!
    assertEquals("/build/", selectedTextIn(ignoreFile))
    closeEditorOf(ignoreFile)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `cancel exclusion of a name that the defaults exclude highlights its line on the Default Exclusions page`(
    @TestDisposable disposable: Disposable,
  ) = runBlocking {
    val nodeModules = dir("projectRoot/node_modules")
    assertFalse(isInContent(nodeModules))
    assertTrue(presentationOf(UnmarkRootAction(), nodeModules).isEnabledAndVisible)
    val notifications = collectNotifications(disposable)
    val settingsRequests = interceptSettingsDialog(disposable)

    invoke(UnmarkRootAction(), nodeModules)
    dispatchEvents()

    assertNull(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME), "No file appears in the project root")
    assertFalse(isInContent(nodeModules))
    assertEquals(listOf("node_modules"), settingsRequests.map(::highlightedDefaultExclusionOf))
    assertEquals(emptyList<Pair<String, String>>(), notifications)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `cancel exclusion of a root directory that the defaults exclude highlights its anchored line`(
    @TestDisposable disposable: Disposable,
  ) = runBlocking {
    val target = dir("projectRoot/target")
    val settingsRequests = interceptSettingsDialog(disposable)

    invoke(UnmarkRootAction(), target)
    dispatchEvents()

    assertNull(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME), "No file appears in the project root")
    assertFalse(isInContent(target))
    assertEquals(listOf("/target/"), settingsRequests.map(::highlightedDefaultExclusionOf))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `cancel exclusion is not offered for a default name while the defaults are off`() = runBlocking {
    val nodeModules = dir("projectRoot/node_modules")

    assertTrue(isInContent(nodeModules))
    assertTrue(findDefaultExclusions(project, nodeModules).isEmpty())
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------------------------------------------

  /** Runs [action] on [files] as the project view does, and waits for the index. */
  /** Returns the title and the content of each balloon of the project, in the order of the balloons, until [disposable] goes away. */
  private fun collectNotifications(disposable: Disposable): List<Pair<String, String>> {
    val notifications = ArrayList<Pair<String, String>>()
    project.messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(notification.title to notification.content)
      }
    })
    return notifications
  }

  /** Runs the events that the action queued after itself, as the line that "Cancel Exclusion" opens. */
  private suspend fun dispatchEvents() {
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
  }

  /** Returns the selected text of the editor of [file], or `null` while no text editor shows [file]. */
  private suspend fun selectedTextIn(file: VirtualFile): String? = withContext(Dispatchers.EDT) {
    (FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor)?.editor?.selectionModel?.selectedText
  }

  private suspend fun closeEditorOf(file: VirtualFile) {
    withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).closeFile(file) }
  }

  /**
   * Replaces the Settings dialog, which a headless test cannot show, until [disposable] goes away. Returns the Default Exclusions page of
   * each request, after the configuration of the request.
   */
  private fun interceptSettingsDialog(disposable: Disposable): List<AnalysisIgnoreDefaultsConfigurable> {
    val requests = ArrayList<AnalysisIgnoreDefaultsConfigurable>()
    val settingsUtil = object : ShowSettingsUtilImpl() {
      override fun <T : Configurable?> showSettingsDialog(project: Project?, configurableClass: Class<T>, additionalConfiguration: Consumer<in T>?) {
        assertEquals(AnalysisIgnoreDefaultsConfigurable::class.java, configurableClass)
        val configurable = configurableClass.getDeclaredConstructor().newInstance()
        additionalConfiguration?.accept(configurable)
        requests.add(configurable as AnalysisIgnoreDefaultsConfigurable)
      }
    }
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, settingsUtil, disposable)
    return requests
  }

  private suspend fun invoke(action: AnAction, vararg files: VirtualFile) {
    withContext(Dispatchers.EDT) { action.actionPerformed(eventOf(action, files)) }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }

  private suspend fun presentationOf(action: AnAction, vararg files: VirtualFile): Presentation {
    val event = eventOf(action, files)
    readAction { action.update(event) }
    return event.presentation
  }

  private fun eventOf(action: AnAction, files: Array<out VirtualFile>): AnActionEvent {
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .build()
    return AnActionEvent.createEvent(action, context, Presentation(), "", ActionUiKind.NONE, null)
  }

  /** Reads [files] and updates the entities at once, as the scanning of the project does. */
  private fun discover(vararg files: VirtualFile) {
    for (file in files) {
      service.applyNow(file)
    }
  }

  private suspend fun isInContent(file: VirtualFile): Boolean = readAction { fileIndex.isInContent(file) }

  private suspend fun excludeFolderUrls(): List<String> = readAction {
    ModuleRootManager.getInstance(module).contentEntries.flatMap { entry -> entry.excludeFolderUrls }
  }

  /** The text of the `.analysisignore` file in the directory at [relativeDirPath], as the disk holds it. */
  private fun textOfIgnoreFile(relativeDirPath: String): String {
    val ignoreFile = requireNotNull(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME")) {
      "No $ANALYSIS_IGNORE_FILE_NAME in $relativeDirPath"
    }
    return VfsUtilCore.loadText(ignoreFile)
  }

  private fun dir(relativePath: String): VirtualFile = projectModel.baseProjectDir.newVirtualDirectory(relativePath)

  private fun file(relativePath: String): VirtualFile = projectModel.baseProjectDir.newVirtualFile(relativePath)

  private fun writeAnalysisIgnoreFile(relativeDirPath: String, vararg lines: String): VirtualFile =
    projectModel.baseProjectDir.newVirtualFile("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME", lines.joinToString("\n").toByteArray())
}
