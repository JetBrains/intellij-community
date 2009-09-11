package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import org.jetbrains.annotations.NonNls;

public class TurnRefsToSuperTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSuperClass() throws Exception {
    doTest("AClass", "ASuper", true, false);
  }

  public void testMethodFromSuper() throws Exception {
    doTest("AClass", "ASuper", true, false);
  }

  public void testRemoveImport() throws Exception {
    doTest("pack1.AClass", "pack1.AnInterface", true, false);
  }

  public void testToArray() throws Exception {
    doTest("A", "I", true, false);
  }


  public void testArrayElementAssignment() throws Exception {
    doTest("C", "I", true, false);
  }

  public void testReturnValue() throws Exception {
    doTest("A", "I", true, false);
  }

  public void testReturnValue2() throws Exception {
    doTest("A", "I", true, false);
  }

  public void testCast() throws Exception {
    doTest("A", "I", true, false);
  }


  public void testUseAsArg() throws Exception {
    doTest("AClass", "I", true, false);
  }

  public void testClassUsage() throws Exception {
    doTest("A", "I", true, false);
  }

  public void testInstanceOf() throws Exception {
    doTest("A", "I", false, false);
  }

  public void testFieldTest() throws Exception {
    doTest("Component1", "IDoSomething", false, false);
  }

  public void testScr34000() throws Exception {
    doTest("SimpleModel", "Model", false, false);
  }

  public void testScr34020() throws Exception {
    doTest("java.util.List", "java.util.Collection", false, false);
  }

   public void testCommonInheritor() throws Exception {
    doTest("Client.V", "Client.L", false, false);
  }

  public void testCommonInheritorFail() throws Exception {
    doTest("Client.V", "Client.L", false, false);
  }

  public void testCommonInheritorResults() throws Exception {
    doTest("Client.V", "Client.L", false, false);
  }

  public void testCommonInheritorResultsFail() throws Exception {
    doTest("Client.V", "Client.L", false, false);
  }

  public void testCommonInheritorResultsFail2() throws Exception {
    doTest("Client.V", "Client.L", false, false);
  }


  public void testIDEA6505() throws Exception {
    doTest("Impl", "IB", false, true);
  }

  public void testIDEADEV5517() throws Exception {
    doTest("Xyz", "Xint", false, true);
  }

  public void testIDEADEV5517Noop() throws Exception {
    doTest("Xyz", "Xint", false, true);
  }

  public void testIDEADEV6136() throws Exception {
    doTest("A", "B", false, true);
  }

  public void testIDEADEV25669() throws Exception {
    doTest("p.A", "p.Base", false, true);
  }

  public void testIDEADEV23807() throws Exception {
    doTest("B", "A", false, true);
  }

  private void doTest(@NonNls final String className, @NonNls final String superClassName, final boolean replaceInstanceOf,
                      final boolean lowercaseFirstLetter) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        TurnRefsToSuperTest.this.performAction(className, superClassName, replaceInstanceOf);
      }
    }, lowercaseFirstLetter);
  }

  public String getTestRoot() {
    return "/refactoring/turnRefsToSuper/";
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) {
    final PsiClass aClass = myJavaFacade.findClass(className);
    assertNotNull("Class " + className + " not found", aClass);
    PsiClass superClass = myJavaFacade.findClass(superClassName);
    assertNotNull("Class " + superClassName + " not found", superClass);

    new TurnRefsToSuperProcessor(myProject, aClass, superClass, replaceInstanceOf).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}