// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.comment;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ChangeToEndOfLineCommentIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "comment/to_end_of_line", IntentionPowerPackBundle.message("change.to.end.of.line.comment.intention.name"));

  @Test
  public void testTrimLines() { ipp.get().doTest(); }
  @Test
  public void testConvertMultiLineTodo() { ipp.get().doTest(); }
  @Test
  public void testConvertMultiLineTodo2() { ipp.get().doTest(); }
  @Test
  public void testEmptyLines() { ipp.get().doTest(); }
  @Test
  public void testSingleLineBlockAtEof() { ipp.get().doTest(); }
  @Test
  public void testNewlineOnly() { ipp.get().doTest(); }
}