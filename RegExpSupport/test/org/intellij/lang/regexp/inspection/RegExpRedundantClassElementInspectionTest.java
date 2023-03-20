// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

public class RegExpRedundantClassElementInspectionTest extends RegExpInspectionTestCase {
  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new RegExpRedundantClassElementInspection();
  }

  public void testAnyDigit() {
    quickfixTest("[\\w.<weak_warning descr=\"Redundant '\\d' in RegExp\"><caret>\\d</weak_warning>]",
                 "[\\w.]", RegExpBundle.message("inspection.quick.fix.remove.redundant.0.class.element", "\\d"));
  }

  public void testAnyNonDigit() {
    quickfixTest("^[\\W.<weak_warning descr=\"Redundant '\\D' in RegExp\"><caret>\\D</weak_warning>]",
                 "^[\\W.]", RegExpBundle.message("inspection.quick.fix.remove.redundant.0.class.element", "\\D"));
  }

  public void testNoHighlighting() {
    highlightTest("\\w{2}[.,]?[\\d\\W]");
  }
}
