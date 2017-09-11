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

import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
/**
 * @author peter
 */
class ChooseByNameHddTest extends JavaCodeInsightFixtureTestCase {
  void "test go to file by full path"() {
    def psiFile = myFixture.addFileToProject("foo/index.html", "foo")
    def vFile = psiFile.virtualFile
    def path = vFile.path

    ApplicationManager.application.runReadAction {
      def model = new GotoFileModel(project)
      def popup = ChooseByNamePopup.createPopup(project, model, (PsiElement)null)
      assert ChooseByNameTest.calcPopupElements(popup, path) == [psiFile]
      assert ChooseByNameTest.calcPopupElements(popup, FileUtil.toSystemDependentName(path)) == [psiFile]
      assert ChooseByNameTest.calcPopupElements(popup, vFile.parent.path) == [psiFile.containingDirectory]
      assert ChooseByNameTest.calcPopupElements(popup, path + ':0') == [psiFile]
      popup.close(false)
    }
  }

  void "test prefer same-named classes visible in current module"() {
    int moduleCount = 10
    def modules = (0..moduleCount-1).collect {
      PsiTestUtil.addModule(project, StdModuleTypes.JAVA, "mod$it", myFixture.tempDirFixture.findOrCreateDir("mod$it"))
    }
    ModuleRootModificationUtil.addDependency(myFixture.module, modules[2])
    (0..moduleCount-1).each { myFixture.addFileToProject("mod$it/Foo.java", "class Foo {}") }

    def place = myFixture.addClass("class A {}")
    def popup = ReadAction.compute { ChooseByNamePopup.createPopup(project, new GotoClassModel2(project), place) }
    def resultModules = ChooseByNameTest.calcPopupElements(popup, 'Foo').collect {
      ModuleUtilCore.findModuleForPsiElement(it as PsiElement).name
    }
    assert resultModules[0] == 'mod2'
    popup.close(false)
  }

  void "test paths relative to topmost module"() {
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm1', myFixture.tempDirFixture.findOrCreateDir("foo"))
    PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'm2', myFixture.tempDirFixture.findOrCreateDir("foo/bar"))
    def file = myFixture.addFileToProject('foo/bar/goo/doo.txt', '')
    def popup = ReadAction.compute { ChooseByNamePopup.createPopup(project, new GotoFileModel(project), file) }
    assert ChooseByNameTest.calcPopupElements(popup, "doo", false) == [file]
    assert ChooseByNameTest.calcPopupElements(popup, "goo/doo", false) == [file]
    assert ChooseByNameTest.calcPopupElements(popup, "bar/goo/doo", false) == [file]
    assert ChooseByNameTest.calcPopupElements(popup, "foo/bar/goo/doo", false) == [file]
  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    runnable.run()
  }
}