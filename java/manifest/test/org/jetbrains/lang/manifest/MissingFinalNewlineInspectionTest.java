// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.lang.manifest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.lang.manifest.highlighting.MissingFinalNewlineInspection;

public class MissingFinalNewlineInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MissingFinalNewlineInspection());
  }

  public void testEmptyFile() {
    myFixture.configureByText(ManifestFileType.INSTANCE, "");
    assertEquals(0, myFixture.getAvailableIntentions().size());
  }

  public void testNoProblem() {
    myFixture.configureByText(ManifestFileType.INSTANCE, "Manifest-Version: 1.0\n");
    assertEquals(0, myFixture.getAvailableIntentions().size());
  }

  public void testFix() {
    myFixture.configureByText(ManifestFileType.INSTANCE, "Manifest-Version: 1.0");
    IntentionAction intention = myFixture.findSingleIntention(ManifestBundle.message("inspection.newline.fix"));
    myFixture.launchAction(intention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("Manifest-Version: 1.0\n");
  }
}
