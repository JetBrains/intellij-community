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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.util.ref.GCUtil;

@PlatformTestCase.WrapInCommand
public class CodeFragmentsTest extends PsiTestCase{
  public void testAddImport() {
    PsiCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("AAA.foo()", null, null, false);
    PsiClass arrayListClass = myJavaFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject()));
    PsiReference ref = fragment.findReferenceAt(0);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ref.bindToElement(arrayListClass);
    });

    assertEquals("ArrayList.foo()", fragment.getText());
  }

  public void testDontLoseDocument() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("a", null, null, true);
    ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
      document.insertString(1, "b");
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      assertEquals("ab", fragment.getText());
      assertEquals("ab", fragment.getExpression().getText());

      //noinspection UnusedAssignment
      document = null;
    });


    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertEquals("ab", PsiDocumentManager.getInstance(myProject).getDocument(fragment).getText());
  }

  public void testDontRecreateFragmentPsi() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("a", null, null, true);
    VirtualFile file = fragment.getViewProvider().getVirtualFile();
    assertInstanceOf(file, LightVirtualFile.class);

    ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(EmptyRunnable.getInstance(), false, true));


    assertSame(fragment, PsiManager.getInstance(myProject).findFile(file));
    assertTrue(fragment.isValid());
  }

  public void testCorrectFragmentPsiAfterGc() {
    VirtualFile file = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("a", null, null, true).getViewProvider().getVirtualFile();
    assertInstanceOf(file, LightVirtualFile.class);

    GCUtil.tryGcSoftlyReachableObjects();

    assertInstanceOf(PsiManager.getInstance(myProject).findFile(file), PsiExpressionCodeFragment.class);
  }
}
