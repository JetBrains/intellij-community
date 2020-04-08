// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("RegExpRedundantEscape")
public class RedundantEscapeInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    quickfixTest("<warning descr=\"Redundant character escape '\\;' in RegExp\">\\;</warning>", ";", "Remove redundant escape");
  }

  public void testCharacterClass() {
    highlightTest("<warning descr=\"Redundant character escape '\\-' in RegExp\">\\-</warning>[<warning descr=\"Redundant character escape '\\*' in RegExp\">\\*</warning>\\-\\[\\]\\\\<warning descr=\"Redundant character escape '\\+' in RegExp\">\\+</warning>]");
  }

  public void testWhitespace() {
    highlightTest("a<warning descr=\"Redundant character escape '\\ ' in RegExp\">\\ </warning>b<warning descr=\"Redundant character escape '\\ ' in RegExp\">\\ </warning>c");
  }

  public void testEscapedU() {
    quickfixTest("<warning descr=\"Redundant character escape '\\u' in RegExp\">\\u</warning>", "u", "Remove redundant escape",
                 new RegExpFileType(EcmaScriptRegexpLanguage.INSTANCE));
  }

  public void testPoundSign() {
    highlightTest("<warning descr=\"Redundant character escape '\\#' in RegExp\">\\#</warning>(?x)\\#");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new RedundantEscapeInspection();
  }
}
