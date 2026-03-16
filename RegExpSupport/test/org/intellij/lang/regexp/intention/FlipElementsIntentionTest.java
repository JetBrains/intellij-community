// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpFileType;

/**
 * @author Bas Leijdekkers
 */
public final class FlipElementsIntentionTest extends BasePlatformTestCase {
  
  public void testSimple() {
    doTest("two<caret>|one|three", "one|two|three");
  }

  public void testEmptyBranch() {
    doTest("refreshing|<caret>", "|refreshing");
  }
  
  public void testCharacterClass() {
    //noinspection RegExpDuplicateCharacterInClass
    doTest("[<caret>abs]", "[bas]");
  }

  private void doTest(@Language("RegExp") String before, @Language("RegExp") String after) {
    myFixture.configureByText(RegExpFileType.INSTANCE, before);
    myFixture.launchAction(myFixture.findSingleIntention("Swap "));
    myFixture.checkResult(after);
  }
}
