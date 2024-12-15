// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import junit.framework.TestCase;
import one.util.streamex.IntStreamEx;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("CallToSystemGC")
@SkipSlowTestLocally
public class StubAstSwitchTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_modifying_file_with_stubs_via_VFS() throws IOException {
    final PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Foo.java", "class Foo {}");
    assertNotNull(file.getStub());
    PsiClass cls = ((PsiJavaFile)file).getClasses()[0];
    assertNotNull(file.getStub());

    long oldCount = getPsiManager().getModificationTracker().getModificationCount();

    WriteAction.run(() -> file.getVirtualFile().setBinaryContent(file.getVirtualFile().contentsToByteArray()));

    assertTrue(getPsiManager().getModificationTracker().getModificationCount() != oldCount);

    assertFalse(cls.isValid());
    assertNotNull(file.getStub());
    assertNotEquals(cls, PsiTreeUtil.findElementOfClassAtOffset(file, 1, PsiClass.class, false));
    assertNull(file.getStub());
  }

  public void test_reachable_psi_classes_remain_valid_when_nothing_changes() {
    int count = 1000;
    List<SoftReference<PsiClass>> classList = IntStream.range(0, count)
      .mapToObj(n -> new SoftReference<>(myFixture.addClass("class Foo" + n + " {}"))).toList();
    System.gc();
    System.gc();
    System.gc();
    assertTrue(ContainerUtil.all(classList, ref -> {
      PsiClass cls = ref.get();
      if (cls == null || cls.isValid()) return true;
      assertNotNull(cls.getText());//load AST
      //noinspection ConstantValue
      return cls.isValid();
    }));
  }

  public void test_traversing_PSI_and_switching_concurrently() throws InterruptedException {
    int count = 100;
    List<PsiClass> classList = IntStream.range(0, count)
      .mapToObj(num -> myFixture.addClass(
        "class Foo" + num + " {\n" +
        "void foo" + num + "(" + IntStreamEx.range(0, 250).mapToObj(parNum -> "int i" + parNum).joining(", ") + ") {}\n" +
        "}")).toList();
    final CountDownLatch latch = new CountDownLatch(count);
    for (PsiClass c : classList) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        Thread.yield();
        ReadAction.compute(() -> c.getText());
        latch.countDown();
      });
      for (PsiMethod m : c.getMethods()) {
        PsiParameter[] parameters = m.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
          assertEquals(i, m.getParameterList().getParameterIndex(parameters[i]));
        }
      }
    }

    latch.await();
  }

  public void test_smart_pointer_survives_an_external_modification_of_a_stubbed_file() throws IOException {
    final PsiFile file = myFixture.addFileToProject("A.java", "class A {}");
    PsiClass oldClass = JavaPsiFacade.getInstance(getProject()).findClass("A", GlobalSearchScope.allScope(getProject()));
    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(oldClass);

    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    assertNotNull(document);
    assertEquals(file, PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document));
    assertEquals(document, PsiDocumentManager.getInstance(getProject()).getCachedDocument(file));

    assertNotNull(((PsiFileImpl)file).getStub());

    WriteAction.run(() -> VfsUtil.saveText(file.getVirtualFile(), "import java.util.*; class A {}; class B {}"));
    assertEquals(pointer.getElement(), oldClass);
  }

  public void test_do_not_parse_when_resolving_references_inside_an_anonymous_class() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("A.java", """

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
      """);
    assertFalse(file.isContentsLoaded());
    PsiClass bClass = ((PsiJavaFile)file).getClasses()[1];
    assertEquals(3, DirectClassInheritorsSearch.search(bClass).findAll().size());
    assertFalse(file.isContentsLoaded());

    PsiMethod fooMethod = bClass.getMethods()[0];
    assertFalse(file.isContentsLoaded());

    PsiMethod override = OverridingMethodsSearch.search(fooMethod).findFirst();
    assertNotNull(override);
    assertFalse(file.isContentsLoaded());

    assertTrue(override.getContainingClass() instanceof PsiAnonymousClass);
    assertFalse(file.isContentsLoaded());

    assertEquals(bClass, override.getContainingClass().getSuperClass());
    assertEquals(bClass.getInnerClasses()[0], override.getContainingClass().getInnerClasses()[0].getSuperClass());
    assertFalse(file.isContentsLoaded());
  }

  public void test_AST_can_be_gc_ed_and_recreated() {
    PsiClass psiClass = myFixture.addClass("class Foo {}");
    PsiFileImpl file = (PsiFileImpl)psiClass.getContainingFile();
    assertNotNull(file.getStub());

    assertNotNull(psiClass.getNameIdentifier());
    assertNull(file.getStub());
    assertNotNull(file.getTreeElement());

    GCUtil.tryGcSoftlyReachableObjects();
    assertNull(file.getTreeElement());
    assertNotNull(file.getStub());

    assertNotNull(psiClass.getNameIdentifier());
    assertNull(file.getStub());
    assertNotNull(file.getTreeElement());
  }

  public void test_no_AST_loading_on_file_rename() {
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class Foo {}");
    assertEquals(1, file.getClasses().length);
    assertNotNull(((PsiFileImpl)file).getStub());

    WriteCommandAction.runWriteCommandAction(getProject(), (Computable<PsiElement>)() -> file.setName("b.java"));
    assertEquals(1, file.getClasses().length);
    assertNotNull(((PsiFileImpl)file).getStub());

    assertEquals("Foo", file.getClasses()[0].getNameIdentifier().getText());
    assertTrue(((PsiFileImpl)file).isContentsLoaded());
  }

  public void test_use_green_stub_after_AST_loaded_and_gc_ed() {
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{public static void foo() { }}");
    assertNotNull(((PsiFileImpl)file).getStubTree());

    assertNotNull(file.getClasses()[0].getNameIdentifier());
    loadAndGcAst(file);
    assertNoStubLoaded(file);

    assertTrue(file.getClasses()[0].getMethods()[0].getModifierList().hasExplicitModifier(PsiModifier.STATIC));
    assertNull(((PsiFileImpl)file).getTreeElement());
  }

  public void test_use_green_stub_after_building_it_from_AST() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
    assertNotNull(psiClass.getNameIdentifier());

    loadAndGcAst(file);

    assertNoStubLoaded(file);
    StubElement<?> hardRefToStub = file.getGreenStub();
    assertNotNull(hardRefToStub);
    assertEquals(hardRefToStub, file.getStub());

    loadAndGcAst(file);
    assertSame(hardRefToStub, file.getGreenStub());

    assertEquals(1, psiClass.getTypeParameters().length);
    assertNull(file.getTreeElement());
  }

  private static void loadAndGcAst(PsiFile file) {
    GCWatcher.tracking(file.getNode()).ensureCollected();
    assertNull(((PsiFileImpl)file).getTreeElement());
  }

  private static void assertNoStubLoaded(final PsiFile file) {
    LeakHunter.checkLeak(file, StubTree.class, candidate -> candidate.getRoot().getPsi().equals(file));
  }

  public void test_node_has_same_PSI_when_loaded_in_green_stub_presence() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    StubTree stubTree = file.getStubTree();
    PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
    assertNotNull(psiClass.getNameIdentifier());
    GCUtil.tryGcSoftlyReachableObjects();

    assertSame(stubTree, file.getGreenStubTree());
    assertSame(file.getNode().getLastChildNode().getPsi(), psiClass);
  }

  public void test_load_stub_from_non_file_PSI_after_AST_is_unloaded() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    PsiClass cls = file.getClasses()[0];
    assertNotNull(cls.getNameIdentifier());

    loadAndGcAst(file);

    assertNotNull(((PsiClassImpl)cls).getStub());
  }

  public void test_load_PSI_via_stub_when_AST_is_gc_ed_but_PSI_exists_that_was_loaded_via_AST_but_knows_its_stub_index() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}");
    PsiElement cls = file.getLastChild();
    assertTrue(cls instanceof PsiClass);

    GCUtil.tryGcSoftlyReachableObjects();
    assertNotNull(file.getTreeElement());// we still hold a strong reference to AST

    assertEquals(cls, myFixture.findClass("A"));

    // now we know stub index and can GC AST
    GCUtil.tryGcSoftlyReachableObjects();
    assertNull(file.getTreeElement());
    assertEquals(cls, myFixture.findClass("A"));
    assertNull(file.getTreeElement());
  }

  public void test_bind_stubs_to_AST_after_AST_has_been_loaded_and_gc_ed() {
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}");
    loadAndGcAst(file);

    PsiClass cls1 = file.getClasses()[0];
    PsiElement cls2 = file.getLastChild();
    assertEquals(cls1, cls2);
  }

  public void test_concurrent_stub_and_AST_reloading() throws ExecutionException, InterruptedException {
    int maxFile = 3;
    final List<PsiJavaFileImpl> files = IntStream.range(0, maxFile).mapToObj(
        num -> (PsiJavaFileImpl)myFixture.addFileToProject("a" + num + ".java", "import foo.bar; class A{}"))
      .toList();
    for (int iteration = 0; iteration <= 3; iteration++) {
      GCWatcher.tracking(ContainerUtil.map(files, PsiFileImpl::getNode)).ensureCollected();
      for (PsiJavaFileImpl file : files) {
        assertNull(file.getTreeElement());
      }

      List<Future<PsiImportList>> stubFutures = new ArrayList<>();
      List<Future<PsiImportList>> astFutures = new ArrayList<>();

      for (int i = 0; i < 3; i++) {
        final PsiJavaFileImpl file = files.get(i);
        stubFutures.add(ApplicationManager.getApplication()
                          .executeOnPooledThread(() -> ReadAction.compute(() -> file.getImportList())));
        astFutures.add(ApplicationManager.getApplication()
                         .executeOnPooledThread(
                           () -> ReadAction.compute(() -> PsiTreeUtil.findElementOfClassAtOffset(file, 0, PsiImportList.class, false))));
      }

      GCUtil.tryGcSoftlyReachableObjects();

      for (int i = 0; i < maxFile; i++) {
        PsiImportList stubImport = stubFutures.get(i).get();
        PsiImportList astImport = astFutures.get(i).get();
        if (!stubImport.equals(astImport)) {
          TestCase.fail("Different import psi in " +
                        files.get(i).getName() +
                        ": stub=" +
                        stubImport +
                        ", ast=" +
                        astImport);
        }
      }
    }
  }

  public void test_DummyHolder_calcStubTree_does_not_fail() {
    String text = "{ new Runnable() { public void run() {} }; }";
    PsiFile file = JavaPsiFacade.getElementFactory(getProject()).createCodeBlockFromText(text, null).getContainingFile();

    // The main thing is it doesn't fail; DummyHolder.calcStubTree can be changed to null in future if we decide we don't need it
    StubTree stubTree = UsefulTestCase.assertInstanceOf(file, DummyHolder.class).calcStubTree();

    assertTrue(ContainerUtil.exists(stubTree.getPlainList(),
                                    it -> JavaStubElementTypes.ANONYMOUS_CLASS.equals(it.getElementType())));
  }

  public void test_stub_index_is_cleared_on_AST_change() {
    PsiClass clazz = myFixture.addClass("class Foo { int a; }");
    PsiField field = clazz.getFields()[0];
    final PsiFileImpl file = (PsiFileImpl)clazz.getContainingFile();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    assertNotNull(file.calcStubTree());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      file.getViewProvider().getDocument().insertString(file.getText().indexOf("int"), "void foo();");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    GCUtil.tryGcSoftlyReachableObjects();

    assertNotNull(file.calcStubTree());

    assertTrue(field.isValid());
    assertEquals("a", field.getName());
  }
}
