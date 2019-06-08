/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.compiler

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.PsiTestUtil

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
    myFixture.addFileToProject("BaseClass.java", "public interface BaseClass{}")
    myFixture.addFileToProject("A/ClassA.java", "public class ClassA implements BaseClass{}")
    myFixture.addFileToProject("B/ClassB.java", "public class ClassB implements BaseClass{}")
    rebuildProject()
    assertEmpty(dirtyModules())
  }

  @Throws(Exception::class)
  fun testDirtyScopeCachedResults() {
    val file1 = myFixture.addFileToProject("A/Foo.java", "public class Foo {" +
                                                        "static class Bar extends Foo {} " +
                                                        "}")
    myFixture.addFileToProject("B/Unrelated.java", "public class Unrelated {}")
    rebuildProject()
    val foo = (file1 as PsiClassOwner).classes[0]
    assertOneElement(ClassInheritorsSearch.search(foo, foo.useScope, false).findAll())

    try {
      assertTrue(CompilerReferenceService.IS_ENABLED_KEY.asBoolean())
      CompilerReferenceService.IS_ENABLED_KEY.setValue(false)
      assertOneElement(ClassInheritorsSearch.search(foo, foo.useScope, false).findAll())
    }
    finally {
      CompilerReferenceService.IS_ENABLED_KEY.setValue(true)
    }
  }

  fun testLeafModuleTyping() {
    myFixture.addFileToProject("BaseClass.java", "public interface BaseClass{}")
    val classA = myFixture.addFileToProject("A/ClassA.java", "public class ClassA implements BaseClass{}")
    myFixture.addFileToProject("B/ClassB.java", "public class ClassB implements BaseClass{}")
    rebuildProject()
    myFixture.openFileInEditor(classA.virtualFile)
    myFixture.type("/*typing in module A*/")
    assertEquals("A", assertOneElement(dirtyModules()).name)
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals("A", assertOneElement(dirtyModules()).name)
  }

  fun testModulePathRename() {
    myFixture.addFileToProject("A/Foo.java", "class Foo { void m() {System.out.println(123);} }")
    rebuildProject()
    myFixture.renameElement(PsiManager.getInstance(myFixture.project).findDirectory(myFixture.findFileInTempDir("A"))!!, "XXX")
    assertEquals("A", assertOneElement(dirtyModules()).name)
    myFixture.addFileToProject("XXX/Bar.java", "class Bar { void m() {System.out.println(123);} }")
    rebuildProject()
    assertEmpty(dirtyModules())
    val javaLangSystem = myFixture.javaFacade.findClass("java.lang.System")!!
    val referentFiles = (CompilerReferenceService.getInstance(myFixture.project) as CompilerReferenceServiceImpl).getReferentFiles(javaLangSystem)!!
    assertEquals(setOf("Foo.java", "Bar.java"), referentFiles.map { it.name }.toSet())
  }

  private fun addTwoModules() {
    moduleA = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "A", myFixture.tempDirFixture.findOrCreateDir("A"))
    moduleB = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "B", myFixture.tempDirFixture.findOrCreateDir("B"))
    ModuleRootModificationUtil.addDependency(moduleA!!, module)
    ModuleRootModificationUtil.addDependency(moduleB!!, module)
  }

  private fun dirtyModules() =
    (CompilerReferenceService.getInstance(project) as CompilerReferenceServiceImpl)
          .allDirtyModulesForTest
}