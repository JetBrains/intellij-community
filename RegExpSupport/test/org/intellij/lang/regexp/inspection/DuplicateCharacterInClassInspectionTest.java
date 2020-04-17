// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DuplicateCharacterInClassInspectionTest extends RegExpInspectionTestCase {

  public void testPredefinedCharacterClass() {
    quickfixTest("[\\w-<warning descr=\"Duplicate predefined character class '\\w' inside character class\"><caret>\\w</warning>]",
                 "[\\w-]", RegExpBundle.message("inspection.quick.fix.remove.duplicate.0.from.character.class", "\\w"));
  }

  public void testQuoted() {
    highlightTest("[\\Qabc?*+.)<warning descr=\"Duplicate character ')' inside character class\">)</warning>]<warning descr=\"Duplicate character ']' inside character class\">]</warning>[<warning descr=\"Duplicate character ']' inside character class\">]</warning></w<warning descr=\"Duplicate character 'a' inside character class\">a</warning>rni<warning descr=\"Duplicate character 'n' inside character class\">n</warning>g>\\E]");
  }

  public void testNotPosixCharacterClass() {
    highlightTest("[:xdig<warning descr=\"Duplicate character 'i' inside character class\">i</warning>t<warning descr=\"Duplicate character ':' inside character class\">:</warning>]+");
  }

  public void testSpace() {
    quickfixAllTest("[ <warning descr=\"Duplicate character ' ' inside character class\"><caret> </warning><warning descr=\"Duplicate character ' ' inside character class\"> </warning>]", "[ ]");
  }
  
  public void testNegatedClass() {
    highlightTest("[^a<warning descr=\"Duplicate character 'a' inside character class\">a</warning>]");
  }

  public void testNestedClass() {
    highlightTest("[<[^<>]*>]*<[^<>]*");
  }

  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new DuplicateCharacterInClassInspection();
  }
}
