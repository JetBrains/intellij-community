/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class DetectIndentAndTypeTest extends BasePlatformTestCase {

  private CodeStyleSettings mySettings;
  private static final String myText = """
    public class T {
    \tvoid run() {
    \t\tint t = 1 + <caret>2;
    \t}
    }""";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(CodeStyle.createTestSettings());
    mySettings = CodeStyle.getSettings(getProject());
    mySettings.AUTODETECT_INDENTS = true;
    DetectableIndentOptionsProvider optionsProvider = DetectableIndentOptionsProvider.getInstance();
    if (optionsProvider != null) {
      optionsProvider.setEnabledInTest(true);
    }
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      DetectableIndentOptionsProvider optionsProvider = DetectableIndentOptionsProvider.getInstance();
      if (optionsProvider != null) {
        optionsProvider.setEnabledInTest(false);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testWhenTabsDetected_SetIndentSizeToTabSize() {
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;

    indentOptions.INDENT_SIZE = 1;
    indentOptions.TAB_SIZE = 2;

    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                public class T {
                                \tvoid run() {
                                \t\tint a = 2;<caret>
                                \t}
                                }
                                """);
    myFixture.type('\n');
    myFixture.checkResult("""
                            public class T {
                            \tvoid run() {
                            \t\tint a = 2;
                            \t\t<caret>
                            \t}
                            }
                            """);
  }

  public void testContinuationTab_AsTabSize() {
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    common.ALIGN_MULTILINE_BINARY_OPERATION = false;
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;

    indentOptions.TAB_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;

    myFixture.configureByText(JavaFileType.INSTANCE, myText);
    myFixture.type('\n');

    myFixture.checkResult(
      """
        public class T {
        \tvoid run() {
        \t\tint t = 1 +\s
        \t\t\t2;
        \t}
        }""");
  }

  public void testContinuationTabs_AsDoubleTabSize() {
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    common.ALIGN_MULTILINE_BINARY_OPERATION = false;
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;

    indentOptions.TAB_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
    myFixture.configureByText(JavaFileType.INSTANCE, myText);
    myFixture.type('\n');

    myFixture.checkResult(
      """
        public class T {
        \tvoid run() {
        \t\tint t = 1 +\s
        \t\t\t\t2;
        \t}
        }""");
  }

  public void testWhenTabsDetected_SetContinuationIndentSizeToDoubleTabSize() {
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;

    indentOptions.INDENT_SIZE = 1;
    indentOptions.TAB_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;

    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                public class T {
                                \tvoid run() {
                                \t\tint a = 2 <caret>+ 2;
                                \t}
                                }
                                """);
    myFixture.type('\n');
    myFixture.checkResult("""
                            public class T {
                            \tvoid run() {
                            \t\tint a = 2\s
                            \t\t\t\t<caret>+ 2;
                            \t}
                            }
                            """);
  }
  
  public void testDoNotIndentOptions_WhenTabsDetected_AndUseTabsWasSetByDefault() {
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;
    
    indentOptions.USE_TAB_CHARACTER = true;
    
    indentOptions.TAB_SIZE = 8;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 8;

    myFixture.configureByText(JavaFileType.INSTANCE, myText);
    PsiFile file = myFixture.getFile();
    CommonCodeStyleSettings.IndentOptions options = mySettings.getIndentOptionsByFile(file);

    assertThat(options.INDENT_SIZE).isEqualTo(4);
    assertThat(indentOptions.CONTINUATION_INDENT_SIZE).isEqualTo(8);
  }
  
}
