/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.navigation
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
/**
 * @author peter
 */
class ChooseByNameHddTest extends JavaCodeInsightFixtureTestCase {

  public void "test go to file by full path"() {
    def psiFile = myFixture.addFileToProject("foo/index.html", "foo")
    def vFile = psiFile.virtualFile
    def path = vFile.path

    ApplicationManager.application.runReadAction {
      def model = new GotoFileModel(project)
      def popup = ChooseByNamePopup.createPopup(project, model, new GotoFileItemProvider(project, null, model))
      assert ChooseByNameTest.calcPopupElements(popup, path) == [psiFile]
      assert ChooseByNameTest.calcPopupElements(popup, FileUtil.toSystemDependentName(path)) == [psiFile]
      assert ChooseByNameTest.calcPopupElements(popup, vFile.parent.path) == [psiFile.containingDirectory]
      assert ChooseByNameTest.calcPopupElements(popup, path + ':0') == [psiFile]
      popup.close(false)
    }
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
