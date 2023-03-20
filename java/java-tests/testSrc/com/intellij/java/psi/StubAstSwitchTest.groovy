// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ref.GCUtil
import com.intellij.util.ref.GCWatcher

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
@SkipSlowTestLocally
class StubAstSwitchTest extends LightJavaCodeInsightFixtureTestCase {

  void "test modifying file with stubs via VFS"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject('Foo.java', 'class Foo {}')
    assert file.stub
    def cls = ((PsiJavaFile)file).classes[0]
    assert file.stub

    def oldCount = psiManager.modificationTracker.modificationCount

    ApplicationManager.application.runWriteAction { file.virtualFile.setBinaryContent(file.virtualFile.contentsToByteArray()) }

    assert psiManager.modificationTracker.modificationCount != oldCount

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

  void "test smart pointer survives an external modification of a stubbed file"() {
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
    loadAndGcAst(file)
    assertNoStubLoaded(file)

    assert file.classes[0].methods[0].modifierList.hasExplicitModifier(PsiModifier.STATIC)
    assert !((PsiFileImpl)file).getTreeElement()
  }

  void "test use green stub after building it from AST"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    PsiClass psiClass = ((PsiJavaFile)file).classes[0]
    assert psiClass.nameIdentifier

    loadAndGcAst(file)

    assertNoStubLoaded(file)
    StubElement hardRefToStub = file.greenStub
    assert hardRefToStub
    assert hardRefToStub == file.stub

    loadAndGcAst(file)
    assert hardRefToStub.is(file.greenStub)

    assert psiClass.typeParameters.length == 1
    assert !file.treeElement
  }

  private static void loadAndGcAst(PsiFile file) {
    GCWatcher.tracking(file.node).ensureCollected()
    assert !((PsiFileImpl)file).treeElement
  }

  private static assertNoStubLoaded(PsiFile file) {
    LeakHunter.checkLeak(file, StubTree) { candidate -> candidate.root.psi == file }
  }

  void "test node has same PSI when loaded in green stub presence"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    def stubTree = file.stubTree
    PsiClass psiClass = ((PsiJavaFile)file).classes[0]
    assert psiClass.nameIdentifier
    GCUtil.tryGcSoftlyReachableObjects()

    assert stubTree.is(file.greenStubTree)
    assert file.node.lastChildNode.psi.is(psiClass)
  }

  void "test load stub from non-file PSI after AST is unloaded"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}")
    def cls = file.classes[0]
    assert cls.nameIdentifier

    loadAndGcAst(file)

    assert ((PsiClassImpl) cls).stub
  }

  void "test load PSI via stub when AST is gc-ed but PSI exists that was loaded via AST but knows its stub index"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}")
    def cls = file.lastChild
    assert cls instanceof PsiClass

    GCUtil.tryGcSoftlyReachableObjects()
    assert file.treeElement // we still hold a strong reference to AST

    assert cls == myFixture.findClass('A') 
    
    // now we know stub index and can GC AST
    GCUtil.tryGcSoftlyReachableObjects()
    assert !file.treeElement

    assert cls == myFixture.findClass('A')
    assert !file.treeElement
  }

  void "test bind stubs to AST after AST has been loaded and gc-ed"() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}")
    loadAndGcAst(file)

    def cls1 = file.classes[0]
    def cls2 = file.lastChild
    assert cls1 == cls2
  }

  void "test concurrent stub and AST reloading"() {
    def fileNumbers = 0..<3
    List<PsiJavaFileImpl> files = fileNumbers.collect {
      (PsiJavaFileImpl)myFixture.addFileToProject("a${it}.java", "import foo.bar; class A{}")
    }
    for (iteration in 0..<3) {
      GCWatcher.tracking(files.collect { it.node }).ensureCollected()
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

  void "test stub index is cleared on AST change"() {
    def clazz = myFixture.addClass("class Foo { int a; }")
    def field = clazz.fields[0]
    def file = clazz.containingFile as PsiFileImpl
    WriteCommandAction.runWriteCommandAction(project, {
      file.viewProvider.document.insertString(0, ' ')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    })
    
    assert file.calcStubTree()

    WriteCommandAction.runWriteCommandAction(project, {
      file.viewProvider.document.insertString(file.text.indexOf('int'), 'void foo();')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    })
    
    GCUtil.tryGcSoftlyReachableObjects()

    assert file.calcStubTree()
    
    assert field.valid
    assert field.name == 'a'
  }
}
