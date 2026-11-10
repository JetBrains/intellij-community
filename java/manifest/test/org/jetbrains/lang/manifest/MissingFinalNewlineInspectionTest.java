// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.lang.manifest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.lang.manifest.highlighting.MissingFinalNewlineInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class MissingFinalNewlineInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MissingFinalNewlineInspection());
  }

  @Test
  public void testEmptyFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(ManifestFileType.INSTANCE, "");
    assertThat(myFixture.filterAvailableIntentions(ManifestBundle.message("inspection.newline.fix"))).isNullOrEmpty();
  }

  @Test
  public void testNoProblem() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(ManifestFileType.INSTANCE, "Manifest-Version: 1.0\n");
    assertThat(myFixture.filterAvailableIntentions(ManifestBundle.message("inspection.newline.fix"))).isNullOrEmpty();
  }

  @Test
  public void testFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(ManifestFileType.INSTANCE, "Manifest-Version: 1.0");
    IntentionAction intention = myFixture.findSingleIntention(ManifestBundle.message("inspection.newline.fix"));
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResult("Manifest-Version: 1.0\n");
    });
  }
}
