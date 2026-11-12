// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class JoinConcatenatedStringLiteralsIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "concatenation/join_concat", IntentionPowerPackBundle.message(
                "join.concatenated.string.literals.intention.name"));

    @Test
    public void testSimple() { ipp.get().doTest(); }
    @Test
    public void testPolyadic() { ipp.get().doTest(); }
    @Test
    public void testNonString() { ipp.get().doTest(); }
    @Test
    public void testNonString2() { ipp.get().doTest(); }
    @Test
    public void testNotAvailable() { ipp.get().assertIntentionNotAvailable(); }
    @Test
    public void testInvalidLiteral() { ipp.get().assertIntentionNotAvailable(); }
    @Test
    public void testKeepCommentsAndWhitespace() { ipp.get().doTest(); }
    @Test
    public void testTextBlocks() { ipp.get().doTest(); }
    @Test
    public void testTextBlocksTailingLineBreak() { ipp.get().doTest(); }
    @Test
    public void testTextBlocksAndStringLiteral() { ipp.get().doTest(); }
    @Test
    public void testKeepEscapes() { ipp.get().doTest(); }

}
