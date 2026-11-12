// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ReplaceConcatenationWithFormatStringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "concatenation/string_format", IntentionPowerPackBundle.message(
                "replace.concatenation.with.format.string.intention.name"));

    @Test
    public void testNumericBinaryExpression() { ipp.get().doTest(); }
    @Test
    public void testHexadecimalLiteral() { ipp.get().doTest(); }
    @Test
    public void testPercentInLiteral() { ipp.get().doTest(); }
    @Test
    public void testParameters() { ipp.get().doTest(); }
    @Test
    public void testLineSeparator() { ipp.get().doTest(); }
    @Test
    public void testPreserveTextBlock() {
      LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
        IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> {
            ipp.get().doTest(IntentionPowerPackBundle.message(
              "replace.concatenation.with.format.string.intention.name.formatted"));
        });
    }
}
