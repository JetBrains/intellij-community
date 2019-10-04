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
package com.intellij.java.navigation


import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

/**
 * @author peter
 */
@CompileStatic
class ChooseByNameHddTest extends JavaCodeInsightFixtureTestCase {
  void "test go to file by full path"() {
    def psiFile = myFixture.addFileToProject("foo/index.html", "foo")
    def vFile = psiFile.virtualFile
    def path = vFile.path

    def contributor = ChooseByNameTest.createFileContributor(project)

    assert ChooseByNameTest.calcContributorElements(contributor, path) == [psiFile]
    assert ChooseByNameTest.calcContributorElements(contributor, FileUtil.toSystemDependentName(path)) == [psiFile]
    assert ChooseByNameTest.calcContributorElements(contributor, vFile.parent.path) == [psiFile.containingDirectory]
    assert ChooseByNameTest.calcContributorElements(contributor, path + ':0') == [psiFile]
  }

  void "test prefer same-named classes visible in current module"() {
    int moduleCount = 10
    def modules = (0..moduleCount-1).collect {
      PsiTestUtil.addModule(project, StdModuleTypes.JAVA, "mod$it", myFixture.tempDirFixture.findOrCreateDir("mod$it"))
    }
    ModuleRootModificationUtil.addDependency(myFixture.module, modules[2])
    (0..moduleCount-1).each { myFixture.addFileToProject("mod$it/Foo.java", "class Foo {}") }

    def place = myFixture.addClass("class A {}")
    def contributor = ChooseByNameTest.createFileContributor(project, place)
    def resultModules = ChooseByNameTest.calcContributorElements(contributor, 'Foo').collect {
      ModuleUtilCore.findModuleForPsiElement(it as PsiElement).name
    }
    assert resultModules[0] == 'mod2'
  }

  void "test paths relative to topmost module"() {
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm1', myFixture.tempDirFixture.findOrCreateDir("foo"))
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm2', myFixture.tempDirFixture.findOrCreateDir("foo/bar"))
    def file = myFixture.addFileToProject('foo/bar/goo/doo.txt', '')
    def contributor = ChooseByNameTest.createFileContributor(project, file)
    assert ChooseByNameTest.calcContributorElements(contributor, "doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "goo/doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "bar/goo/doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "foo/bar/goo/doo") == [file]
  }


}