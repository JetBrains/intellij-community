/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.hint.ParameterInfoComponent;
import com.intellij.codeInsight.hint.api.impls.AnnotationParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;

import java.io.IOException;

public class ParameterInfoTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/parameterInfo/";
  }

  public void testPrivateMethodOfEnclosingClass() { doTest("param"); }
  public void testNotAccessible() { doTest("param"); }

  private void doTest(String paramsList) {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertTrue(itemsToShow.length > 0);
    Object[] params = handler.getParametersForDocumentation(itemsToShow[0], context);
    assertNotNull(params);
    String joined = StringUtil.join(params, o -> ((PsiParameter)o).getName(), ",");
    assertEquals(paramsList, joined);
  }

  public void testParameterInfoDoesNotShowInternalJetbrainsAnnotations() throws IOException {
    myFixture.configureByText("x.java", "class X { void f(@org.intellij.lang.annotations.Flow int i) { f(<caret>0); }}");

    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiMethod method = PsiTreeUtil.getParentOfType(getFile().findElementAt(context.getOffset()), PsiMethod.class);
    assertNotNull(method);
    MockParameterInfoUIContext<PsiMethod> uiContext = new MockParameterInfoUIContext<>(method);
    String list = MethodParameterInfoHandler.updateMethodPresentation(method, PsiSubstitutor.EMPTY, uiContext);
    assertEquals("int i", list);
    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(method.getParameterList().getParameters()[0], false, null);
    assertEquals(1, annotations.length);
  }

  public void testSelectionWithGenerics() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, -1);
    MockUpdateParameterInfoContext updateParameterInfoContext = new MockUpdateParameterInfoContext(getEditor(), getFile(), itemsToShow);
    updateParameterInfoContext.setParameterOwner(list);
    handler.updateParameterInfo(list, updateParameterInfoContext);
    assertTrue(updateParameterInfoContext.isUIComponentEnabled(0) || updateParameterInfoContext.isUIComponentEnabled(1));
  }

  public void testStopAtAccessibleStaticCorrectCandidate() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertEquals(0, ((MethodCandidateInfo)itemsToShow[0]).getElement().getParameterList().getParametersCount());
  }

  public void testAfterGenericsInsideCall() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    PsiMethod method = ((MethodCandidateInfo)itemsToShow[0]).getElement();
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, 1);
    parameterContext.setUIComponentEnabled(true);
    PsiSubstitutor substitutor = ((MethodCandidateInfo)itemsToShow[0]).getSubstitutor();
    String presentation = MethodParameterInfoHandler.updateMethodPresentation(method, substitutor, parameterContext);
    assertEquals("<html>Class&lt;T&gt; type, <b>boolean tags</b></html>", presentation);
  }

  public void testNoParams() { doTestPresentation("<html>&lt;no parameters&gt;</html>", -1); }
  public void testGenericsInsideCall() { doTestPresentation("<html>List&lt;String&gt; param</html>", -1); }
  public void testGenericsOutsideCall() { doTestPresentation("<html>List&lt;String&gt; param</html>", -1); }
  public void testIgnoreVarargs() { doTestPresentation("<html>Class&lt;T&gt; a, <b>Class&lt;? extends CharSequence&gt;... stopAt</b></html>", 1); }

  private void doTestPresentation(String expectedString, int parameterIndex) {
    myFixture.configureByFile(getTestName(false) + ".java");
    String presentation = parameterPresentation(parameterIndex);
    assertEquals(expectedString, presentation);
  }

  private String parameterPresentation(int parameterIndex) {
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    PsiMethod method = ((MethodCandidateInfo)itemsToShow[0]).getElement();
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, parameterIndex);
    PsiSubstitutor substitutor = ((MethodCandidateInfo)itemsToShow[0]).getSubstitutor();
    return MethodParameterInfoHandler.updateMethodPresentation(method, substitutor, parameterContext);
  }

  public void testAnnotationWithGenerics() {
    myFixture.configureByFile(getTestName(false) + ".java");
    String text = annoParameterPresentation();
    assertEquals("<html>Class&lt;List&lt;String[]&gt;&gt; <b>value</b>()</html>", text);
  }

  private String annoParameterPresentation() {
    AnnotationParameterInfoHandler handler = new AnnotationParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiAnnotationParameterList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof PsiAnnotationMethod);
    PsiAnnotationMethod method = (PsiAnnotationMethod)itemsToShow[0];
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, -1);
    return AnnotationParameterInfoHandler.updateUIText(method, parameterContext);
  }

  public void testParameterAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Documented @Target({ElementType.PARAMETER}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals("<html>@TA String s</html>", parameterPresentation(-1));
  }

  public void testParameterUndocumentedAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Target({ElementType.PARAMETER}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals("<html>String s</html>", parameterPresentation(-1));
  }

  public void testParameterTypeAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Documented @Target({ElementType.PARAMETER, ElementType.TYPE_USE}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals("<html>@TA String s</html>", parameterPresentation(-1));
  }

  public void testParameterUndocumentedTypeAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Target({ElementType.PARAMETER, ElementType.TYPE_USE}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals("<html>@TA String s</html>", parameterPresentation(-1));
  }
}