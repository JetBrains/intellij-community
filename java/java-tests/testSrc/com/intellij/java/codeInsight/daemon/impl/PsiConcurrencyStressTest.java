// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ResultOfMethodCallIgnored")
@SkipSlowTestLocally
@StressTestApplication
public class PsiConcurrencyStressTest {
  private static final Logger LOG = Logger.getInstance(PsiConcurrencyStressTest.class);
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());
  private volatile PsiJavaFile myFile;
  private volatile boolean writeActionInProgress;

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_5);
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/src";
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure( root);
  }

  @Test
  public void testStress() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Project myProject = fixture.getProject();
    int numOfThreads = Runtime.getRuntime().availableProcessors();
    int iterations = Timings.adjustAccordingToMySpeed(20, true);
    LOG.debug("iterations = " + iterations);
    final int readIterations = iterations * 3;

    synchronized (this) {
      PsiClass myClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(myProject).findClass("StressClass", GlobalSearchScope.allScope(myProject)));
      assertNotNull(myClass);
      myFile = ReadAction.computeBlocking(() -> (PsiJavaFile)myClass.getContainingFile());
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    final CountDownLatch reads = new CountDownLatch(numOfThreads);
    final Random random = new Random();

    List<Future<?>> threads = ContainerUtil.map(Collections.nCopies(numOfThreads, ""), i ->
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int i1 = 0; i1 < readIterations; i1++) {
          if (myProject.isDisposed()) return;
          ProgressManager.getInstance().runProcess(() -> ApplicationManager.getApplication().runReadAction(() -> {
            assertFalse(writeActionInProgress);
            readStep(myProject, random);
          }), new DaemonProgressIndicator());
        }

        reads.countDown();
      }));

    final Document document = ReadAction.computeBlocking(() -> documentManager.getDocument(myFile));

    for (int i = 0; i < iterations; i++) {
      Thread.sleep(100);
      WriteCommandAction.writeCommandAction(myProject, myFile).run(() -> {
        writeActionInProgress = true;
        documentManager.commitAllDocuments();
        writeStep(myProject, random);
        documentManager.commitAllDocuments();
        assertEquals(document.getText(), myFile.getText());
        writeActionInProgress = false;
      });
    }

    boolean finished = reads.await(5, TimeUnit.MINUTES);
    assertTrue(finished, () -> "Timed out: " + ThreadDumper.dumpThreadsToString());
    ConcurrencyUtil.getAll(threads);
  }

  private static void mark(final String s) {
    //System.out.print(s);
    //System.out.flush();
  }

  private synchronized PsiClass getPsiClass() {
    assertTrue(myFile.isValid());
    PsiClass psiClass = myFile.getClasses()[0];
    assertTrue(psiClass.isValid());
    return psiClass;
  }

  private synchronized void writeStep(Project project, final Random random) throws IncorrectOperationException {
    switch (random.nextInt(2)) {
      case 0 -> {
        mark("+");
        getPsiClass().add(JavaPsiFacade.getElementFactory(project).createMethod("foo" + System.currentTimeMillis(), PsiTypes.floatType()));
      }
      case 1 -> {
        mark("-");
        final PsiMethod[] psiMethods = getPsiClass().getMethods();
        if (psiMethods.length > 0) {
          WriteCommandAction.runWriteCommandAction(null, () -> psiMethods[random.nextInt(psiMethods.length)].delete());
        }
      }
    }
  }

  private void readStep(Project project, final Random random) {
    PsiClass aClass = getPsiClass();
    switch (random.nextInt(4)) {
      case 0 -> {
        mark("v");
        aClass.getContainingFile().accept(new PsiRecursiveElementVisitor() {
        });
      }
      case 1 -> {
        mark("m");
        for (int offset = 0; offset < myFile.getTextLength(); offset++) {
          myFile.findElementAt(offset);
        }
      }
      case 2 -> {
        mark("h");
        for (HighlightVisitor visitor : HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensionList(project)) {
          HighlightVisitor v = visitor.clone(); // to avoid race for com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor.myAnnotationHolder
          if (!v.suitableForFile(myFile)) continue;
          final HighlightInfoHolder infoHolder = new HighlightInfoHolder(myFile);
          GeneralHighlightingPass.setupAnnotationSession(infoHolder.getAnnotationSession(), myFile.getTextRange(), aClass.getTextRange(), null);
          v.analyze(myFile, true, infoHolder, () -> {
            aClass.accept(new PsiRecursiveElementVisitor() {
              @Override
              public void visitElement(@NotNull final PsiElement element) {
                super.visitElement(element);
                v.visit(element);
              }
            });
          });
        }
      }
      case 3 -> {
        mark("u");
        for (PsiMethod method : aClass.getMethods()) {
          method.getName();
        }
      }
    }
  }
}
