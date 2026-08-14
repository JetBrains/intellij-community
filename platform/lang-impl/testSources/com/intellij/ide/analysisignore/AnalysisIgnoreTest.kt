// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.indexing.roots.ProjectIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.kind.SdkOrigin
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val ENABLED = "ide.analysisignore.file.enabled"

@TestApplication
class AnalysisIgnoreTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex get() = WorkspaceFileIndex.getInstance(projectModel.project)
  private val service get() = AnalysisIgnoreService.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var projectRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    module = projectModel.createModule()
    // A module content root is what makes the tree indexable content, and that is the content that the scanning of a project walks and
    // therefore the content in which AnalysisIgnoreIndexableFileScanner finds a file. Refer to the scanner for the reason that a
    // ProjectRootEntity alone is not enough.
    PsiTestUtil.addSourceContentToRoots(module, projectRoot)
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // What a .analysisignore file excludes
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a listed directory is not in content, its siblings are`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val srcDir = dir("projectRoot/src")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)

    assertFalse(isInContent(buildDir))
    assertTrue(isInContent(srcDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `nothing happens while the feature is switched off`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    writeAnalysisIgnoreFile("projectRoot", "build")

    visit(projectRoot)

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `an entry may name a path several levels deep`() = runBlocking {
    val nestedUnderSub = dir("projectRoot/sub/nested")
    val nestedAtRoot = dir("projectRoot/nested")
    val aDir = dir("projectRoot/a")
    val bDir = dir("projectRoot/a/b")
    val thirdDir = dir("projectRoot/a/b/third")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "sub/nested", "a/b/third")

    discover(excludeFile)

    assertFalse(isInContent(nestedUnderSub))
    assertFalse(isInContent(thirdDir))
    // Each entry is relative to the directory that holds the file, and it excludes only the path that it names. The directories above that
    // path stay in content, and a directory with the same name at the root stays in content.
    assertTrue(isInContent(nestedAtRoot))
    assertTrue(isInContent(aDir))
    assertTrue(isInContent(bDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file below the project root is read, and its entries are relative to its own directory`() = runBlocking {
    val nestedUnderSub = dir("projectRoot/sub/nested")
    val nestedAtRoot = dir("projectRoot/nested")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot/sub", "nested")

    discover(excludeFile)

    assertEquals(mapOf(excludeFile.parent.url to listOf("nested")), patternsByFile())
    assertFalse(isInContent(nestedUnderSub))
    // 'nested' names the child of the directory that holds the file, and not the child of the project root.
    assertTrue(isInContent(nestedAtRoot))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file at any depth is read`() = runBlocking {
    val deepDir = dir("projectRoot/a/b/c/d")
    val excludedDir = dir("projectRoot/a/b/c/d/gone")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot/a/b/c/d", "gone")

    discover(excludeFile)

    assertEquals(mapOf(deepDir.url to listOf("gone")), patternsByFile())
    assertFalse(isInContent(excludedDir))
    assertTrue(isInContent(deepDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `an entry of a nested file may name a path several levels deep`() = runBlocking {
    val bDir = dir("projectRoot/a/b")
    val cDir = dir("projectRoot/a/b/c")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot/a", "b/c")

    discover(excludeFile)

    assertFalse(isInContent(cDir))
    assertTrue(isInContent(bDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the entries of a root file and of a nested file are merged`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val outDir = dir("projectRoot/sub/out")
    val rootFile = writeAnalysisIgnoreFile("projectRoot", "build")
    val nestedFile = writeAnalysisIgnoreFile("projectRoot/sub", "out")

    discover(rootFile, nestedFile)

    assertEquals(setOf(buildDir.url, outDir.url), outOfContent(buildDir, outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a path which does not exist yet is excluded once it is created`() = runBlocking {
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "later")

    discover(excludeFile)

    val laterDir = dir("projectRoot/later")
    assertFalse(isInContent(laterDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a content root nested under an excluded directory is excluded as well`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    val barDir = dir("projectRoot/foo/bar")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "foo")
    PsiTestUtil.addContentRoot(module, barDir)

    discover(excludeFile)

    // The patterns of the file sit on the project root. The walk of getFileInfo stops at the content root 'bar' and then asks the project
    // root about the file. The match covers the whole path from the file up to the project root, and 'foo' is on that path.
    assertFalse(isInContent(fooDir))
    assertFalse(isInContent(barDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern of a path wins over a content root registered for the very same directory`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    PsiTestUtil.addContentRoot(module, fooDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "/foo")

    discover(excludeFile)

    // The condition sits on the project root. The walk of getFileInfo stops at the content root 'foo' and then asks the project root, and the
    // path 'foo' matches.
    assertFalse(isInContent(fooDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern of a name wins over a content root registered for the very same directory`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    PsiTestUtil.addContentRoot(module, fooDir)
    // 'foo' without a '/' matches at any level. The condition of the file tests it against the name of each path below the directory
    // that holds the file.
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "foo")

    discover(excludeFile)

    // The walk of getFileInfo stops at the content root of this directory, and isExcludedAbove then asks the project root about
    // the file. That question tests the name of the nested root itself before it stops there, and 'foo' matches it. The two spellings of one
    // directory thus agree: this is the answer of '/foo' above.
    assertFalse(isInContent(fooDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern excludes a source root of the very same name`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val mainFile = file("projectRoot/src/Main.java")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "src")

    discover(excludeFile)

    // The directory that a real project names 'src' is a source root, and a source root carries a file set of its own. 'src' has to reach
    // it all the same, or the spelling that a user writes first would do nothing while '/src' worked.
    assertEquals(setOf(srcDir.url, mainFile.url), outOfContent(srcDir, mainFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern excludes a content root of another module of the very same name`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    PsiTestUtil.addContentRoot(projectModel.createModule("other"), buildDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)

    // The root of another module counts as much as a root of this one: the index knows the file set, and not the module behind it.
    assertFalse(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern with a wildcard excludes a file inside a source root`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val log = file("projectRoot/src/a.log")
    val notes = file("projectRoot/src/notes.txt")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "*.log")

    discover(excludeFile)

    // The condition sits on the directory of the file alone, and isExcludedAbove is what carries it below the source root.
    assertEquals(setOf(log.url), outOfContent(srcDir, log, notes))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the index holds a file below a nested root as excluded and not as content of that root`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val log = file("projectRoot/src/a.log")
    val notes = file("projectRoot/src/notes.txt")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "*.log")

    discover(excludeFile)

    // The verdict of the index itself, and not the boolean that isInContent makes of it. The source root at 'src' is the nearest file set of
    // this file and the walk of getFileInfo stops there. The patterns sit on the directory that holds the file, one step above that root, and
    // isExcludedAbove is what reaches them. It drops the file set that the walk had already built and answers EXCLUDED.
    assertEquals(WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED, fileInfo(log))

    // What the assertion above rests on: the walk really does stop at the source root, and thus EXCLUDED can only have come from the second
    // walk above it. Without this, a file that no root of the workspace model covered at all would read the same.
    assertEquals(srcDir, fileInfo(log, honorExclusion = false).fileSets.single().root)

    // And what the patterns of the file take is one file below that root, and not the whole root.
    assertEquals(srcDir, fileInfo(notes).fileSets.single().root)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern excludes below a root that it does not name`() = runBlocking {
    val keepDir = dir("projectRoot/keep")
    val buildDir = dir("projectRoot/keep/build")
    PsiTestUtil.addContentRoot(module, keepDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)

    // The condition sits on the directory of the file alone. isExcludedAbove carries it below every root there.
    assertEquals(setOf(buildDir.url), outOfContent(keepDir, buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a directory-only pattern reaches a root below the file`() = runBlocking {
    val subDir = dir("projectRoot/sub")
    val outDir = dir("projectRoot/sub/out")
    val outFile = file("projectRoot/sub/out.txt")
    PsiTestUtil.addContentRoot(module, subDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "out/")

    discover(excludeFile)

    // A trailing slash asks whether the path is a directory. That condition sits on the directory of the file, and isExcludedAbove asks it
    // below the root at 'sub', and thus that root does not hide it.
    assertEquals(setOf(outDir.url), outOfContent(subDir, outDir, outFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a project root below the file is subject to the patterns`() = runBlocking {
    val attachedDir = dir("projectRoot/attached")
    registerProjectRoot(projectModel.project, urlOf(attachedDir))
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "attached")

    discover(excludeFile)

    // A ProjectRootEntity registers a file set of its own, and the walk of getFileInfo stops there as it does at a content root.
    // isExcludedAbove reaches the patterns above it all the same.
    assertFalse(isInContent(attachedDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // A root that the workspace model gains or loses after the file was read
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a content root that appears after the file was read is covered`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "foo")

    discover(excludeFile)
    assertFalse(isInContent(fooDir))

    // No discover here: nothing about the file changed. The patterns of the file have to reach the new content root all the same, or a
    // re-sync of a build system would quietly give the directory back. Nothing re-registers them for this root. They sit on the directory of
    // the file, and isExcludedAbove asks them about a file below the new root at the time of the question.
    PsiTestUtil.addContentRoot(module, fooDir)

    assertFalse(isInContent(fooDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a source root that appears after the file was read is covered`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "src")

    discover(excludeFile)
    assertFalse(isInContent(srcDir))

    PsiTestUtil.addSourceRoot(module, srcDir)

    assertFalse(isInContent(srcDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a content root under an excluded directory stays excluded when it goes away`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    val barDir = dir("projectRoot/foo/bar")
    PsiTestUtil.addContentRoot(module, barDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "foo")

    discover(excludeFile)
    // The nested content root is the nearest file set of this directory. The condition on the project root still finds 'foo' on the path.
    assertFalse(isInContent(barDir))

    PsiTestUtil.removeContentEntry(module, barDir)

    // Without that root the nearest file set is the project root, and the condition finds 'foo' the same way.
    assertFalse(isInContent(barDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a source root under an excluded directory stays excluded when it goes away`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    val barDir = dir("projectRoot/foo/bar")
    PsiTestUtil.addSourceRoot(module, barDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "foo")

    discover(excludeFile)
    assertFalse(isInContent(barDir))

    PsiTestUtil.removeSourceRoot(module, barDir)

    assertFalse(isInContent(barDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a content root that moves below the file carries its patterns to the new directory`() = runBlocking {
    val fromDir = dir("projectRoot/from")
    val toDir = dir("projectRoot/to")
    val logsDir = dir("projectRoot/to/logs")
    PsiTestUtil.addContentRoot(module, fromDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "logs")

    discover(excludeFile)
    assertFalse(isInContent(logsDir))

    // One change with two sides, which is what a re-sync of a build system makes: the root leaves 'from' and appears at 'to'.
    moveContentRoot(fromDir, toDir)

    // 'to' is the nearest file set of 'logs' now, and isExcludedAbove asks the directory above it about the file. The move
    // therefore needs no new registration of the patterns.
    assertFalse(isInContent(logsDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file deleted after a source root below it went away leaves nothing excluded`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val logsDir = dir("projectRoot/src/logs")
    val controlDir = dir("projectRoot/logs")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "logs")

    discover(excludeFile)
    // The source root is the nearest file set of this directory, and isExcludedAbove asks the project root about it.
    assertFalse(isInContent(logsDir))
    assertFalse(isInContent(controlDir))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertFalse(isInContent(logsDir))

    applyVfsEvents(VFileDeleteEvent(this@AnalysisIgnoreTest, excludeFile))
    // Really gone, and not as an event alone: the change of the root above triggered a re-scan of the project, that re-scan queues every
    // .analysisignore file that it meets, and a file that is still on disk would put its entity straight back.
    writeAction { excludeFile.delete(this@AnalysisIgnoreTest) }
    service.processNow()

    // Nothing of the file may be left anywhere. The file put nothing on the source root, and thus the removal of its entity takes every
    // exclusion that the file declared.
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    // The directory that the file named directly, which tells an exclusion left behind apart from an entity that never went away.
    assertTrue(isInContent(controlDir))
    assertTrue(isInContent(logsDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `module exclude roots stay empty, so no iml file is touched`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)

    assertFalse(isInContent(buildDir))
    assertTrue(readAction { ModuleRootManager.getInstance(module).excludeRoots }.isEmpty())
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The format of the patterns
  // -----------------------------------------------------------------------------------------------------------------------------------
  //
  // AnalysisIgnorePatternTest holds the format itself.
  // These tests are about what each shape of a pattern excludes once the condition of the file holds it: a path, a name, a pattern with a
  // wildcard, and a directory-only pattern.

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern without a slash excludes at any level`() = runBlocking {
    val atRoot = dir("projectRoot/node_modules")
    val deep = dir("projectRoot/a/b/node_modules")
    val aDir = projectRoot.findFileByRelativePath("a")!!
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "node_modules")

    discover(excludeFile)

    assertEquals(setOf(atRoot.url, deep.url), outOfContent(atRoot, deep, aDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern with a wildcard excludes the files that it names`() = runBlocking {
    val log = file("projectRoot/a.log")
    val deepLog = file("projectRoot/sub/b.log")
    val text = file("projectRoot/notes.txt")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "*.log")

    discover(excludeFile)

    assertEquals(setOf(log.url, deepLog.url), outOfContent(log, deepLog, text))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a line that the format leaves out changes nothing`() = runBlocking {
    val log = file("projectRoot/a.log")
    val keptLog = file("projectRoot/keep.log")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "*.log", "!keep.log")

    discover(excludeFile)

    // This format has no negation, and thus the '!keep.log' line says nothing and '*.log' takes both files.
    assertEquals(setOf(log.url, keptLog.url), outOfContent(log, keptLog))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a directory-only pattern leaves a file of the same name in content`() = runBlocking {
    val outDir = dir("projectRoot/a/out")
    val outFile = file("projectRoot/b/out")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "out/")

    discover(excludeFile)

    assertEquals(setOf(outDir.url), outOfContent(outDir, outFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern of a path and a pattern with a wildcard reach the index together`() = runBlocking {
    // One condition holds both patterns: '/dist' names one path, '*.log' names a name.
    val dist = dir("projectRoot/dist")
    val nestedDist = dir("projectRoot/sub/dist")
    val log = file("projectRoot/sub/a.log")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "/dist", "*.log")

    discover(excludeFile)

    assertEquals(setOf(dist.url, log.url), outOfContent(dist, nestedDist, log))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a path, a name and a directory-only pattern reach the index together`() = runBlocking {
    val dist = dir("projectRoot/dist")
    val nestedDist = dir("projectRoot/sub/dist")
    val buildAtRoot = dir("projectRoot/build")
    val buildDeep = dir("projectRoot/a/b/build")
    val outDir = dir("projectRoot/c/out")
    val outFile = file("projectRoot/d/out")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "/dist", "build", "out/")

    discover(excludeFile)

    // '/dist' names one path and thus only the one at the top. 'build' names a name and thus one at any level. 'out/' asks whether the
    // path is a directory.
    assertEquals(
      setOf(dist.url, buildAtRoot.url, buildDeep.url, outDir.url),
      outOfContent(dist, nestedDist, buildAtRoot, buildDeep, outDir, outFile),
    )
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a pattern of a name excludes a directory inside a source root`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val logsDir = dir("projectRoot/src/logs")
    val mainFile = file("projectRoot/src/Main.java")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "logs")

    discover(excludeFile)

    // A source root carries a file set of its own and the walk of getFileInfo stops there. This pattern only reaches the directory below it
    // because isExcludedAbove asks the directory that holds the file about that directory.
    assertEquals(setOf(logsDir.url), outOfContent(srcDir, logsDir, mainFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `editing the file changes what a source root below it excludes`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val buildDir = dir("projectRoot/src/build")
    val outDir = dir("projectRoot/src/out")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)
    assertEquals(setOf(buildDir.url), outOfContent(buildDir, outDir))

    writeAction { VfsUtil.saveText(excludeFile, "out") }
    discover(excludeFile)

    // The patterns of the file sit on the directory that holds it, and isExcludedAbove carries them below this source root. One
    // registration answers for both places, and thus a change of the file reaches them in one place.
    assertEquals(setOf(outDir.url), outOfContent(buildDir, outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `deleting the file frees a path inside a source root below it`() = runBlocking {
    val srcDir = dir("projectRoot/src")
    val buildDir = dir("projectRoot/src/build")
    PsiTestUtil.addSourceRoot(module, srcDir)
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)
    assertFalse(isInContent(buildDir))

    applyVfsEvents(VFileDeleteEvent(this@AnalysisIgnoreTest, excludeFile))
    service.processNow()

    // The exclusions of a file sit on the directory that holds it, and the entity of that file owns them there. Once that entity is gone the
    // index unregisters them from that one directory, and the source root below it holds nothing of the file to leave behind.
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(srcDir))
    assertTrue(isInContent(buildDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // A file inside an excluded directory
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file inside an excluded directory changes nothing that the index can see`() = runBlocking {
    val bigDir = dir("projectRoot/big")
    val keepDir = dir("projectRoot/big/keep")
    val rootFile = writeAnalysisIgnoreFile("projectRoot", "big")
    val innerFile = writeAnalysisIgnoreFile("projectRoot/big", "keep")

    discover(rootFile, innerFile)

    // Each file gets an entity of its own, and the one inside 'big' puts a condition on a directory that 'big' already excludes. The
    // index resolves a file from the bottom up, and thus that condition changes nothing: it costs its own registration and no more.
    assertFalse(isInContent(bigDir))
    assertFalse(isInContent(keepDir))
    assertEquals(mapOf(projectRoot.url to listOf("big"), bigDir.url to listOf("keep")), patternsByFile())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a content root under an excluded directory is excluded with everything inside`() = runBlocking {
    val fooDir = dir("projectRoot/foo")
    val barDir = dir("projectRoot/foo/bar")
    val goneDir = dir("projectRoot/foo/bar/gone")
    PsiTestUtil.addContentRoot(module, barDir)
    val rootFile = writeAnalysisIgnoreFile("projectRoot", "foo")
    val innerFile = writeAnalysisIgnoreFile("projectRoot/foo/bar", "gone")

    discover(rootFile, innerFile)

    // 'foo' on the project root excludes the whole directory, as git does. The file in the content root inside it changes nothing more.
    assertFalse(isInContent(fooDir))
    assertFalse(isInContent(barDir))
    assertFalse(isInContent(goneDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the order in which the files are found does not matter`() = runBlocking {
    val bigDir = dir("projectRoot/big")
    val keepDir = dir("projectRoot/big/keep")
    val rootFile = writeAnalysisIgnoreFile("projectRoot", "big")
    val innerFile = writeAnalysisIgnoreFile("projectRoot/big", "keep")

    // The scanning of a project walks on several threads and in no fixed order, and thus the deep file can be read and written before the
    // shallow one. One entity says what one file says, and so the result is the same either way. One call for each file here, because the
    // queue of one run is a set and would decide the order itself.
    discover(innerFile)
    discover(rootFile)
    val deepFirst = outOfContent(bigDir, keepDir)

    service.scheduleChanges(subtreeUrls = listOf(projectRoot.url))
    service.processNow()
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())

    discover(rootFile)
    discover(innerFile)

    assertEquals(setOf(bigDir.url, keepDir.url), deepFirst)
    assertEquals(deepFirst, outOfContent(bigDir, keepDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The narrowed coverage: only the content that the IDE indexes
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a project root without a module content root is not scanned`() = runBlocking {
    val attachedRoot = dir("attached")
    dir("attached/out")
    registerProjectRoot(projectModel.project, urlOf(attachedRoot))

    // A ProjectRootEntity registers a CONTENT_NON_INDEXABLE file set, IndexingIteratorsProviderImpl creates no iterator for such a file
    // set, and thus the scanning of the project never hands this directory to a scanner. A .analysisignore file here has no effect. This is
    // the coverage that this feature gave up when it stopped walking the project on its own.
    assertFalse(readAction { fileIndex.isIndexable(attachedRoot) })
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file under a module content root is read without any project root`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    // No registerProjectRoot anywhere in this test. Discovery follows the indexable content of the project, and a module content root is
    // enough.
    discover(excludeFile)

    assertEquals(mapOf(projectRoot.url to listOf("build")), patternsByFile())
    assertFalse(isInContent(buildDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The scanner
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the scanner visits the content of the project and nothing else`() {
    val session = AnalysisIgnoreIndexableFileScanner().startSession(projectModel.project)

    assertNotNull(session.createVisitor(object : ContentOrigin {}))
    // A library, an SDK or a jar must not exclude anything, and a visitor for such an origin would add the test of the name to every file
    // of every dependency of the project.
    assertNull(session.createVisitor(object : LibraryOrigin {
      override val classRoots: List<VirtualFile> get() = emptyList()
      override val sourceRoots: List<VirtualFile> get() = emptyList()
    }))
    assertNull(session.createVisitor(object : SdkOrigin {
      override val rootsToIndex: Collection<VirtualFile> get() = emptyList()
    }))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `the scanner creates no visitor while the feature is switched off`() {
    val session = AnalysisIgnoreIndexableFileScanner().startSession(projectModel.project)

    // The visitor runs for every file of the project, and thus a switched-off feature must cost nothing at all.
    assertNull(session.createVisitor(object : ContentOrigin {}))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `finding the same file again takes no write action`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")
    discover(excludeFile)

    val beforeTheSecondRead = snapshot()
    service.applyNow(excludeFile)

    // A project is scanned again and again, and thus a file that says what its entity says already must cost no write action at all.
    assertSame(beforeTheSecondRead, snapshot())
    assertFalse(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the walk of the scanning does not enter an excluded directory`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val deepFile = file("projectRoot/build/generated/Deep.java")
    val srcFile = file("projectRoot/src/Kept.java")
    writeAnalysisIgnoreFile("projectRoot", "build")

    val visited = scanContentOf(projectRoot)

    // This is the point of the whole change. The walk asks the index about each child of a directory, and the exclusions of the file are
    // in the index by then, on the same pass and with no second scan.
    assertFalse(visited.contains(buildDir))
    assertFalse(visited.contains(deepFile))
    assertTrue(visited.contains(srcFile))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `visiting a directory reads the file that the directory holds`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    writeAnalysisIgnoreFile("projectRoot", "build")

    visit(projectRoot)

    // No processNow above. The directory is the trigger, and the write is over before the visit returns.
    assertEquals(mapOf(projectRoot.url to listOf("build")), patternsByFile())
    assertFalse(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `visiting the file itself changes nothing`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    visit(excludeFile)

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a directory that lost its file loses its exclusions during the scan`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    givenCachedEntities(projectRoot to listOf("build"))
    assertFalse(isInContent(buildDir))

    // No file on the disk: a user took it away while the IDE was closed. The entity of the session before says what nothing says now.
    visit(projectRoot)

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `one session answers for a directory once`() = runBlocking {
    dir("projectRoot/build")
    givenCachedEntities(projectRoot to listOf("build"))
    val visitors = visitorsOf()

    // No file on the disk, and thus the first visit takes the entity of the session before away.
    visitWith(projectRoot, visitors)
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())

    // The entity comes back, and nothing else changes. The providers of the scanning walk in parallel and two of them may meet one
    // directory, and thus a second visit must cost nothing.
    givenCachedEntities(projectRoot to listOf("build"))
    val afterTheEntityCameBack = snapshot()
    visitWith(projectRoot, visitors)

    // A write builds a new snapshot. The same snapshot therefore says that the second visit wrote nothing.
    assertSame(afterTheEntityCameBack, snapshot())
    assertEquals(mapOf(projectRoot.url to listOf("build")), patternsByFile())
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Changes to a file
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `editing the file changes which paths are excluded`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val outDir = dir("projectRoot/out")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)
    assertFalse(isInContent(buildDir))
    assertTrue(isInContent(outDir))

    writeAction { VfsUtil.saveText(excludeFile, "out") }
    discover(excludeFile)

    assertTrue(isInContent(buildDir))
    assertFalse(isInContent(outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `an unsaved change in the editor already changes which paths are excluded`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val outDir = dir("projectRoot/out")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)
    assertFalse(isInContent(buildDir))
    assertTrue(isInContent(outDir))

    setEditorText(excludeFile, "out")
    discover(excludeFile)

    // No save above on purpose. The user sees "out" in the editor, and the exclusions follow that text and not the text on the disk.
    assertEquals("build", VfsUtilCore.loadText(excludeFile))
    assertTrue(isInContent(buildDir))
    assertFalse(isInContent(outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `an emptied editor buffer removes the exclusions`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    discover(excludeFile)
    assertFalse(isInContent(buildDir))

    setEditorText(excludeFile, "")
    discover(excludeFile)

    // A file that gives no pattern leaves no entity, and the entity from before it must not stay.
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `deleting the file removes its exclusions`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")
    discover(excludeFile)
    assertFalse(isInContent(buildDir))

    applyVfsEvents(VFileDeleteEvent(this@AnalysisIgnoreTest, excludeFile))
    service.processNow()

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `deleting the directory that holds the file removes its exclusions`() = runBlocking {
    val subDir = dir("projectRoot/sub")
    val outDir = dir("projectRoot/sub/out")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot/sub", "out")
    discover(excludeFile)
    assertFalse(isInContent(outDir))

    // The VFS reports one event for the directory and none for the file below it. A listener that only matched the name would keep the
    // exclusion of a file that no longer exists.
    applyVfsEvents(VFileDeleteEvent(this@AnalysisIgnoreTest, subDir))
    service.processNow()

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `renaming the file away removes its exclusions`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")
    discover(excludeFile)
    assertFalse(isInContent(buildDir))

    applyVfsEvents(
      VFilePropertyChangeEvent(this@AnalysisIgnoreTest, excludeFile, VirtualFile.PROP_NAME, ANALYSIS_IGNORE_FILE_NAME, "notes.txt")
    )
    service.processNow()

    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `renaming a file to the ignore name asks for a read of it`() = runBlocking {
    val plainFile = projectModel.baseProjectDir.newVirtualFile("projectRoot/notes.txt", "build".toByteArray())

    val changes = collectAnalysisIgnoreVfsChanges(
      listOf(VFilePropertyChangeEvent(this@AnalysisIgnoreTest, plainFile, VirtualFile.PROP_NAME, "notes.txt", ANALYSIS_IGNORE_FILE_NAME))
    )

    assertEquals(1, changes!!.readEvents.size)
    assertEquals(emptyList<String>(), changes.forgetBaseDirUrls)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `moving the file forgets the directory that held it and asks for a read of the new one`() {
    val otherDir = dir("projectRoot/other")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    val changes = collectAnalysisIgnoreVfsChanges(listOf(VFileMoveEvent(this, excludeFile, otherDir)))

    // The entries of the file are relative to the directory that holds it, and thus a move changes what they mean: the old directory loses
    // its entity, and the file is read again in its new place.
    assertEquals(listOf(projectRoot.url), changes!!.forgetBaseDirUrls)
    assertEquals(1, changes.readEvents.size)
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // The state that survives a restart
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the entities of the session before are the state of this session`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val outDir = dir("projectRoot/sub/out")
    val subDir = projectRoot.findFileByRelativePath("sub")!!
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")
    // The IDE reads a file as soon as it sees one appear, and such a read can still find it empty. Settle that here: this test is about the
    // session before and not about the creation of a file.
    discover(excludeFile)

    givenCachedEntities(projectRoot to listOf("build"), subDir to listOf("out"))
    val afterTheCachedEntities = snapshot()

    // The entities are the whole state of the service, and thus a session that starts with them reads nothing back. The exclusions of the
    // session before are in the index before the first scan of this one, and the file that that scan finds says what its entity says.
    service.applyNow(excludeFile)
    assertSame(afterTheCachedEntities, snapshot())

    // The entity of the file that this session never read is still there, and it still excludes what it excluded.
    assertEquals(setOf(buildDir.url, outDir.url), outOfContent(buildDir, outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `an entity of the session before can be forgotten, and only its own roots go`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    val outDir = dir("projectRoot/sub/out")
    val subDir = projectRoot.findFileByRelativePath("sub")!!
    givenCachedEntities(projectRoot to listOf("build"), subDir to listOf("out"))

    // This is what the directory in the entity buys: the IDE knows which exclusions came from the file that went away.
    service.scheduleChanges(baseDirUrls = listOf(subDir.url))
    service.processNow()

    assertEquals(setOf(buildDir.url), outOfContent(buildDir, outDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `a switched-off feature registers no exclusion for the entities of the session before`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    givenCachedEntities(projectRoot to listOf("build"))

    assertTrue(isInContent(buildDir))
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `switching the feature off removes the entities of the session before`() = runBlocking {
    val buildDir = dir("projectRoot/build")
    givenCachedEntities(projectRoot to listOf("build"))

    service.processNow()

    // The key needs a restart of the IDE, and the entities of the session before must not outlive it.
    assertEquals(emptyMap<String, List<String>>(), patternsByFile())
    assertTrue(isInContent(buildDir))
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Reading a file
  // -----------------------------------------------------------------------------------------------------------------------------------

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file is read without the read lock`() = runBlocking {
    dir("projectRoot/build")
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build")

    // No readAction here on purpose. The read must hold no lock, and @RequiresReadLock on this path would assert.
    withContext(Dispatchers.IO) { service.applyNow(excludeFile) }

    // The patterns say that the read ran and that it wrote.
    assertEquals(mapOf(projectRoot.url to listOf("build")), patternsByFile())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `the read of a file leaves out the lines that the format does not hold`() = runBlocking {
    val excludeFile = writeAnalysisIgnoreFile("projectRoot", "build", "!keep", "[a-z].txt")

    discover(excludeFile)

    // The entity holds the patterns that this format holds, and no other. The read is the one place that names the others in the log.
    assertEquals(mapOf(projectRoot.url to listOf("build")), patternsByFile())
  }

  // -----------------------------------------------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------------------------------------------

  /** Runs on [fileOrDir] exactly what the scanning of the project runs on each file and directory that it walks. */
  private suspend fun visit(fileOrDir: VirtualFile) = visitWith(fileOrDir, visitorsOf())

  /** [visit] with the visitors of a session that the caller keeps, which is what one scanning of the project does. */
  private suspend fun visitWith(fileOrDir: VirtualFile, visitors: List<IndexableFileScanner.IndexableFileVisitor>) {
    withContext(Dispatchers.IO) { PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, visitors) }
  }

  /**
   * The files and the directories that the walk of the scanning visits below [root], in the order of the walk.
   *
   * The body of the iterator repeats [com.intellij.util.indexing.UnindexedFilesScanner]: it hands each file to the scanners and only then
   * takes it. An excluded directory never reaches it, because the index answers about that directory first.
   */
  private suspend fun scanContentOf(root: VirtualFile): List<VirtualFile> = withContext(Dispatchers.IO) {
    val visitors = visitorsOf()
    val visited = ArrayList<VirtualFile>()
    ProjectIndexableFilesIteratorImpl(root).iterateFiles(projectModel.project, ContentIterator { fileOrDir ->
      PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, visitors)
      visited.add(fileOrDir)
      true
    }, IndexableFilesDeduplicateFilter.create())
    visited
  }

  /** The visitors of one session of the scanner, as [com.intellij.util.indexing.UnindexedFilesScanner] builds them for one origin. */
  private fun visitorsOf(): List<IndexableFileScanner.IndexableFileVisitor> {
    val session = AnalysisIgnoreIndexableFileScanner().startSession(projectModel.project)
    return listOfNotNull(session.createVisitor(object : ContentOrigin {}))
  }

  /** Reads [files] and updates the entities at once, as the scanning of the project does. */
  private suspend fun discover(vararg files: VirtualFile) {
    for (file in files) {
      service.applyNow(file)
    }
  }

  /** Runs on [events] exactly what [AnalysisIgnoreVfsListener] runs on the events of a real change. */
  private fun applyVfsEvents(vararg events: VFileEvent) {
    val changes = requireNotNull(collectAnalysisIgnoreVfsChanges(events.toList())) { "The events mean nothing to the feature" }
    changes.applyTo(service)
  }

  /** Puts the entities that the cache of the Workspace Model would hold at the start of a session into the project. */
  private suspend fun givenCachedEntities(vararg files: Pair<VirtualFile, List<String>>) {
    val storage = MutableEntityStorage.create()
    for ((baseDir, patterns) in files) {
      storage.addEntity(AnalysisIgnoreEntity(urlOf(baseDir), patterns, AnalysisIgnoreEntitySource))
    }
    WorkspaceModel.getInstance(projectModel.project).update("Test: the entities of the session before") {
      it.replaceBySource({ source -> source === AnalysisIgnoreEntitySource }, storage)
    }
  }

  /**
   * Moves the content root at [from] to [to] in one change of the workspace model, which is the shape that a re-sync of a build system
   * makes: one `Replaced` change whose two sides name two directories.
   */
  private suspend fun moveContentRoot(from: VirtualFile, to: VirtualFile) {
    WorkspaceModel.getInstance(projectModel.project).update("Test: a content root moves") { builder ->
      val entity = builder.entities(ContentRootEntity::class.java).first { it.url == urlOf(from) }
      builder.modifyContentRootEntity(entity) { url = urlOf(to) }
    }
  }

  private suspend fun isInContent(file: VirtualFile): Boolean = readAction { fileIndex.isInContent(file) }

  /** Returns the URLs of those of [files] that the index no longer holds as content. */
  private suspend fun outOfContent(vararg files: VirtualFile): Set<String> = readAction {
    files.filterNot { fileIndex.isInContent(it) }.mapTo(HashSet()) { it.url }
  }

  /**
   * The verdict of the index for [file] as [WorkspaceFileIndexEx.getFileInfo] gives it: the nearest file set of the file, or the reason that
   * there is none. Every kind is asked for, and thus EXCLUDED here means out of the index altogether and not out of one kind of it.
   */
  private suspend fun fileInfo(file: VirtualFile, honorExclusion: Boolean = true): WorkspaceFileInternalInfo = readAction {
    WorkspaceFileIndexEx.getInstance(projectModel.project).getFileInfo(
      file,
      honorExclusion = honorExclusion,
      includeContentSets = true,
      includeContentNonIndexableSets = true,
      includeExternalSets = true,
      includeExternalSourceSets = true,
      includeExternalNonIndexableSets = true,
      includeCustomKindSets = true,
    )
  }

  /** The snapshot of the Workspace Model now. A write builds a new snapshot, and thus the same one says that nothing wrote. */
  private fun snapshot() = WorkspaceModel.getInstance(projectModel.project).currentSnapshot

  /** Returns the patterns of each `.analysisignore` file as its entity holds them, by the directory that holds that file. */
  private suspend fun patternsByFile(): Map<String, List<String>> = readAction {
    WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(AnalysisIgnoreEntity::class.java)
      .associate { entity -> entity.baseDir.url to entity.patterns }
  }

  private fun urlOf(file: VirtualFile): VirtualFileUrl =
    WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager().getOrCreateFromUrl(file.url)

  private fun dir(relativePath: String): VirtualFile = projectModel.baseProjectDir.newVirtualDirectory(relativePath)

  private fun file(relativePath: String): VirtualFile = projectModel.baseProjectDir.newVirtualFile(relativePath)

  private fun writeAnalysisIgnoreFile(relativeDirPath: String, vararg lines: String): VirtualFile =
    projectModel.baseProjectDir.newVirtualFile("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME", lines.joinToString("\n").toByteArray())

  /**
   * Changes the text of [file] in the editor and does not save it. [FileDocumentManager] then holds a document with an unsaved change.
   *
   * `edtWriteAction` and not `writeAction`: [com.intellij.openapi.editor.Document.setText] runs a command, and a command needs the EDT.
   */
  private suspend fun setEditorText(file: VirtualFile, text: String) {
    edtWriteAction {
      val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file)) { "No document for ${file.presentableUrl}" }
      document.setText(text)
    }
  }
}
