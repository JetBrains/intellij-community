// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

public abstract class JsonHighlightingTestBase extends JsonTestCase {
  protected void doTest() {
    doTestHighlighting(true, true, true);
  }

  protected abstract String getExtension();

  protected void doTestHighlighting(boolean checkInfo, boolean checkWeakWarning, boolean checkWarning) {
    myFixture.testHighlighting(checkWarning, checkInfo, checkWeakWarning, "/highlighting/" + getTestName(false) + "." + getExtension());
  }
}
