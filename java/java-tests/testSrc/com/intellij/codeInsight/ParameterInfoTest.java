/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.hint.ParameterInfoComponent;
import com.intellij.codeInsight.hint.api.impls.AnnotationParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import junit.framework.Assert;

import java.io.IOException;

public class ParameterInfoTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/parameterInfo/";

  private void doTest(String paramsList) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    String joined = invokeParameterInfo();
    assertEquals(paramsList, joined);
  }

  private static String invokeParameterInfo() {
    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    assertNotNull(context.getItemsToShow());
    assertTrue(context.getItemsToShow().length > 0);
    Object[] params = handler.getParametersForDocumentation(context.getItemsToShow()[0], context);
    assertNotNull(params);
    return StringUtil.join(params, new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        return ((PsiParameter)o).getName();
      }
    }, ",");
  }

  public void testPrivateMethodOfEnclosingClass() throws Exception {
    doTest("param");
  }

  public void testNotAccessible() throws Exception {
    doTest("param");
  }

  public void testParameterInfoDoesNotShowInternalJetbrainsAnnotations() throws IOException {
    configureFromFileText("x.java", "class X { void f(@org.intellij.lang.annotations.Flow int i) { f(<caret>0); }}");

    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);

    PsiMethod method = PsiTreeUtil.getParentOfType(myFile.findElementAt(context.getOffset()), PsiMethod.class);
    final String list = MethodParameterInfoHandler.updateMethodPresentation(method, PsiSubstitutor.EMPTY, new MockParameterInfoUIContext<PsiMethod>(method));

    assertEquals("int i", list);

    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(method.getParameterList().getParameters()[0], false, null);
    assertEquals(1, annotations.length);
  }

  public void testNoParams() throws Exception {
    doTestPresentation("<html>&lt;no parameters&gt;</html>");
  }

  public void testGenericsInsideCall() throws Exception {
    doTestPresentation("<html>List&lt;String&gt; param</html>");
  }

  public void testSelectionWithGenerics() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    final Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    final ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, myEditor, handler, -1);
    final Boolean [] enabled = new Boolean[itemsToShow.length];
    final MockUpdateParameterInfoContext updateParameterInfoContext = new MockUpdateParameterInfoContext(myEditor, myFile){
      @Override
      public Object[] getObjectsToView() {
        return itemsToShow;
      }

      @Override
      public void setUIComponentEnabled(int index, boolean b) {
        enabled[index] = b;
      }
    };
    updateParameterInfoContext.setParameterOwner(list);
    handler.updateParameterInfo(list, updateParameterInfoContext);
    assertTrue(ArrayUtilRt.find(enabled, Boolean.TRUE) > -1);
  }

  public void testAfterGenericsInsideCall() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    final Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    final PsiMethod method = ((MethodCandidateInfo)itemsToShow[0]).getElement();
    final ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, myEditor, handler, 1);
    parameterContext.setUIComponentEnabled(true);
    Assert.assertEquals("<html>Class&lt;T&gt; type, <b>boolean tags</b></html>",
                        MethodParameterInfoHandler
                          .updateMethodPresentation(method, ((MethodCandidateInfo)itemsToShow[0]).getSubstitutor(), parameterContext));
  }

  public void testGenericsOutsideCall() throws Exception {
    doTestPresentation("<html>List&lt;String&gt; param</html>");
  }

  private void doTestPresentation(String expectedString) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    final Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    final PsiMethod method = ((MethodCandidateInfo)itemsToShow[0]).getElement();
    final ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, myEditor, handler, -1);
    Assert.assertEquals(expectedString,
                        MethodParameterInfoHandler
                          .updateMethodPresentation(method, ((MethodCandidateInfo)itemsToShow[0]).getSubstitutor(), parameterContext));
  }

  public void testAnnotationWithGenerics() throws Exception {
    doTestAnnotationPresentation("<html>Class&lt;List&lt;String[]&gt;&gt; <b>value</b>()</html>");
  }

  private void doTestAnnotationPresentation(String expectedString) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    String text = invokeParameterInfoForAnnotations();
    Assert.assertEquals(expectedString, text);
  }

  private static String invokeParameterInfoForAnnotations() {
    final AnnotationParameterInfoHandler handler = new AnnotationParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiAnnotationParameterList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    final Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof PsiAnnotationMethod);
    final PsiAnnotationMethod method = (PsiAnnotationMethod)itemsToShow[0];
    final ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, myEditor, handler, -1);
    return AnnotationParameterInfoHandler.updateUIText(method, parameterContext);
  }
}
