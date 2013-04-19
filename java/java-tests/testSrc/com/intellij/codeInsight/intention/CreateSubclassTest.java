package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MultiFileTestCase;

/**
 * @author yole
 */
public class CreateSubclassTest extends MultiFileTestCase {
  public void testGenerics() throws Exception {
    doTest();
  }

  public void testInnerClassImplement() throws Exception {
    doTestInner();
  }

  public void testInnerClass() throws Exception {
    doTestInner();
  }

  private void doTestInner() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass superClass = myJavaFacade.findClass("Test", ProjectScope.getAllScope(myProject));
        assertNotNull(superClass);
        final PsiClass inner = superClass.findInnerClassByName("Inner", false);
        assertNotNull(inner);
        CreateSubclassAction.createInnerClass(inner);
      }
    });
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiDirectory root = myPsiManager.findDirectory(rootDir);
        PsiClass superClass = myJavaFacade.findClass("Superclass", ProjectScope.getAllScope(myProject));
        CreateSubclassAction.createSubclass(superClass, root, "Subclass");
      }
    });
  }

  @Override
  protected String getTestRoot() {
    return "/codeInsight/createSubclass/";
  }
}
