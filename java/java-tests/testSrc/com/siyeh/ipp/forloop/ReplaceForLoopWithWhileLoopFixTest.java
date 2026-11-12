// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.forloop;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.controlflow.ForLoopReplaceableByWhileInspection;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceForLoopWithWhileLoopFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "forloop/while_loop", CommonQuickFixBundle.message("fix.replace.with.x", JavaKeywords.WHILE));

  @Test
  public void testLabeledForLoop() { ipp.get().doTest(); }
  @Test
  public void testNotInBlock() { ipp.get().doTest(); }
  @Test
  public void testDoubleLabelNoBraces() { ipp.get().doTest(); }
  @Test
  public void testUpdatingMuch() { ipp.get().doTest(); }
  @Test
  public void testContinuing() { ipp.get().doTest(); }
  @Test
  public void testConflictingNames() { ipp.get().doTest(); }
  @Test
  public void testNoInit() { ipp.get().doTest(); }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.enableInspections(new ForLoopReplaceableByWhileInspection());
  }
}