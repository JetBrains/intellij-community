package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.JavaTestUtil;
import org.junit.Assert;

/**
 * @author dsl
 */
public class RenameMethodMultiTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/renameMethod/multi/";
  }

  public void testStaticImport1() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport2() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport3() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport4() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testDefaultAnnotationMethod() throws Exception {
    doTest("pack1.A", "int value()", "intValue");
  }

  public void testRename2OverrideFinal() throws Exception {
    try {
      doTest("p.B", "void method()", "finalMethod");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Renaming method will override final \"method <b><code>A.finalMethod()</code></b>\"\n" +
                          "Method finalMethod() will override \n" +
                          "a method of the base class <b><code>p.A</code></b>.", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testAlignedMultilineParameters() throws Exception {
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("void test123(int i, int j)", "test123asd");
  }

  private void doTest(final String methodSignature, final String newName) throws Exception {
    doTest(getTestName(false), methodSignature, newName);
  }

  private void doTest(final String className, final String methodSignature, final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final JavaPsiFacade manager = getJavaFacade();
        final PsiClass aClass = manager.findClass(className, GlobalSearchScope.moduleScope(myModule));
        assertNotNull(aClass);
        final PsiMethod methodBySignature = aClass.findMethodBySignature(manager.getElementFactory().createMethodFromText(
                  methodSignature + "{}", null), false);
        assertNotNull(methodBySignature);
        final RenameProcessor renameProcessor = new RenameProcessor(myProject, methodBySignature, newName, false, false);
        renameProcessor.run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

}
