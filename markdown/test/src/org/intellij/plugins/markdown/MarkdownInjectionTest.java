// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown;

import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.plugins.markdown.injection.LanguageGuesser;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;

public class MarkdownInjectionTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testFenceWithLang() {
    doTest("```java\n" +
           "{\"foo\":\n" +
           "  <caret>\n" +
           "  bar\n" +
           "}\n" +
           "```", true);
  }

  public void testFenceWithLangWithDisabledAutoInjection() {
    MarkdownApplicationSettings markdownSettings = MarkdownApplicationSettings.getInstance();
    boolean oldValue = markdownSettings.isDisableInjections();
    try {
      markdownSettings.setDisableInjections(true);
      doTest("```java\n" +
             "{\"foo\":\n" +
             "  <caret>\n" +
             "  bar\n" +
             "}\n" +
             "```", false);
    }
    finally {
      markdownSettings.setDisableInjections(oldValue);
    }
  }

  public void testFenceWithJs() {
    assert JavascriptLanguage.INSTANCE != null;
    assertNotNull(LanguageGuesser.INSTANCE.guessLanguage("js"));
  }

  private void doTest(String text, boolean shouldHaveInjection) {
    final PsiFile file = myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    assertEquals(shouldHaveInjection, !file.findElementAt(myFixture.getCaretOffset()).getLanguage().isKindOf(MarkdownLanguage.INSTANCE));
  }
}
