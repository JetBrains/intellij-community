package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.08.2003
 * Time: 15:44:18
 * To change this template use Options | File Templates.
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class CompletionStyleTest extends LightCodeInsightTestCase{
  private static final String BASE_PATH = "/codeInsight/completion/style";

  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).clearStatistics();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testGenericParametersReplace() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before1.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 2);
    checkResultByFile(path + "/after1.java");
  }

  public void testIDEADEV4566() throws Exception {
    configureFromFileText(
      "A.java",
      "public class A {\n" +
      "  public static Object createA(String s) {}\n" +
      "  public static void foo() {\n" +
      "    createA(<caret>);\n" +
      "  }\n" +
      "}");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, 0);
    checkResultByText(
      "public class A {\n" +
      "  public static Object createA(String s) {}\n" +
      "  public static void foo() {\n" +
      "    createA(String.copyValueOf(<caret>));\n" +
      "  }\n" +
      "}");
  }

  public void testIDEADEV5935() throws Exception {
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

  public void testIDEADEV2878() throws Exception {
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

  public void testMethodsParametersStyle1() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before3.java");
    performSmartCompletion();
    checkResultByFile(path + "/after3.java");
  }

  public void testMethodsParametersStyle2() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before4.java");
    performSmartCompletion();
    checkResultByFile(path + "/after4.java");
  }

  public void testKeywordsReplace() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before6.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 3); //equals(), true & false go first
    checkResultByFile(path + "/after6.java");
  }

  public void testLocalVariablePreselect() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before5.java");
    performSmartCompletion();
    assertEquals("xxxx", getSelected().getLookupString());
  }

  public void testMethodCompletionInsideInlineTags() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before7.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 1);
    checkResultByFile(path + "/after7.java");
  }

  public void testConstantsCompletion1() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before8.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after8.java");
  }

  public void testConstantsCompletion2() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before9.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after9.java");
  }

  public void testConstantsCompletion3() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before10.java");
    performSmartCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, 0);
    checkResultByFile(path + "/after10.java");
  }

  public void testCaretPositionAfterCompletion1() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before11.java");
    performNormalCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, 0);
    checkResultByFile(path + "/after11.java");
  }

  public void testCaretPositionAfterCompletion2() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before12.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, 0);
    checkResultByFile(path + "/after12.java");
  }

  public void testParensReuse() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before13.java");
    performNormalCompletion();
    checkResultByFile(path + "/after13.java");
  }

  public void testParensInSynchronized() throws Exception{
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = true;
    final String path = BASE_PATH;

    configureByFile(path + "/before22.java");
    performNormalCompletion();
    checkResultByFile(path + "/after22.java");
  }

  public void testMethodReplacementReuseParens1() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before15.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after15.java");
  }

  public void testMethodReplacementReuseParens2() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before16.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after16.java");
  }

  public void testMethodReplacementReuseParens3() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before17.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after17.java");
  }

  public void testCastAfterNew1() throws Exception{
    final String path = BASE_PATH;

    configureByFile(path + "/before21.java");
    performSmartCompletion();
    checkResultByFile(path + "/after21.java");
  }

  public void testCastParensStyle1() throws Exception{
    final String path = BASE_PATH;
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    final boolean space_within_cast_parentheses = styleSettings.SPACE_WITHIN_CAST_PARENTHESES;
    final boolean space_after_type_cast = styleSettings.SPACE_AFTER_TYPE_CAST;

    styleSettings.SPACE_WITHIN_CAST_PARENTHESES = true;
    styleSettings.SPACE_AFTER_TYPE_CAST = false;
    configureByFile(path + "/before31.java");
    performSmartCompletion();
    checkResultByFile(path + "/after31.java");
    styleSettings.SPACE_WITHIN_CAST_PARENTHESES = space_within_cast_parentheses;
    styleSettings.SPACE_AFTER_TYPE_CAST = space_after_type_cast;
  }

  public void testMethodParensStyle2() throws Exception{
    final String path = BASE_PATH;
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    final boolean space_before_method_call_parentheses = styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES;
    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    configureByFile(path + "/before32.java");
    performNormalCompletion();
    checkResultByFile(path + "/after32.java");
    styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = space_before_method_call_parentheses;
  }

  public void testMethodParensStyle3() throws Exception{
    final String path = BASE_PATH;
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
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

  public void testStaticsCompletion1() throws Exception{
    final String path = BASE_PATH;
    configureByFile(path + "/before33.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after33.java");
  }

  public void testStaticsCompletion2() throws Exception{
    final String path = BASE_PATH;
    configureByFile(path + "/before39.java");
    performSmartCompletion();
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after39.java");
  }

  public void testJavaDocLinkCompletion1() throws Exception{
    final String path = BASE_PATH;
    configureByFile(path + "/before36.java");
    performNormalCompletion();
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile(path + "/after36.java");
  }


  public void testGetterNameInInterface() throws Exception{
    final String path = BASE_PATH;
    configureByFile(path + "/before38.java");
    performNormalCompletion();
    checkResultByFile(path + "/after38.java");
  }

  private void performSmartCompletion(){
    new CodeCompletionHandlerBase(CompletionType.SMART).invoke(getProject(), getEditor(), getFile());
  }

  private void performNormalCompletion(){
    new CodeCompletionHandlerBase(CompletionType.BASIC).invoke(getProject(), getEditor(), getFile());
  }

  private void select(char completionChar, int index){
    ((TestLookupManager)LookupManager.getInstance(getProject())).forceSelection(completionChar, index);
  }

  private void select(char completionChar, LookupElement item){
    ((TestLookupManager)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private LookupElement getSelected(){
    return LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem();
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();    //To change body of overriden methods use Options | File Templates.
  }
}
