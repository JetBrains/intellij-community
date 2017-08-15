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
package com.intellij.java.index

import com.intellij.lang.FCTSBackedLighterAST
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.BombedProgressIndicator
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ref.GCUtil
import groovy.transform.CompileStatic

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
/**
 * @author peter
 */
@SkipSlowTestLocally
@CompileStatic
class ConcurrentIndexTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).disableBackgroundCommit(getProject())
  }

  void "test concurrent switching with checkCanceled"() {
    def N = Math.max(2, (int)(Runtime.runtime.availableProcessors()))
    def halfN = N / 2
    for (iteration in 1..200) {
      def name = "Foo" + iteration
//      if (iteration % 10 == 0) println "Finding $name"

      //noinspection GroovyUnusedAssignment
      PsiFile file = myFixture.addFileToProject("${name}.java", "class $name {}")
      List<Future> futuresToWait = []
      def sameStartCondition = new CountDownLatch(N)

      for(i in 1.. halfN) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          sameStartCondition.countDown()
          sameStartCondition.await()
          for (j in 0..10) {
            new BombedProgressIndicator(10).runBombed {
              ApplicationManager.application.runReadAction {
                checkFindClass(name)
              }
            }
          }
        })
      }

      for(i in 1..(N - halfN)) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          sameStartCondition.countDown()
          sameStartCondition.await()
          ApplicationManager.application.runReadAction {
            checkFindClass(name)
          }
        })
      }

      for(future in futuresToWait) future.get()
    }
  }

  private void checkFindClass(String name) {
    PsiClass clazz = myFixture.findClass(name)
    def node = clazz.node
    if (node == null) {
      assert false
    }
  }

  void "test cancellable and non-cancellable progress"() {
    def N = Math.max(2, (int)(Runtime.runtime.availableProcessors()))
    def halfN = N / 2
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("Foo.java", "class Foo {" + ("public void foo() {}\n") * 1000 + "}")
    assert myFixture.findClass("Foo").node

    for (i in 1..20) {
      println "iteration $i"
      WriteCommandAction.runWriteCommandAction(project) {
        ((PsiJavaFile) file).importList.add(JavaPsiFacade.getElementFactory(project).createImportStatementOnDemand("foo.bar$i"))
      }
      PlatformTestUtil.tryGcSoftlyReachableObjects()
      assert !file.contentsLoaded

      List<Future> futuresToWait = []
      def sameStartCondition = new CountDownLatch(N)

      for(j in 1..halfN) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          new BombedProgressIndicator(10).runBombed {
            ApplicationManager.application.runReadAction {
              sameStartCondition.countDown()
              sameStartCondition.await()
              assert myFixture.findClass("Foo").node
            }
          }
        })
      }

      for(j in 1..(N - halfN)) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          ApplicationManager.application.runReadAction {
            sameStartCondition.countDown()
            sameStartCondition.await()
            assert myFixture.findClass("Foo").node
          }
        })
      }

      for(future in futuresToWait) future.get()
    }
  }

  void "test forceUpdateAffectsReadOfDataForUnsavedDocuments"() {
    def N = Math.max(2, (int)(Runtime.runtime.availableProcessors()))
    def halfN = N / 2
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("Foo.java", "class Foo {" + ("public void foo() {}\n") * 1000 + "}")
    assert myFixture.findClass("Foo").node

    for (i in 1..20) {
      println "iteration $i"
      WriteCommandAction.runWriteCommandAction(project) {
        ((PsiJavaFile) file).importList.add(JavaPsiFacade.getElementFactory(project).createImportStatementOnDemand("foo.bar$i"))
      }
      PlatformTestUtil.tryGcSoftlyReachableObjects()
      assert !file.contentsLoaded

      myFixture.addFileToProject("Foo" + i + ".java", "class Foo" + i + " {" + ("public void foo() {}\n") * 1000 + "}")

      List<Future> futuresToWait = []
      def sameStartCondition = new CountDownLatch(N)

      for(j in 1..halfN) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          ApplicationManager.application.runReadAction {
            sameStartCondition.countDown()
            sameStartCondition.await()
            assert myFixture.getJavaFacade().findClass("Foo", GlobalSearchScope.fileScope(file)).node
          }
        })
      }

      for(j in 1..(N - halfN)) {
        futuresToWait.add(ApplicationManager.application.executeOnPooledThread {
          ApplicationManager.application.runReadAction {
            sameStartCondition.countDown()
            sameStartCondition.await()
            assert myFixture.findClass("Foo" + i).node
          }
        })
      }

      for(future in futuresToWait) future.get()
    }
  }

  void "test concurrent light AST access during uncommitted document indexing"() {
    def clazz = myFixture.addClass('class Bar { void foo(Object o) {}}')

    def text = " foo(null);"
    for (i in 0..20) {
      text = "new Runnable() { void run() {\n " + text + "\n}}.run();"
    }
    text = "class Foo {{ " + text * 200 + "}}"

    def file = myFixture.addFileToProject('a.java', text)
    def document = file.viewProvider.document
    for (i in 1..5) {
      WriteCommandAction.runWriteCommandAction project, {
        document.insertString(document.text.indexOf('null') + 1, ' ')
        document.insertString(document.text.indexOf('(null') + 1, ' ')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      GCUtil.tryGcSoftlyReachableObjects()

      assert file.node.lighterAST instanceof FCTSBackedLighterAST
      List<Future> futures = []
      futures << ApplicationManager.application.executeOnPooledThread { ReadAction.run {
        assert !JavaNullMethodArgumentUtil.hasNullArgument(clazz.methods[0], 0)
      } }
      futures << ApplicationManager.application.executeOnPooledThread { ReadAction.run {
        assert JavaPsiFacade.getInstance(project).findClass('Foo', GlobalSearchScope.allScope(project))
      } }
      futures.each { it.get() }
    }
  }
}
