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
package com.intellij.java.codeInsight;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ClsGenerics18HighlightingTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    ClsGenericsHighlightingTestUtil.clsGenericsHighlightingFixture(LanguageLevel.JDK_1_8);
  private String myTestName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    myTestName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  private void doTest() {
    ClsGenericsHighlightingTestUtil.doTest(codeInsightFixture.get(), myTestName);
  }

  @Test
  public void testIDEA121866() { doTest(); }

  @Test
  public void testIDEA127714() { doTest(); }

  @Test
  public void testoverload() { doTest(); }

  @Test
  public void testIDEA151367() {
    doTest();
  }

  @Test
  public void testIDEA157254() {
    doTest();
  }

  @Test
  public void testOuterClassTypeArgs() {
    doTest();
  }

  @Test
  public void testCaptureContext() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myTestName;
    ClsGenericsHighlightingTestUtil.addLibrary(myFixture, name + ".jar", name + "-sources.jar");
    Project project = myFixture.getProject();
    PsiElement navigationElement = ReadAction.computeBlocking(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("a.Pair", GlobalSearchScope.allScope(project));
      assertNotNull(aClass);
      PsiFile containingFile = aClass.getContainingFile();
      return containingFile.getNavigationElement();
    });
    assertInstanceOf(PsiFileImpl.class, navigationElement);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.openFileInEditor(((PsiFile)navigationElement).getVirtualFile());
      myFixture.checkHighlighting();
    });
  }
}
