// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.initialization;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see com.siyeh.ipp.initialization.SplitDeclarationAndInitializationIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class SplitDeclarationAndInitializationIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "initialization", IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.name"));

  @Test
  public void testSimple() { ipp.get().doTest(); }
  @Test
  public void testSimpleStatic() { ipp.get().doTest(); }
  @Test
  public void testArrayInitializer() { ipp.get().doTest(); }
  @Test
  public void testArray() { ipp.get().doTest(); }
  @Test
  public void testFieldUsedBeforeInitializer() { ipp.get().doTest(); }
  @Test
  public void testFieldUsedBeforeInitializer1() { ipp.get().doTest(); }
  @Test
  public void testMultipleFieldsSingleDeclaration() { ipp.get().doTest(); }
  @Test
  public void testMultipleFieldsSingleDeclaration2() { ipp.get().doTest(); }
  @Test
  public void testMultipleFieldsSingleDeclaration3() { ipp.get().doTest(); }
  @Test
  public void testNotInsideCodeBlock() { ipp.get().doTest(); }
  @Test
  public void testRecordStaticField() { ipp.get().doTest(); }
  @Test
  public void testWithInjection() { ipp.get().doTest(); }
  @Test
  public void testExistingInitializerAfterInstance() { ipp.get().doTest(); }
  @Test
  public void testExistingInitializerStaticMismatch() { ipp.get().doTest(); }
  @Test
  public void testNoInitializer() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testInsideCodeBlock() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testRecord() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testInterface() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testImplicitClass() { ipp.get().assertIntentionNotAvailable(); }
}
