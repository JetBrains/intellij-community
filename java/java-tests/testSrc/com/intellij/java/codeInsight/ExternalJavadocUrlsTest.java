// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ExternalJavadocUrlsTest {
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

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(getProjectFixture());

  /**
   * Returns the project of the tests. A subclass runs the tests of this class again in its own project.
   */
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  protected static void setMockJavadocUrl(@NotNull ModifiableRootModel model) {
    model.getModuleExtension(JavaModuleExternalPaths.class).setJavadocUrls(new String[]{"http://doc"});
  }

  @Test
  public void testVarargs() {
    doTestMethod("""
             class Test {
               void <caret>foo(Class<?>... cl) { }
             }""",

           "foo(java.lang.Class...)", "foo-java.lang.Class...-");

  }

  @Test
  public void testTypeParams() {
    doTestMethod("""
             class Test {
               <T> void <caret>sort(T[] a, Comparator<? super T> c) { }
             }
             class Comparator<X>{}""",

           "sort(T[],Comparator)", "sort-T:A-Comparator-", "sort(T[], Comparator)");
  }

  @Test
  public void testConstructor() {
    doTestMethod("""
             class Test {
               Test<caret>() { }
             }""",
           "<init>()", "Test--", "Test()");
  }
  
  @Test
  public void testImplicitClass() {
    doTestMethod("""
             void <caret>main() {}
             """, "main()", "main--");
  }
  
  @Test
  public void testImplicitClassWithPackage() {
    doTestMethod("""
             package foo.bar;
             
             void <caret>main() {}
             """);
  }

  /// See IDEA-366031
  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", text);
    ReadAction.runBlocking(() -> doTest(myFixture.getElementAtCaret(), expected));
  }

  protected void doTestMethod(String text, String... expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", text);
    ReadAction.runBlocking(() -> {
      PsiElement elementAtCaret = myFixture.getElementAtCaret();
      doTest(PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class, false), expected);
    });
  }

  private static void doTest(PsiElement element, String... expected) {
    assertNotNull(element);
    List<String> urls = JavaDocumentationProvider.getExternalJavaDocUrl(element);
    if (expected.length == 0) {
      assertNull(urls);
    } else {
      assertNotNull(urls);
      List<String> actual = ContainerUtil.map(urls, url -> url.substring(url.indexOf('#') + 1));
      assertEquals(List.of(expected), actual);
    }
  }
}
