// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.contextNavBarPathStrings;

public class JavaNavBarTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ide/navigationToolbar";
  }

  public void testSimple() {
    myFixture.configureByFile("simple.java");
    assertNavBarModel("src", "Simple", "foo");
  }

  public void testEnumMember() {
    myFixture.configureByFile("enumMember.java");
    assertNavBarModel("src", "EnumMember", "BAR", "foo");
  }

  public void testLambdaExpression() {
    myFixture.configureByFile("lambdaExpression.java");
    assertNavBarModel("src", "LambdaExpression", "foo", "Lambda");
  }

  public void testMultipleClasses() {
    myFixture.configureByFile("multipleClasses.java");
    assertNavBarModel("src", "multipleClasses.java", "Bar");
  }

  public void testImplicitClass() {
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
  public void testMethodPresentableTextDoesNotResolve() {
    myFixture.addClass("package anno; import java.lang.annotation.*; " +
                       "@Target(ElementType.TYPE_USE) public @interface Marker {}");
    PsiFile file = myFixture.configureByText("Foo.java", """
      import anno.Marker;
      class Foo {
        void bar(String @Marker ... args) {}
      }""");
    PsiMethod method = ((PsiJavaFile)file).getClasses()[0].getMethods()[0];
    JavaNavBarExtension extension = new JavaNavBarExtension();

    // Compute in dumb mode first, so that a smart-mode call does not cache the parameter type
    // (which would hide a regression that resolves during its construction).
    String dumbText = DumbModeTestUtils.computeInDumbModeSynchronously(
      getProject(), () -> ReadAction.compute(() -> extension.getPresentableText(method, true)));
    String smartText = ReadAction.compute(() -> extension.getPresentableText(method, true));

    assertEquals("Navbar presentable text must be computed without resolve", smartText, dumbText);
  }

  public void assertNavBarModel(String... expectedItems) {
    DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
    List<String> items = contextNavBarPathStrings(dataContext);
    assertOrderedEquals(items, expectedItems);
  }
}
