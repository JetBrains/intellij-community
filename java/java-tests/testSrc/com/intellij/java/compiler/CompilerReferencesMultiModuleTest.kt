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
import com.intellij.psi.PsiManager
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
    ModuleRootModificationUtil.addDependency(moduleA!!, myModule)
    ModuleRootModificationUtil.addDependency(moduleB!!, myModule)
  }

  private fun dirtyModules() =
    (CompilerReferenceService.getInstance(project) as CompilerReferenceServiceImpl)
          .dirtyScopeHolder
          .allDirtyModulesForTest
}