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
package com.intellij.psi
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.reference.SoftReference
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

import java.util.concurrent.CountDownLatch
/**
 * @author peter
 */
class StubAstSwitchTest extends LightCodeInsightFixtureTestCase {

  public void "test modifying file with stubs via VFS"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject('Foo.java', 'class Foo {}')
    assert file.stub
    def cls = ((PsiJavaFile)file).classes[0]
    assert file.stub

    def oldCount = psiManager.modificationTracker.javaStructureModificationCount

    ApplicationManager.application.runWriteAction { file.virtualFile.setBinaryContent(file.virtualFile.contentsToByteArray()) }

    assert psiManager.modificationTracker.javaStructureModificationCount != oldCount

    assert !cls.valid
    assert file.stub
    assert cls != PsiTreeUtil.findElementOfClassAtOffset(file, 1, PsiClass, false)
    assert !file.stub
  }

  public void "test reachable psi classes remain valid when nothing changes"() {
    int count = 1000
    List<SoftReference<PsiClass>> classList = (0..<count).collect { new SoftReference(myFixture.addClass("class Foo$it {}")) }
    System.gc()
    System.gc()
    System.gc()
    assert classList.every {
      def cls = it.get()
      if (!cls || cls.valid) return true
      cls.text //load AST
      return cls.valid
    }
  }

  public void "test traversing PSI and switching concurrently"() {
    int count = 100
    List<PsiClass> classList = (0..<count).collect {
      myFixture.addClass("class Foo$it { " +
                         "void foo$it(" +
                         (0..250).collect { "int i$it"}.join(", ") +
                         ") {}" +
                         " }")
    }
    CountDownLatch latch = new CountDownLatch(count)
    for (c in classList) {
      ApplicationManager.application.executeOnPooledThread {
        Thread.yield();
        ApplicationManager.application.runReadAction {
          c.text;
        }
        latch.countDown()
      }
      for (m in c.methods) {
        def parameters = m.parameterList.parameters
        for (i in 0..<parameters.size()) {
          assert i == m.parameterList.getParameterIndex(parameters[i])
        }
      }
    }
    latch.await()
  }

  public void "test external modification of a stubbed file with smart pointer switches the file to AST"() {
    PsiFile file = myFixture.addFileToProject("A.java", "class A {}")
    def oldClass = JavaPsiFacade.getInstance(project).findClass("A", GlobalSearchScope.allScope(project))
    def pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(oldClass)
    
    def document = FileDocumentManager.instance.getDocument(file.virtualFile)
    assert document
    assert file == PsiDocumentManager.getInstance(project).getCachedPsiFile(document)
    assert document == PsiDocumentManager.getInstance(project).getCachedDocument(file)

    assert ((PsiFileImpl)file).stub

    ApplicationManager.application.runWriteAction { VfsUtil.saveText(file.virtualFile, "import java.util.*; class A {}; class B {}") }
    assert pointer.element == oldClass
    assert ((PsiFileImpl)file).treeElement
  }

  public void "test do not parse when resolving references inside an anonymous class"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """
class A {
    Object field = new B() {
      void foo(Object o) {
      }
    };
}

class B {
  void foo(Object o) {}
}
""")
    assert !file.contentsLoaded
    PsiClass bClass = ((PsiJavaFile) file).classes[1]
    assert !file.contentsLoaded

    def fooMethod = bClass.methods[0]
    assert !file.contentsLoaded

    def override = OverridingMethodsSearch.search(fooMethod).findAll().first()
    assert override
    assert !file.contentsLoaded

    assert override.containingClass instanceof PsiAnonymousClass
    assert !file.contentsLoaded

    assert bClass == override.containingClass.superClass
  }
}
