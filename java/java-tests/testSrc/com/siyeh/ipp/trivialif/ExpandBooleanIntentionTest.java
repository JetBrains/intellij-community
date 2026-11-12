// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ExpandBooleanIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "trivialif/expand_boolean", IntentionPowerPackBundle.message("expand.boolean.declaration.intention.name"));

  @Test
  public void testBraces() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.declaration.intention.name"));}
  @Test
  public void testFormatting() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }
  @Test
  public void testFormatting2() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  @Test
  public void testIncomplete1() { ipp.get().doTest(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }
  @Test
  public void testIncomplete2() { ipp.get().doTest(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  @Test
  public void testIncomplete3() { ipp.get().assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  @Test
  public void testIncomplete4() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testIncomplete5() { ipp.get().assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  @Test
  public void testIncomplete6() { ipp.get().assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }

  private void doFormattingTest(String intentionText) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    final int oldValue = settings.IF_BRACE_FORCE;
    try {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      ipp.get().doTest(intentionText);
    } finally {
      settings.IF_BRACE_FORCE = oldValue;
    }
  }
}
