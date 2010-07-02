package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ModifyAnnotationsTest extends PsiTestCase {
  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {

        public void run() {
          try {
            String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/modifyAnnotations";
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17("mock 1.5"));
            PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("mock 1.5");
  }

  public void testReplaceAnnotation() throws Exception {
    //be sure not to load tree
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.ALL);
    PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    final PsiAnnotation[] annotations = aClass.getModifierList().getAnnotations();
    assertEquals(1, annotations.length);
    assertEquals("A", annotations[0].getNameReferenceElement().getReferenceName());
    final PsiAnnotation newAnnotation = myJavaFacade.getElementFactory().createAnnotationFromText("@B", null);
    //here the tree is going to be loaded
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              annotations[0].replace(newAnnotation);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, null, null);

    assertEquals("@B", aClass.getModifierList().getText());
  }
}
