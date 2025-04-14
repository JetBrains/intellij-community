// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.ui.components.breadcrumbs.Crumb;

import java.util.List;

public class JsonBreadcrumbsTest extends JsonTestCase {

  private void doTest(String... components) {
    myFixture.configureByFile("breadcrumbs/" + getTestName(false) + ".json");
    List<Crumb> caret = myFixture.getBreadcrumbsAtCaret();
    assertOrderedEquals(caret.stream().map(Crumb::getText).toArray(String[]::new), components);
  }

  public void testComplexItems() {
    doTest("foo", "bar", "0", "0", "baz");
  }
}
