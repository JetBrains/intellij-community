package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.02.2003
 * Time: 15:49:56
 * To change this template use Options | File Templates.
 */
public class KeywordCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/keywords";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testModifiersFileScope_1() throws Exception {
    configureByFile(BASE_PATH + "/fileScope-1.java");
    testByCount(7, "package", "public", "private", "import", "final", "class", "interface", "abstract", null);
  }

  private static final String[] ourClassScopeKeywords =
    new String[]{"public", "private", "protected", "import", "final", "class", "interface", "abstract", "enum", null};

  public void testModifiersFileScope_2() throws Exception {
    configureByFile(BASE_PATH + "/fileScope-2.java");
    testByCount(7, ourClassScopeKeywords);
  }

  public void testModifiersClassScope_1() throws Exception {
    configureByFile(BASE_PATH + "/classScope-1.java");
    testByCount(5, ourClassScopeKeywords);
  }

  public void testModifiersClassScope_2() throws Exception {
    configureByFile(BASE_PATH + "/classScope-2.java");
    testByCount(4, ourClassScopeKeywords);
  }

  public void testModifiersClassScope_3() throws Exception {
    configureByFile(BASE_PATH + "/classScope-3.java");
    testByCount(0, ourClassScopeKeywords);
  }

  public void testAfterAnnotations() throws Exception {
    configureByFile(BASE_PATH + "/afterAnnotations.java");
    testByCount(6, "public", "final", "class", "interface", "abstract", "enum", null);
  }

  public void testExtends_1() throws Exception {
    configureByFile(BASE_PATH + "/extends-1.java");
    testByCount(2, "extends", "implements", null);
  }

  public void testExtends_2() throws Exception {
    configureByFile(BASE_PATH + "/extends-2.java");
    testByCount(1, "extends", "implements", "AAA", "BBB", "instanceof");
  }

  public void testExtends_3() throws Exception {
    configureByFile(BASE_PATH + "/extends-3.java");
    testByCount(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof");
  }

  public void testExtends_4() throws Exception {
    configureByFile(BASE_PATH + "/extends-4.java");
    testByCount(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof");
  }

  public void testExtends_5() throws Exception {
    configureByFile(BASE_PATH + "/extends-5.java");
    testByCount(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof");
  }

  public void testExtends_6() throws Exception {
    configureByFile(BASE_PATH + "/extends-6.java");
    testByCount(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof");
  }

  public void testExtends_7() throws Exception {
    configureByFile(BASE_PATH + "/extends-7.java");
    checkResultByFile(BASE_PATH + "/extends-7-result.java");
  }

  public void testExtends_8() throws Exception {
    configureByFile(BASE_PATH + "/extends-8.java");
    checkResultByFile(BASE_PATH + "/extends-8-result.java");
  }

  public void testExtends_9() throws Exception {
    configureByFile(BASE_PATH + "/extends-9.java");
    checkResultByFile(BASE_PATH + "/extends-9-result.java");
  }

  public void testExtends_10() throws Exception {
    configureByFile(BASE_PATH + "/extends-10.java");
    checkResultByFile(BASE_PATH + "/extends-10-result.java");
  }

  public void testExtends_11() throws Exception {
    configureByFile(BASE_PATH + "/extends-11.java");
    checkResultByFile(BASE_PATH + "/extends-11-result.java");
  }

  public void testExtends_12() throws Exception {
    configureByFile(BASE_PATH + "/extends-12.java");
    checkResultByFile(BASE_PATH + "/extends-12-result.java");
  }

  public void testSynchronized_1() throws Exception {
    configureByFile(BASE_PATH + "/synchronized-1.java");
    checkResultByFile(BASE_PATH + "/synchronized-1-result.java");
  }

  public void testSynchronized_2() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    configureByFile(BASE_PATH + "/synchronized-2.java");
    checkResultByFile(BASE_PATH + "/synchronized-2-result.java");
  }

  public void testModifiersClassScope() throws Exception{
    configureByFile(BASE_PATH + "/classScope-4.java");
    testByCount(10, "package", "public", "private", "protected", "transient", "volatile", "static", "import", "final", "class", "interface",
                "abstract");
  }

  public void testMethodThrows() throws Exception{
    configureByFile(BASE_PATH + "/methodScope-1.java");
    testByCount(1, "throws");
  }

  public void testModifiersInMethodScope_1() throws Exception{
    configureByFile(BASE_PATH + "/methodScope-2.java");
    testByCount(1, "final", "public", "static", "volatile", "abstract");
  }

  public void testModifiersInMethodScope_2() throws Exception{
    configureByFile(BASE_PATH + "/methodScope-3.java");
    testByCount(1, "final", "public", "static", "volatile", "abstract", "throws", "instanceof");
  }

  public void testModifiersInMethodScope_3() throws Exception{
    configureByFile(BASE_PATH + "/methodScope-4.java");
    testByCount(6, "final", "try", "for", "while", "return", "throw");
  }

  public void testModifiersInMethodScope_5() throws Exception{
    configureByFile(BASE_PATH + "/methodScope-5.java");
    checkResultByFile(BASE_PATH + "/methodScope-5-out.java");
  }

  public void testExtendsInCastTypeParameters() throws Throwable { doTest(); }
  public void testExtendsWithRightContextInClassTypeParameters() throws Throwable { doTest(); }

  public void testExtraBracketAfterFinally() throws Throwable { doTest(); }
  public void testExtraBracketAfterFinally1() throws Throwable { doTest(); }

  public void testTrueInVariableDeclaration() throws Throwable { doTest(); }
  public void testNullInIf() throws Throwable { doTest(); }

  public void testExtendsInMethodParameters() throws Throwable { doTest(); }

  private void doTest() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testInstanceOf_1() throws Exception{
    configureByFile(BASE_PATH + "/instanceof-1.java");
    checkResultByFile(BASE_PATH + "/instanceof-1-result.java");
    //testByCount(new String[]{"instanceof"}, 1);
  }

  public void testInstanceOf_2() throws Exception{
    configureByFile(BASE_PATH + "/instanceof-2.java");
    checkResultByFile(BASE_PATH + "/instanceof-2-result.java");
    //testByCount(new String[]{"instanceof"}, 1);
  }

  public void testInstanceOf_3() throws Exception{
    configureByFile(BASE_PATH + "/instanceof-3.java");
    checkResultByFile(BASE_PATH + "/instanceof-3-result.java");
    //testByCount(new String[]{"instanceof"}, 1);
  }

  public void testCatchFinally() throws Exception{
    configureByFile(BASE_PATH + "/catch-1.java");
    testByCount(2, "catch", "finally");
  }

  public void testSuper1() throws Exception{
    configureByFile(BASE_PATH + "/super-1.java");
    testByCount(1, "super");
  }

  public void testSuper2() throws Exception{
    configureByFile(BASE_PATH + "/super-2.java");
    testByCount(0, "super");
  }

  public void testContinue() throws Exception{
    configureByFile(BASE_PATH + "/continue.java");
    checkResultByFile(BASE_PATH + "/continue_after.java");
  }

  public void testThrowsOnSeparateLine() throws Exception{
    configureByFile(BASE_PATH + "/throwsOnSeparateLine.java");
    checkResultByFile(BASE_PATH + "/throwsOnSeparateLine_after.java");
  }

  public void testDefaultInAnno() throws Exception{
    configureByFile(BASE_PATH + "/" + getTestName(true) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(true) + "_after.java");
  }

  public void testTryInExpression() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(true) + ".java");
    assertStringItems("toString", "this");
  }

  public void testNull() throws Exception{
    configureByFile(BASE_PATH + "/NullInMethodCall.java");
    checkResultByFile(BASE_PATH + "/NullInMethodCall_After.java");
    configureByFile(BASE_PATH + "/NullInMethodCall2.java");
    checkResultByFile(BASE_PATH + "/NullInMethodCall2_After.java");
  }
}