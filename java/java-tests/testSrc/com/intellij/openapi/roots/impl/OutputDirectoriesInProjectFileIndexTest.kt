// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class OutputDirectoriesInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private val compilerProjectExtension: CompilerProjectExtension
    get() = CompilerProjectExtension.getInstance(projectModel.project)!!

  private lateinit var outputDir: VirtualFile
  private lateinit var module1Dir: VirtualFile
  private lateinit var srcDir1: VirtualFile
  private lateinit var module1OutputDir: VirtualFile
  private lateinit var module1: Module
  private lateinit var module2: Module
  private lateinit var module2Dir: VirtualFile
  
  @BeforeEach
  fun setUp() {
    outputDir = projectModel.baseProjectDir.newVirtualDirectory("out")
    module1Dir = projectModel.baseProjectDir.newVirtualDirectory("module1")
    srcDir1 = projectModel.baseProjectDir.newVirtualDirectory("module1/src1")
    module1OutputDir = projectModel.baseProjectDir.newVirtualDirectory("out/module1")
    module2Dir = projectModel.baseProjectDir.newVirtualDirectory("module1/module2")
    module1 = projectModel.createModule("module1")
    module2 = projectModel.createModule("module2")
    setCompilerOutput(outputDir.url)
    PsiTestUtil.addContentRoot(module1, module1Dir)
    PsiTestUtil.addSourceRoot(module1, srcDir1)
    PsiTestUtil.addContentRoot(module2, module2Dir)
  }

  private fun setCompilerOutput(outputDirUrl: String){
    runWriteActionAndWait {
      compilerProjectExtension.compilerOutputUrl = outputDirUrl
    }
  }

  @Test
  fun testExcludeCompilerOutputOutsideOfContentRoot() {
    fileIndex.assertScope(outputDir, EXCLUDED)
    fileIndex.assertScope(module1OutputDir, EXCLUDED)
    fileIndex.assertScope(outputDir.parent, NOT_IN_PROJECT)
    
    val moduleOutputUrl = module1OutputDir.url
    VfsTestUtil.deleteFile(outputDir)
    PsiTestUtil.setCompilerOutputPath(module1, moduleOutputUrl, false)
    outputDir = projectModel.baseProjectDir.newVirtualDirectory("out")
    module1OutputDir = projectModel.baseProjectDir.newVirtualDirectory("out/module1")
    fileIndex.assertScope(outputDir, EXCLUDED)
    fileIndex.assertScope(module1OutputDir, EXCLUDED)
    
    PsiTestUtil.setCompilerOutputPath(module1, moduleOutputUrl, true)
    PsiTestUtil.setCompilerOutputPath(module2, moduleOutputUrl, false)
    PsiTestUtil.setCompilerOutputPath(module2, moduleOutputUrl, true)

    // now no module inherits project output dir, but it still should be project-excluded
    fileIndex.assertScope(outputDir, EXCLUDED)

    // project output inside module content shouldn't be projectExcludeRoot
    var projectOutputUnderContent = projectModel.baseProjectDir.newVirtualDirectory("module1/projectOutputUnderContent")
    setCompilerOutput(projectOutputUnderContent.url)
    fireRootsChanged()
    fileIndex.assertScope(outputDir, NOT_IN_PROJECT)
    fileIndex.assertInModule(projectOutputUnderContent, module1, module1Dir, EXCLUDED)

    VfsTestUtil.deleteFile(projectOutputUnderContent)
    projectOutputUnderContent = projectModel.baseProjectDir.newVirtualDirectory("module1/projectOutputUnderContent")
    fileIndex.assertScope(outputDir, NOT_IN_PROJECT)
    fileIndex.assertInModule(projectOutputUnderContent, module1, module1Dir, EXCLUDED)
  }

  @Test
  fun testResettingProjectOutputPath() {
    val output1 = projectModel.baseProjectDir.newVirtualDirectory("module1/output1")
    val output2 = projectModel.baseProjectDir.newVirtualDirectory("module1/output2")
    fileIndex.assertInModule(output1, module1, module1Dir)
    fileIndex.assertInModule(output2, module1, module1Dir)

    setCompilerOutput(output1.url)
    fireRootsChanged()
    fileIndex.assertInModule(output1, module1, module1Dir, EXCLUDED)
    fileIndex.assertInModule(output2, module1, module1Dir)

    setCompilerOutput(output2.url)
    fireRootsChanged()
    fileIndex.assertInModule(output1, module1, module1Dir)
    fileIndex.assertInModule(output2, module1, module1Dir, EXCLUDED)
  }

  @Test
  fun testExcludedOutputDirShouldBeExcludedRightAfterItsCreation(@TestDisposable disposable: Disposable) {
    var projectOutput = projectModel.baseProjectDir.newVirtualDirectory("module1/projectOutput")
    var module2Output = projectModel.baseProjectDir.newVirtualDirectory("module1/module2Output")
    var module2TestOutput = projectModel.baseProjectDir.newVirtualDirectory("module1/module2/module2TestOutput")
    fileIndex.assertInModule(projectOutput, module1, module1Dir)
    fileIndex.assertInModule(module2Output, module1, module1Dir)
    fileIndex.assertInModule(module2TestOutput, module2, module2Dir)

    setCompilerOutput(projectOutput.url)
    PsiTestUtil.setCompilerOutputPath(module2, module2Output.url, false)
    PsiTestUtil.setCompilerOutputPath(module2, module2TestOutput.url, true)
    PsiTestUtil.setExcludeCompileOutput(module2, true)
    fileIndex.assertInModule(projectOutput, module1, module1Dir, EXCLUDED)
    fileIndex.assertInModule(module2Output, module1, module1Dir, EXCLUDED)
    fileIndex.assertInModule(module2TestOutput, module2, module2Dir, EXCLUDED)
    
    VfsTestUtil.deleteFile(projectOutput)
    VfsTestUtil.deleteFile(module2Output)
    VfsTestUtil.deleteFile(module2TestOutput)
    val created: MutableList<VirtualFile> = ArrayList()
    val l: VirtualFileListener = object : VirtualFileListener {
      override fun fileCreated(e: VirtualFileEvent) {
        val file = e.file
        val fileName = e.fileName
        val (module, dir) = if (fileName.contains("module2TestOutput")) module2 to module2Dir else module1 to module1Dir
        fileIndex.assertInModule(file, module, dir, EXCLUDED)
        created.add(file)
      }
    }
    VirtualFileManager.getInstance().addVirtualFileListener(l, disposable)
    projectOutput = projectModel.baseProjectDir.newVirtualDirectory("module1/${projectOutput.name}")
    fileIndex.assertInModule(projectOutput, module1, module1Dir, EXCLUDED)
    module2Output = projectModel.baseProjectDir.newVirtualDirectory("module1/${module2Output.name}")
    fileIndex.assertInModule(module2Output, module1, module1Dir, EXCLUDED)
    module2TestOutput = projectModel.baseProjectDir.newVirtualDirectory("module1/module2/${module2TestOutput.name}")
    fileIndex.assertInModule(module2TestOutput, module2, module2Dir, EXCLUDED)
    assertEquals(3, created.size, created.toString())
  }

  @Test
  fun testSameSourceAndOutput() {
    PsiTestUtil.setCompilerOutputPath(module1, srcDir1.url, false)
    fileIndex.assertInModule(srcDir1, module1, module1Dir, EXCLUDED)
  }

  private fun fireRootsChanged() {
    runWriteAction {
      ProjectRootManagerEx.getInstanceEx(projectModel.project).makeRootsChange(EmptyRunnable.getInstance(), 
                                                                               RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    }
  }
}
