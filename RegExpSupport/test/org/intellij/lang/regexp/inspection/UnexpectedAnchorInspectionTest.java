/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("RegExpUnexpectedAnchor")
public class UnexpectedAnchorInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    highlightTest("<warning descr=\"Anchor '$' in unexpected position\">$</warning><warning descr=\"Anchor '^' in unexpected position\">^</warning>");
  }

  public void testAZ() {
    highlightTest("\n<warning descr=\"Anchor '\\A' in unexpected position\">\\A</warning><warning descr=\"Anchor '\\Z' in unexpected position\">\\Z</warning>\n");
  }

  public void testNoWarn() {
    highlightTest("^$");
    highlightTest("\n^$\n");
  }

  public void testCommentMode() {
    highlightTest("(?x)\n" +
                  "# comment\n" +
                  "^impedance");
  }

  public void testIDEA184428() {
    highlightTest("\\(\\s*<=\\s*;\\s*`<warning descr=\"Anchor '$' in unexpected position\">$</warning>\"SeqNo\"\\s*;\\s*-?\\d+j\\s*\\)s");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new UnexpectedAnchorInspection();
  }
}
