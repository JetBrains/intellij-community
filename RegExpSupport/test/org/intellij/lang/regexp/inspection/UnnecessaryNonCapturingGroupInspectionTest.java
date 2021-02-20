// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryNonCapturingGroupInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    quickfixTest("abc <warning descr=\"Unnecessary non-capturing group '(?:def)'\"><caret>(?:</warning>def) ghi",
                 "abc def ghi",
                 RegExpBundle.message("inspection.quick.fix.remove.unnecessary.non.capturing.group"));
  }

  public void testNoWarnOnRegularGroup() {
    highlightTest("abc (def) ghi");
  }

  public void testNoWarnOnAlternation() {
    highlightTest("aa(?:bb|bbb)cccc");
  }

  public void testNoWarnOnNestedClosure() {
    highlightTest("\\d{2}(?:\\d{3})?");
    highlightTest("\\d{2}(?:\\d{3}){2}");
    highlightTest("\\d{2}(?:\\d{3})+");
    highlightTest("\\d{2}(?:\\d{3})*");
  }

  public void testTopLevelAlternation() {
    quickfixTest("<warning descr=\"Unnecessary non-capturing group '(?:xx|xy)'\">(?:</warning>xx|xy)", "xx|xy",
                 RegExpBundle.message("inspection.quick.fix.remove.unnecessary.non.capturing.group"));
  }

  public void testSingleAtom() {
    quickfixTest("aaa<warning descr=\"Unnecessary non-capturing group '(?:b)'\">(?:<caret></warning>b)+aaa",
                 "aaab+aaa",
                 RegExpBundle.message("inspection.quick.fix.remove.unnecessary.non.capturing.group"));
  }

  public void testCorrectEscaping() {
    quickfixTest("<warning descr=\"Unnecessary non-capturing group '(?:[\\w-]+:)'\"><caret>(?:</warning>[\\w-]+:)[\\w-]+",
                 "[\\w-]+:[\\w-]+",
                 RegExpBundle.message("inspection.quick.fix.remove.unnecessary.non.capturing.group"));
  }

  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new UnnecessaryNonCapturingGroupInspection();
  }
}