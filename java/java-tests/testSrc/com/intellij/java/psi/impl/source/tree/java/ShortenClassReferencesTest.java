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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ShortenClassReferencesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(BASE_PATH);
  }

  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/shortenClassRefs";

  @Test
  public void testSCR22368() {
    doTest();
  }
  @Test
  public void testSCR22368_1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      JavaPsiFacadeEx facade = JavaPsiFacadeEx.getInstanceEx(myFixture.getProject());
      PsiElementFactory factory = facade.getElementFactory();
      PsiClass aClass = factory.createClass("X");
      PsiMethod methodFromText = factory.createMethodFromText("""
                                                                void method() {
                                                                    IntelliJIDEARulezz<>
                                                                }""", null);
      PsiMethod method = (PsiMethod)aClass.add(methodFromText);
      PsiCodeBlock body = method.getBody();
      assertNotNull(body);
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)body.getStatements()[0];
      PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)declarationStatement.getFirstChild().getFirstChild();
      PsiClass javaUtilListClass = facade.findClass(CommonClassNames.JAVA_UTIL_LIST);
      assertNotNull(javaUtilListClass);
      PsiElement resultingElement = referenceElement.bindToElement(javaUtilListClass);
      assertEquals("List<>", resultingElement.getText());
      assertEquals("""
                     void method() {
                         List<>
                     }""", method.getText());
    });
  }

  @Test
  public void testSCR37254() {
    doTest();
  }

  @Test
  public void testTypeAnnotatedRef() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("pkg/TA.java");
    doTest();
    ReadAction.runBlocking(() -> {
      for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiParameter.class)) {
        PsiTypeElement typeElement = parameter.getTypeElement();
        assertNotNull(typeElement);
        PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
        assertNotNull(ref);
        PsiAnnotation annotation = PsiTreeUtil.getChildOfType(ref, PsiAnnotation.class);
        assertNull(annotation);
      }
    });
  }

  @Test
  public void testOuterClassReferenceInInstanceof() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", "class Outer<T> {class Inner {} {boolean b = new Inner() instanceof Outer.Inner;}}\n" +
                                        "class Outer1 {class Inner {} {boolean b = new Inner() instanceof Outer1.Inner;}}");
    doShortenRefs();
    myFixture.checkResult("class Outer<T> {class Inner {} {boolean b = new Inner() instanceof Outer.Inner;}}\n" +
                          "class Outer1 {class Inner {} {boolean b = new Inner() instanceof Inner;}}");
  }

  @Test
  public void testConflictingClassInSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class Outer{}");
    myFixture.addClass("package p2; public class Outer{}");
    myFixture.configureByText("a.java", "package p2; class Outer1 extends p1.Outer {}");
    doShortenRefs();
    myFixture.checkResult("""
                            package p2;

                            import p1.Outer;

                            class Outer1 extends Outer {}""");
  }

  @Test
  public void testWhiteSpaceForMovedTypeAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    PsiElement elementAtCaret = ReadAction.computeBlocking(() -> myFixture.getElementAtCaret());
    assertTrue(elementAtCaret instanceof PsiParameter);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                             () -> {
                                               PsiTypeElement typeElement = (PsiTypeElement)JavaCodeStyleManager.getInstance(myFixture.getProject()).shortenClassReferences(((PsiParameter)elementAtCaret).getTypeElement());
                                               assertTrue(typeElement.isValid());
                                               assertEquals("List<String>", typeElement.getText());
                                             });

    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    doShortenRefs();
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
  }

  private void doShortenRefs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
        JavaCodeStyleManager.getInstance(myFixture.getProject()).shortenClassReferences(myFixture.getFile());
        PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      }), "", "");
    });
  }
}
