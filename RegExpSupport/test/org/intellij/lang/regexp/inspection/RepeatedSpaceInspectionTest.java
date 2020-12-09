// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"RegExpRepeatedSpace", "RegExpRedundantEscape", "RegExpDuplicateCharacterInClass"})
public class RepeatedSpaceInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    highlightTest("<warning descr=\"2 consecutive spaces in RegExp\">  </warning>");
  }

  public void testIgnoreQuoted() {
    highlightTest("\\Q     \\E");
  }

  public void testIgnoreInClass() {
    highlightTest("[   ]");
  }

  public void testIgnoreInClass2() {
    highlightTest(" [ -x]");
  }

  public void testReplacement() {
    quickfixTest("<warning descr=\"5 consecutive spaces in RegExp\">     </warning>", " {5}", "Replace with ' {5}'");
  }

  public void testReplacement2() {
    quickfixTest("\\Q     \\E<warning descr=\"3 consecutive spaces in RegExp\">   <caret></warning>", "\\Q     \\E {3}", "Replace with ' {3}'");
  }

  public void testEscapedWhitespace() {
    quickfixTest("<warning descr=\"3 consecutive spaces in RegExp\"><caret>\\   </warning>", " {3}", "Replace with ' {3}",
                 RegExpFileType.forLanguage(EcmaScriptRegexpLanguage.INSTANCE));
  }

  public void testNoStringIndexOutOfBoundsException() {
    highlightTest("<error descr=\"Illegal/unsupported escape sequence\">\\</error>");
  }

  public void testNoStringIndexOutOfBoundsException2() {
    highlightTest("<error descr=\"Illegal/unsupported escape sequence\">\\c</error>");
  }

  @Override
  @NotNull
  protected LocalInspectionTool getInspection() {
    return new RepeatedSpaceInspection();
  }
}
