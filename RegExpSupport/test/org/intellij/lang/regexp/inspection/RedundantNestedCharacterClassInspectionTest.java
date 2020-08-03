// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"RegExpDuplicateCharacterInClass", "RegExpRedundantNestedCharacterClass"})
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
    highlightTest("[^abc[^cde]]");
    // JDK 8: conjunction of inverted [abc] and inverted [cde], which equals inverted [c]
    // JDK 9: the inverse of the conjunction of [abc] and inverted [cde], which equals the inverse of inverted [de], which is [de]
  }

  public void testNoWarn() {
    highlightTest("[a-z&&[^aeouiy]]" ); // intersection of [a-z] with [aeouiy] inverted, which equals the  alphabet except vowels
  }

  public void testNoWarn2() {
    highlightTest("[^a[abc]]");
    // JDK 8: conjunction of inverted [a] and [abc], which equals [bc]
    // JDK 9: inverted conjunction of [a] and [abc], which equals inverted [abc]
  }

  public void testNegatedIntersection() {
    highlightTest("[^a&&[^abc]]");
    // JDK 8: intersection of inverted [a] and inverted [abc], which equals inverted [abc]
    // JDK 9: inverted intersection of [a] and inverted [abc], which equals inverted empty class, which matches everything
  }


  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new RedundantNestedCharacterClassInspection();
  }
}
