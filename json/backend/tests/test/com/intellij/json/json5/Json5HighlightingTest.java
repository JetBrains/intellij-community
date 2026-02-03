// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.json.JsonHighlightingTestBase;
import com.intellij.json.json5.codeinsight.Json5StandardComplianceInspection;

public class Json5HighlightingTest extends JsonHighlightingTestBase {

  @Override
  protected String getExtension() {
    return "json5";
  }

  public void testJSON5() {
    myFixture.enableInspections(new Json5StandardComplianceInspection());
    doTestHighlighting(false, true, true);
  }
}
