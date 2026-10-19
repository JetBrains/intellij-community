// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.ide.OptionalExclusionUtil
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ENABLED = "ide.analysisignore.file.enabled"

private const val DEFAULTS = "ide.analysisignore.defaults.enabled"

/**
 * The [default entity][AnalysisIgnoreDefaultEntitySource] of a project root without a `.analysisignore` file, and how a file in the root
 * directory removes it.
 */
@TestApplication
class AnalysisIgnoreDefaultExclusionsTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project get() = projectModel.project
  private val fileIndex get() = WorkspaceFileIndex.getInstance(project)
  private val service get() = AnalysisIgnoreService.getInstance(project)
  private val defaultsFile get() = AnalysisIgnoreDefaultsFile.getInstance()

  private lateinit var module: Module
  private lateinit var projectRoot: VirtualFile

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    defaultsFile.setPathForTests(projectModel.baseProjectDir.rootPath.resolve("config/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME"), disposable)
    projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    module = projectModel.createModule()
    ModuleRootModificationUtil.addContentRoot(module, projectRoot)
    runBlocking { registerProjectRoot(project, urlOf(projectRoot)) }
    service.syncDefaultsBlocking()
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a project root without a file holds a default entity with the default lines`() = runBlocking {
    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    // The default entity is not a file.
    assertEquals(emptySet<String>(), service.knownBaseDirUrls())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `the anywhere names are excluded at any depth and the root names at the root only`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")
    val targetBelow = dir("projectRoot/sub/target")
    val srcDir = dir("projectRoot/src")

    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
    assertTrue(isInContent(targetBelow))
    assertTrue(isInContent(srcDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `nothing is excluded while the feature is switched off`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")

    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertTrue(isInContent(yarnBelow))
    assertTrue(isInContent(targetAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "false")
  fun `nothing is excluded while the defaults are switched off`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")
    val buildDir = dir("projectRoot/build")

    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertTrue(isInContent(yarnBelow))
    assertTrue(isInContent(targetAtRoot))

    withContext(Dispatchers.EDT) { assertTrue(OptionalExclusionUtil.exclude(project, buildDir)) }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    assertEquals("/build/\n", textOfIgnoreFile("projectRoot"))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a file at the root removes the default entity and its removal brings it back`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")
    val customDir = dir("projectRoot/custom")
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot", "/custom/")

    service.applyNow(ignoreFile)

    // The update that adds the entity of the file also removes the default entity.
    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertFalse(isInContent(customDir))
    assertTrue(isInContent(yarnBelow))
    assertTrue(isInContent(targetAtRoot))

    // As the VFS listener does when the file goes away.
    service.forgetNow(projectRoot.url)

    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertTrue(isInContent(customDir))
    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `an empty file at the root removes the defaults`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")

    discover(writeAnalysisIgnoreFile("projectRoot"))

    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertTrue(isInContent(yarnBelow))
    assertTrue(isInContent(targetAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a file below the root adds to the defaults of the root`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")
    val customDir = dir("projectRoot/sub/custom")
    discover(writeAnalysisIgnoreFile("projectRoot/sub", "/custom/"))

    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
    assertFalse(isInContent(customDir))

    service.forgetNow(projectRoot.url + "/sub")

    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertTrue(isInContent(customDir))
    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `the file that Mark as Excluded creates keeps the defaults in effect`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")
    val targetBelow = dir("projectRoot/sub/target")

    withContext(Dispatchers.EDT) { assertTrue(OptionalExclusionUtil.exclude(project, buildDir)) }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val expected = AnalysisIgnoreDefaults.BUILT_IN_LINES + "/build/"
    assertEquals(expected.joinToString("\n", postfix = "\n"), textOfIgnoreFile("projectRoot"))
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, service.defaultLinesAddedTo(projectRoot.findChild(ANALYSIS_IGNORE_FILE_NAME)!!))
    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertFalse(isInContent(buildDir))
    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
    assertTrue(isInContent(targetBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `Mark as Excluded in a nested content root writes the line alone and keeps the defaults`() = runBlocking {
    val subRoot = dir("projectRoot/sub")
    val buildDir = dir("projectRoot/sub/build")
    val yarnElsewhere = dir("projectRoot/other/.yarn")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("sub"), subRoot)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    withContext(Dispatchers.EDT) { assertTrue(OptionalExclusionUtil.exclude(project, buildDir)) }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    // A file below the project root gets no default lines, and the defaults of the root stay in effect.
    assertEquals("/build/\n", textOfIgnoreFile("projectRoot/sub"))
    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertFalse(isInContent(buildDir))
    assertFalse(isInContent(yarnElsewhere))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a library below a default name keeps its files, also after Mark as Excluded`() = runBlocking {
    val libraryRoot = dir("projectRoot/.venv/lib")
    val otherInVenv = dir("projectRoot/.venv/other")
    val buildDir = dir("projectRoot/build")
    ModuleRootModificationUtil.addModuleLibrary(module, libraryRoot.url)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    assertTrue(isInLibrary(libraryRoot))
    assertFalse(isInContent(otherInVenv))

    withContext(Dispatchers.EDT) { assertTrue(OptionalExclusionUtil.exclude(project, buildDir)) }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    // The file now holds '.venv' as a line. A file set below the matching directory includes its files again.
    assertTrue(isInLibrary(libraryRoot))
    assertFalse(isInContent(otherInVenv))
    assertFalse(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a content root without a project root gets no defaults`() = runBlocking {
    val otherRoot = dir("other")
    val yarnBelow = dir("other/sub/.yarn")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("other"), otherRoot)
    service.syncDefaultsBlocking()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    assertEquals(setOf(projectRoot.url), defaultPatternsByRoot().keys)
    assertTrue(isInContent(yarnBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a project root added later gets its default entity without a scan`() = runBlocking {
    val attachedRoot = dir("attached")
    val yarnBelow = dir("attached/sub/.yarn")
    val srcDir = dir("attached/src")

    registerProjectRoot(project, urlOf(attachedRoot))

    // The service reacts to the new project root in the event log of the Workspace Model, and no file is read for it.
    withTimeout(10.seconds) {
      while (attachedRoot.url !in defaultPatternsByRoot()) delay(10.milliseconds)
    }
    assertFalse(isInContent(yarnBelow))
    assertTrue(isInContent(srcDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a scan gives a new project root its default entity before it starts`() = runBlocking {
    val attachedRoot = dir("attached")
    registerProjectRoot(project, urlOf(attachedRoot))

    AnalysisIgnoreIndexableFileScanner().startSession(project)

    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, defaultPatternsByRoot()[attachedRoot.url])
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new file at a project root without a module gets the root lines too`() = runBlocking {
    val attachedRoot = dir("attached")
    registerProjectRoot(project, urlOf(attachedRoot))
    service.syncDefaultsBlocking()

    withContext(Dispatchers.EDT) { AnalysisIgnoreFileWriter.appendLine(project, attachedRoot, "/build/") }

    val expected = AnalysisIgnoreDefaults.BUILT_IN_LINES + "/build/"
    assertEquals(expected.joinToString("\n", postfix = "\n"), textOfIgnoreFile("attached"))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new empty file at the root gets the default lines and keeps the defaults in effect`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val targetAtRoot = dir("projectRoot/target")

    // As New File does. The VFS listener queues the new file.
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot")
    service.awaitPendingFills()
    service.processNow()

    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES.joinToString("\n", postfix = "\n"), textOfIgnoreFile("projectRoot"))
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, service.defaultLinesAddedTo(ignoreFile))
    // The file replaces the default entity with the same lines.
    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertFalse(isInContent(yarnBelow))
    assertFalse(isInContent(targetAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new empty file below the root stays empty and keeps the defaults`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")

    val ignoreFile = writeAnalysisIgnoreFile("projectRoot/sub")
    service.awaitPendingFills()
    service.processNow()

    assertEquals("", textOfIgnoreFile("projectRoot/sub"))
    assertNull(service.defaultLinesAddedTo(ignoreFile))
    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertFalse(isInContent(yarnBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new empty file at the root gets the default lines while a file below the root exists`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    discover(writeAnalysisIgnoreFile("projectRoot/sub", "/custom/"))
    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertFalse(isInContent(yarnBelow))

    val rootFile = writeAnalysisIgnoreFile("projectRoot")
    service.awaitPendingFills()
    service.processNow()

    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES.joinToString("\n", postfix = "\n"), textOfIgnoreFile("projectRoot"))
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, service.defaultLinesAddedTo(rootFile))
    assertFalse(isInContent(yarnBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new file with text gets no default lines`() = runBlocking {
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot", "/custom/")
    service.awaitPendingFills()
    service.processNow()

    assertEquals("/custom/", textOfIgnoreFile("projectRoot"))
    assertNull(service.defaultLinesAddedTo(ignoreFile))
    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "false")
  fun `a new empty file stays empty while the defaults are off`() = runBlocking {
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot")
    service.awaitPendingFills()
    service.processNow()

    assertEquals("", textOfIgnoreFile("projectRoot"))
    assertNull(service.defaultLinesAddedTo(ignoreFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `an empty file that appears on disk gets no default lines and turns off the defaults`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")

    // As a checkout does. The empty file in the repository turns off the defaults on purpose.
    val path = projectRoot.toNioPath().resolve(ANALYSIS_IGNORE_FILE_NAME)
    Files.createFile(path)
    val ignoreFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    service.awaitPendingFills()
    service.processNow()

    assertEquals("", textOfIgnoreFile("projectRoot"))
    assertNull(service.defaultLinesAddedTo(ignoreFile))
    assertEquals(emptyMap<String, List<String>>(), defaultPatternsByRoot())
    assertTrue(isInContent(yarnBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a file in a directory that the defaults exclude leaves the defaults in place`() = runBlocking {
    val yarnBelow = dir("projectRoot/sub/.yarn")
    val nodeModules = dir("projectRoot/node_modules")
    val packageDir = dir("projectRoot/node_modules/pkg")

    // As a package that ships its own file. The file gets an entity, and the defaults hide its directory.
    writeAnalysisIgnoreFile("projectRoot/node_modules/pkg", "/x/")
    service.awaitPendingFills()
    service.processNow()

    assertEquals(setOf(packageDir.url), service.knownBaseDirUrls())
    assertEquals(mapOf(projectRoot.url to AnalysisIgnoreDefaults.BUILT_IN_LINES), defaultPatternsByRoot())
    assertFalse(isInContent(nodeModules))
    assertFalse(isInContent(yarnBelow))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `deleting a filled file forgets its default lines`() = runBlocking {
    val ignoreFile = writeAnalysisIgnoreFile("projectRoot")
    service.awaitPendingFills()
    service.processNow()
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, service.defaultLinesAddedTo(ignoreFile))

    writeAction { ignoreFile.delete(this@AnalysisIgnoreDefaultExclusionsTest) }

    assertNull(service.defaultLinesAddedTo(ignoreFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `edited default lines replace the patterns of the default entity`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val nodeModules = dir("projectRoot/node_modules")
    assertTrue(isInContent(buildDir))
    assertFalse(isInContent(nodeModules))

    writeDefaultsFile("/build/")
    // As the listener of the service does at once.
    service.processNow()

    assertEquals(mapOf(projectRoot.url to listOf("/build/")), defaultPatternsByRoot())
    assertFalse(isInContent(buildDir))
    assertTrue(isInContent(nodeModules))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a change of the default lines syncs the default entity without a call`() = runBlocking {
    writeDefaultsFile("/build/")

    withTimeout(10.seconds) {
      while (defaultPatternsByRoot() != mapOf(projectRoot.url to listOf("/build/"))) {
        delay(10.milliseconds)
      }
    }
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a new empty file at the root gets the edited default lines`() = runBlocking {
    writeDefaultsFile("/build/", "node_modules")
    service.processNow()

    val ignoreFile = writeAnalysisIgnoreFile("projectRoot")
    service.awaitPendingFills()
    service.processNow()

    assertEquals("/build/\nnode_modules\n", textOfIgnoreFile("projectRoot"))
    assertEquals(listOf("/build/", "node_modules"), service.defaultLinesAddedTo(ignoreFile))
  }

  @Test
  fun `without the defaults file the default lines are the built-in lines`() {
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, defaultsFile.lines)
  }

  @Test
  fun `the defaults file skips a comment, a blank line, and an unsupported pattern`() {
    writeDefaultsFile("# a comment", "", "node_modules", "!keep", "[ab]", "/build/  ")
    assertEquals(listOf("node_modules", "/build/"), defaultsFile.lines)
  }

  @Test
  fun `an empty defaults file holds no default lines`() {
    writeDefaultsFile()
    assertEquals(emptyList<String>(), defaultsFile.lines)
  }

  @Test
  fun `the template holds the built-in lines below a comment`() {
    val template = defaultsFile.template()
    assertTrue(template.startsWith("# "))
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, supportedPatternsOf("template", template))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `the defaults file in the VFS changes the default lines, and its deletion brings the built-in lines back`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("config/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME", "/build/\n".toByteArray())
    assertEquals(listOf("/build/"), defaultsFile.lines)
    awaitDefaultPatterns(listOf("/build/"))

    writeAction { file.delete(this@AnalysisIgnoreDefaultExclusionsTest) }
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, defaultsFile.lines)
    awaitDefaultPatterns(AnalysisIgnoreDefaults.BUILT_IN_LINES)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `a change of the defaults file outside the IDE changes the default lines after a VFS refresh`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("config/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME", "node_modules\n".toByteArray())
    assertEquals(listOf("node_modules"), defaultsFile.lines)

    Files.writeString(defaultsFile.path, "/build/\n")
    assertEquals(listOf("node_modules"), defaultsFile.lines)

    file.refresh(false, false)
    assertEquals(listOf("/build/"), defaultsFile.lines)
    awaitDefaultPatterns(listOf("/build/"))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `an unsaved edit of the defaults file changes the default lines`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("config/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME", "node_modules\n".toByteArray())
    val document = readAction { requireNotNull(FileDocumentManager.getInstance().getDocument(file)) }

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) { document.setText("/build/\n") }
    }

    assertEquals(listOf("/build/"), defaultsFile.lines)
    awaitDefaultPatterns(listOf("/build/"))
    assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
  }

  @Test
  fun `a read of an absent defaults file makes no refresh report its directory when it appears`(@TestDisposable disposable: Disposable) {
    // The VFS knows the directory, but not its children. A refresh of such a directory checks its known children and each name that a
    // search did not find.
    val settingsDir = projectModel.baseProjectDir.rootPath.resolve("settings")
    Files.createDirectories(settingsDir)
    val settingsVirtualDir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(settingsDir))
    defaultsFile.setPathForTests(settingsDir.resolve("options/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME"), disposable)
    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES, defaultsFile.lines)

    val reportedPaths = ArrayList<String>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        events.mapTo(reportedPaths) { it.path }
      }
    })
    Files.createDirectories(settingsDir.resolve("options"))
    settingsVirtualDir.refresh(false, false)

    assertEquals(emptyList<String>(), reportedPaths)
  }

  @Test
  fun `the defaults file is writable without the warning about a file outside the project`() {
    val file = projectModel.baseProjectDir.newVirtualFile("config/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME", ByteArray(0))
    val otherFile = projectModel.baseProjectDir.newVirtualFile("other/$ANALYSIS_IGNORE_DEFAULTS_FILE_NAME", ByteArray(0))
    assertTrue(EditAnalysisIgnoreDefaultsAction.AccessExtension().isWritable(file))
    assertFalse(EditAnalysisIgnoreDefaultsAction.AccessExtension().isWritable(otherFile))
  }

  /** The patterns of each [default entity][AnalysisIgnoreDefaultEntitySource], by the URL of its project root. */
  private fun defaultPatternsByRoot(): Map<String, List<String>> =
    WorkspaceModel.getInstance(project).currentSnapshot.defaultEntities().associate { it.baseDir.url to it.patterns }

  /** Waits until the default entity of the project root holds [patterns], as the listener of the service syncs it. */
  private suspend fun awaitDefaultPatterns(patterns: List<String>) {
    withTimeout(10.seconds) {
      while (defaultPatternsByRoot() != mapOf(projectRoot.url to patterns)) {
        delay(10.milliseconds)
      }
    }
  }

  /**
   * Writes [lines] into the defaults file past the VFS, and reads the file again. The action does the same after the dialog of the Welcome
   * screen.
   */
  private fun writeDefaultsFile(vararg lines: String) {
    val path = defaultsFile.path
    Files.createDirectories(path.parent)
    Files.writeString(path, lines.joinToString("\n"))
    defaultsFile.reload()
  }

  private suspend fun isInContent(file: VirtualFile): Boolean = readAction { fileIndex.isInContent(file) }

  private suspend fun isInLibrary(file: VirtualFile): Boolean = readAction { ProjectFileIndex.getInstance(project).isInLibrary(file) }

  /** Reads [files] and updates the entities at once, as the scanning of the project does. */
  private fun discover(vararg files: VirtualFile) {
    for (file in files) {
      service.applyNow(file)
    }
  }

  private fun urlOf(file: VirtualFile) = WorkspaceModel.getInstance(project).getVirtualFileUrlManager().storeAndGet(file.url)

  private fun textOfIgnoreFile(relativeDirPath: String): String {
    val ignoreFile = requireNotNull(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME")) {
      "No $ANALYSIS_IGNORE_FILE_NAME in $relativeDirPath"
    }
    return VfsUtilCore.loadText(ignoreFile)
  }

  private fun dir(relativePath: String): VirtualFile = projectModel.baseProjectDir.newVirtualDirectory(relativePath)

  private fun writeAnalysisIgnoreFile(relativeDirPath: String, vararg lines: String): VirtualFile =
    projectModel.baseProjectDir.newVirtualFile("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME", lines.joinToString("\n").toByteArray())
}
