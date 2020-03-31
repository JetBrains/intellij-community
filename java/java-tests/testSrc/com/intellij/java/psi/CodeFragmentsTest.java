// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.ref.GCUtil;

@HeavyPlatformTestCase.WrapInCommand
public class CodeFragmentsTest extends LightIdeaTestCase {
  public void testAddImport() {
    Project project = getProject();
    PsiCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("AAA.foo()", null, null, false);
    JavaPsiFacadeEx javaPsiFacade = JavaPsiFacadeEx.getInstanceEx(project);
    PsiClass arrayListClass = javaPsiFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(project));
    PsiReference ref = fragment.findReferenceAt(0);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ref.bindToElement(arrayListClass);
    });

    assertEquals("ArrayList.foo()", fragment.getText());
  }

  public void testDontLoseDocument() {
    Project project = getProject();
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true);
    Runnable writeAction = () -> ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
      document.insertString(1, "b");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      assertEquals("ab", fragment.getText());
      assertEquals("ab", fragment.getExpression().getText());

      //noinspection UnusedAssignment
      document = null;
    });
    CommandProcessor.getInstance().executeCommand(project, writeAction, "", "");
    GCUtil.tryGcSoftlyReachableObjects();
    assertEquals("ab", PsiDocumentManager.getInstance(project).getDocument(fragment).getText());
  }

  public void testDontRecreateFragmentPsi() {
    Project project = getProject();
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true);
    VirtualFile file = fragment.getViewProvider().getVirtualFile();
    assertInstanceOf(file, LightVirtualFile.class);

    ApplicationManager.getApplication()
      .runWriteAction(() -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true));

    assertSame(fragment, PsiManager.getInstance(project).findFile(file));
    assertTrue(fragment.isValid());
  }

  public void testCorrectFragmentPsiAfterGc() {
    Project project = getProject();
    VirtualFile file =
      JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true).getViewProvider().getVirtualFile();
    assertInstanceOf(file, LightVirtualFile.class);

    GCUtil.tryGcSoftlyReachableObjects();

    assertInstanceOf(PsiManager.getInstance(project).findFile(file), PsiExpressionCodeFragment.class);
  }
}
