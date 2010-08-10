/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.indexing.StorageException;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class PsiConcurrencyStressTest extends PsiTestCase {

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {

        public void run() {
          try {
            LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
            String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src";
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
            PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  private PsiJavaFile myFile;
  public void testStress() throws Exception {
    int numOfThreads = 10;
    final int readIterations = 50000;
    final int writeIterations = 30;

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
        public void run() {
          for (int i = 0; i < readIterations; i++) {
            if (myPsiManager == null) return;
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                try {
                  readStep(random);
                }
                catch (StorageException e) {
                  LOG.error(e);
                }
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
        protected void run(final Result result) throws Throwable {
          documentManager.commitAllDocuments();
          writeStep(random);
          documentManager.commitAllDocuments();
          assertEquals(document.getText(), myFile.getText());
        }
      }.execute();
    }

    reads.await();
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
          psiMethods[random.nextInt(psiMethods.length)].delete();
        }
        break;
    }
  }

  private void readStep(final Random random) throws StorageException {
    PsiClass aClass = getPsiClass();
    switch (random.nextInt(5)) {
      case 0 :
        mark("v");
        aClass.getContainingFile().accept(new PsiRecursiveElementVisitor() {}); break;

      case 1 :
        mark("m");
        aClass.getMethods();
        break;

        //case 2 : {
      //  mark("h");
      //  psiFile.accept(new PsiRecursiveElementVisitor() {
      //    @Override public void visitElement(final PsiElement element) {
      //      super.visitElement(element);
      //
      //      final HighlightInfoHolder infoHolder = new HighlightInfoHolder(psiFile, HighlightInfoFilter.EMPTY_ARRAY);
      //      infoHolder.setWritable(true);
      //      new HighlightVisitorImpl(getPsiManager()).visit(element, infoHolder);
      //    }
      //  });
      //  break;
      //}

      case 3 :
        mark("u");
        //FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID);
        break;
      case 4 :
        mark("\n");
        break;

    }
  }

  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    final Exception[] e = new Exception[1];
    Runnable runnable1 = new Runnable() {
      public void run() {
        runnable.run();
      }
    };

    if (myRunCommandForTest) {
      CommandProcessor.getInstance().executeCommand(myProject, runnable1, "", null);
    }
    else {
      runnable1.run();
    }

    if (e[0] != null) {
      throw e[0];
    }
  }
}
