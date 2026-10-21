// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.regexp.RegExpParserDefinition;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class EcmaScriptRegExpParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PathManagerEx.getCommunityHomePath() + "/RegExpSupport/testData/ecmascript_psi", "regexp",
                   new EcmaScriptUnicodeRegexpParserDefinition(), new RegExpParserDefinition());

  @Test
  public void testDanglingMetaCharacter1() {
    parsingFixture.get().doCodeTest("{");
  }

  @Test
  public void testDanglingMetaCharacter2() {
    parsingFixture.get().doCodeTest("}");
  }

  @Test
  public void testDanglingMetaCharacter3() {
    parsingFixture.get().doCodeTest("]");
  }

  @Test
  public void testEscapedMetaCharacter() {
    //noinspection RegExpRedundantEscape
    parsingFixture.get().doCodeTest("\\]");
  }

  @Test
  public void testExtendedUnicode() {
    parsingFixture.get().doCodeTest("\\u{9}");
  }
}
