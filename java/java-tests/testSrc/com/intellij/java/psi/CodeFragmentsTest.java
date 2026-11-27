// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CodeFragmentsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  @Test
  public void testAddImport() {
    Project project = projectFixture.get().getProject();
    PsiCodeFragment fragment = ReadAction.computeBlocking(
      () -> JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("AAA.foo()", null, null, false));
    JavaPsiFacadeEx javaPsiFacade = JavaPsiFacadeEx.getInstanceEx(project);
    PsiClass arrayListClass = ReadAction.computeBlocking(() -> javaPsiFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(project)));
    PsiReference ref = ReadAction.computeBlocking(() -> fragment.findReferenceAt(0));
    WriteAction.runAndWait(() -> {
      ref.bindToElement(arrayListClass);
    });

    assertEquals("ArrayList.foo()", ReadAction.computeBlocking(() -> fragment.getText()));
  }

  @Test
  public void testDontLoseDocument() {
    Project project = projectFixture.get().getProject();
    PsiExpressionCodeFragment fragment = ReadAction.computeBlocking(
      () -> JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true));
    Runnable writeAction = () -> WriteAction.run(() -> {
      Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
      document.insertString(1, "b");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      assertEquals("ab", fragment.getText());
      assertEquals("ab", fragment.getExpression().getText());

      //noinspection UnusedAssignment
      document = null;
    });
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, writeAction, "", ""));
    GCUtil.tryGcSoftlyReachableObjects();
    assertEquals("ab", ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getDocument(fragment).getText()));
  }

  @Test
  public void testDontRecreateFragmentPsi() {
    Project project = projectFixture.get().getProject();
    PsiExpressionCodeFragment fragment = ReadAction.computeBlocking(
      () -> JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true));
    VirtualFile file = fragment.getViewProvider().getVirtualFile();
    assertInstanceOf(LightVirtualFile.class, file);

    WriteAction.runAndWait(() -> ProjectRootManagerEx.getInstanceEx(project).
      makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.NO_RESCAN_NEEDED));

    ReadAction.runBlocking(() -> {
      assertSame(fragment, PsiManager.getInstance(project).findFile(file));
      assertTrue(fragment.isValid());
    });
  }

  @Test
  public void testCorrectFragmentPsiAfterGc() {
    Project project = projectFixture.get().getProject();
    VirtualFile file = ReadAction.computeBlocking(
      () -> JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("a", null, null, true).getViewProvider().getVirtualFile());
    assertInstanceOf(LightVirtualFile.class, file);

    GCUtil.tryGcSoftlyReachableObjects();

    assertInstanceOf(PsiExpressionCodeFragment.class, ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(file)));
  }
}
