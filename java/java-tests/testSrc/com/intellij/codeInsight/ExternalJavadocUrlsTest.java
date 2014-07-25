/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class ExternalJavadocUrlsTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PsiTestUtil.setJavadocUrls(myModule, "http://doc" );
  }

  public void testVarargs() {
    doTest("class Test {\n" +
           "  void <caret>foo(Class<?>... cl) { }\n" +
           "}",

           "foo-java.lang.Class...-", "foo-java.lang.Class<?>...-", "foo(java.lang.Class...)", "foo(java.lang.Class<?>...)");

  }

  public void testTypeParams() {
    doTest("class Test {\n" +
           "  <T> void <caret>sort(T[] a, Comparator<? super T> c) { }\n" +
           "}\n" +
           "class Comparator<X>{}",

           "sort-T:A-Comparator-", "sort-T:A-Comparator<? super T>-", "sort(T[], Comparator)", "sort(T[], Comparator<? super T>)");
  }

  protected void doTest(String text, String... expected) {
    myFixture.configureByText("Test.java", text);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    PsiMethod member = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class, false);
    assertNotNull(member);
    List<String> urls = JavaDocumentationProvider.getExternalJavaDocUrl(member);
    assertNotNull(urls);
    List<String> actual = ContainerUtil.map(urls, new Function<String, String>() {
      @Override
      public String fun(String url) {
        return url.substring(url.indexOf('#') + 1);
      }
    });
    assertOrderedEquals(actual, expected);
  }
}
