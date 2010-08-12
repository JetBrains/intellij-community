package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

/**
 * @author dsl
 */
public class BindToElementTest extends CodeInsightTestCase {
  public static final String TEST_ROOT = PathManagerEx.getTestDataPath() + "/psi/impl/bindToElementTest/".replace('/', File.separatorChar);

  protected void setUp() throws Exception {
    super.setUp();
    VirtualFile root = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
          new File(new File(TEST_ROOT), "prj")
        );
      }
    });
    assertNotNull(root);
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final ContentEntry contentEntry = rootModel.addContentEntry(root);
    contentEntry.addSourceFolder(root, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }

  public void testSingleClassImport() throws Exception {
    doTest(new Runnable() {
      public void run() {
        PsiElement element = myFile.findElementAt(myEditor.getCaretModel().getOffset());
        final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement.class);
        final PsiClass aClassA = JavaPsiFacade.getInstance(myProject).findClass("p2.A", GlobalSearchScope.moduleScope(myModule));
        assertNotNull(aClassA);
        try {
          referenceElement.bindToElement(aClassA);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void testReplacingType() throws Exception {
    doTest(new Runnable() {
      public void run() {
        final PsiElement elementAt = myFile.findElementAt(myEditor.getCaretModel().getOffset());
        final PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(elementAt, PsiTypeElement.class);
        final PsiClass aClassA = JavaPsiFacade.getInstance(myProject).findClass("p2.A", GlobalSearchScope.moduleScope(myModule));
        assertNotNull(aClassA);
        final PsiElementFactory factory = myJavaFacade.getElementFactory();
        final PsiClassType type = factory.createType(aClassA);
        try {
          typeElement.replace(factory.createTypeElement(type));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  private void doTest(final Runnable runnable) throws Exception {
    final String relativeFilePath = "/psi/impl/bindToElementTest/" + getTestName(false);
    configureByFile(relativeFilePath + ".java");
    runnable.run();
    checkResultByFile(relativeFilePath + ".java.after");
  }
}
