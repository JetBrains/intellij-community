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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GenerateMembersUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @NonNls private static final String BASE_PATH = "/codeInsight/generateMembersUtil/";

  @Test
  public void testBeforeInner() {
    doTest();
  }

  @Test
  public void testNoExtraEmptyLine() {
    doTest();
  }

  @Test
  public void testNoRemoveComment() { doTest(); }

  @Test
  public void testSCR5798() { doTest(); }

  @Test
  public void testSCR6491() { doTest(); }
  @Test
  public void testSCR6491_1() { doTest(); }
  @Test
  public void testSCR6491_2() { doTest(); }

  @Test
  public void testCaretAtDeclaration() { doTest(); }
  @Test
  public void testCaretAfterComment() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myFixture.getProject());
      PsiMethod method = factory.createMethod("foo", PsiTypes.voidType());
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      List<GenerationInfo> list = Collections.singletonList(new PsiGenerationInfo<>(method));
      List<GenerationInfo> members =
        WriteAction.compute(() -> GenerateMembersUtil.insertMembersAtOffset(myFixture.getFile(), offset, list));
      members.get(0).positionCaret(myFixture.getEditor(), true);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java", true);
    });
  }

  @Test
  public void testSetupGeneratedMethodNotOverridingInitialBody() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      String methodText = "public void tearDown() {\n //comment\n }";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myFixture.getProject());
      PsiMethod method = factory.createMethodFromText(methodText, null);
      GenerateMembersUtil.setupGeneratedMethod(method);
      assertEquals(methodText, method.getText());
    
      //empty template
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(myFixture.getProject())
        .createFileFromText(JavaLanguage.INSTANCE, "class A {void foo() {}}\n class B extends A {void foo() {}\n}");

      ApplicationManager.getApplication().runWriteAction(() -> {
        PsiMethod newMethod = file.getClasses()[1].getMethods()[0];
        GenerateMembersUtil.setupGeneratedMethod(newMethod);
        assertEquals("""
                       @Override void foo() {
                           super.foo();
                           }""", newMethod.getText());
      });
    });
  }

  @Test
  public void testSetupGeneratedMethodExcludingRedundantThrown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(myFixture.getProject())
        .createFileFromText(JavaLanguage.INSTANCE, "class A {void foo() {}}\n class B extends A {void foo() throws Exception {}\n}");

      ApplicationManager.getApplication().runWriteAction(() -> {
        PsiMethod newMethod = file.getClasses()[1].getMethods()[0];
        GenerateMembersUtil.setupGeneratedMethod(newMethod);
        assertEquals("""
                       @Override void foo()  {
                           super.foo();
                           }""", newMethod.getText());
      });
    });
  }
}
