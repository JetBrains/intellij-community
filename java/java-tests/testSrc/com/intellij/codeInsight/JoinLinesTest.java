/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JoinLinesTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNormal() throws Exception { doTest(); }

  public void testStringLiteral() throws Exception { doTest(); }
  public void testLiteralSCR4989() throws Exception { doTest(); }

  public void testSCR3493() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493a() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493b() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493c() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493d() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493e() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
    }
  }
  public void testSCR5959() throws Exception {
    doTest();
  }
  public void testSCR6299() throws Exception {
    doTest();
  }

  public void testLocalVar() throws Exception { doTest(); }

  public void testSlashComment() throws Exception { doTest(); }
  public void testDocComment() throws Exception { doTest(); }

  public void testOnEmptyLine() throws Exception { doTest(); }
  public void testCollapseClass() throws Exception { doTest(); }
  public void testSCR10386() throws Exception { doTest(); }
  public void testDeclarationWithInitializer() throws Exception {doTest(); }

  public void testUnwrapCodeBlock1() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      settings.getCommonSettings(JavaLanguage.INSTANCE).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
      settings.getCommonSettings(JavaLanguage.INSTANCE).IF_BRACE_FORCE = old;
    }
  }

  public void testUnwrapCodeBlock2() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean use_tab_character = settings.useTabCharacter(null);
    boolean smart_tabs = settings.isSmartTabs(null);
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      settings.getCommonSettings(JavaLanguage.INSTANCE).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest();
    } finally {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = smart_tabs;
      settings.getCommonSettings(JavaLanguage.INSTANCE).IF_BRACE_FORCE = old;
    }
  }

  public void testAssignmentExpression() throws Exception {
    doTest();
  }
  public void testReformatInsertsNewlines() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    final Element root = new Element("fake");
    settings.writeExternal(root);
    try {
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
      doTest();
    } finally {
      settings.readExternal(root);
    }
  }
  
  public void testForceBrace() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest();
    } finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  public void testWrongWrapping() throws Exception{
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.setDefaultRightMargin(80);
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testSubsequentJoiningAndUnexpectedTextRemoval() throws Exception {
    // Inspired by IDEA-65342
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.setDefaultRightMargin(50);
    settings.getCommonSettings(JavaLanguage.INSTANCE).CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTest(2);
  }
  
  public void testLeaveTrailingComment() throws Exception { doTest(); }

  public void testConvertComment() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(".java");
  }
  
  private void doTest(int times) throws Exception {
    doTest(".java", times);
  }

  private void doTest(@NonNls final String ext) throws Exception {
    doTest(ext, 1);
  }
  
  private void doTest(@NonNls final String ext, int times) throws Exception {
    @NonNls String path = "/codeInsight/joinLines/";

    configureByFile(path + getTestName(false) + ext);
    while (times-- > 0) {
      performAction();
    }
    checkResultByFile(path + getTestName(false) + "_after" + ext);
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES);

    actionHandler.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
}
