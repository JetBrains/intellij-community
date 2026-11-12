// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.imports;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ReplaceOnDemandImportIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "imports", IntentionPowerPackBundle.message("replace.on.demand.import.intention.name"));

  @Test
  public void testStaticImport() { ipp.get().doTest(); }
  @Test
  public void testModuleImport() { ipp.get().doTest(); }
  @Test
  public void testModuleImportImplicitImport() { ipp.get().doTest(); }
  @Test
  public void testAlreadyImportedClass() { ipp.get().doTest(); }
  @Test
  public void testAlreadyImportedStaticMember() { ipp.get().doTest(); }
}
