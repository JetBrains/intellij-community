/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import junit.framework.Assert;
import org.jetbrains.annotations.Nullable;

public class EncapsulateFieldsTest extends MultiFileTestCase{
  public void testAlreadyExist() throws Exception {
    doTest("i" , null);
  }

  public void testDiffWithReturnTypeOnly() throws Exception {
    doTest("i", "There already is a method <b><code>Test setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only.");
  }

   public void testDiffWithReturnTypeOnlyInHierarchy() throws Exception {
    doTest("i", "There already is a method <b><code>Super setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only.");
  }

  public void testHideOverriderMethod() throws Exception {
    doTest("i", "A", "There is already a method <b><code>B.getI()</code></b> which would hide generated getter for a.i");
  }

  public void testHideOuterclassMethod() throws Exception {
    doTest("i", "A.B", "There is already a method <b><code>A.getI()</code></b> which would be hidden by generated getter");
  }

  public void testMoveJavadocToGetter() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.projectScope(myProject));
        assertNotNull("Tested class not found", aClass);
        final PsiField field = aClass.findFieldByName("i", false);
        assertNotNull(field);
        doTest(aClass, field, null, true, true);
      }
    });
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/encapsulateFields/";
  }


  private void doTest(final String fieldName, final String conflicts) throws Exception {
    doTest(fieldName, "Test", conflicts);
  }

  private void doTest(final String fieldName, final String className, final String conflicts) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(myProject));

        assertNotNull("Tested class not found", aClass);


        doTest(aClass, aClass.findFieldByName(fieldName, false), conflicts, true, true);
      }
    });
  }



  private static void doTest(final PsiClass aClass, final PsiField field, final String conflicts,
                             final boolean generateGetters, final boolean  generateSetters) {
    try {
      final Project project = aClass.getProject();
      EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, new EncapsulateFieldsDescriptor() {
        @Override
        public PsiField[] getSelectedFields() {
          return new PsiField[]{field};
        }

        @Override
        public String[] getGetterNames() {
          return new String[]{PropertyUtil.suggestGetterName(project, field)};
        }

        @Override
        public String[] getSetterNames() {
          return new String[]{PropertyUtil.suggestSetterName(project, field)};
        }

        @Override
        @Nullable
        public PsiMethod[] getGetterPrototypes() {
          return isToEncapsulateGet() ? new PsiMethod[]{PropertyUtil.generateGetterPrototype(field)} : null;
        }

        @Override
        @Nullable
        public PsiMethod[] getSetterPrototypes() {
          return isToEncapsulateSet() ? new PsiMethod[]{PropertyUtil.generateSetterPrototype(field)} : null;
        }

        @Override
        public boolean isToEncapsulateGet() {
          return generateGetters;
        }

        @Override
        public boolean isToEncapsulateSet() {
          return generateSetters;
        }

        @Override
        public boolean isToUseAccessorsWhenAccessible() {
          return true;
        }

        @Override
        public String getFieldsVisibility() {
          return null;
        }

        @Override
        public String getAccessorsVisibility() {
          return PsiModifier.PUBLIC;
        }

        @Override
        public int getJavadocPolicy() {
          return DocCommentPolicy.MOVE;
        }
      });
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        Assert.assertEquals(conflicts, e.getMessage());
        return;
      } else {
        e.printStackTrace();
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

}
