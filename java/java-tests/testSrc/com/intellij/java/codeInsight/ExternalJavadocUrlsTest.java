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

import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExternalJavadocUrlsTest extends LightCodeInsightFixtureTestCase {
  private static final ProjectDescriptor DESCRIPTOR = new ProjectDescriptor(LanguageLevel.HIGHEST) {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk17();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      setMockJavadocUrl(model);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }

  protected static void setMockJavadocUrl(@NotNull ModifiableRootModel model) {
    model.getModuleExtension(JavaModuleExternalPaths.class).setJavadocUrls(new String[]{"http://doc"});
  }

  public void testVarargs() {
    doTest("class Test {\n" +
           "  void <caret>foo(Class<?>... cl) { }\n" +
           "}",

           "foo(java.lang.Class...)", "foo-java.lang.Class...-");

  }

  public void testTypeParams() {
    doTest("class Test {\n" +
           "  <T> void <caret>sort(T[] a, Comparator<? super T> c) { }\n" +
           "}\n" +
           "class Comparator<X>{}",

           "sort(T[],Comparator)", "sort-T:A-Comparator-", "sort(T[], Comparator)");
  }

  protected void doTest(String text, String... expected) {
    myFixture.configureByText("Test.java", text);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    PsiMethod member = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class, false);
    assertNotNull(member);
    List<String> urls = JavaDocumentationProvider.getExternalJavaDocUrl(member);
    assertNotNull(urls);
    List<String> actual = ContainerUtil.map(urls, url -> url.substring(url.indexOf('#') + 1));
    assertOrderedEquals(actual, expected);
  }
}
