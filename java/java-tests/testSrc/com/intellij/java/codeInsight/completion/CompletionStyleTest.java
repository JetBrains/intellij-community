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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/testData")
public class CompletionStyleTest extends LightCodeInsightTestCase{
  private static final String BASE_PATH = "/codeInsight/completion/style";

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testGenericParametersReplace() {
    final String path = BASE_PATH;

    configureByFile(path + "/before1.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 2);
    checkResultByFile(path + "/after1.java");
  }

  public void testIDEADEV5935() {
    configureFromFileText("A.java",
                          "public class A {\n" +
                          "  public static void foo(String param1, String param2) {\n" +
                          "  }\n" +
                          "\n" +
                          "  public static void main(String[] args) {\n" +
                          "    String param1 = args[0];\n" +
                          "    String param2 = args[1];\n" +
                          "    foo(<caret>);\n" +
                          "  }\n" +
                          "}"
    );
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
    checkResultByText(
      "public class A {\n" +
      "  public static void foo(String param1, String param2) {\n" +
      "  }\n" +
      "\n" +
      "  public static void main(String[] args) {\n" +
      "    String param1 = args[0];\n" +
      "    String param2 = args[1];\n" +
      "    foo(param1, <caret>);\n" +
      "  }\n" +
      "}"
    );
  }

  public void testIDEADEV2878() {
    configureFromFileText(
      "A.java",
      "class Bar<T> {}\n" +
      "class Foo {\n" +
      "public void createFoo(Bar<?> <caret>)\n" + "}");
    performNormalCompletion();
    checkResultByText(
      "class Bar<T> {}\n" +
      "class Foo {\n" +
      "public void createFoo(Bar<?> bar<caret>)\n" + "}");
  }

  public void testMethodsParametersStyle1() {
    final String path = BASE_PATH;

    configureByFile(path + "/before3.java");
    performSmartCompletion();
    checkResultByFile(path + "/after3.java");
  }

  public void testMethodsParametersStyle2() {
    final String path = BASE_PATH;

    configureByFile(path + "/before4.java");
    performSmartCompletion();
    checkResultByFile(path + "/after4.java");
  }

  public void testKeywordsReplace() {
    final String path = BASE_PATH;

    configureByFile(path + "/before6.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after6.java");
  }

  public void testLocalVariablePreselect() {
    final String path = BASE_PATH;

    configureByFile(path + "/before5.java");
    performSmartCompletion();
    assertEquals("xxxx", getSelected().getLookupString());
  }

  public void testMethodCompletionInsideInlineTags() {
    final String path = BASE_PATH;

    configureByFile(path + "/before7.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 1);
    checkResultByFile(path + "/after7.java");
  }

  public void testConstantsCompletion1() {
    final String path = BASE_PATH;

    configureByFile(path + "/before8.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after8.java");
  }

  public void testConstantsCompletion2() {
    final String path = BASE_PATH;

    configureByFile(path + "/before9.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after9.java");
  }

  public void testConstantsCompletion3() {
    final String path = BASE_PATH;

    configureByFile(path + "/before10.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after10.java");
  }

  public void testCaretPositionAfterCompletion1() {
    final String path = BASE_PATH;

    configureByFile(path + "/before11.java");
    performNormalCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, 0);
    checkResultByFile(path + "/after11.java");
  }

  public void testCaretPositionAfterCompletion2() {
    final String path = BASE_PATH;

    configureByFile(path + "/before12.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, 0);
    checkResultByFile(path + "/after12.java");
  }

  public void testParensReuse() {
    final String path = BASE_PATH;

    configureByFile(path + "/before13.java");
    performNormalCompletion();
    checkResultByFile(path + "/after13.java");
  }

  public void testParensInSynchronized() {
    CommonCodeStyleSettings styleSettings = getCodeStyleSettings();
    styleSettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    styleSettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = true;
    final String path = BASE_PATH;

    configureByFile(path + "/before22.java");
    performNormalCompletion();
    checkResultByFile(path + "/after22.java");
  }

  public void testMethodReplacementReuseParens1() {
    final String path = BASE_PATH;

    configureByFile(path + "/before15.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after15.java");
  }

  public void testMethodReplacementReuseParens2() {
    final String path = BASE_PATH;

    configureByFile(path + "/before16.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after16.java");
  }

  public void testMethodReplacementReuseParens3() {
    final String path = BASE_PATH;

    configureByFile(path + "/before17.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after17.java");
  }

  public void testCastAfterNew1() {
    final String path = BASE_PATH;

    configureByFile(path + "/before21.java");
    performSmartCompletion();
    checkResultByFile(path + "/after21.java");
  }

  public void testCastParensStyle1() {
    final String path = BASE_PATH;

    getCodeStyleSettings().SPACE_WITHIN_CAST_PARENTHESES = true;
    getCodeStyleSettings().SPACE_AFTER_TYPE_CAST = false;
    configureByFile(path + "/before31.java");
    performSmartCompletion();
    checkResultByFile(path + "/after31.java");
  }

  public void testMethodParensStyle2() {
    final String path = BASE_PATH;
    CommonCodeStyleSettings styleSettings = getCodeStyleSettings();
    final boolean space_before_method_call_parentheses = styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES;
    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    configureByFile(path + "/before32.java");
    performNormalCompletion();
    checkResultByFile(path + "/after32.java");
    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = space_before_method_call_parentheses;
  }

  private static CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }


  public void testMethodParensStyle3() {
    final String path = BASE_PATH;
    CommonCodeStyleSettings styleSettings = getCodeStyleSettings();
    final boolean space_before_method_call_parentheses = styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES;
    final boolean space_within_method_call_parentheses = styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES;

    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    configureByFile(path + "/before32.java");
    performNormalCompletion();
    checkResultByFile(path + "/after32-a.java");
    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = space_before_method_call_parentheses;
    styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = space_within_method_call_parentheses;
  }

  /*public void testClassNameCompletion1() throws Exception{
    final String path = BASE_PATH;
    configureByFile(path + "/before34.java");
    performClassNameCompletion();
    select('<', getSelected());
    checkResultByFile(path + "/after34.java");
  } */

  public void testStaticsCompletion1() {
    final String path = BASE_PATH;
    configureByFile(path + "/before33.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after33.java");
  }

  public void testStaticsCompletion2() {
    final String path = BASE_PATH;
    configureByFile(path + "/before39.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after39.java");
  }

  public void testJavaDocLinkCompletion1() {
    final String path = BASE_PATH;
    configureByFile(path + "/before36.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after36.java");
  }


  public void testGetterNameInInterface() {
    final String path = BASE_PATH;
    configureByFile(path + "/before38.java");
    performNormalCompletion();
    checkResultByFile(path + "/after38.java");
  }

  private void performSmartCompletion(){
    new CodeCompletionHandlerBase(CompletionType.SMART).invokeCompletion(getProject(), getEditor());
  }

  private void performNormalCompletion(){
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), getEditor());
  }

  private void select(char completionChar, int index){
    ((LookupManagerImpl)LookupManager.getInstance(getProject())).forceSelection(completionChar, index);
  }

  private void select(char completionChar, LookupElement item){
    ((LookupManagerImpl)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private LookupElement getSelected(){
    return LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem();
  }

  @Override
  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();    
  }

  public void testAfterNew15() {
    final LanguageLevelProjectExtension ll = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel old = ll.getLanguageLevel();
    ll.setLanguageLevel(LanguageLevel.JDK_1_5);

    try {
      final String path = BASE_PATH;
      configureByFile(path + "/AfterNew15.java");
      performSmartCompletion();
      select('\n', getSelected());
      checkResultByFile(path + "/AfterNew15-out.java");
    }
    finally {
      ll.setLanguageLevel(old);
    }
  }

}
