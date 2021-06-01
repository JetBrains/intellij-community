// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;


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

  public void assertNavBarModel(String... expectedItems) {
    NavBarModel model = new NavBarModel(myFixture.getProject());
    model.updateModel(((EditorEx)myFixture.getEditor()).getDataContext());
    List<String> items = new ArrayList<>();
    for (int i = 0; i < model.size(); i++) {
      items.add(NavBarPresentation.calcPresentableText(model.get(i), false));
    }
    assertOrderedEquals(items, expectedItems);
  }
}
