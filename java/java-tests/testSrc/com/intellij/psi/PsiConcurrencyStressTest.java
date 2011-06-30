/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.IncorrectOperationException;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PsiConcurrencyStressTest extends PsiTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  private PsiJavaFile myFile;
  private volatile boolean writeActionInProgress;
  public void testStress() throws Exception {
    int numOfThreads = 10;
    int iterations = Timings.adjustAccordingToMySpeed(20);
    System.out.println("iterations = " + iterations);
    final int readIterations = iterations * 3;
    final int writeIterations = iterations;

    synchronized (this) {
      PsiClass myClass = myJavaFacade.findClass("StressClass", GlobalSearchScope.allScope(myProject));
      assertNotNull(myClass);
      myFile = (PsiJavaFile)myClass.getContainingFile();
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());

    final CountDownLatch reads = new CountDownLatch(numOfThreads);
    final Random random = new Random();
    for (int i = 0; i < numOfThreads; i++) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          for (int i = 0; i < readIterations; i++) {
            if (myPsiManager == null) return;
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                assertFalse(writeActionInProgress);
                readStep(random);
              }
            });
          }

          reads.countDown();
        }
      }, "stress thread" + i).start();
    }

    final Document document = documentManager.getDocument(myFile);

    for (int i = 0; i < writeIterations; i++) {
      Thread.sleep(100);
      new WriteCommandAction(myProject, myFile) {
        @Override
        protected void run(final Result result) throws Throwable {
          writeActionInProgress = true;
          documentManager.commitAllDocuments();
          writeStep(random);
          documentManager.commitAllDocuments();
          assertEquals(document.getText(), myFile.getText());
          writeActionInProgress = false;
        }
      }.execute();
    }

    reads.await(5, TimeUnit.MINUTES);
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
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              psiMethods[random.nextInt(psiMethods.length)].delete();
            }
          });
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
          public void visitElement(final PsiElement element) {
            super.visitElement(element);

            final HighlightInfoHolder infoHolder = new HighlightInfoHolder(myFile, HighlightInfoFilter.EMPTY_ARRAY);
            final HighlightVisitorImpl visitor = new HighlightVisitorImpl(getProject());
            visitor.analyze(new Runnable() {
              @Override
              public void run() {
                visitor.visit(element, infoHolder);
              }
            }, false, myFile);
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
  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    runnable.run();
  }
}
