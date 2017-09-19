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
package com.intellij.java.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.PsiClassImpl
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubTree
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.reference.SoftReference
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ref.GCUtil
import groovy.transform.CompileStatic

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
/**
 * @author peter
 */
@SkipSlowTestLocally
class StubAstSwitchTest extends LightCodeInsightFixtureTestCase {

  void "test modifying file with stubs via VFS"() {
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

  void "test reachable psi classes remain valid when nothing changes"() {
    int count = 1000
    List<SoftReference<PsiClass>> classList = (0..<count).collect { new SoftReference<PsiClass>(myFixture.addClass("class Foo$it {}")) }
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

  void "test traversing PSI and switching concurrently"() {
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
        Thread.yield()
        ApplicationManager.application.runReadAction {
          c.text
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

  void "test external modification of a stubbed file with smart pointer switches the file to AST"() {
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

  void "test do not parse when resolving references inside an anonymous class"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """
class A {
    Object field = new B() {
      void foo(Object o) {
      }

      class MyInner extends Inner {}
    };
    Runnable r = () -> { new B() {}; };
    Runnable r2 = (new B(){})::hashCode();
}

class B {
  void foo(Object o) {}
  static class Inner {}
}
""")
    assert !file.contentsLoaded
    PsiClass bClass = ((PsiJavaFile) file).classes[1]
    assert DirectClassInheritorsSearch.search(bClass).findAll().size() == 3
    assert !file.contentsLoaded

    def fooMethod = bClass.methods[0]
    assert !file.contentsLoaded

    def override = OverridingMethodsSearch.search(fooMethod).findAll().first()
    assert override
    assert !file.contentsLoaded

    assert override.containingClass instanceof PsiAnonymousClass
    assert !file.contentsLoaded

    assert bClass == override.containingClass.superClass
    assert bClass.innerClasses[0] == override.containingClass.innerClasses[0].superClass
    assert !file.contentsLoaded
  }

  void "test AST can be gc-ed and recreated"() {
    def psiClass = myFixture.addClass("class Foo {}")
    def file = psiClass.containingFile as PsiFileImpl
    assert file.stub

    assert psiClass.nameIdentifier
    assert !file.stub
    assert file.treeElement

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement
    assert file.stub

    assert psiClass.nameIdentifier
    assert !file.stub
    assert file.treeElement
  }

  void "test no AST loading on file rename"() {
    PsiJavaFile file = (PsiJavaFile) myFixture.addFileToProject('a.java', 'class Foo {}')
    assert file.classes.length == 1
    assert ((PsiFileImpl)file).stub

    WriteCommandAction.runWriteCommandAction project, { file.setName('b.java') }
    assert file.classes.length == 1
    assert ((PsiFileImpl)file).stub

    assert file.classes[0].nameIdentifier.text == 'Foo'
    assert ((PsiFileImpl)file).contentsLoaded
  }

  void "test use green stub after AST loaded and gc-ed"() {
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{public static void foo() { }}")
    //noinspection GroovyUnusedAssignment
    StubTree stubHardRef = ((PsiFileImpl)file).stubTree

    assert file.classes[0].nameIdentifier
    GCUtil.tryGcSoftlyReachableObjects()

    assert !((PsiFileImpl)file).treeElement
    assertNoStubLoaded(file)

    assert file.classes[0].methods[0].modifierList.hasExplicitModifier(PsiModifier.STATIC)
    assert !((PsiFileImpl)file).getTreeElement()
  }

  void "test use green stub after building it from AST"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    PsiClass psiClass = ((PsiJavaFile)file).classes[0]
    assert psiClass.nameIdentifier
    GCUtil.tryGcSoftlyReachableObjects()

    assert !file.treeElement
    assertNoStubLoaded(file)
    StubElement hardRefToStub = file.greenStub
    assert hardRefToStub
    assert hardRefToStub == file.stub

    assert file.node

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement
    assert hardRefToStub.is(file.greenStub)

    assert psiClass.typeParameters.length == 1
    assert !file.treeElement
  }

  private static assertNoStubLoaded(PsiFile file) {
    LeakHunter.checkLeak(file, StubTree) { candidate -> candidate.root.psi == file }
  }

  void "test node is not deeply parsed when loaded in green stub presence"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    def stubTree = file.stubTree
    PsiClass psiClass = ((PsiJavaFile)file).classes[0]
    assert psiClass.nameIdentifier
    GCUtil.tryGcSoftlyReachableObjects()

    assert stubTree.is(file.greenStubTree)
    assert !file.node.parsed
  }

  void "test load stub from non-file PSI after AST is unloaded"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    def cls = file.classes[0]
    assert cls.nameIdentifier

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement

    assert ((PsiClassImpl) cls).stub
  }

  void "test load PSI via stub when AST is gc-ed but PSI exists that has never known stub"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}")
    def cls = file.lastChild
    assert cls instanceof PsiClass

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement

    assert cls == myFixture.findClass('A')
  }

  void "test load PSI via stub when AST is gc-ed and PSI remains that never knew stub"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}")
    def cls = file.lastChild
    assert cls instanceof PsiClass

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement

    assert cls == myFixture.findClass('A')
  }

  void "test bind stubs to AST after AST has been loaded and gc-ed"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}")
    file.node

    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement

    def cls1 = file.classes[0]
    def cls2 = file.lastChild
    assert cls1 == cls2
  }

  void "test concurrent stub and AST reloading"() {
    def fileNumbers = 0..<10
    List<PsiJavaFileImpl> files = fileNumbers.collect {
      (PsiJavaFileImpl)myFixture.addFileToProject("a${it}.java", "import foo.bar; class A{}")
    }
    for (iteration in 0..10) {
      GCUtil.tryGcSoftlyReachableObjects()
      files.each { assert !it.treeElement }

      List<Future<PsiImportList>> stubFutures = []
      List<Future<PsiImportList>> astFutures = []

      for (i in fileNumbers) {
        def file = files[i]
        stubFutures << ApplicationManager.application.executeOnPooledThread({ ReadAction.compute {
          file.importList
        } } as Callable)
        astFutures << ApplicationManager.application.executeOnPooledThread({ ReadAction.compute {
          PsiTreeUtil.findElementOfClassAtOffset(file, 0, PsiImportList, false)
        } } as Callable)
      }
      GCUtil.tryGcSoftlyReachableObjects()

      for (i in fileNumbers) {
        def stubImport = stubFutures[i].get()
        def astImport = astFutures[i].get()
        if (stubImport != astImport) {
          fail("Different import psi in ${files[i].name}: stub=$stubImport, ast=$astImport")
        }
      }
    }
  }

  void "test DummyHolder calcStubTree does not fail"() {
    def text = "{ new Runnable() { public void run() {} }; }"
    def file = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(text, null).containingFile

    // main thing is it doesn't fail; DummyHolder.calcStubTree can be changed to null in future if we decide we don't need it
    def stubTree = assertInstanceOf(file, DummyHolder).calcStubTree()

    assert stubTree.plainList.find { it.stubType == JavaStubElementTypes.ANONYMOUS_CLASS }
  }

  @CompileStatic
  void "test getStub performance with cached PSI"() {
    def text = "class Foo { " + "void bar(int a, int b, int c, int d, int e) { int x = null; }\n" * 1000 + "}"
    def file = myFixture.addFileToProject "a.java", text

    PsiMethod[] methods = ((PsiJavaFile) file).classes[0].methods
    def params = methods.collect { PsiMethod method -> method.parameterList.parameters }
    def literal = file.findElementAt(text.indexOf('null')).parent as PsiLiteralExpression // the only cached PSI without stubIndex

    GCUtil.tryGcSoftlyReachableObjects()

    def fileImpl = (PsiFileImpl)file
    assert !fileImpl.treeElement
    assert !fileImpl.stub
    
    PlatformTestUtil.startPerformanceTest('getStub performance', 100, { 
      10_000.times { 
        if (fileImpl.stub != null) {
          throw new IllegalStateException("has stub")
        }
      }
    }).assertTiming()
    
    assert params
    assert literal
  }
}
