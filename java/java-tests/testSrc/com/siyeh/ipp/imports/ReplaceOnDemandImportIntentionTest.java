// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.imports;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ReplaceOnDemandImportIntentionTest extends IPPTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  public void testStaticImport() { doTest(); }
  public void testModuleImport() { doTest(); }
  public void testModuleImportImplicitImport() { doTest(); }
  public void testModuleImportModuleInfo() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.on.demand.import.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "imports";
  }
}
