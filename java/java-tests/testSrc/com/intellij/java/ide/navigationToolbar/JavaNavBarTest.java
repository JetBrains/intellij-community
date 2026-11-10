// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.navigationToolbar;

import com.intellij.JavaTestUtil;
import com.intellij.ide.navigationToolbar.JavaNavBarExtension;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.contextNavBarPathStrings;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@TestApplication
public class JavaNavBarTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ide/navigationToolbar");
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("simple.java");
    assertNavBarModel("src", "Simple", "foo");
  }

  @Test
  public void testEnumMember() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("enumMember.java");
    assertNavBarModel("src", "EnumMember", "BAR", "foo");
  }

  @Test
  public void testLambdaExpression() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("lambdaExpression.java");
    assertNavBarModel("src", "LambdaExpression", "foo", "Lambda");
  }

  @Test
  public void testMultipleClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("multipleClasses.java");
    assertNavBarModel("src", "multipleClasses.java", "Bar");
  }

  @Test
  public void testImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("implicitClass.java");
    assertNavBarModel("src", "implicitClass.java", "test");
  }

  /**
   * Regression test for IDEA-379478: computing the presentable (popup) text of a method for the
   * navbar must not resolve references. Formatting the signature builds the parameter types, and a
   * regression that eagerly computes type nullability there would resolve the parameters' type-use
   * annotations - which fails with {@code IndexNotReadyException} in dumb mode.
   * <p>
   * The annotation lives in a separate file, so resolving it requires indexes; if the presentable
   * text is computed without resolve (as it must be), the result is identical in dumb and smart mode.
   */
  @Test
  public void testMethodPresentableTextDoesNotResolve() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package anno; import java.lang.annotation.*; " +
                       "@Target(ElementType.TYPE_USE) public @interface Marker {}");
    PsiFile file = myFixture.configureByText("Foo.java", """
      import anno.Marker;
      class Foo {
        void bar(String @Marker ... args) {}
      }""");
    PsiMethod method = ReadAction.computeBlocking(() -> ((PsiJavaFile)file).getClasses()[0].getMethods()[0]);
    JavaNavBarExtension extension = new JavaNavBarExtension();

    // Compute in dumb mode first, so that a smart-mode call does not cache the parameter type
    // (which would hide a regression that resolves during its construction).
    String dumbText = DumbModeTestUtils.computeInDumbModeSynchronously(
      myFixture.getProject(), () -> ReadAction.compute(() -> extension.getPresentableText(method, true)));
    String smartText = ReadAction.compute(() -> extension.getPresentableText(method, true));

    assertEquals(smartText, dumbText, "Navbar presentable text must be computed without resolve");
  }

  public void assertNavBarModel(String... expectedItems) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
    List<String> items = ReadAction.computeBlocking(() -> contextNavBarPathStrings(dataContext));
    assertIterableEquals(Arrays.asList(expectedItems), items);
  }
}
