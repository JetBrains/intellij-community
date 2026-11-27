// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.index;

import com.intellij.idea.IJIgnore;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FCTSBackedLighterAST;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SkipSlowTestLocally
@TestApplication
public class ConcurrentIndexTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ((PsiDocumentManagerEx)PsiDocumentManager.getInstance(myFixture.getProject())).disableBackgroundCommit(myFixture.getProject());
  }

  @Test
  public void test_concurrent_switching_with_checkCanceled() throws Exception {
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    Application app = ApplicationManager.getApplication();
    for (int iteration = 1; iteration <= 200; iteration++) {
      String name = "Foo" + iteration;

      codeInsightFixture.get().addFileToProject(name + ".java", "class " + name + " {}");

      List<Future<?>> futuresToWait = new ArrayList<>();
      CountDownLatch startAllAtOnceLatch = new CountDownLatch(N);
      for (int i = 1; i <= halfN; i++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          startAllAtOnceLatch.countDown();
          try {
            startAllAtOnceLatch.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          for (int j = 0; j <= 10; j++) {
            BombedProgressIndicator indicator = new BombedProgressIndicator(/*cancelAfter: */ 10 /*invocations*/);
            indicator.runBombed(() -> {
              app.runReadAction(() -> checkFindClass(name));
            });
          }
        }));
      }

      for (int i = 1; i <= N - halfN; i++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          startAllAtOnceLatch.countDown();
          try {
            startAllAtOnceLatch.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          app.runReadAction(() -> checkFindClass(name));
        }));
      }

      ConcurrencyUtil.getAll(futuresToWait);
    }
  }

  private void checkFindClass(@NotNull String name) {
    PsiClass clazz = codeInsightFixture.get().findClass(name);
    ASTNode node = clazz.getNode();
    assertNotNull(node, "Class [" + name + "] must be available in indexes");
  }

  @Test
  public void test_cancellable_and_non_cancellable_progress() throws Exception {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    PsiFileImpl psiFile = (PsiFileImpl)myFixture.addFileToProject(
      "Foo.java",
      "class Foo {" +
      "public void foo() {}\n".repeat(1000) +
      "}");
    ReadAction.runBlocking(() -> {
      assertNotNull(myFixture.findClass("Foo").getNode(),
                    "File.java is indexed by Stub index: Foo class could be found in Stub index");
    });

    Application app = ApplicationManager.getApplication();
    Project project = myFixture.getProject();
    for (int i = 1; i <= 20; i++) {
      int finalI = i;
      WriteCommandAction.runWriteCommandAction(project, () -> {
        ((PsiJavaFile)psiFile).getImportList()
          .add(JavaPsiFacade.getElementFactory(project).createImportStatementOnDemand("foo.bar" + finalI));
      });
      // Wait for com.intellij.codeInsight.daemon.impl.PsiChangeHandler$Change to release the reference
      GCWatcher.tracking(ReadAction.computeBlocking(() -> psiFile.getNode())).ensureCollectedWithinTimeout(5_000);
      assertFalse(psiFile.isContentsLoaded());

      List<Future<?>> futuresToWait = new ArrayList<>();
      CountDownLatch startAllAtOnceLatch = new CountDownLatch(N);

      for (int j = 1; j <= halfN; j++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          return new BombedProgressIndicator(/*cancelAfter: */ 10 /*invocations*/).runBombed(() -> {
            app.runReadAction(() -> {
              startAllAtOnceLatch.countDown();
              try {
                startAllAtOnceLatch.await();
              }
              catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              assertNotNull(myFixture.findClass("Foo").getNode(),
                            "File.java is indexed by Stub index: Foo class could be found in Stub index");
            });
          });
        }));
      }

      for (int j = 1; j <= N - halfN; j++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          app.runReadAction(() -> {
            startAllAtOnceLatch.countDown();
            try {
              startAllAtOnceLatch.await();
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            assertNotNull(myFixture.findClass("Foo").getNode(),
                          "File.java is indexed by Stub index: Foo class could be found in Stub index");
          });
        }));
      }


      ConcurrencyUtil.getAll(futuresToWait);
    }
  }

  @Test
  @IJIgnore(issue = "IDEA-352828")
  public void test_forceUpdateAffectsReadOfDataForUnsavedDocuments() throws Exception {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject(
      "Foo.java",
      "class Foo {" +
      " public void foo() {}\n".repeat(1000) +
      "}"
    );
    ReadAction.runBlocking(() -> assertNotNull(myFixture.findClass("Foo").getNode()));

    Application app = ApplicationManager.getApplication();
    for (int i = 1; i <= 20; i++) {
      int finalI = i;
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        ((PsiJavaFile)file).getImportList()
          .add(JavaPsiFacade.getElementFactory(myFixture.getProject()).createImportStatementOnDemand("foo.bar" + finalI));
      });

      GCWatcher.tracking(ReadAction.computeBlocking(() -> file.getNode())).ensureCollectedWithinTimeout(5_000); // wait for document commit queue
      assertFalse(file.isContentsLoaded());

      myFixture.addFileToProject("Foo" + i + ".java",
                                 "class Foo" + i + " {" + "public void foo() {}\n".repeat(1000) + "}");

      List<Future<?>> futuresToWait = new ArrayList<>();
      CountDownLatch sameStartCondition = new CountDownLatch(N);

      for (int j = 1; j <= halfN; j++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          app.runReadAction(() -> {
            sameStartCondition.countDown();
            try {
              sameStartCondition.await();
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            assertNotNull(myFixture.getJavaFacade().findClass("Foo", GlobalSearchScope.fileScope(file)).getNode());
          });
        }));
      }


      for (int j = 1; j <= N - halfN; j++) {
        futuresToWait.add(app.executeOnPooledThread(() -> {
          app.runReadAction(() -> {
            sameStartCondition.countDown();
            try {
              sameStartCondition.await();
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            assertNotNull(myFixture.findClass("Foo" + finalI).getNode());
          });
        }));
      }

      ConcurrencyUtil.getAll(futuresToWait);
    }
  }

  @Test
  public void test_concurrent_light_AST_access_during_uncommitted_document_indexing() throws Exception {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("class Bar { void foo(Object o) {}}");

    String text = " foo(null);";
    for (int i = 0; i <= 20; i++) {
      //noinspection StringConcatenationInLoop
      text = "new Runnable() { void run() {\n " + text + "\n}}.run();";
    }

    text = "class Foo {{ " + text.repeat(200) + "}}";

    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", text);
    Document document = ReadAction.computeBlocking(() -> file.getViewProvider().getDocument());
    for (int i = 1; i <= 5; i++) {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        document.insertString(document.getText().indexOf("null") + 1, " ");
        document.insertString(document.getText().indexOf("(null") + 1, " ");
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      });

      // Wait for com.intellij.codeInsight.daemon.impl.PsiChangeHandler$Change to release the reference
      GCWatcher.tracking(ReadAction.computeBlocking(() -> file.getNode())).ensureCollectedWithinTimeout(5_000);
      assertFalse(file.isContentsLoaded());

      ReadAction.runBlocking(() -> assertInstanceOf(FCTSBackedLighterAST.class, file.getNode().getLighterAST()));
      List<Future<?>> futures = new ArrayList<>();
      futures.add(((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
        new Task.Backgroundable(myFixture.getProject(), "hasNull") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            ReadAction.run(() -> {
              assertFalse(JavaNullMethodArgumentUtil.hasNullArgument(clazz.getMethods()[0], 0));
            });
          }
        }));
      Project project = myFixture.getProject();// https://issues.apache.org/jira/browse/GROOVY-9562
      futures.add(((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
        new Task.Backgroundable(myFixture.getProject(), "findClass") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            ReadAction.run(() -> {
              assertNotNull(JavaPsiFacade.getInstance(project).findClass("Foo", GlobalSearchScope.allScope(project)));
            });
          }
        }));
      ConcurrencyUtil.getAll(futures);
    }
  }
}
