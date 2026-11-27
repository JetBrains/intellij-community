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
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@HeavyPlatformTestCase.WrapInCommand
@TestApplication
public class BindToElementTest {
  private static final Logger LOG = Logger.getInstance(BindToElementTest.class);
  public static final String TEST_ROOT = JavaTestUtil.getJavaTestDataPath() + "/psi/impl/bindToElementTest/".replace('/', File.separatorChar);

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    VirtualFile root = WriteCommandAction.runWriteCommandAction(null, (Computable<VirtualFile>)() -> StandardFileSystems.local().refreshAndFindFileByPath(
      new File(new File(TEST_ROOT), "prj").getAbsolutePath()
    ));
    assertNotNull(root);
    PsiTestUtil.addSourceRoot(heavyFixture.get().getModule(), root);
  }

  @Test
  public void testSingleClassImport() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    doTest(() -> {
      final PsiJavaCodeReferenceElement referenceElement = ReadAction.computeBlocking(() -> {
        PsiElement element = fixture.getFile().findElementAt(fixture.getEditor().getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement.class);
      });
      final PsiClass aClassA = ReadAction.computeBlocking(
        () -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("p2.A", GlobalSearchScope.moduleScope(fixture.getModule())));
      assertNotNull(aClassA);
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
        try {
          referenceElement.bindToElement(aClassA);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      });
    });
  }

  @Test
  public void testReplacingType() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    doTest(() -> {
      final PsiTypeElement typeElement = ReadAction.computeBlocking(() -> {
        final PsiElement elementAt = fixture.getFile().findElementAt(fixture.getEditor().getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(elementAt, PsiTypeElement.class);
      });
      final PsiClass aClassA = ReadAction.computeBlocking(
        () -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("p2.A", GlobalSearchScope.moduleScope(fixture.getModule())));
      assertNotNull(aClassA);
      final PsiElementFactory factory = fixture.getJavaFacade().getElementFactory();
      final PsiClassType type = ReadAction.computeBlocking(() -> factory.createType(aClassA));
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
        try {
          typeElement.replace(factory.createTypeElement(type));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      });
    });
  }

  private void doTest(final Runnable runnable) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final String relativeFilePath = "/psi/impl/bindToElementTest/" + fixture.getTestName(false);
    fixture.configureByFile(relativeFilePath + ".java");
    runnable.run();
    fixture.checkResultByFile(relativeFilePath + ".java.after");
  }
}
