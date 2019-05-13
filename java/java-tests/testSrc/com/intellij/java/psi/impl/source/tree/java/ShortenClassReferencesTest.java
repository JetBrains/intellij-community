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

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author dsl
 */
public class ShortenClassReferencesTest extends LightCodeInsightFixtureTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/shortenClassRefs";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  public void testSCR22368() { doTest(); }
  public void testSCR22368_1() {
    JavaPsiFacadeEx facade = JavaPsiFacadeEx.getInstanceEx(getProject());
    PsiElementFactory factory = facade.getElementFactory();
    PsiClass aClass = factory.createClass("X");
    PsiMethod methodFromText = factory.createMethodFromText("void method() {\n" +
                                                            "    IntelliJIDEARulezz<\n" +
                                                            "}", null);
    PsiMethod method = (PsiMethod)aClass.add(methodFromText);
    PsiCodeBlock body = method.getBody();
    assertNotNull(body);
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)body.getStatements()[0];
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)declarationStatement.getFirstChild().getFirstChild();
    PsiClass javaUtilListClass = facade.findClass(CommonClassNames.JAVA_UTIL_LIST);
    assertNotNull(javaUtilListClass);
    PsiElement resultingElement = referenceElement.bindToElement(javaUtilListClass);
    assertEquals("List<", resultingElement.getText());
    assertEquals("void method() {\n" +
                 "    List<\n" +
                 "}", method.getText());
  }

  public void testSCR37254() { doTest(); }

  public void testTypeAnnotatedRef() {
    myFixture.configureByFile("pkg/TA.java");
    doTest();
    for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiParameter.class)) {
      PsiTypeElement typeElement = parameter.getTypeElement();
      assertNotNull(typeElement);
      PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
      assertNotNull(ref);
      PsiAnnotation annotation = PsiTreeUtil.getChildOfType(ref, PsiAnnotation.class);
      assertNull(annotation);
    }
  }

  public void testOuterClassReferenceInInstanceof() {
    myFixture.configureByText("a.java", "class Outer<T> {class Inner {} {boolean b = new Inner() instanceof Outer.Inner;}}\n" +
                                        "class Outer1 {class Inner {} {boolean b = new Inner() instanceof Outer1.Inner;}}");
    doShortenRefs();
    myFixture.checkResult("class Outer<T> {class Inner {} {boolean b = new Inner() instanceof Outer.Inner;}}\n" +
                          "class Outer1 {class Inner {} {boolean b = new Inner() instanceof Inner;}}");
  }

  public void testConflictingClassInSamePackage() {
    myFixture.addClass("package p1; public class Outer{}");
    myFixture.addClass("package p2; public class Outer{}");
    myFixture.configureByText("a.java", "package p2; class Outer1 extends p1.Outer {}");
    doShortenRefs();
    myFixture.checkResult("package p2;\n" +
                          "\n" +
                          "import p1.Outer;\n" +
                          "\n" +
                          "class Outer1 extends Outer {}");
  }

  public void testWhiteSpaceForMovedTypeAnnotations() {
    myFixture.configureByFile(getTestName(false) + ".java");
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertTrue(elementAtCaret instanceof PsiParameter);
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> {
                                               PsiTypeElement typeElement = (PsiTypeElement)JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(((PsiParameter)elementAtCaret).getTypeElement());
                                               assertTrue(typeElement != null && typeElement.isValid());
                                               assertEquals("List<String>", typeElement.getText());
                                             });

    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    doShortenRefs();
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doShortenRefs() {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(myFixture.getFile());
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }), "", "");
  }
}
