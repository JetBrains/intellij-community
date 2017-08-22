/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17("mock 1.5"));
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
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
