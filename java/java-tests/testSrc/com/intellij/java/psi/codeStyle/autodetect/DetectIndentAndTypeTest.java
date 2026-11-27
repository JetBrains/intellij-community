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
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@TestApplication
public class DetectIndentAndTypeTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private CodeStyleSettings mySettings;
  private static final String myText = """
    public class T {
    \tvoid run() {
    \t\tint t = 1 + <caret>2;
    \t}
    }""";

  @BeforeEach
  void setUp() {
    CodeStyleSettingsManager.getInstance(codeInsightFixture.get().getProject()).setTemporarySettings(CodeStyle.createTestSettings());
    mySettings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    mySettings.AUTODETECT_INDENTS = true;
    DetectableIndentOptionsProvider optionsProvider = DetectableIndentOptionsProvider.getInstance();
    if (optionsProvider != null) {
      optionsProvider.setEnabledInTest(true);
    }
  }

  @AfterEach
  void tearDown() {
    try {
      CodeStyleSettingsManager.getInstance(codeInsightFixture.get().getProject()).dropTemporarySettings();
    }
    finally {
      DetectableIndentOptionsProvider optionsProvider = DetectableIndentOptionsProvider.getInstance();
      if (optionsProvider != null) {
        optionsProvider.setEnabledInTest(false);
      }
    }
  }

  @Test
  public void testWhenTabsDetected_SetIndentSizeToTabSize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testContinuationTab_AsTabSize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testContinuationTabs_AsDoubleTabSize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testWhenTabsDetected_SetContinuationIndentSizeToDoubleTabSize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
  
  @Test
  public void testDoNotIndentOptions_WhenTabsDetected_AndUseTabsWasSetByDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CommonCodeStyleSettings common = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = common.getIndentOptions();

    assert indentOptions != null;
    
    indentOptions.USE_TAB_CHARACTER = true;
    
    indentOptions.TAB_SIZE = 8;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 8;

    myFixture.configureByText(JavaFileType.INSTANCE, myText);
    PsiFile file = myFixture.getFile();
    CommonCodeStyleSettings.IndentOptions options = ReadAction.computeBlocking(() -> mySettings.getIndentOptionsByFile(file));

    assertThat(options.INDENT_SIZE).isEqualTo(4);
    assertThat(indentOptions.CONTINUATION_INDENT_SIZE).isEqualTo(8);
  }
  
}
