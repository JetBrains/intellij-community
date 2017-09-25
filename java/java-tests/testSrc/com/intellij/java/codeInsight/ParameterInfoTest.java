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
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.hint.ParameterInfoComponent;
import com.intellij.codeInsight.hint.api.impls.AnnotationParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;
import org.jetbrains.annotations.NotNull;

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

  public void testParameterInfoDoesNotShowInternalJetbrainsAnnotations() {
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
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargs() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargsMultipleArgs() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargsSingleArg() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargsArray() {
    doTest2CandidatesWithPreselection();
  }

  public void testSuperConstructorCalls() {
    myFixture.configureByText("x.java",
                              "class A {\n" +
                              "       public A(String s, int... p) {}\n" +
                              "   }\n" +
                              "   class B extends A {\n" +
                              "       public B() {\n" +
                              "           super(<caret>\"a\", 1);\n" +
                              "       }\n" +
                              "   }");
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertNotNull(callExpression);
    assertNotNull(new MethodParameterInfoHandler().findElementForUpdatingParameterInfo(
      new MockUpdateParameterInfoContext(getEditor(), getFile(), new Object[] {callExpression.resolveMethodGenerics()})));
  }

  private void doTest2CandidatesWithPreselection() {
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
    MockUpdateParameterInfoContext updateParameterInfoContext = updateParameterInfo(handler, list, itemsToShow);
    assertTrue(updateParameterInfoContext.isUIComponentEnabled(0) || updateParameterInfoContext.isUIComponentEnabled(1));
  }

  @NotNull
  private MockUpdateParameterInfoContext updateParameterInfo(MethodParameterInfoHandler handler,
                                                             PsiExpressionList list,
                                                             Object[] itemsToShow) {
    MockUpdateParameterInfoContext updateParameterInfoContext = new MockUpdateParameterInfoContext(getEditor(), getFile(), itemsToShow);
    updateParameterInfoContext.setParameterOwner(list);
    handler.updateParameterInfo(list, updateParameterInfoContext);
    return updateParameterInfoContext;
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
    return parameterPresentation(0, parameterIndex);
  }

  private String parameterPresentation(int lineIndex, int parameterIndex) {
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = createContext();
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertTrue(itemsToShow[lineIndex] instanceof MethodCandidateInfo);
    PsiMethod method = ((MethodCandidateInfo)itemsToShow[lineIndex]).getElement();
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, parameterIndex);
    PsiSubstitutor substitutor = ((MethodCandidateInfo)itemsToShow[lineIndex]).getSubstitutor();
    return MethodParameterInfoHandler.updateMethodPresentation(method, substitutor, parameterContext);
  }

  private CreateParameterInfoContext createContext() {
    int caretOffset = getEditor().getCaretModel().getOffset();
    PsiExpressionList argList = PsiTreeUtil.findElementOfClassAtOffset(getFile(), caretOffset, PsiExpressionList.class, false);
    return new MockCreateParameterInfoContext(getEditor(), getFile()) {
      @Override
      public int getParameterListStart() {
        return argList == null ? caretOffset : argList.getTextRange().getStartOffset();
      }
    };
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

  public void testHighlightMethodJustChosenInCompletion() {
    myFixture.configureByText("a.java", "class Foo {" +
                                        "{ bar<caret> }" +
                                        "void bar(boolean a);" +
                                        "void bar(String a);" +
                                        "void bar(int a);" +
                                        "void bar2(int a);" +
                                        "}");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals("(String a)", LookupElementPresentation.renderElement(elements[1]).getTailText());
    myFixture.getLookup().setCurrentItem(elements[1]);
    myFixture.type('\n');

    assertEquals("<html>boolean a</html>", parameterPresentation(0, -1));
    assertEquals("<html>String a</html>", parameterPresentation(1, -1));
    assertEquals("<html>int a</html>", parameterPresentation(2, -1));

    checkHighlighted(1);
  }

  public void testHighlightConstructorJustChosenInCompletion() {
    Registry.get("java.completion.show.constructors").setValue(true);
    Disposer.register(myFixture.getTestRootDisposable(), () -> Registry.get("java.completion.show.constructors").setValue(false));

    myFixture.addClass("class Bar {" +
                       "Bar(boolean a);" +
                       "Bar(String a);" +
                       "Bar(int a);" +
                       "}; " +
                       "class Bar2 {}");
    myFixture.configureByText("a.java", "class Foo {{ new Bar<caret> }}");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals("(boolean a) (default package)", LookupElementPresentation.renderElement(elements[2]).getTailText());
    myFixture.getLookup().setCurrentItem(elements[2]);
    myFixture.type('\n');
    myFixture.checkResult("class Foo {{ new Bar(<caret>) }}");

    assertEquals("<html>boolean a</html>", parameterPresentation(0, -1));
    assertEquals("<html>String a</html>", parameterPresentation(1, -1));
    assertEquals("<html>int a</html>", parameterPresentation(2, -1));

    checkHighlighted(0);
  }

  public void testNoStrikeoutForSingleDeprecatedMethod() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class C { void m() { System.runFinalizersOnExit(true<caret>); } }");
    assertEquals("<html>boolean value</html>", parameterPresentation(-1));
  }

  private void checkHighlighted(int lineIndex) {
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = createContext();
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    Object[] itemsToShow = context.getItemsToShow();
    assertEquals(itemsToShow[lineIndex], updateParameterInfo(handler, list, itemsToShow).getHighlightedParameter());
  }

}