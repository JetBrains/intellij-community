// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_CONTENT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY_SOURCE_ONLY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.FileBasedIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class ExcludePatternsInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)
  
  lateinit var contentRoot: VirtualFile
  lateinit var module: Module
  
  @BeforeEach
  fun setUp() {
    contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    module = projectModel.createModule()
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
  }

  @Test
  fun testExcludeFileFromLibrary() {
    /*
      root/      (library root)
        dir/
          a.txt  <- excluded by pattern
        a.txt    <- excluded by pattern
        A.java
     */
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val txt1 = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val txt2 = projectModel.baseProjectDir.newVirtualFile("root/dir/a.txt")
    val java = projectModel.baseProjectDir.newVirtualFile("root/A.java")
    registerLibrary(libraryRoot) { file: VirtualFile -> "a.txt".contentEquals(file.nameSequence) }
    fileIndex.assertScope(txt1, EXCLUDED)
    fileIndex.assertScope(txt2, EXCLUDED)
    fileIndex.assertScope(java, IN_LIBRARY or IN_SOURCE)
    assertIndexableContent(listOf(java), listOf(txt1, txt2))
  }

  private fun assertIndexableContent(mustContain: List<VirtualFile>?, mustNotContain: List<VirtualFile>?) {
    val collected = HashSet<VirtualFile>()
    FileBasedIndex.getInstance().iterateIndexableFiles({ fileOrDir: VirtualFile ->
        if (!collected.add(fileOrDir)) {
          return@iterateIndexableFiles Assertions.fail("$fileOrDir visited twice")
        }
        true
      }, projectModel.project, null)
    if (mustContain != null) UsefulTestCase.assertContainsElements(collected, mustContain)
    if (mustNotContain != null) UsefulTestCase.assertDoesntContain(collected, mustNotContain)
  }

  @Test
  fun testExcludeDirectoryFromLibrary() {
    /*
      root/      (library root)
        dir/     <- excluded directory
          a.txt
        a.txt
        A.java
     */
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val txt1 = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val txt2 = projectModel.baseProjectDir.newVirtualFile("root/dir/a.txt")
    val java = projectModel.baseProjectDir.newVirtualFile("root/A.java")
    registerLibrary(libraryRoot) { file: VirtualFile -> "dir".contentEquals(file.nameSequence) }
    fileIndex.assertScope(txt2, EXCLUDED)
    fileIndex.assertScope(txt1, IN_LIBRARY or IN_SOURCE)
    fileIndex.assertScope(java, IN_LIBRARY or IN_SOURCE)
    assertIndexableContent(listOf(java, txt1), listOf(txt2))
  }

  @Test
  fun testExcludeDirectoryFromLibraryThatIsUnderContentRoot() {
    /*
      content/         (content root)
        library/    (library root)
          dir/      <- excluded by pattern
            a.txt
          a.txt
          A.java
     */
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("content/library")
    val txt1 = projectModel.baseProjectDir.newVirtualFile("content/library/a.txt")
    val txt2 = projectModel.baseProjectDir.newVirtualFile("content/library/dir/a.txt")
    val java = projectModel.baseProjectDir.newVirtualFile("content/library/A.java")
    registerLibrary(libraryRoot) { file: VirtualFile -> "dir".contentEquals(file.nameSequence) }
    fileIndex.assertInModule(txt2, module, contentRoot, EXCLUDED)
    fileIndex.assertInModule(txt1, module, contentRoot, IN_CONTENT or IN_LIBRARY or IN_SOURCE or IN_LIBRARY_SOURCE_ONLY)
    fileIndex.assertInModule(java, module, contentRoot, IN_CONTENT or IN_LIBRARY or IN_SOURCE or IN_LIBRARY_SOURCE_ONLY)
    assertIndexableContent(listOf(java, txt1), listOf(txt2))
  }

  @Test
  fun testExcludeLibraryRoot() {
    /*
      root/  (library root)  <- excluded library root
        a.txt
        A.java
     */
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val txt = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val java = projectModel.baseProjectDir.newVirtualFile("root/A.java")
    registerLibrary(libraryRoot) { file: VirtualFile -> file == libraryRoot }
    fileIndex.assertScope(txt, EXCLUDED)
    fileIndex.assertScope(java, EXCLUDED)
  }

  @Test
  fun testExcludeLibraryRootThatIsUnderContentRoot() {
    /*
      content/       (content root)
        library/  (library root) <- excluded library root
          a.txt
          A.java
     */
    val myLibraryRoot = projectModel.baseProjectDir.newVirtualDirectory("content/library")
    val txt = projectModel.baseProjectDir.newVirtualFile("content/library/a.txt")
    val java = projectModel.baseProjectDir.newVirtualFile("content/library/A.java")
    registerLibrary(myLibraryRoot) { file: VirtualFile -> file == myLibraryRoot }
    fileIndex.assertInModule(txt, module, contentRoot, EXCLUDED)
    fileIndex.assertInModule(java, module, contentRoot, EXCLUDED)
    assertIndexableContent(null, listOf(txt, java))
  }

  @Test
  fun testExcludeOnlyFiles() {
    /*
      root/   (library root)
        dir/
        subdir/
          dir  (file that is named as directory)
     */
    val myLibraryRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val dir = projectModel.baseProjectDir.newVirtualDirectory("root/dir")
    val txt = projectModel.baseProjectDir.newVirtualFile("root/subdir/dir")
    registerLibrary(myLibraryRoot) { file: VirtualFile -> !file.isDirectory && "dir".contentEquals(file.nameSequence) }
    fileIndex.assertScope(txt, EXCLUDED)
    fileIndex.assertScope(dir, IN_LIBRARY or IN_SOURCE)
  }

  private fun registerLibrary(root: VirtualFile, excludePattern: Condition<in VirtualFile>?) {
    runWriteAction {
      ProjectRootManagerEx.getInstanceEx(projectModel.project).makeRootsChange(
        {
          AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(object : AdditionalLibraryRootsProvider() {
            override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
              return if (projectModel.project === project) listOf(
                SyntheticLibrary.newImmutableLibrary(listOf(root), emptyList<VirtualFile>(), emptySet<VirtualFile>(), excludePattern)
              )
              else emptyList()
            }
          }, projectModel.project)
        }, RootsChangeRescanningInfo.TOTAL_RESCAN)
    }
  }
}
