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
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * @author peter
 */
@CompileStatic
class ChooseByNameHddTest extends JavaCodeInsightFixtureTestCase {
  void "test go to file by full path"() {
    def psiFile = myFixture.addFileToProject("foo/index.html", "foo")
    def vFile = psiFile.virtualFile
    def path = vFile.path

    def contributor = ChooseByNameTest.createFileContributor(project, testRootDisposable)

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

    def place = myFixture.addClass("class A {}").containingFile
    def contributor = ChooseByNameTest.createFileContributor(project, testRootDisposable, place)
    def resultModules = ChooseByNameTest.calcContributorElements(contributor, 'Foo').collect {
      ModuleUtilCore.findModuleForPsiElement(it as PsiElement).name
    }
    assert resultModules[0] == 'mod2'
  }

  void "test paths relative to topmost module"() {
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm1', myFixture.tempDirFixture.findOrCreateDir("foo"))
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm2', myFixture.tempDirFixture.findOrCreateDir("foo/bar"))
    def file = myFixture.addFileToProject('foo/bar/goo/doo.txt', '')
    def contributor = ChooseByNameTest.createFileContributor(project, testRootDisposable, file)
    assert ChooseByNameTest.calcContributorElements(contributor, "doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "goo/doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "bar/goo/doo") == [file]
    assert ChooseByNameTest.calcContributorElements(contributor, "foo/bar/goo/doo") == [file]
  }

  void "test source-test-resources priority"() {
    def srcDir = myFixture.tempDirFixture.findOrCreateDir("src")
    def resourcesDir = myFixture.tempDirFixture.findOrCreateDir("resources")
    def testSrcDir = myFixture.tempDirFixture.findOrCreateDir("test")

    PsiTestUtil.addSourceRoot(module, srcDir, JavaSourceRootType.SOURCE)
    PsiTestUtil.addSourceRoot(module, testSrcDir, JavaSourceRootType.TEST_SOURCE)
    PsiTestUtil.addSourceRoot(module, resourcesDir, JavaResourceRootType.RESOURCE)
    PsiTestUtil.removeSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(""))

    def testSrcFile1 = myFixture.addFileToProject("${testSrcDir.name}/fileForSearch.txt", "")
    def testSrcFile2 = myFixture.addFileToProject("${testSrcDir.name}/sub/fileForSearch.txt", "")
    def srcFile1 = myFixture.addFileToProject("${srcDir.name}/sub/fileForSearch.txt", "")
    def srcFile2 = myFixture.addFileToProject("${srcDir.name}/sub/sub/fileForSearch.txt", "")
    def resourcesFile = myFixture.addFileToProject("${resourcesDir.name}/fileForSearch.txt", "")

    def contextFile = myFixture.addFileToProject("context.txt", "")
    def contextFile2 = myFixture.addFileToProject("${testSrcDir.name}/context.txt", "")

    //tests have low priority because of com.intellij.psi.util.proximity.InResolveScopeWeigher
    assert gotoFile("fileForSearch.txt", false, contextFile) == [srcFile1, srcFile2, resourcesFile, testSrcFile1, testSrcFile2]

    //tests have high priority because of search from same root
    assert gotoFile("fileForSearch.txt", false, contextFile2) == [testSrcFile1, testSrcFile2, srcFile1, srcFile2, resourcesFile]
  }

  private List<Object> gotoFile(String text, boolean checkboxState = false, PsiElement context = null) {
    return ChooseByNameTest.calcContributorElements(ChooseByNameTest.createFileContributor(project, testRootDisposable, context,
                                                                                           checkboxState), text)
  }

}