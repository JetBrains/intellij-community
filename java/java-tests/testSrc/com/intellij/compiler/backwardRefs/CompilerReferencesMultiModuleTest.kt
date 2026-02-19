// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.compiler.CompilerReferenceService
import com.intellij.java.compiler.CompilerReferencesTestBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import org.intellij.lang.annotations.Language

class CompilerReferencesMultiModuleTest : CompilerReferencesTestBase() {
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
    assertOneElement(ClassInheritorsSearch.search(foo, foo.useScope, false).findAll())

    val registryValue = Registry.get("compiler.ref.index")
    try {
      registryValue.setValue(false)
      assertOneElement(ClassInheritorsSearch.search(foo, foo.useScope, false).findAll())
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