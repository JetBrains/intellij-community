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
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
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

  public void testNormal() { doTest(); }

  public void testStringLiteral() { doTest(); }
  public void testLiteralSCR4989() { doTest(); }

  public void testSCR3493() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493a() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493b() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493c() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493d() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR3493e() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  public void testSCR5959() {
    doTest();
  }
  public void testSCR6299() {
    doTest();
  }

  public void testLocalVar() { doTest(); }

  public void testSlashComment() { doTest(); }
  public void testDocComment() { doTest(); }

  public void testOnEmptyLine() { doTest(); }
  public void testCollapseClass() { doTest(); }
  public void testSCR10386() { doTest(); }
  public void testDeclarationWithInitializer() {doTest(); }

  public void testUnwrapCodeBlock1() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
      settings.IF_BRACE_FORCE = old;
    }
  }

  public void testUnwrapCodeBlock2() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
      settings.IF_BRACE_FORCE = old;
    }
  }

  @NotNull
  protected CommonCodeStyleSettings getJavaSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testAssignmentExpression() {
    doTest();
  }
  public void testReformatInsertsNewlines() {
    CommonCodeStyleSettings settings = getJavaSettings();
    final Element root = new Element("fake");
    settings.writeExternal(root);
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
      doTest();
    } finally {
      settings.readExternal(root);
    }
  }
  
  public void testForceBrace() {
    CommonCodeStyleSettings settings = getJavaSettings();
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest();
    } finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  public void testWrongWrapping() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 80;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testSubsequentJoiningAndUnexpectedTextRemoval() {
    // Inspired by IDEA-65342
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 50;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTest(2);
  }
  
  public void testLeaveTrailingComment() { doTest(); }

  public void testConvertComment() {
    doTest();
  }

  public void testJoiningMethodCallWhenItDoesntFit() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.RIGHT_MARGIN = 20;
    doTest();
  }

  public void testMultipleBlockComments() { doTest(); }

  public void testPreserveSpaceIfOnNewLineOptionEnabled() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.CATCH_ON_NEW_LINE = true;
    doTest();
  }

  private void doTest() {
    doTest(".java");
  }
  
  private void doTest(int times) {
    doTest(".java", times);
  }

  private void doTest(@NonNls final String ext) {
    doTest(ext, 1);
  }
  
  private void doTest(@NonNls final String ext, int times) {
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
