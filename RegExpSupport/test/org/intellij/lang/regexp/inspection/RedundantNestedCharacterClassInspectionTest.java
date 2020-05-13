// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RedundantNestedCharacterClassInspectionTest extends RegExpInspectionTestCase {

  public void testConjunction() {
    quickfixTest("[a<warning descr=\"Redundant nested character class\"><caret>[</warning>b]]", "[ab]",
                 RegExpBundle.message("inspection.quick.fix.replace.redundant.character.class.with.contents"));
  }

  public void testIntersection() {
    quickfixTest("[a-z&&<warning descr=\"Redundant nested character class\"><caret>[</warning>aeoiuy]]", "[a-z&&aeoiuy]",
                 RegExpBundle.message("inspection.quick.fix.replace.redundant.character.class.with.contents"));
  }

  public void testNegation() {
    quickfixTest("[^*=<warning descr=\"Redundant nested character class\"><caret>[</warning>^&]]", "[^*=&]",
                 RegExpBundle.message("inspection.quick.fix.replace.redundant.character.class.with.contents"));
  }

  public void testNoWarn() {
    highlightTest("[a-z&&[^aeouiy]]" );
  }


  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new RedundantNestedCharacterClassInspection();
  }
}
