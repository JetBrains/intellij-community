// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.unicode;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see com.siyeh.ipp.unicode.ReplaceOctalEscapeWithUnicodeEscapeIntention
 */
@TestApplication
public class ReplaceOctalEscapeWithUnicodeUnescapeIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "unicode/octal", IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.name"));

  @Test
  public void testSimple() { ipp.get().doTest(); }
  @Test
  public void testSelection() { ipp.get().doTest(); }
  @Test
  public void testSelectionSingleSlash() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testSelectionIncomplete() { ipp.get().doTest(); }
  @Test
  public void testSelection2() { ipp.get().doTest(); }
  @Test
  public void testStringTemplate() { ipp.get().doTest(); }
  @Test
  public void testOtherEscape() { ipp.get().assertIntentionNotAvailable(); }
}