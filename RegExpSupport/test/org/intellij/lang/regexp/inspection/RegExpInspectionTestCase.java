/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpFileType;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public abstract class RegExpInspectionTestCase extends LightPlatformCodeInsightFixtureTestCase {

  @NotNull
  protected abstract LocalInspectionTool getInspection();

  protected void highlightTest(@Language("RegExp") String code) {
    myFixture.enableInspections(getInspection());
    myFixture.configureByText(RegExpFileType.INSTANCE, code);
    myFixture.testHighlighting();
  }

  protected void quickfixTest(@Language("RegExp") String before, @Language("RegExp") String after, String hint) {
    myFixture.enableInspections(getInspection());
    myFixture.configureByText(RegExpFileType.INSTANCE, before);
    final IntentionAction intention = findIntention(hint);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult(after);
  }

  protected IntentionAction findIntention(@NotNull final String hint) {
    final List<IntentionAction> intentions = myFixture.filterAvailableIntentions(hint);
    Assert.assertFalse("\"" + hint + "\" not in " + intentions, intentions.isEmpty());
    Assert.assertFalse("Too many quickfixes found for \"" + hint + "\": " + intentions + "]", intentions.size() > 1);
    return intentions.get(0);
  }
}
