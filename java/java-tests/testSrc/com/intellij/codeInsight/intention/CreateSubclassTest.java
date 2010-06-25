package com.intellij.codeInsight.intention;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.codeInsight.intention.impl.CreateSubclassAction;

/**
 * @author yole
 */
public class CreateSubclassTest extends MultiFileTestCase {
  public void testGenerics() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiDirectory root = myPsiManager.findDirectory(rootDir);
        PsiClass superClass = myJavaFacade.findClass("Superclass", ProjectScope.getAllScope(myProject));
        CreateSubclassAction.createSubclass(superClass, root, "Subclass");
      }
    });
  }

  protected String getTestRoot() {
    return "/codeInsight/createSubclass/";
  }
}
