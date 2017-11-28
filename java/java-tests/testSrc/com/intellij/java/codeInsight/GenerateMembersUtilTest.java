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

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GenerateMembersUtilTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/generateMembersUtil/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testBeforeInner() {
    doTest();
  }

  public void testNoExtraEmptyLine() {
    doTest();
  }

  public void testNoRemoveComment() { doTest(); }

  public void testSCR5798() { doTest(); }

  public void testSCR6491() { doTest(); }
  public void testSCR6491_1() { doTest(); }
  public void testSCR6491_2() { doTest(); }

  public void testCaretAtDeclaration() { doTest(); }
  public void testCaretAfterComment() { doTest(); }

  private void doTest() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiMethod method = factory.createMethod("foo", PsiType.VOID);
    int offset = getEditor().getCaretModel().getOffset();
    List<GenerationInfo> list = Collections.singletonList(new PsiGenerationInfo<>(method));
    List<GenerationInfo> members =
      WriteAction.compute(() -> GenerateMembersUtil.insertMembersAtOffset(getFile(), offset, list));
    members.get(0).positionCaret(myEditor, true);
    checkResultByFile(null, BASE_PATH + getTestName(false) + "_after.java", true);
  }

  public void testSetupGeneratedMethodNotOverridingInitialBody() {
    String methodText = "public void tearDown() {\n //comment\n }";
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiMethod method = factory.createMethodFromText(methodText, null);
    GenerateMembersUtil.setupGeneratedMethod(method);
    assertEquals(methodText, method.getText());
    
    //empty template
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText(JavaLanguage.INSTANCE, "class A {void foo() {}}\n class B extends A {void foo() {}\n}");

    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiMethod newMethod = file.getClasses()[1].getMethods()[0];
      GenerateMembersUtil.setupGeneratedMethod(newMethod);
      assertEquals("@Override void foo() {\n" +
                   "    super.foo();\n" +
                   "    }", newMethod.getText());
    });

  }
}
