// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"RegExpRedundantEscape", "RegExpDuplicateCharacterInClass"})
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
                 RegExpFileType.forLanguage(EcmaScriptRegexpLanguage.INSTANCE));
  }

  public void testPoundSign() {
    highlightTest("<warning descr=\"Redundant character escape '\\#' in RegExp\">\\#</warning>(?x)\\#");
  }

  public void testCurlyBrace() {
    highlightTest("\\{TEST}", RegExpFileType.forLanguage(EcmaScriptRegexpLanguage.INSTANCE));
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new RegExpRedundantEscapeInspection();
  }
}
