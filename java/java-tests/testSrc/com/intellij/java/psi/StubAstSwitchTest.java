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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiDocumentManagerEx;
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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import one.util.streamex.IntStreamEx;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("CallToSystemGC")
@SkipSlowTestLocally
@TestApplication
public class StubAstSwitchTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_modifying_file_with_stubs_via_VFS() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Foo.java", "class Foo {}");
    PsiClass cls = ReadAction.computeBlocking(() -> {
      assertNotNull(file.getStub());
      PsiClass result = ((PsiJavaFile)file).getClasses()[0];
      assertNotNull(file.getStub());
      return result;
    });

    long oldCount = PsiManager.getInstance(myFixture.getProject()).getModificationTracker().getModificationCount();

    EdtTestUtil.runInEdtAndWait(
      () -> WriteAction.run(() -> file.getVirtualFile().setBinaryContent(file.getVirtualFile().contentsToByteArray())));

    assertTrue(PsiManager.getInstance(myFixture.getProject()).getModificationTracker().getModificationCount() != oldCount);

    ReadAction.runBlocking(() -> {
      assertFalse(cls.isValid());
      assertNotNull(file.getStub());
      assertNotEquals(cls, PsiTreeUtil.findElementOfClassAtOffset(file, 1, PsiClass.class, false));
      assertNull(file.getStub());
    });
  }

  @Test
  public void test_reachable_psi_classes_remain_valid_when_nothing_changes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int count = 1000;
    List<SoftReference<PsiClass>> classList = IntStream.range(0, count)
      .mapToObj(n -> new SoftReference<>(myFixture.addClass("class Foo" + n + " {}"))).toList();
    System.gc();
    System.gc();
    System.gc();
    ReadAction.runBlocking(() -> assertTrue(ContainerUtil.all(classList, ref -> {
      PsiClass cls = ref.get();
      if (cls == null || cls.isValid()) return true;
      assertNotNull(cls.getText());//load AST
      //noinspection ConstantValue
      return cls.isValid();
    })));
  }

  @Test
  public void test_traversing_PSI_and_switching_concurrently() throws InterruptedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
      ReadAction.runBlocking(() -> {
        for (PsiMethod m : c.getMethods()) {
          PsiParameter[] parameters = m.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            assertEquals(i, m.getParameterList().getParameterIndex(parameters[i]));
          }
        }
      });
    }

    latch.await();
  }

  @Test
  public void test_smart_pointer_survives_an_external_modification_of_a_stubbed_file() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addFileToProject("A.java", "class A {}");
    PsiClass oldClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(myFixture.getProject()).findClass("A", GlobalSearchScope.allScope(myFixture.getProject())));
    SmartPsiElementPointer<PsiClass> pointer = ReadAction.computeBlocking(() -> {
      SmartPsiElementPointer<PsiClass> result = SmartPointerManager.getInstance(myFixture.getProject()).createSmartPsiElementPointer(oldClass);

      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      assertNotNull(document);
      assertEquals(file, PsiDocumentManager.getInstance(myFixture.getProject()).getCachedPsiFile(document));
      assertEquals(document, PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file));

      assertNotNull(((PsiFileImpl)file).getStub());
      return result;
    });

    EdtTestUtil.runInEdtAndWait(() -> WriteAction.run(() -> VfsUtil.saveText(file.getVirtualFile(), "import java.util.*; class A {}; class B {}")));
    assertEquals(ReadAction.computeBlocking(() -> pointer.getElement()), oldClass);
  }

  @Test
  public void test_do_not_parse_when_resolving_references_inside_an_anonymous_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
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
    });
  }

  @Test
  public void test_AST_can_be_gc_ed_and_recreated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("class Foo {}");
    PsiFileImpl file = (PsiFileImpl)psiClass.getContainingFile();
    ReadAction.runBlocking(() -> {
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
    });
  }

  @Test
  public void test_no_AST_loading_on_file_rename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class Foo {}");
    ReadAction.runBlocking(() -> {
      assertEquals(1, file.getClasses().length);
      assertNotNull(((PsiFileImpl)file).getStub());
    });

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<PsiElement>)() -> file.setName("b.java"));
    ReadAction.runBlocking(() -> {
      assertEquals(1, file.getClasses().length);
      assertNotNull(((PsiFileImpl)file).getStub());

      assertEquals("Foo", file.getClasses()[0].getNameIdentifier().getText());
      assertTrue(((PsiFileImpl)file).isContentsLoaded());
    });
  }

  @Test
  public void test_use_green_stub_after_AST_loaded_and_gc_ed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{public static void foo() { }}");
    ReadAction.runBlocking(() -> {
      assertNotNull(((PsiFileImpl)file).getStubTree());

      assertNotNull(file.getClasses()[0].getNameIdentifier());
    });
    loadAndGcAst(file);
    assertNoStubLoaded(file);

    ReadAction.runBlocking(() -> {
      assertTrue(file.getClasses()[0].getMethods()[0].getModifierList().hasExplicitModifier(PsiModifier.STATIC));
      assertNull(((PsiFileImpl)file).getTreeElement());
    });
  }

  @Test
  public void test_use_green_stub_after_building_it_from_AST() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    PsiClass psiClass = ReadAction.computeBlocking(() -> {
      PsiClass result = ((PsiJavaFile)file).getClasses()[0];
      assertNotNull(result.getNameIdentifier());
      return result;
    });

    loadAndGcAst(file);

    assertNoStubLoaded(file);
    StubElement<?> hardRefToStub = ReadAction.computeBlocking(() -> file.getGreenStub());
    assertNotNull(hardRefToStub);
    assertEquals(hardRefToStub, ReadAction.computeBlocking(() -> file.getStub()));

    loadAndGcAst(file);
    assertSame(hardRefToStub, ReadAction.computeBlocking(() -> file.getGreenStub()));

    ReadAction.runBlocking(() -> {
      assertEquals(1, psiClass.getTypeParameters().length);
      assertNull(file.getTreeElement());
    });
  }

  private static void loadAndGcAst(PsiFile file) {
    ReadAction.runBlocking(() -> GCWatcher.tracking(file.getNode()).ensureCollected());
    assertNull(((PsiFileImpl)file).getTreeElement());
  }

  private static void assertNoStubLoaded(final PsiFile file) {
    LeakHunter.checkLeak(file, StubTree.class, candidate -> candidate.getRoot().getPsi().equals(file));
  }

  @Test
  public void test_node_has_same_PSI_when_loaded_in_green_stub_presence() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    ReadAction.runBlocking(() -> {
      StubTree stubTree = file.getStubTree();
      PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
      assertNotNull(psiClass.getNameIdentifier());
      GCUtil.tryGcSoftlyReachableObjects();

      assertSame(stubTree, file.getGreenStubTree());
      assertSame(file.getNode().getLastChildNode().getPsi(), psiClass);
    });
  }

  @Test
  public void test_load_stub_from_non_file_PSI_after_AST_is_unloaded() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A<T>{}");
    PsiClass cls = ReadAction.computeBlocking(() -> {
      PsiClass result = file.getClasses()[0];
      assertNotNull(result.getNameIdentifier());
      return result;
    });

    loadAndGcAst(file);

    ReadAction.runBlocking(() -> assertNotNull(((PsiClassImpl)cls).getStub()));
  }

  @Test
  public void test_load_PSI_via_stub_when_AST_is_gc_ed_but_PSI_exists_that_was_loaded_via_AST_but_knows_its_stub_index() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}");
    ReadAction.runBlocking(() -> {
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
    });
  }

  @Test
  public void test_bind_stubs_to_AST_after_AST_has_been_loaded_and_gc_ed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class A{}");
    loadAndGcAst(file);

    ReadAction.runBlocking(() -> {
      PsiClass cls1 = file.getClasses()[0];
      PsiElement cls2 = file.getLastChild();
      assertEquals(cls1, cls2);
    });
  }

  @Test
  public void test_concurrent_stub_and_AST_reloading() throws ExecutionException, InterruptedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int maxFile = 3;
    final List<PsiJavaFileImpl> files = IntStream.range(0, maxFile).mapToObj(
        num -> (PsiJavaFileImpl)myFixture.addFileToProject("a" + num + ".java", "import foo.bar; class A{}"))
      .toList();
    for (int iteration = 0; iteration <= 3; iteration++) {
      ReadAction.runBlocking(() -> GCWatcher.tracking(ContainerUtil.map(files, PsiFileImpl::getNode)).ensureCollected());
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
          fail("Different import psi in " +
                        files.get(i).getName() +
                        ": stub=" +
                        stubImport +
                        ", ast=" +
                        astImport);
        }
      }
    }
  }

  @Test
  public void test_DummyHolder_calcStubTree_does_not_fail() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = "{ new Runnable() { public void run() {} }; }";
    PsiFile file = ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createCodeBlockFromText(text, null).getContainingFile());

    // The main thing is it doesn't fail; DummyHolder.calcStubTree can be changed to null in future if we decide we don't need it
    StubTree stubTree = ReadAction.computeBlocking(() -> assertInstanceOf(DummyHolder.class, file).calcStubTree());

    assertTrue(ContainerUtil.exists(stubTree.getPlainList(),
                                    it -> JavaStubElementTypes.ANONYMOUS_CLASS.equals(it.getElementType())));
  }

  @Test
  public void test_cached_stub_is_dropped_when_an_uncommitted_document_is_gc_ed() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Keep the commit pending, so the file stays in the stub-only state until the document is collected.
    ((PsiDocumentManagerEx)PsiDocumentManager.getInstance(myFixture.getProject())).disableBackgroundCommit(myFixture.getTestRootDisposable());

    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Foo.java", "class Foo { void a() {} }");
    StubTree stub = ReadAction.computeBlocking(() -> file.getStubTree());
    assertNotNull(stub);
    VirtualFile vFile = file.getVirtualFile();

    String newText = "class Foo { int x; void b() {} }";
    changeFileAndCollectUncommittedDocument(vFile, newText).ensureCollected();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(vFile));

    ReadAction.runBlocking(() -> {
      Document reloadedDocument = FileDocumentManager.getInstance().getDocument(vFile);
      assertNotNull(reloadedDocument);
      assertTrue(PsiDocumentManager.getInstance(myFixture.getProject()).isCommitted(reloadedDocument));

      assertNotNull(file.getNode());
      assertEquals(newText, file.getText());
      assertEquals("b", ((PsiJavaFile)file).getClasses()[0].getMethods()[0].getName());
    });
  }

  private GCWatcher changeFileAndCollectUncommittedDocument(VirtualFile vFile, String newText) throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Document document = ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(vFile));
    assertNotNull(document);
    EdtTestUtil.runInEdtAndWait(() -> WriteAction.run(() -> VfsUtil.saveText(vFile, newText)));
    assertFalse(PsiDocumentManager.getInstance(myFixture.getProject()).isCommitted(document));
    return GCWatcher.tracking(document);
  }

  @Test
  public void test_stub_index_is_cleared_on_AST_change() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("class Foo { int a; }");
    PsiField field = ReadAction.computeBlocking(() -> clazz.getFields()[0]);
    final PsiFileImpl file = (PsiFileImpl)clazz.getContainingFile();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    });

    ReadAction.runBlocking(() -> assertNotNull(file.calcStubTree()));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      file.getViewProvider().getDocument().insertString(file.getText().indexOf("int"), "void foo();");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    });

    GCUtil.tryGcSoftlyReachableObjects();

    ReadAction.runBlocking(() -> assertNotNull(file.calcStubTree()));

    ReadAction.runBlocking(() -> {
      assertTrue(field.isValid());
      assertEquals("a", field.getName());
    });
  }
}
