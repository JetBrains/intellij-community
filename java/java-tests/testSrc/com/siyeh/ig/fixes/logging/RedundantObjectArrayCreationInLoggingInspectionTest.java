// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.logging;

import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.java.JavaBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class RedundantObjectArrayCreationInLoggingInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = String.format("Fix all '%s' problems in file",
                                  JavaBundle.message("inspection.redundant.array.creation.display.name"));
    myFixture.configureByFile("logging/redundant_object_array_creation_in_logging/Logger.java");
    myFixture.enableInspections(new RedundantArrayForVarargsCallInspection());
  }

  public void testObjectArrayCreationInLoggingDebug() { doTest(); }
  public void testObjectArrayCreationInLoggingInfo() { doTest(); }
  public void testObjectArrayCreationInLoggingWarn() { doTest(); }
  public void testObjectArrayCreationInLoggingError() { doTest(); }
  public void testObjectArrayCreationInLoggingTrace() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "logging/redundant_object_array_creation_in_logging";
  }
}
