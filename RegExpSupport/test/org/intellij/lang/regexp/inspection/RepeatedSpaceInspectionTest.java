// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"RegExpRepeatedSpace", "RegExpRedundantEscape"})
public class RepeatedSpaceInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    highlightTest("<warning descr=\"2 consecutive spaces in RegExp\">  </warning>");
  }

  public void testIgnoreQuoted() {
    highlightTest("\\Q     \\E");
  }

  public void testIgnoreInClass() {
    highlightTest("[ <warning descr=\"Duplicate character ' ' inside character class\"> </warning><warning descr=\"Duplicate character ' ' inside character class\"> </warning>]");
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
                 new RegExpFileType(EcmaScriptRegexpLanguage.INSTANCE));
  }

  @Override
  @NotNull
  protected LocalInspectionTool getInspection() {
    return new RepeatedSpaceInspection();
  }
}
