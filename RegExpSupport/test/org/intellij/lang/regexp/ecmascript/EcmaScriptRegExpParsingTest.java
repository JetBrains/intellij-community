// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.ParsingTestCase;
import org.intellij.lang.regexp.RegExpParserDefinition;

import java.io.IOException;

/**
 * @author Bas Leijdekkers
 */
public class EcmaScriptRegExpParsingTest extends ParsingTestCase {

  public EcmaScriptRegExpParsingTest() {
    super("ecmascript_psi", "regexp", new EcmaScriptUnicodeRegexpParserDefinition(), new RegExpParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/RegExpSupport/testData";
  }

  public void testDanglingMetaCharacter1() throws IOException {
    doCodeTest("{");
  }

  public void testDanglingMetaCharacter2() throws IOException {
    doCodeTest("}");
  }

  public void testDanglingMetaCharacter3() throws IOException {
    doCodeTest("]");
  }

  public void testExtendedUnicode() throws IOException {
    doCodeTest("\\u{9}");
  }
}
