// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.JavadocOrderRootType;
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
  private static final String DOC_URL = "https://docs.oracle.com/en/java/javase/14/docs/api/";
  private static final ProjectDescriptor DESCRIPTOR = new ProjectDescriptor(LanguageLevel.JDK_14) {
    @Override
    public Sdk getSdk() {
       Sdk sdk = IdeaTestUtil.getMockJdk21();
       SdkModificator modificator = sdk.getSdkModificator();
       modificator.addRoot(DOC_URL, new JavadocOrderRootType());
       ApplicationManager.getApplication().runWriteAction(() -> modificator.commitChanges());

       return sdk;
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
    doTestMethod("""
             class Test {
               void <caret>foo(Class<?>... cl) { }
             }""",

           "foo(java.lang.Class...)", "foo-java.lang.Class...-");

  }

  public void testTypeParams() {
    doTestMethod("""
             class Test {
               <T> void <caret>sort(T[] a, Comparator<? super T> c) { }
             }
             class Comparator<X>{}""",

           "sort(T[],Comparator)", "sort-T:A-Comparator-", "sort(T[], Comparator)");
  }

  public void testConstructor() {
    doTestMethod("""
             class Test {
               Test<caret>() { }
             }""",
           "<init>()", "Test--", "Test()");
  }
  
  public void testImplicitClass() {
    doTestMethod("""
             void <caret>main() {}
             """, "main()", "main--");
  }
  
  public void testImplicitClassWithPackage() {
    doTestMethod("""
             package foo.bar;
             
             void <caret>main() {}
             """);
  }

  /// See IDEA-366031
  public void testDocumentationPath() {
    doTestElement("""
      void function() {
        Str<caret>ing toto = null;
      }
      """,
       "https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/lang/String.html", 
      "https://docs.oracle.com/en/java/javase/14/docs/api/java/lang/String.html");
  }

  protected void doTestElement(String text, String... expected) {
    myFixture.configureByText("Test.java", text);
    doTest(myFixture.getElementAtCaret(), expected);
  }

  protected void doTestMethod(String text, String... expected) {
    myFixture.configureByText("Test.java", text);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    doTest(PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class, false), expected);
  }

  private static void doTest(PsiElement element, String... expected) {
    assertNotNull(element);
    List<String> urls = JavaDocumentationProvider.getExternalJavaDocUrl(element);
    if (expected.length == 0) {
      assertNull(urls);
    } else {
      assertNotNull(urls);
      List<String> actual = ContainerUtil.map(urls, url -> url.substring(url.indexOf('#') + 1));
      assertOrderedEquals(actual, expected);
    }
  }
}
