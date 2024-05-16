// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.index;

import com.intellij.idea.IJIgnore;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FCTSBackedLighterAST;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@SkipSlowTestLocally
public class ConcurrentIndexTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject())).disableBackgroundCommit(getProject());
  }

  public void test_concurrent_switching_with_checkCanceled() throws ExecutionException, InterruptedException {
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    for (int iteration = 1; iteration <= 200; iteration++) {
      final String name = "Foo" + iteration;

      myFixture.addFileToProject(name + ".java", "class " + name + " {}");
      List<Future<?>> futuresToWait = new ArrayList<>();
      final CountDownLatch sameStartCondition = new CountDownLatch(N);

      for (int i = 1; i <= halfN; i++) {
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          sameStartCondition.countDown();
          try {
            sameStartCondition.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          for (int j = 0; j <= 10; j++) {
            new BombedProgressIndicator(10).runBombed(() -> {
              ApplicationManager.getApplication().runReadAction(() -> checkFindClass(name));
            });
          }
        }));
      }


      for (int i = 1; i <= N - halfN; i++) {
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          sameStartCondition.countDown();
          try {
            sameStartCondition.await();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          ApplicationManager.getApplication().runReadAction(() -> checkFindClass(name));
        }));
      }

      for (Future<?> future : futuresToWait) future.get();
    }
  }

  private void checkFindClass(String name) {
    PsiClass clazz = myFixture.findClass(name);
    ASTNode node = clazz.getNode();
    assertNotNull(node);
  }

  public void test_cancellable_and_non_cancellable_progress() throws ExecutionException, InterruptedException {
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    final PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject(
      "Foo.java",
      "class Foo {" +
      "public void foo() {}\n".repeat(1000) +
      "}");
    assertNotNull(myFixture.findClass("Foo").getNode());

    for (int i = 1; i <= 20; i++) {
      System.out.println("iteration " + i);
      int finalI = i;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        ((PsiJavaFile)file).getImportList()
          .add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("foo.bar" + finalI));
      });
      GCWatcher.tracking(file.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      List<Future<?>> futuresToWait = new ArrayList<>();
      final CountDownLatch sameStartCondition = new CountDownLatch(N);

      for (int j = 1; j <= halfN; j++) {
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          return new BombedProgressIndicator(10).runBombed(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
              sameStartCondition.countDown();
              try {
                sameStartCondition.await();
              }
              catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              assertNotNull(myFixture.findClass("Foo").getNode());
            });
          });
        }));
      }


      for (int j = 1; j <= N - halfN; j++) {
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ApplicationManager.getApplication().runReadAction(() -> {
            sameStartCondition.countDown();
            try {
              sameStartCondition.await();
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            assertNotNull(myFixture.findClass("Foo").getNode());
          });
        }));
      }


      for (Future<?> future : futuresToWait) future.get();
    }
  }

  @IJIgnore(issue = "IDEA-352828")
  public void test_forceUpdateAffectsReadOfDataForUnsavedDocuments() throws ExecutionException, InterruptedException {
    int N = Math.max(2, Runtime.getRuntime().availableProcessors());
    int halfN = N / 2;
    final PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Foo.java", "class Foo {" +
                                                                                 "public void foo() {}\n".repeat(1000) +
                                                                                 "}");
    assertNotNull(myFixture.findClass("Foo").getNode());

    for (int i = 1; i <= 20; i++) {
      System.out.println("iteration " + i);
      int finalI = i;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        ((PsiJavaFile)file).getImportList()
          .add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("foo.bar" + finalI));
      });
      GCWatcher.tracking(file.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      myFixture.addFileToProject("Foo" + i + ".java",
                                 "class Foo" + i + " {" + "public void foo() {}\n".repeat(1000) + "}");

      List<Future<?>> futuresToWait = new ArrayList<>();
      final CountDownLatch sameStartCondition = new CountDownLatch(N);

      for (int j = 1; j <= halfN; j++) {
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ApplicationManager.getApplication().runReadAction(() -> {
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
        futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ApplicationManager.getApplication().runReadAction(() -> {
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

      for (Future<?> future : futuresToWait) future.get();
    }
  }

  public void test_concurrent_light_AST_access_during_uncommitted_document_indexing() throws ExecutionException, InterruptedException {
    final PsiClass clazz = myFixture.addClass("class Bar { void foo(Object o) {}}");

    String text = " foo(null);";
    for (int i = 0; i <= 20; i++) {
      //noinspection StringConcatenationInLoop
      text = "new Runnable() { void run() {\n " + text + "\n}}.run();";
    }

    text = "class Foo {{ " + text.repeat(200) + "}}";

    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", text);
    final Document document = file.getViewProvider().getDocument();
    for (int i = 1; i <= 5; i++) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        document.insertString(document.getText().indexOf("null") + 1, " ");
        document.insertString(document.getText().indexOf("(null") + 1, " ");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      GCWatcher.tracking(file.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(file.getNode().getLighterAST() instanceof FCTSBackedLighterAST);
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
      final Project project = getProject();// https://issues.apache.org/jira/browse/GROOVY-9562
      futures.add(((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
        new Task.Backgroundable(myFixture.getProject(), "findClass") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            ReadAction.run(() -> {
              assertNotNull(JavaPsiFacade.getInstance(project).findClass("Foo", GlobalSearchScope.allScope(project)));
              });
          }
        }));
      for (Future<?> future : futures) {
        future.get();
      }
    }
  }
}
