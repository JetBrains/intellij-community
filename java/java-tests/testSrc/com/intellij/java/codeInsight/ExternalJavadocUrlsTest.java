// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExternalJavadocUrlsTest extends LightJavaCodeInsightFixtureTestCase {
  private static final ProjectDescriptor DESCRIPTOR = new ProjectDescriptor(LanguageLevel.JDK_14) {
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

  public void testConstructor() {
    doTest("class Test {\n" +
           "  Test<caret>() { }\n" +
           "}",
           "<init>()", "Test--", "Test()");
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
