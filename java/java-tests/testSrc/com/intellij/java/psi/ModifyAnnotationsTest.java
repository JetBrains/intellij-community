// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ModifyAnnotationsTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/modifyAnnotations";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure( root);
  }

  public void testReplaceAnnotation() {
    //be sure not to load tree
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, getTestRootDisposable());
    PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    final PsiAnnotation[] annotations = aClass.getModifierList().getAnnotations();
    assertEquals(1, annotations.length);
    assertEquals("A", annotations[0].getNameReferenceElement().getReferenceName());
    final PsiAnnotation newAnnotation = myJavaFacade.getElementFactory().createAnnotationFromText("@B", null);
    //here the tree is going to be loaded
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());
    CommandProcessor.getInstance().executeCommand(myProject, () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        annotations[0].replace(newAnnotation);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), null, null);

    assertEquals("@B", aClass.getModifierList().getText());
  }
}
