// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.commutative;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class FlipCommutativeMethodCallIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "commutative/flip", IntentionPowerPackBundle.message("flip.commutative.method.call.intention.name1", "foo"));

    @Test
    public void testSubstitution() { ipp.get().doTest(); }
    @Test
    public void testNoQualifier() { ipp.get().doTest(); }
    @Test
    public void testThisQualifier() { ipp.get().doTest(); }
    @Test
    public void testNoQualifierStatic() { ipp.get().assertIntentionNotAvailable(); }
    @Test
    public void testNoQualifier2() { ipp.get().assertIntentionNotAvailable(); }
    @Test
    public void testSameQualifierArgument() { ipp.get().assertIntentionNotAvailable(); }
    @Test
    public void testArgumentCastNoQualifier() { ipp.get().doTest(); }
}
