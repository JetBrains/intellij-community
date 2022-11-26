// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JavaSyntheticLibrary
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class PackageIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private lateinit var module: Module

  private lateinit var sourceRootDir: VirtualFile
  private lateinit var sourcePackDir: VirtualFile

  private val packageIndex
    get() = PackageIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    sourceRootDir = projectModel.baseProjectDir.newVirtualDirectory("src")
    sourcePackDir = projectModel.baseProjectDir.newVirtualDirectory("src/pack") 
  }

  @Test
  fun `add remove source root`() {
    assertPackage(null, sourcePackDir, sourceRootDir)

    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    assertPackage("", sourceRootDir)
    assertPackage("pack", sourcePackDir)
    
    PsiTestUtil.removeSourceRoot(module, sourceRootDir)
    assertPackage(null, sourcePackDir, sourceRootDir)
  }

  @Test
  fun `change package prefix`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    fun setPackagePrefix(prefix: String) {
      ModuleRootModificationUtil.modifyModel(module) { model ->
        model.contentEntries.single().sourceFolders.single().packagePrefix = prefix
        true
      }
    }

    setPackagePrefix("prefix")
    assertPackage("prefix", sourceRootDir)
    assertPackage("prefix.pack", sourcePackDir)

    setPackagePrefix("")
    assertPackage("", sourceRootDir)
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `excluded folder in module`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    assertPackage("pack", sourcePackDir)
    PsiTestUtil.addExcludedRoot(module, sourcePackDir)
    
    assertPackage(null, sourcePackDir)
    
    PsiTestUtil.removeExcludedRoot(module, sourcePackDir)
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `create new directory`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    assertPackage("pack", sourcePackDir)
    assertPackage("pack.xxx")
    val newDirectory = projectModel.baseProjectDir.newVirtualDirectory("src/pack/xxx")
    assertPackage("pack.xxx", newDirectory)
  }
  
  @Test
  fun `rename directory`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    assertPackage("pack", sourcePackDir)
    assertPackage("pack2")
    runWriteActionAndWait { sourcePackDir.rename(this, "pack2") }
    assertPackage("pack2", sourcePackDir)
  }

  @Test
  fun `move directory`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    val newParent = projectModel.baseProjectDir.newVirtualDirectory("src/xxx")
    assertPackage("pack", sourcePackDir)
    assertPackage("xxx.pack")
    runWriteActionAndWait { sourcePackDir.move(this, newParent) }
    assertPackage("xxx.pack", sourcePackDir)
  }

  @Test
  fun `project level library`() {
    doTestLibrary {
      projectModel.addProjectLevelLibrary("lib", it)
    }
  }

  @Test
  fun `application level library`() {
    doTestLibrary {
      projectModel.addApplicationLevelLibrary("lib", it)
    }
  }
  
  private fun doTestLibrary(addLibrary: ((LibraryEx.ModifiableModelEx) -> Unit) -> Library) {
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("classes/exc")
    val classesPack = projectModel.baseProjectDir.newVirtualDirectory("classes/pack")
    val docsRoot = projectModel.baseProjectDir.newVirtualDirectory("docs")
    val library = addLibrary {
      it.addRoot(classesRoot, OrderRootType.CLASSES)
      it.addRoot(sourceRootDir, OrderRootType.SOURCES)
      it.addRoot(docsRoot, OrderRootType.DOCUMENTATION)
      it.addExcludedRoot(excludedRoot.url)
    }
    assertPackage(null, classesRoot, sourceRootDir, docsRoot)

    ModuleRootModificationUtil.addDependency(module, library)
    assertPackage(null, docsRoot, excludedRoot)
    assertPackage("", classesRoot, sourceRootDir)
    assertPackage("pack", classesPack, sourcePackDir)
  }
  
  @Test
  fun `module-level library`() {
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    val docsRoot = projectModel.baseProjectDir.newVirtualDirectory("docs")
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(classesRoot, OrderRootType.CLASSES)
      it.addRoot(sourceRootDir, OrderRootType.SOURCES)
      it.addRoot(docsRoot, OrderRootType.DOCUMENTATION)
    }
    assertPackage(null, docsRoot)
    assertPackage("", classesRoot, sourceRootDir)
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `package name with dots`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    val src2RootDir = projectModel.baseProjectDir.newVirtualDirectory("src2")
    PsiTestUtil.addSourceRoot(module, src2RootDir)
    val bar1 = projectModel.baseProjectDir.newVirtualDirectory("src/pack/foo/bar")
    val bar2 = projectModel.baseProjectDir.newVirtualDirectory("src2/pack.foo/bar")
    assertPackage("", sourceRootDir, src2RootDir)
    assertPackage("pack", sourcePackDir)
    assertPackage("pack.foo", bar1.parent, bar2.parent)
    assertPackage("pack.foo.bar", bar1, bar2)
  }

  @Test
  fun `module and library roots`() {
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    val libSrcRootDir = projectModel.baseProjectDir.newVirtualDirectory("lib/src")
    val libSrcPackDir = projectModel.baseProjectDir.newVirtualDirectory("lib/src/pack")
    val libClassesRootDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val libClassesPackDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes/pack")
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libClassesRootDir, OrderRootType.CLASSES)
      it.addRoot(libSrcRootDir, OrderRootType.SOURCES)
    }
    assertPackage("", sourceRootDir, libSrcRootDir, libClassesRootDir)
    assertPackage("pack", sourcePackDir, libSrcPackDir, libClassesPackDir)
    assertPackage("", false, sourceRootDir, libClassesRootDir)
    assertPackage("pack", false, sourcePackDir, libClassesPackDir)
  }

  @Test
  fun `module root under library root`() {
    val moduleSourceRoot1 = projectModel.baseProjectDir.newVirtualDirectory("src/module")
    val libraryClassesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    val moduleSourceRoot2 = projectModel.baseProjectDir.newVirtualDirectory("classes/module")
    PsiTestUtil.addSourceRoot(module, moduleSourceRoot1)
    PsiTestUtil.addSourceRoot(module, moduleSourceRoot2)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libraryClassesRoot, OrderRootType.CLASSES)
      it.addRoot(sourceRootDir, OrderRootType.SOURCES)
    }
    if (WorkspaceFileIndexEx.IS_ENABLED) {
      assertPackage("", sourceRootDir, libraryClassesRoot, moduleSourceRoot1, moduleSourceRoot2)
    }
    else {
      assertPackage("", sourceRootDir, libraryClassesRoot, moduleSourceRoot2)
    }
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `library root under module root`() {
    val librarySourceRoot = projectModel.baseProjectDir.newVirtualDirectory("src/lib/source")
    val libraryClassesRoot = projectModel.baseProjectDir.newVirtualDirectory("src/lib/classes")
    PsiTestUtil.addSourceRoot(module, sourceRootDir)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libraryClassesRoot, OrderRootType.CLASSES)
      it.addRoot(librarySourceRoot, OrderRootType.SOURCES)
    }
    assertPackage("", sourceRootDir, librarySourceRoot, libraryClassesRoot)
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `same source root in multiple libraries`() {
    projectModel.addModuleLevelLibrary(module, "lib1") {
      it.addRoot(sourceRootDir, OrderRootType.SOURCES)
    }
    projectModel.addModuleLevelLibrary(module, "lib2") {
      it.addRoot(sourceRootDir, OrderRootType.SOURCES)
    }
    assertPackage("", sourceRootDir)
    assertPackage("pack", sourcePackDir)
  }

  @Test
  fun `synthetic java library`() {
    val classesRootDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val classesPackDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes/pack")
    val library = JavaSyntheticLibrary("test", listOf(sourceRootDir), listOf(classesRootDir), emptySet())

    val disposable = registerSyntheticLibrary(library)

    assertPackage("", classesRootDir, sourceRootDir)
    assertPackage("pack", classesPackDir, sourcePackDir)

    unregisterLibrary(disposable)

    assertPackage(null, sourceRootDir, classesRootDir)
  }

  @Test
  fun `synthetic non-java library`() {
    val classesRootDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val library = SyntheticLibrary.newImmutableLibrary(listOf(sourceRootDir), listOf(classesRootDir), emptySet(), null)
    registerSyntheticLibrary(library)
    assertPackage(null, sourceRootDir, classesRootDir)
  }

  private fun unregisterLibrary(disposable: Disposable) {
    Disposer.dispose(disposable)
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun registerSyntheticLibrary(library: SyntheticLibrary): Disposable {
    val disposable = Disposer.newDisposable()
    Disposer.register(projectModel.project, disposable)
    ExtensionTestUtil.maskExtensions(AdditionalLibraryRootsProvider.EP_NAME, listOf(object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(library)
      }
    }), disposable)
    UIUtil.dispatchAllInvocationEvents()
    return disposable
  }

  private fun assertPackage(packageName: String?, vararg directories: VirtualFile) {
    assertPackage(packageName, true, *directories)
  }
  
  private fun assertPackage(packageName: String?, includeLibrarySources: Boolean, vararg directories: VirtualFile) {
    runReadAction {
      directories.forEach {
        assertEquals(packageName, packageIndex.getPackageNameByDirectory(it), "Package name mismatch for ${it.presentableUrl}")
      }
      if (packageName != null) {
        val actual = packageIndex.getDirectoriesByPackageName(packageName, includeLibrarySources).sortedBy { it.url }
        val expected = directories.sortedBy { it.url }
        assertEquals(expected, actual, "Directories mismatch for '$packageName' package")
      }
    }
  }
}