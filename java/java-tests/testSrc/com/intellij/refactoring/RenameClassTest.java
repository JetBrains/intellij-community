package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
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
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("XX", GlobalSearchScope.allScope(getProject()));
        assertNotNull("Class XX not found", aClass);

        final RenameProcessor processor = new RenameProcessor(myProject, aClass, "Y", true, true) {
          @Override
          protected boolean showAutomaticRenamingDialog(AutomaticRenamer automaticVariableRenamer) {
            for (PsiNamedElement element : automaticVariableRenamer.getElements()) {
              automaticVariableRenamer.setRename(element, automaticVariableRenamer.getNewName(element));
            }
            return true;
          }
        };
        for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
          processor.addRenamerFactory(factory);
        }
        processor.run();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testAutomaticRenameVarsCollision() throws Exception {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) throws Exception {
    doTest(new PerformAction() {
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

  protected String getTestRoot() {
    return "/refactoring/renameClass/";
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}
