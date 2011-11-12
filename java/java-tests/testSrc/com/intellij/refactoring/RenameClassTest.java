package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import org.jetbrains.annotations.NonNls;

public class RenameClassTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNonJava() throws Exception {
    doTest("pack1.Class1", "Class1New");
  }

  public void testCollision() throws Exception {
    doTest("pack1.MyList", "List");
  }

  public void testInnerClass() throws Exception {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() throws Exception {
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testConstructorJavadoc() throws Exception {
    doTest("Test", "Test1");
  }

  public void testCollision1() throws Exception {
    doTest("Loader", "Reader");
  }

  public void testImplicitReferenceToDefaultCtr() throws Exception {
    doTest("pack1.Parent", "ParentXXX");
  }

  public void testImplicitlyImported() throws Exception {
    doTest("pack1.A", "Object");
  }

  public void testAutomaticRenameVars() throws Exception {
    doRenameClass("XX", "Y");
  }

  private void doRenameClass(final String className, final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
        assertNotNull("Class XX not found", aClass);

        final RenameProcessor processor = new RenameProcessor(myProject, aClass, newName, true, true);
        for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
          processor.addRenamerFactory(factory);
        }
        processor.run();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testAutomaticRenameInheritors() throws Exception {
    doRenameClass("MyClass", "MyClass1");
  }

  public void testAutomaticRenameVarsCollision() throws Exception {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        RenameClassTest.this.performAction(qClassName, newName);
      }
    });
  }

  private void performAction(String qClassName, String newName) throws Exception {
    PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(myProject, aClass, newName, true, true).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/renameClass/";
  }
}
