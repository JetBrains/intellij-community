// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ModifyAnnotationsTest {
  private static final Logger LOG = Logger.getInstance(ModifyAnnotationsTest.class);

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/modifyAnnotations";
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure( root);
  }

  @Test
  public void testReplaceAnnotation() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    //be sure not to load tree
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, fixture.getTestRootDisposable());
    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("Test", GlobalSearchScope.allScope(project)));
    assertNotNull(aClass);
    final PsiAnnotation[] annotations = ReadAction.computeBlocking(() -> aClass.getModifierList().getAnnotations());
    assertEquals(1, annotations.length);
    assertEquals("A", ReadAction.computeBlocking(() -> annotations[0].getNameReferenceElement().getReferenceName()));
    final PsiAnnotation newAnnotation =
      ReadAction.computeBlocking(() -> fixture.getJavaFacade().getElementFactory().createAnnotationFromText("@B", null));
    //here the tree is going to be loaded
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, fixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        annotations[0].replace(newAnnotation);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), null, null));

    assertEquals("@B", ReadAction.computeBlocking(() -> aClass.getModifierList().getText()));
  }
}
