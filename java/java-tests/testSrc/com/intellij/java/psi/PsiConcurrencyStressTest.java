// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.java.psi;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.Timings;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SkipSlowTestLocally
public class PsiConcurrencyStressTest extends DaemonAnalyzerTestCase {
  private volatile PsiJavaFile myFile;
  private volatile boolean writeActionInProgress;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure( root);
  }

  @Override
  protected void tearDown() throws Exception {
    myFile = null;
    super.tearDown();
  }

  public void testStress() throws Exception {
    DaemonProgressIndicator.setDebug(false);
    int numOfThreads = Runtime.getRuntime().availableProcessors();
    int iterations = Timings.adjustAccordingToMySpeed(20, true);
    LOG.debug("iterations = " + iterations);
    final int readIterations = iterations * 3;

    synchronized (this) {
      PsiClass myClass = myJavaFacade.findClass("StressClass", GlobalSearchScope.allScope(myProject));
      assertNotNull(myClass);
      myFile = (PsiJavaFile)myClass.getContainingFile();
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());

    final CountDownLatch reads = new CountDownLatch(numOfThreads);
    final Random random = new Random();

    List<Future<?>> threads = ContainerUtil.map(Collections.nCopies(numOfThreads, ""), i ->
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int i1 = 0; i1 < readIterations; i1++) {
          if (myPsiManager == null) return;
          ProgressManager.getInstance().runProcess(() -> ApplicationManager.getApplication().runReadAction(() -> {
            assertFalse(writeActionInProgress);
            readStep(random);
          }), new DaemonProgressIndicator());
        }

        reads.countDown();
      }));

    final Document document = documentManager.getDocument(myFile);

    for (int i = 0; i < iterations; i++) {
      Thread.sleep(100);
      WriteCommandAction.writeCommandAction(myProject, myFile).run(() -> {
        writeActionInProgress = true;
        documentManager.commitAllDocuments();
        writeStep(random);
        documentManager.commitAllDocuments();
        assertEquals(document.getText(), myFile.getText());
        writeActionInProgress = false;
      });
    }

    assertTrue("Timed out", reads.await(5, TimeUnit.MINUTES));
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

  private synchronized void writeStep(final Random random) throws IncorrectOperationException {
    switch (random.nextInt(2)) {
      case 0 :
        mark("+");
        getPsiClass().add(myJavaFacade.getElementFactory().createMethod("foo" + System.currentTimeMillis(), PsiType.FLOAT));
        break;
      case 1 :
        mark("-");
        final PsiMethod[] psiMethods = getPsiClass().getMethods();
        if (psiMethods.length > 0) {
          WriteCommandAction.runWriteCommandAction(null, () -> psiMethods[random.nextInt(psiMethods.length)].delete());
        }
        break;
    }
  }

  private void readStep(final Random random) {
    PsiClass aClass = getPsiClass();
    switch (random.nextInt(4)) {
      case 0:
        mark("v");
        aClass.getContainingFile().accept(new PsiRecursiveElementVisitor() {
        });
        break;

      case 1:
        mark("m");
        for (int offset=0; offset<myFile.getTextLength();offset++) {
          myFile.findElementAt(offset);
        }
        break;

      case 2:
        mark("h");
        aClass.accept(new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull final PsiElement element) {
            super.visitElement(element);

            final HighlightInfoHolder infoHolder = new HighlightInfoHolder(myFile);
            for (HighlightVisitor visitor : HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensionList(getProject())) {
              HighlightVisitor v = visitor.clone(); // to avoid race for com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor.myAnnotationHolder
              v.analyze(myFile, true, infoHolder, () -> v.visit(element));
            }
          }
        });
        break;

      case 3:
        mark("u");
        for (PsiMethod method : aClass.getMethods()) {
          method.getName();
        }
        break;
    }
  }

  @Override
  protected void invokeTestRunnable(@NotNull final Runnable runnable) {
    runnable.run();
  }
}
