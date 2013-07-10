/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;

public class InlineSuperClassTest extends MultiFileTestCase {
  @Override
  protected String getTestRoot() {
    return "/refactoring/inlineSuperClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean fail) throws Exception {
    doTest(fail, false);
  }

  private void doTest(final boolean fail, final boolean inlineOne) throws Exception {
    try {
      doTest(new PerformAction() {
        @Override
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(myProject));

          if (aClass == null) aClass = myJavaFacade.findClass("p.Test", GlobalSearchScope.allScope(myProject));
          assertNotNull("Class Test not found", aClass);

          PsiClass superClass = myJavaFacade.findClass("Super", GlobalSearchScope.allScope(myProject));

          if (superClass == null) superClass = myJavaFacade.findClass("p1.Super", GlobalSearchScope.allScope(myProject));
          assertNotNull("Class Super not found", superClass);

          new InlineSuperClassRefactoringProcessor(getProject(), inlineOne ? aClass : null, superClass, DocCommentPolicy.ASIS, aClass).run();

          //LocalFileSystem.getInstance().refresh(false);
          //FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }

  public void testInlineOneClass() throws Exception {
    doTest(false, true);
  }
  
  public void testInlineOneClassWithConflicts() throws Exception {
    doTest(true, true);
  }

  public void testAbstractOverrides() throws Exception {
    doTest();
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testSimpleGenerics() throws Exception {
    doTest();
  }

  public void testConflictGenerics() throws Exception {
    doTest(true);
  }

  public void testImports() throws Exception {
    doTest();
  }

  public void testGenerics() throws Exception {
    doTest();
  }

  public void testNewexpr() throws Exception {
    doTest();
  }

  public void testConflictConstructors() throws Exception {
    doTest(true);
  }

  public void testConflictMultipleConstructors() throws Exception {
    doTest(true);
  }

  public void testMultipleConstructors() throws Exception {
    doTest();
  }

  public void testImplicitChildConstructor() throws Exception {
    doTest();
  }

  public void testStaticMembers() throws Exception {
    doTest();
  }

  public void testSuperReference() throws Exception {
    doTest();
  }

  public void testInnerclassReference() throws Exception {
    doTest();
  }

  public void testStaticImport() throws Exception {
    doTest();
  }

  public void testNewArrayInitializerExpr() throws Exception {
    doTest();
  }
  
  public void testNewArrayDimensionsExpr() throws Exception {
    doTest();
  }

  public void testNewArrayComplexDimensionsExpr() throws Exception {
    doTest();
  }

  public void testSuperConstructorWithReturnInside() throws Exception {
    doTest(true);
  }

  public void testSuperConstructorWithFieldInitialization() throws Exception {
     doTest();
  }

  public void testSuperConstructorWithParam() throws Exception {
     doTest();
  }

  public void testChildConstructorImplicitlyCallsSuper() throws Exception {
    doTest();
  }

  public void testNoChildConstructorCallsSuperDefault() throws Exception {
    doTest();
  }

  public void testReplaceGenericsInside() throws Exception {
    doTest();
  }

  public void testMultipleSubclasses() throws Exception {
    doTestMultipleSubclasses();
  }

  public void testMultipleSubstitutions() throws Exception {
    doTestMultipleSubclasses();
  }

  public void testMultipleSubclassesInheritsOneBaseBase() throws Exception {
    doTestMultipleSubclasses();
  }

  public void testInlineSuperclassExtendsList() throws Exception {
    doTest();
  }

  public void testInterfaceHierarchyWithSubstitution() throws Exception {
    doTest();
  }

  private void doTestMultipleSubclasses() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass superClass = myJavaFacade.findClass("Super", GlobalSearchScope.allScope(myProject));
        if (superClass == null) superClass = myJavaFacade.findClass("p1.Super", GlobalSearchScope.allScope(myProject));
        assertNotNull("Class Super not found", superClass);
        new InlineSuperClassRefactoringProcessor(getProject(), null, superClass, DocCommentPolicy.ASIS,
                                                 myJavaFacade.findClass("Test", GlobalSearchScope.allScope(myProject)),
                                                 myJavaFacade.findClass("Test1", GlobalSearchScope.allScope(myProject))).run();
      }
    });
  }
}
