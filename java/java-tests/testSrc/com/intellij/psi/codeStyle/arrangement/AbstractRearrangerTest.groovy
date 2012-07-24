/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:54 PM
 */
abstract class AbstractRearrangerTest extends LightCodeInsightFixtureTestCase {
  
  def FileType fileType
  
  @Override
  protected void setUp() {
    super.setUp()
    CodeStyleSettingsManager.getInstance(myFixture.project).temporarySettings = new CodeStyleSettings()
  }

  @Override
  protected void tearDown() {
    CodeStyleSettingsManager.getInstance(myFixture.project).dropTemporarySettings()
    super.tearDown()
  }

  protected void doTest(@NotNull String initial,
                        @NotNull String expected,
                        @NotNull List<ArrangementRule> rules,
                        Collection<TextRange> ranges = null)
  {
    def (String textToUse, List<TextRange> rangesToUse) = parseRanges(initial)
    if (rangesToUse && ranges) {
      fail("Duplicate ranges info detected: explicitly given: $ranges, derived from markup: $rangesToUse. Text:\n$initial")
    }
    if (!rangesToUse) {
      rangesToUse = ranges ?: [TextRange.from(0, initial.length())]
    }
    
    myFixture.configureByText(fileType, textToUse)
    def settings = CodeStyleSettingsManager.getInstance(myFixture.project).currentSettings.getCommonSettings(JavaLanguage.INSTANCE)
    settings.arrangementRules = rules
    ArrangementEngine engine = ServiceManager.getService(myFixture.project, ArrangementEngine)
    engine.arrange(myFixture.file, rangesToUse);
    assertEquals(expected, myFixture.editor.document.text);
  }
  
  @NotNull
  private static def parseRanges(@NotNull String text) {
    def clearText = new StringBuilder(text)
    def ranges = []
    int shift = 0
    int shiftIncrease = '<range>'.length() * 2 + 1
    def match = text =~ '(?is)<range>.*?</range>'
    match.each {
      ranges << TextRange.create(match.start() - shift, match.end() - shift - shiftIncrease)
      clearText.delete(match.end() - '</range>'.length() - shift, match.end() - shift)
      clearText.delete(match.start() - shift, match.start() + '<range>'.length() - shift)
      shift += shiftIncrease
    }
    
    [clearText.toString(), ranges]
  }
}
