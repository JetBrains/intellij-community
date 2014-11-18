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
package com.intellij.index
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.TimeoutUtil

class IndexGeneratedTest extends JavaCodeInsightFixtureTestCase {
  protected void invokeTestRunnable(Runnable runnable) {
    WriteCommandAction.runWriteCommandAction(project, runnable)
  }

  public void "test changing a file without psi makes the document committed and updates index"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    FileDocumentManager.instance.getDocument(vFile).text = "import zoo.Zoo; class Foo1 {}"
    assert PsiDocumentManager.getInstance(project).uncommittedDocuments
    psiFile = null

    PlatformTestUtil.tryGcSoftlyReachableObjects()

    assert !((PsiManagerEx) psiManager).fileManager.getCachedPsiFile(vFile)

    FileDocumentManager.instance.saveAllDocuments()

    VfsUtil.saveText(vFile, "class Foo3 {}")

    assert !PsiDocumentManager.getInstance(project).uncommittedDocuments

    assert JavaPsiFacade.getInstance(project).findClass("Foo3", scope)
  }


}