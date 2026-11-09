// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.compiler.CompilerReferenceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CompilerReferencesFixtureTestCase
import com.intellij.util.FileContentUtilCore
import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path

class CompilerReferencesMultiModuleTest : CompilerReferencesFixtureTestCase() {
  private var moduleA: Module? = null
  private var moduleB: Module? = null

  override fun setUp() {
    super.setUp()
    addTwoModules()
    installCompiler()
  }

  override fun tearDown() {
    moduleA = null
    moduleB = null
    super.tearDown()
  }

  fun testNoChanges() {
    addClass("BaseClass.java", "public interface BaseClass{}")
    addClass("A/ClassA.java", "public class ClassA implements BaseClass{}")
    addClass("B/ClassB.java", "public class ClassB implements BaseClass{}")
    rebuildProject()
    assertEmpty(dirtyModules())
  }

  @Throws(Exception::class)
  fun testDirtyScopeCachedResults() {
    val file1 = addClass("A/Foo.java", """public class Foo { 
                                       static class Bar extends Foo {} 
                                       }""")
    addClass("B/Unrelated.java", "public class Unrelated {}")
    rebuildProject()
    val foo = (file1 as PsiClassOwner).classes[0]
    assertOneElement(runReadActionBlocking { ClassInheritorsSearch.search(foo, foo.useScope, false).findAll()})

    val registryValue = Registry.get("compiler.ref.index")
    try {
      registryValue.setValue(false)
      assertOneElement(runReadActionBlocking { ClassInheritorsSearch.search(foo, foo.useScope, false).findAll()})
    }
    finally {
      registryValue.setValue(true)
    }
  }

  fun testLeafModuleTyping() {
    addClass("BaseClass.java", "public interface BaseClass{}")
    val classA = addClass("A/ClassA.java", "public class ClassA implements BaseClass{}")
    addClass("B/ClassB.java", "public class ClassB implements BaseClass{}")
    rebuildProject()
    myFixture.openFileInEditor(classA.virtualFile)
    myFixture.type("/*typing in module A*/")
    assertEquals("A", assertOneElement(dirtyModules()))
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals("A", assertOneElement(dirtyModules()))
  }

  fun testNoScopeRestrictionWhenMostModulesAreDirty() {
    val moduleC = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "C", myFixture.tempDirFixture.findOrCreateDir("C"))
    IdeaTestUtil.setModuleLanguageLevel(moduleC, LanguageLevel.JDK_11)
    ModuleRootModificationUtil.setModuleSdk(moduleC, ModuleRootManager.getInstance(module).sdk)
    ModuleRootModificationUtil.addDependency(moduleC, module)
    addClass("BaseClass.java", "public interface BaseClass{}")
    val classA = addClass("A/ClassA.java", "public class ClassA implements BaseClass{}")
    val classB = addClass("B/ClassB.java", "public class ClassB implements BaseClass{}")
    val classC = addClass("C/ClassC.java", "public class ClassC implements BaseClass{}")
    val unrelatedB = addClass("B/UnrelatedB.java", "public class UnrelatedB {}")
    val textB = myFixture.addFileToProject("B/notes.txt", "BaseClass")
    rebuildProject()
    val service = CompilerReferenceService.getInstance(project)
    val baseClass = myFixture.findClass("BaseClass")
    assertNotNull(service.getScopeWithCodeReferences(baseClass))

    edit(classA)
    assertEquals(setOf("A"), dirtyModules().toSet())
    val scope = service.getScopeWithCodeReferences(baseClass)
    assertNotNull("1 of 4 modules is dirty", scope)
    assertTrue("dirty file", classA.virtualFile in scope!!)
    assertTrue("clean file with a reference", classB.virtualFile in scope)
    assertTrue("file that is not Java", textB.virtualFile in scope)
    assertFalse("clean file without a reference", unrelatedB.virtualFile in scope)

    edit(classB)
    assertEquals(setOf("A", "B"), dirtyModules().toSet())
    assertNotNull("2 of 4 modules are dirty", service.getScopeWithCodeReferences(baseClass))

    edit(classC)
    assertEquals(setOf("A", "B", "C"), dirtyModules().toSet())
    assertNull("3 of 4 modules are dirty", service.getScopeWithCodeReferences(baseClass))
  }

  fun testNoScopeRestrictionAfterVfsChangesMakeMostModulesDirty() {
    val moduleC = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "C", myFixture.tempDirFixture.findOrCreateDir("C"))
    IdeaTestUtil.setModuleLanguageLevel(moduleC, LanguageLevel.JDK_11)
    ModuleRootModificationUtil.setModuleSdk(moduleC, ModuleRootManager.getInstance(module).sdk)
    ModuleRootModificationUtil.addDependency(moduleC, module)
    addClass("BaseClass.java", "public interface BaseClass{}")
    val files = runWriteActionAndWait {
      listOf("A", "B", "C").map {
        val file = myFixture.tempDirFixture.findOrCreateDir(it).createChildData(this, "Class$it.java")
        VfsUtil.saveText(file, "public class Class$it implements BaseClass{}")
        file
      }
    }
    rebuildProject()
    val service = CompilerReferenceService.getInstance(project)
    val baseClass = myFixture.findClass("BaseClass")
    assertNotNull(service.getScopeWithCodeReferences(baseClass))

    // With a loaded document and PSI, a VFS change reloads the document. The PSI modification count changes only at the next commit.
    val documents = runReadActionBlocking { files.map { PsiManager.getInstance(project).findFile(it)!!.viewProvider.document!! } }
    runWriteActionAndWait {
      for (file in files) {
        VfsUtil.saveText(file, VfsUtil.loadText(file) + "\n// Edited")
      }
    }
    assertTrue(documents.all { PsiDocumentManager.getInstance(project).isUncommited(it) })
    assertEquals(setOf("A", "B", "C"), dirtyModules().toSet())
    assertNull("3 of 4 modules are dirty", service.getScopeWithCodeReferences(baseClass))
  }

  private fun edit(file: PsiFile) {
    myFixture.openFileInEditor(file.virtualFile)
    myFixture.type("/*edit*/")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  private fun addClass(relativePath: String, @Language("JAVA") text: String) = myFixture.addFileToProject(relativePath, text)

  fun testModulePathRename() {
    addClass("A/Foo.java", "class Foo { void m() {System.out.println(123);} }")
    rebuildProject()
    val moduleARoot = PsiManager.getInstance(myFixture.project).findDirectory(myFixture.findFileInTempDir("A"))!!
    myFixture.renameElement(moduleARoot, "XXX")
    assertTrue(dirtyModules().contains("A"))
    addClass("XXX/Bar.java", "class Bar { void m() {System.out.println(123);} }")
    rebuildProject()
    assertEmpty(dirtyModules())
    val javaLangSystem = myFixture.javaFacade.findClass("java.lang.System")!!
    val referentFiles = (CompilerReferenceService.getInstance(myFixture.project) as CompilerReferenceServiceImpl).getReferentFilesForTests(javaLangSystem)!!
    assertEquals(setOf("Foo.java", "Bar.java"), referentFiles.map { it.name }.toSet())
  }

  fun testReparseDoesNotMarkModulesUnderReparsedFileDirty() {
    val foo = addClass("A/Foo.java", "class Foo {}").virtualFile
    // A reparse event has the PROP_NAME property but does not rename the file.
    // The module file path is below the file path, so a module scan for this event marks module C dirty.
    // The test does not build the project, because a save cannot write the module file below a file.
    val moduleC = runWriteActionAndWait { ModuleManager.getInstance(project).newModule(foo.path + "/C.iml", JavaModuleType.getModuleType().id) }
    val dirtyScopeHolder = (CompilerReferenceService.getInstance(project) as CompilerReferenceServiceImpl).dirtyScopeHolder
    dirtyScopeHolder.compilerActivityStarted()
    dirtyScopeHolder.compilerActivityFinished(listOf(moduleC))
    assertFalse(dirtyModules().contains("C"))
    FileContentUtilCore.reparseFiles(foo)
    assertFalse(dirtyModules().contains("C"))
  }

  fun testSymlinkRetargetFromDirectoryToDirectoryMarksModuleBelowLinkDirty() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val root = Path.of(myFixture.tempDirPath)
    val link = Files.createSymbolicLink(root.resolve("link"), Files.createDirectories(root.resolve("dir1/inner")).parent)
    Files.createDirectories(root.resolve("dir2/inner"))
    addCleanModules("$link/C.iml", "$link/inner/D.iml", "$root/link2/E.iml")
    val events = retargetSymlink(link, root.resolve("dir2"))
    assertEquals(listOf(VirtualFile.PROP_SYMLINK_TARGET), events)
    assertEquals(setOf("C", "D"), dirtyModules().filter { it in setOf("C", "D", "E") }.toSet())
  }

  fun testSymlinkRetargetFromFileToDirectoryIsDeleteAndCreate() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val root = Path.of(myFixture.tempDirPath)
    val link = Files.createSymbolicLink(root.resolve("link"), Files.createFile(root.resolve("file1")))
    addCleanModules("$link/C.iml")
    val events = retargetSymlink(link, Files.createDirectories(root.resolve("dir1")))
    assertEquals(listOf("delete", "create"), events)
    assertFalse(dirtyModules().contains("C"))
  }

  fun testSymlinkRetargetFromFileToFileDoesNotMarkModuleBelowLinkDirty() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val root = Path.of(myFixture.tempDirPath)
    val link = Files.createSymbolicLink(root.resolve("link"), Files.createFile(root.resolve("file1")))
    addCleanModules("$link/C.iml")
    val events = retargetSymlink(link, Files.createFile(root.resolve("file2")))
    // The new target is a different file, so the refresh can also send a content change for the link.
    assertContainsElements(events, VirtualFile.PROP_SYMLINK_TARGET)
    assertDoesntContain(events, "delete", "create")
    assertFalse(dirtyModules().contains("C"))
  }

  /**
   * Adds a module for each of [moduleFilePaths]. The module name is the file name without the extension.
   * The modules are not dirty after the call.
   */
  private fun addCleanModules(vararg moduleFilePaths: String) {
    val modules = runWriteActionAndWait {
      moduleFilePaths.map { ModuleManager.getInstance(project).newModule(it, JavaModuleType.getModuleType().id) }
    }
    val dirtyScopeHolder = (CompilerReferenceService.getInstance(project) as CompilerReferenceServiceImpl).dirtyScopeHolder
    dirtyScopeHolder.compilerActivityStarted()
    dirtyScopeHolder.compilerActivityFinished(modules)
    assertEmpty(dirtyModules().filter { name -> modules.any { it.name == name } })
  }

  /**
   * Changes the target of [link] to [newTarget] and refreshes the VFS.
   * Returns the VFS events for [link].
   */
  private fun retargetSymlink(link: Path, newTarget: Path): List<String> {
    val linkFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(link)!!
    val events = mutableListOf<String>()
    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(vfsEvents: List<VFileEvent>) {
        for (event in vfsEvents.filter { it.path == linkFile.path }) {
          events += when (event) {
            is VFilePropertyChangeEvent -> event.propertyName
            is VFileDeleteEvent -> "delete"
            is VFileCreateEvent -> "create"
            else -> event.javaClass.simpleName
          }
        }
      }
    })
    Files.delete(link)
    Files.createSymbolicLink(link, newTarget)
    VfsUtil.markDirtyAndRefresh(false, true, true, linkFile.parent)
    return events
  }

  private fun addTwoModules() {
    moduleA = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "A", myFixture.tempDirFixture.findOrCreateDir("A"))
    moduleB = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "B", myFixture.tempDirFixture.findOrCreateDir("B"))

    IdeaTestUtil.setModuleLanguageLevel(moduleA!!, LanguageLevel.JDK_11)
    IdeaTestUtil.setModuleLanguageLevel(moduleB!!, LanguageLevel.JDK_11)
    ModuleRootModificationUtil.addDependency(moduleA!!, module)
    ModuleRootModificationUtil.addDependency(moduleB!!, module)
  }

  private fun dirtyModules(): Collection<String> =
    (CompilerReferenceService.getInstance(project) as CompilerReferenceServiceImpl).dirtyScopeHolder.allDirtyModules.map { module -> module.name }
}