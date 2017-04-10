/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;

import java.io.IOException;

public class OutOfCodeBlockModificationTest extends DefaultActionTestCase {

  public void testInsideBlock() throws IOException {
    doTest("class a{ void f(){int i;<caret> int j;}}", true);
  }
  public void testInsideBlock2() throws IOException {
    doTest("class a{ void f(){ <caret>}}", true);
  }
  public void testOutsideBlock() throws IOException {
    doTest("class a{ <caret> void f(){}}", false);
  }

  private void doTest(String fileText, boolean inside) throws IOException {
    configureFromFileText("a.java",fileText);
    PsiFile file = getFile();
    PsiManager manager = file.getManager();
    PsiModificationTracker modificationTracker = manager.getModificationTracker();
    long codeBlockModificationCount = modificationTracker.getOutOfCodeBlockModificationCount();
    performAction('n');
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    documentManager.commitAllDocuments();
    long afterCodeBlockModificationCount = modificationTracker.getOutOfCodeBlockModificationCount();
    assertTrue("Out of code block modification " + (inside ? "" : "not") + " detected",
               codeBlockModificationCount == afterCodeBlockModificationCount == inside);
  }
}