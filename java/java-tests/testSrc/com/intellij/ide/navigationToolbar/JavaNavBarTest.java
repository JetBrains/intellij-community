// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.ex.EditorEx;
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

  public void assertNavBarModel(String... expectedItems) {
    DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
    List<String> items = contextNavBarPathStrings(dataContext);
    assertOrderedEquals(items, expectedItems);
  }
}
