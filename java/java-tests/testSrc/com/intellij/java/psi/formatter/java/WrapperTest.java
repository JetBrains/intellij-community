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
package com.intellij.java.psi.formatter.java;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class WrapperTest extends JavaFormatterTestCase {
  @Override
  protected String getBasePath() {
    return "/psi/formatter/wrapping";
  }

  @Override
  protected String prepareText(final String text) {
    String result = text;
    result = StringUtil.trimStart(result, "\n");
    return result;
  }


  public void testExtendsKeywordWrappingForced() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTest("ExtendsWrappingForced", "ExtendsWrappingForced_after");
  }

  public void testExtendsKeywordWrappingAsNeeded() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 20;

    doTest("ExtendsWrappingForced", "ExtendsWrappingForced_after");
  }

  public void testExtendsListWrappingNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 45;
    doTest("ExtendsListWrapping", "ExtendsListWrapping_normal");
  }

  public void testExtendsListWrappingChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 45;
    doTest("ExtendsListWrapping", "ExtendsListWrapping_chopped");
  }

  public void testMethodParametersWrappingNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("MethodParameterList", "MethodParameterList_normal");
  }

  public void testMethodParametersWrappingChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("MethodParameterList", "MethodParameterList_chopped");
  }

  public void testMethodParametersWrappingChoppedParenMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("MethodParameterList", "MethodParameterList_chopped_parenmoved");
  }

  public void testCallParametersWrappingNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("CallParameterList", "CallParameterList_normal");
  }

  public void testNotBreakedIfNoParameters() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("CallNoParameteresList", "CallNoParameteresList_after");
  }

  public void testCallParametersWrappingChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("CallParameterList", "CallParameterList_chopped");
  }

  public void testNestedLongCalls() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 40;
    doTest("NestedLongCalls", "NestedLongCalls_after");
  }

  public void testCallParametersWrappingChoppedParenMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 50;
    doTest("CallParameterList", "CallParameterList_chopped_parenmoved");
  }

  public void testCallParametersWrappingChoppedBothParenMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 50;
    doTest("CallParameterList", "CallParameterList_chopped_bothparenmoved");
  }

  public void testChainedMethodCall() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 52;
    doTest("ChainedMethodCall", "ChainedMethodCall_normal");
  }

  public void testChainedMethodCallChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 52;
    doTest("ChainedMethodCall", "ChainedMethodCall_chopped");
  }

  public void testChainedCallsAndParams() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 120;
    doTest("ChainedCallsAndParams", "ChainedCallsAndParams_normal");
  }

  public void testParenthesesExpressionLParenWrap() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    settings.PARENTHESES_EXPRESSION_RPAREN_WRAP = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE=5;
    settings.RIGHT_MARGIN = 25;
    doTest("ParenthesizedExpressionWrap", "ParenthesizedExpressionWrap_left");
  }

  public void testParenthesesExpressionRParenWrap() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    settings.PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 25;
    doTest("ParenthesizedExpressionWrap", "ParenthesizedExpressionWrap_right");
  }

  public void testParenthesesExpressionBothParenWrap() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    settings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    settings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 25;
    doTest("ParenthesizedExpressionWrap", "ParenthesizedExpressionWrap_both");
  }

  public void testBinaryOperationNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 27;
    doTest("BinaryOperation", "BinaryOperation_normal");
  }

  public void testBinaryOperationChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 27;
    doTest("BinaryOperation", "BinaryOperation_chopped");
  }

  public void testBinaryOperationChoppedSignMove() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 27;

    doTest("BinaryOperation", "BinaryOperation_chopped_signmoved");
  }

  public void testTernaryOperationNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.RIGHT_MARGIN = 27;
    doTest("TernaryOperation", "TernaryOperation_normal");
  }

  public void testTernaryOperationChopped() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 27;
    doTest("TernaryOperation", "TernaryOperation_chopped");
  }

  public void testTernaryOperationChoppedSignMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 27;
    doTest("TernaryOperation", "TernaryOperation_chopped_signmoved");
  }

  public void testModifierList() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.MODIFIER_LIST_WRAP = true;

    doTest("ModifierList", "ModifierList_after");
  }

  public void testSimpleMethods() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    settings.RIGHT_MARGIN = 50;

    doTest("SimpleMethods", "SimpleMethods_after");
  }

  public void testForStatementNormal() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    settings.RIGHT_MARGIN = 80;

    doTest("ForStatement", "ForStatement_normal");
  }

  public void testForStatementLParenMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 80;

    doTest("ForStatement", "ForStatement_lparen");
  }

  public void testForStatementRParenMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 80;

    doTest("ForStatement", "ForStatement_rparen");
  }

  public void testForStatementBothParensMoved() throws Exception {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    defaultCodeStyle();
    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE = true;
    settings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 80;

    doTest("ForStatement", "ForStatement_both");
  }

  public void testKillSpacesInEmptyLine() throws Exception {
    defaultCodeStyle();
    doTest("KillSpacesInEmptyLine", "KillSpacesInEmptyLine_after");
  }

  public void testSCR11423() throws Exception {
      defaultCodeStyle();
      CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);
      settings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
      settings.THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

      settings.RIGHT_MARGIN = 80;

      doTest("SCR11423", "SCR11423_after");
  }

  public void testIncompleteFor() throws Exception {
    defaultCodeStyle();

    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);
    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 20;

    doTest("IncompleteFor", "IncompleteFor_after");
  }
  public void testSCR27373() throws Exception {
    defaultCodeStyle();

    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    settings.RIGHT_MARGIN = 100;

    doTest("SCR27373", "SCR27373_after");
  }

  public void testDoNotWrapCallChainIfParametersWrapped() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 140;
    getSettings(JavaLanguage.INSTANCE).CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 2;
    getSettings(JavaLanguage.INSTANCE).getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 2;
    getSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doTextTest("class Foo {\n" +
               "  void foo() {\n" +
               "    for (int i = 0; i < descriptors.length; i++) {\n" +
               "      descriptors[i] = manager.createProblemDescriptor(results.get(i), \"Usage of the API documented as @since 1.5\", (LocalQuickFix)null,ProblemHighlightType.GENERIC_ERROR_OR_WARNING);\n" +
               "    }" +
               "  }\n" +
               "}",
               "class Foo {\n" +
               "  void foo() {\n" +
               "    for (int i = 0; i < descriptors.length; i++) {\n" +
               "      descriptors[i] = manager.createProblemDescriptor(results.get(i), \"Usage of the API documented as @since 1.5\", (LocalQuickFix) null,\n" +
               "                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);\n" +
               "    }\n" +
               "  }\n" +
               "}");
  }

  private void defaultCodeStyle() {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);

    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.THROWS_LIST_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    settings.PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
    settings.PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
    settings.MODIFIER_LIST_WRAP = false;
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;

    settings.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
    settings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;

    settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;

    settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;

    settings.RIGHT_MARGIN = 120;
  }

  public void testThrowsListWrapping() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 20;

    getSettings(JavaLanguage.INSTANCE).THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).THROWS_LIST_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_THROWS_LIST = false;

    doTextTest("class Foo {\n" +
               "    void methodFoo() throws IOException, InvalidOperationException {\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void methodFoo()\n" +
               "            throws IOException, InvalidOperationException {\n" +
               "    }\n" +
               "}");

    getSettings(JavaLanguage.INSTANCE).THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings(JavaLanguage.INSTANCE).THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_THROWS_LIST = false;

    doTextTest("class Foo {\n" +
               "    void methodFoo() throws IOException, InvalidOperationException {\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void methodFoo() throws\n" +
               "            IOException,\n" +
               "            InvalidOperationException {\n" +
               "    }\n" +
               "}");

  }
}
