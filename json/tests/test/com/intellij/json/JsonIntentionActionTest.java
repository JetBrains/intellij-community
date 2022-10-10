// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

public class JsonIntentionActionTest extends JsonTestCase {

  private void doTest(@SuppressWarnings("SameParameterValue") String intentionKey) {
    myFixture.testHighlighting("/intention/" + getTestName(false) + ".json");
    myFixture.launchAction(myFixture.getAvailableIntention(JsonBundle.message(intentionKey)));
    myFixture.checkResultByFile("/intention/" + getTestName(false) + "_after.json");
  }

  public void testSortProperties() {
    doTest("json.intention.sort.properties");
  }

  public void testSortMalformedJson() {
    doTest("json.intention.sort.properties");
  }

  public void testSortPropertiesShouldReformat() {
    doTest("json.intention.sort.properties");
  }

  public void testSortRecursively() {
    doTest("json.intention.sort.properties");
  }
}
