// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyleBean;
import com.intellij.formatting.BraceStyle;
import com.intellij.formatting.WrapType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class JavaCodeStyleBean extends CodeStyleBean {
  @NotNull
  @Override
  protected Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  public boolean isLineCommentAtFirstColumn() {
    return getCommonSettings().LINE_COMMENT_AT_FIRST_COLUMN;
  }

  public void setLineCommentAtFirstColumn(boolean value) {getCommonSettings().LINE_COMMENT_AT_FIRST_COLUMN = value;}

  public boolean isBlockCommentAtFirstColumn() {
    return getCommonSettings().BLOCK_COMMENT_AT_FIRST_COLUMN;
  }

  public void setBlockCommentAtFirstColumn(boolean value) {getCommonSettings().BLOCK_COMMENT_AT_FIRST_COLUMN = value;}

  public boolean isLineCommentAddSpace() {
    return getCommonSettings().LINE_COMMENT_ADD_SPACE;
  }

  public void setLineCommentAddSpace(boolean value) {getCommonSettings().LINE_COMMENT_ADD_SPACE = value;}

  public boolean isKeepLineBreaks() {
    return getCommonSettings().KEEP_LINE_BREAKS;
  }

  public void setKeepLineBreaks(boolean value) {getCommonSettings().KEEP_LINE_BREAKS = value;}

  public boolean isKeepFirstColumnComment() {
    return getCommonSettings().KEEP_FIRST_COLUMN_COMMENT;
  }

  public void setKeepFirstColumnComment(boolean value) {getCommonSettings().KEEP_FIRST_COLUMN_COMMENT = value;}

  public boolean isKeepControlStatementInOneLine() {
    return getCommonSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE;
  }

  public void setKeepControlStatementInOneLine(boolean value) {getCommonSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = value;}

  public int getKeepBlankLinesInDeclarations() {
    return getCommonSettings().KEEP_BLANK_LINES_IN_DECLARATIONS;
  }

  public void setKeepBlankLinesInDeclarations(int value) {getCommonSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = value;}

  public int getKeepBlankLinesInCode() {
    return getCommonSettings().KEEP_BLANK_LINES_IN_CODE;
  }

  public void setKeepBlankLinesInCode(int value) {getCommonSettings().KEEP_BLANK_LINES_IN_CODE = value;}

  public int getKeepBlankLinesBeforeRightBrace() {
    return getCommonSettings().KEEP_BLANK_LINES_BEFORE_RBRACE;
  }

  public void setKeepBlankLinesBeforeRightBrace(int value) {getCommonSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = value;}

  public int getBlankLinesBeforePackage() {
    return getCommonSettings().BLANK_LINES_BEFORE_PACKAGE;
  }

  public void setBlankLinesBeforePackage(int value) {getCommonSettings().BLANK_LINES_BEFORE_PACKAGE = value;}

  public int getBlankLinesAfterPackage() {
    return getCommonSettings().BLANK_LINES_AFTER_PACKAGE;
  }

  public void setBlankLinesAfterPackage(int value) {getCommonSettings().BLANK_LINES_AFTER_PACKAGE = value;}

  public int getBlankLinesBeforeImports() {
    return getCommonSettings().BLANK_LINES_BEFORE_IMPORTS;
  }

  public void setBlankLinesBeforeImports(int value) {getCommonSettings().BLANK_LINES_BEFORE_IMPORTS = value;}

  public int getBlankLinesAfterImports() {
    return getCommonSettings().BLANK_LINES_AFTER_IMPORTS;
  }

  public void setBlankLinesAfterImports(int value) {getCommonSettings().BLANK_LINES_AFTER_IMPORTS = value;}

  public int getBlankLinesAroundClass() {
    return getCommonSettings().BLANK_LINES_AROUND_CLASS;
  }

  public void setBlankLinesAroundClass(int value) {getCommonSettings().BLANK_LINES_AROUND_CLASS = value;}

  public int getBlankLinesAroundField() {
    return getCommonSettings().BLANK_LINES_AROUND_FIELD;
  }

  public void setBlankLinesAroundField(int value) {getCommonSettings().BLANK_LINES_AROUND_FIELD = value;}

  public int getBlankLinesAroundMethod() {
    return getCommonSettings().BLANK_LINES_AROUND_METHOD;
  }

  public void setBlankLinesAroundMethod(int value) {getCommonSettings().BLANK_LINES_AROUND_METHOD = value;}

  public int getBlankLinesBeforeMethodBody() {
    return getCommonSettings().BLANK_LINES_BEFORE_METHOD_BODY;
  }

  public void setBlankLinesBeforeMethodBody(int value) {getCommonSettings().BLANK_LINES_BEFORE_METHOD_BODY = value;}

  public int getBlankLinesAroundFieldInInterface() {
    return getCommonSettings().BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
  }

  public void setBlankLinesAroundFieldInInterface(int value) {getCommonSettings().BLANK_LINES_AROUND_FIELD_IN_INTERFACE = value;}

  public int getBlankLinesAroundMethodInInterface() {
    return getCommonSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE;
  }

  public void setBlankLinesAroundMethodInInterface(int value) {getCommonSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = value;}

  public int getBlankLinesAfterClassHeader() {
    return getCommonSettings().BLANK_LINES_AFTER_CLASS_HEADER;
  }

  public void setBlankLinesAfterClassHeader(int value) {getCommonSettings().BLANK_LINES_AFTER_CLASS_HEADER = value;}

  public int getBlankLinesAfterAnonymousClassHeader() {
    return getCommonSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER;
  }

  public void setBlankLinesAfterAnonymousClassHeader(int value) {getCommonSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = value;}

  public int getBlankLinesBeforeClassEnd() {
    return getCommonSettings().BLANK_LINES_BEFORE_CLASS_END;
  }

  public void setBlankLinesBeforeClassEnd(int value) {getCommonSettings().BLANK_LINES_BEFORE_CLASS_END = value;}

  public int getBraceStyle() {
    return getCommonSettings().BRACE_STYLE;
  }

  public void setBraceStyle(int value) {getCommonSettings().BRACE_STYLE = value;}

  public BraceStyle getClassBraceStyle() {
    return BraceStyle.fromInt(getCommonSettings().CLASS_BRACE_STYLE);
  }

  public void setClassBraceStyle(BraceStyle value) {getCommonSettings().CLASS_BRACE_STYLE = value.intValue();}

  public BraceStyle getMethodBraceStyle() {
    return BraceStyle.fromInt(getCommonSettings().METHOD_BRACE_STYLE);
  }

  public void setMethodBraceStyle(BraceStyle value) {getCommonSettings().METHOD_BRACE_STYLE = value.intValue();}

  public BraceStyle getLambdaBraceStyle() {
    return BraceStyle.fromInt(getCommonSettings().LAMBDA_BRACE_STYLE);
  }

  public void setLambdaBraceStyle(BraceStyle value) {getCommonSettings().LAMBDA_BRACE_STYLE = value.intValue();}

  public boolean isDoNotIndentTopLevelClassMembers() {
    return getCommonSettings().DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS;
  }

  public void setDoNotIndentTopLevelClassMembers(boolean value) {getCommonSettings().DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = value;}

  public boolean isElseOnNewLine() {
    return getCommonSettings().ELSE_ON_NEW_LINE;
  }

  public void setElseOnNewLine(boolean value) {getCommonSettings().ELSE_ON_NEW_LINE = value;}

  public boolean isWhileOnNewLine() {
    return getCommonSettings().WHILE_ON_NEW_LINE;
  }

  public void setWhileOnNewLine(boolean value) {getCommonSettings().WHILE_ON_NEW_LINE = value;}

  public boolean isCatchOnNewLine() {
    return getCommonSettings().CATCH_ON_NEW_LINE;
  }

  public void setCatchOnNewLine(boolean value) {getCommonSettings().CATCH_ON_NEW_LINE = value;}

  public boolean isFinallyOnNewLine() {
    return getCommonSettings().FINALLY_ON_NEW_LINE;
  }

  public void setFinallyOnNewLine(boolean value) {getCommonSettings().FINALLY_ON_NEW_LINE = value;}

  public boolean isIndentCaseFromSwitch() {
    return getCommonSettings().INDENT_CASE_FROM_SWITCH;
  }

  public void setIndentCaseFromSwitch(boolean value) {getCommonSettings().INDENT_CASE_FROM_SWITCH = value;}

  public boolean isCaseStatementOnNewLine() {
    return getCommonSettings().CASE_STATEMENT_ON_NEW_LINE;
  }

  public void setCaseStatementOnNewLine(boolean value) {getCommonSettings().CASE_STATEMENT_ON_NEW_LINE = value;}

  public boolean isSpecialElseIfTreatment() {
    return getCommonSettings().SPECIAL_ELSE_IF_TREATMENT;
  }

  public void setSpecialElseIfTreatment(boolean value) {getCommonSettings().SPECIAL_ELSE_IF_TREATMENT = value;}

  public boolean isAlignMultilineChainedMethods() {
    return getCommonSettings().ALIGN_MULTILINE_CHAINED_METHODS;
  }

  public void setAlignMultilineChainedMethods(boolean value) {getCommonSettings().ALIGN_MULTILINE_CHAINED_METHODS = value;}

  public boolean isAlignMultilineParameters() {
    return getCommonSettings().ALIGN_MULTILINE_PARAMETERS;
  }

  public void setAlignMultilineParameters(boolean value) {getCommonSettings().ALIGN_MULTILINE_PARAMETERS = value;}

  public boolean isAlignMultilineParametersInCalls() {
    return getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS;
  }

  public void setAlignMultilineParametersInCalls(boolean value) {getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = value;}

  public boolean isAlignMultilineResources() {
    return getCommonSettings().ALIGN_MULTILINE_RESOURCES;
  }

  public void setAlignMultilineResources(boolean value) {getCommonSettings().ALIGN_MULTILINE_RESOURCES = value;}

  public boolean isAlignMultilineFor() {
    return getCommonSettings().ALIGN_MULTILINE_FOR;
  }

  public void setAlignMultilineFor(boolean value) {getCommonSettings().ALIGN_MULTILINE_FOR = value;}

  public boolean isAlignMultilineBinaryOperation() {
    return getCommonSettings().ALIGN_MULTILINE_BINARY_OPERATION;
  }

  public void setAlignMultilineBinaryOperation(boolean value) {getCommonSettings().ALIGN_MULTILINE_BINARY_OPERATION = value;}

  public boolean isAlignMultilineAssignment() {
    return getCommonSettings().ALIGN_MULTILINE_ASSIGNMENT;
  }

  public void setAlignMultilineAssignment(boolean value) {getCommonSettings().ALIGN_MULTILINE_ASSIGNMENT = value;}

  public boolean isAlignMultilineTernaryOperation() {
    return getCommonSettings().ALIGN_MULTILINE_TERNARY_OPERATION;
  }

  public void setAlignMultilineTernaryOperation(boolean value) {getCommonSettings().ALIGN_MULTILINE_TERNARY_OPERATION = value;}

  public boolean isAlignMultilineThrowsList() {
    return getCommonSettings().ALIGN_MULTILINE_THROWS_LIST;
  }

  public void setAlignMultilineThrowsList(boolean value) {getCommonSettings().ALIGN_MULTILINE_THROWS_LIST = value;}

  public boolean isAlignThrowsKeyword() {
    return getCommonSettings().ALIGN_THROWS_KEYWORD;
  }

  public void setAlignThrowsKeyword(boolean value) {getCommonSettings().ALIGN_THROWS_KEYWORD = value;}

  public boolean isAlignMultilineExtendsList() {
    return getCommonSettings().ALIGN_MULTILINE_EXTENDS_LIST;
  }

  public void setAlignMultilineExtendsList(boolean value) {getCommonSettings().ALIGN_MULTILINE_EXTENDS_LIST = value;}

  public boolean isAlignMultilineMethodBrackets() {
    return getCommonSettings().ALIGN_MULTILINE_METHOD_BRACKETS;
  }

  public void setAlignMultilineMethodBrackets(boolean value) {getCommonSettings().ALIGN_MULTILINE_METHOD_BRACKETS = value;}

  public boolean isAlignMultilineParenthesizedExpression() {
    return getCommonSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION;
  }

  public void setAlignMultilineParenthesizedExpression(boolean value) {
    getCommonSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = value;
  }

  public boolean isAlignMultilineArrayInitializerExpression() {
    return getCommonSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION;
  }

  public void setAlignMultilineArrayInitializerExpression(boolean value) {
    getCommonSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = value;
  }

  public boolean isAlignGroupFieldDeclarations() {
    return getCommonSettings().ALIGN_GROUP_FIELD_DECLARATIONS;
  }

  public void setAlignGroupFieldDeclarations(boolean value) {getCommonSettings().ALIGN_GROUP_FIELD_DECLARATIONS = value;}

  public boolean isAlignConsecutiveVariableDeclarations() {
    return getCommonSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS;
  }

  public void setAlignConsecutiveVariableDeclarations(boolean value) {getCommonSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = value;}

  public boolean isAlignSubsequentSimpleMethods() {
    return getCommonSettings().ALIGN_SUBSEQUENT_SIMPLE_METHODS;
  }

  public void setAlignSubsequentSimpleMethods(boolean value) {getCommonSettings().ALIGN_SUBSEQUENT_SIMPLE_METHODS = value;}

  public boolean isSpaceAroundAssignmentOperators() {
    return getCommonSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS;
  }

  public void setSpaceAroundAssignmentOperators(boolean value) {getCommonSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = value;}

  public boolean isSpaceAroundLogicalOperators() {
    return getCommonSettings().SPACE_AROUND_LOGICAL_OPERATORS;
  }

  public void setSpaceAroundLogicalOperators(boolean value) {getCommonSettings().SPACE_AROUND_LOGICAL_OPERATORS = value;}

  public boolean isSpaceAroundEqualityOperators() {
    return getCommonSettings().SPACE_AROUND_EQUALITY_OPERATORS;
  }

  public void setSpaceAroundEqualityOperators(boolean value) {getCommonSettings().SPACE_AROUND_EQUALITY_OPERATORS = value;}

  public boolean isSpaceAroundRelationalOperators() {
    return getCommonSettings().SPACE_AROUND_RELATIONAL_OPERATORS;
  }

  public void setSpaceAroundRelationalOperators(boolean value) {getCommonSettings().SPACE_AROUND_RELATIONAL_OPERATORS = value;}

  public boolean isSpaceAroundBitwiseOperators() {
    return getCommonSettings().SPACE_AROUND_BITWISE_OPERATORS;
  }

  public void setSpaceAroundBitwiseOperators(boolean value) {getCommonSettings().SPACE_AROUND_BITWISE_OPERATORS = value;}

  public boolean isSpaceAroundAdditiveOperators() {
    return getCommonSettings().SPACE_AROUND_ADDITIVE_OPERATORS;
  }

  public void setSpaceAroundAdditiveOperators(boolean value) {getCommonSettings().SPACE_AROUND_ADDITIVE_OPERATORS = value;}

  public boolean isSpaceAroundMultiplicativeOperators() {
    return getCommonSettings().SPACE_AROUND_MULTIPLICATIVE_OPERATORS;
  }

  public void setSpaceAroundMultiplicativeOperators(boolean value) {getCommonSettings().SPACE_AROUND_MULTIPLICATIVE_OPERATORS = value;}

  public boolean isSpaceAroundShiftOperators() {
    return getCommonSettings().SPACE_AROUND_SHIFT_OPERATORS;
  }

  public void setSpaceAroundShiftOperators(boolean value) {getCommonSettings().SPACE_AROUND_SHIFT_OPERATORS = value;}

  public boolean isSpaceAroundUnaryOperator() {
    return getCommonSettings().SPACE_AROUND_UNARY_OPERATOR;
  }

  public void setSpaceAroundUnaryOperator(boolean value) {getCommonSettings().SPACE_AROUND_UNARY_OPERATOR = value;}

  public boolean isSpaceAroundLambdaArrow() {
    return getCommonSettings().SPACE_AROUND_LAMBDA_ARROW;
  }

  public void setSpaceAroundLambdaArrow(boolean value) {getCommonSettings().SPACE_AROUND_LAMBDA_ARROW = value;}

  public boolean isSpaceAroundMethodRefDblColon() {
    return getCommonSettings().SPACE_AROUND_METHOD_REF_DBL_COLON;
  }

  public void setSpaceAroundMethodRefDblColon(boolean value) {getCommonSettings().SPACE_AROUND_METHOD_REF_DBL_COLON = value;}

  public boolean isSpaceAfterComma() {
    return getCommonSettings().SPACE_AFTER_COMMA;
  }

  public void setSpaceAfterComma(boolean value) {getCommonSettings().SPACE_AFTER_COMMA = value;}

  public boolean isSpaceAfterCommaInTypeArguments() {
    return getCommonSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS;
  }

  public void setSpaceAfterCommaInTypeArguments(boolean value) {getCommonSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = value;}

  public boolean isSpaceBeforeComma() {
    return getCommonSettings().SPACE_BEFORE_COMMA;
  }

  public void setSpaceBeforeComma(boolean value) {getCommonSettings().SPACE_BEFORE_COMMA = value;}

  public boolean isSpaceAfterForSemicolon() {
    return getCommonSettings().SPACE_AFTER_SEMICOLON;
  }

  public void setSpaceAfterForSemicolon(boolean value) {getCommonSettings().SPACE_AFTER_SEMICOLON = value;}

  public boolean isSpaceBeforeForSemicolon() {
    return getCommonSettings().SPACE_BEFORE_SEMICOLON;
  }

  public void setSpaceBeforeForSemicolon(boolean value) {getCommonSettings().SPACE_BEFORE_SEMICOLON = value;}

  public boolean isSpaceWithinParentheses() {
    return getCommonSettings().SPACE_WITHIN_PARENTHESES;
  }

  public void setSpaceWithinParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_PARENTHESES = value;}

  public boolean isSpaceWithinMethodCallParentheses() {
    return getCommonSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES;
  }

  public void setSpaceWithinMethodCallParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = value;}

  public boolean isSpaceWithinEmptyMethodCallParentheses() {
    return getCommonSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES;
  }

  public void setSpaceWithinEmptyMethodCallParentheses(boolean value) {
    getCommonSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = value;
  }

  public boolean isSpaceWithinMethodParentheses() {
    return getCommonSettings().SPACE_WITHIN_METHOD_PARENTHESES;
  }

  public void setSpaceWithinMethodParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_METHOD_PARENTHESES = value;}

  public boolean isSpaceWithinEmptyMethodParentheses() {
    return getCommonSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES;
  }

  public void setSpaceWithinEmptyMethodParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = value;}

  public boolean isSpaceWithinIfParentheses() {
    return getCommonSettings().SPACE_WITHIN_IF_PARENTHESES;
  }

  public void setSpaceWithinIfParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_IF_PARENTHESES = value;}

  public boolean isSpaceWithinWhileParentheses() {
    return getCommonSettings().SPACE_WITHIN_WHILE_PARENTHESES;
  }

  public void setSpaceWithinWhileParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_WHILE_PARENTHESES = value;}

  public boolean isSpaceWithinForParentheses() {
    return getCommonSettings().SPACE_WITHIN_FOR_PARENTHESES;
  }

  public void setSpaceWithinForParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_FOR_PARENTHESES = value;}

  public boolean isSpaceWithinTryParentheses() {
    return getCommonSettings().SPACE_WITHIN_TRY_PARENTHESES;
  }

  public void setSpaceWithinTryParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_TRY_PARENTHESES = value;}

  public boolean isSpaceWithinCatchParentheses() {
    return getCommonSettings().SPACE_WITHIN_CATCH_PARENTHESES;
  }

  public void setSpaceWithinCatchParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_CATCH_PARENTHESES = value;}

  public boolean isSpaceWithinSwitchParentheses() {
    return getCommonSettings().SPACE_WITHIN_SWITCH_PARENTHESES;
  }

  public void setSpaceWithinSwitchParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_SWITCH_PARENTHESES = value;}

  public boolean isSpaceWithinSynchronizedParentheses() {
    return getCommonSettings().SPACE_WITHIN_SYNCHRONIZED_PARENTHESES;
  }

  public void setSpaceWithinSynchronizedParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = value;}

  public boolean isSpaceWithinCastParentheses() {
    return getCommonSettings().SPACE_WITHIN_CAST_PARENTHESES;
  }

  public void setSpaceWithinCastParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_CAST_PARENTHESES = value;}

  public boolean isSpaceWithinBrackets() {
    return getCommonSettings().SPACE_WITHIN_BRACKETS;
  }

  public void setSpaceWithinBrackets(boolean value) {getCommonSettings().SPACE_WITHIN_BRACKETS = value;}

  public boolean isSpaceWithinBraces() {
    return getCommonSettings().SPACE_WITHIN_BRACES;
  }

  public void setSpaceWithinBraces(boolean value) {getCommonSettings().SPACE_WITHIN_BRACES = value;}

  public boolean isSpaceWithinArrayInitializerBraces() {
    return getCommonSettings().SPACE_WITHIN_ARRAY_INITIALIZER_BRACES;
  }

  public void setSpaceWithinArrayInitializerBraces(boolean value) {getCommonSettings().SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = value;}

  public boolean isSpaceWithinEmptyArrayInitializerBraces() {
    return getCommonSettings().SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES;
  }

  public void setSpaceWithinEmptyArrayInitializerBraces(boolean value) {
    getCommonSettings().SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = value;
  }

  public boolean isSpaceAfterTypeCast() {
    return getCommonSettings().SPACE_AFTER_TYPE_CAST;
  }

  public void setSpaceAfterTypeCast(boolean value) {getCommonSettings().SPACE_AFTER_TYPE_CAST = value;}

  public boolean isSpaceBeforeMethodCallParentheses() {
    return getCommonSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES;
  }

  public void setSpaceBeforeMethodCallParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = value;}

  public boolean isSpaceBeforeMethodParentheses() {
    return getCommonSettings().SPACE_BEFORE_METHOD_PARENTHESES;
  }

  public void setSpaceBeforeMethodParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_METHOD_PARENTHESES = value;}

  public boolean isSpaceBeforeIfParentheses() {
    return getCommonSettings().SPACE_BEFORE_IF_PARENTHESES;
  }

  public void setSpaceBeforeIfParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_IF_PARENTHESES = value;}

  public boolean isSpaceBeforeWhileParentheses() {
    return getCommonSettings().SPACE_BEFORE_WHILE_PARENTHESES;
  }

  public void setSpaceBeforeWhileParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_WHILE_PARENTHESES = value;}

  public boolean isSpaceBeforeForParentheses() {
    return getCommonSettings().SPACE_BEFORE_FOR_PARENTHESES;
  }

  public void setSpaceBeforeForParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_FOR_PARENTHESES = value;}

  public boolean isSpaceBeforeTryParentheses() {
    return getCommonSettings().SPACE_BEFORE_TRY_PARENTHESES;
  }

  public void setSpaceBeforeTryParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_TRY_PARENTHESES = value;}

  public boolean isSpaceBeforeCatchParentheses() {
    return getCommonSettings().SPACE_BEFORE_CATCH_PARENTHESES;
  }

  public void setSpaceBeforeCatchParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_CATCH_PARENTHESES = value;}

  public boolean isSpaceBeforeSwitchParentheses() {
    return getCommonSettings().SPACE_BEFORE_SWITCH_PARENTHESES;
  }

  public void setSpaceBeforeSwitchParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_SWITCH_PARENTHESES = value;}

  public boolean isSpaceBeforeSynchronizedParentheses() {
    return getCommonSettings().SPACE_BEFORE_SYNCHRONIZED_PARENTHESES;
  }

  public void setSpaceBeforeSynchronizedParentheses(boolean value) {getCommonSettings().SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = value;}

  public boolean isSpaceBeforeClassLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_CLASS_LBRACE;
  }

  public void setSpaceBeforeClassLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_CLASS_LBRACE = value;}

  public boolean isSpaceBeforeMethodLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_METHOD_LBRACE;
  }

  public void setSpaceBeforeMethodLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_METHOD_LBRACE = value;}

  public boolean isSpaceBeforeIfLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_IF_LBRACE;
  }

  public void setSpaceBeforeIfLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_IF_LBRACE = value;}

  public boolean isSpaceBeforeElseLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_ELSE_LBRACE;
  }

  public void setSpaceBeforeElseLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_ELSE_LBRACE = value;}

  public boolean isSpaceBeforeWhileLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_WHILE_LBRACE;
  }

  public void setSpaceBeforeWhileLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_WHILE_LBRACE = value;}

  public boolean isSpaceBeforeForLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_FOR_LBRACE;
  }

  public void setSpaceBeforeForLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_FOR_LBRACE = value;}

  public boolean isSpaceBeforeDoLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_DO_LBRACE;
  }

  public void setSpaceBeforeDoLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_DO_LBRACE = value;}

  public boolean isSpaceBeforeSwitchLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_SWITCH_LBRACE;
  }

  public void setSpaceBeforeSwitchLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_SWITCH_LBRACE = value;}

  public boolean isSpaceBeforeTryLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_TRY_LBRACE;
  }

  public void setSpaceBeforeTryLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_TRY_LBRACE = value;}

  public boolean isSpaceBeforeCatchLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_CATCH_LBRACE;
  }

  public void setSpaceBeforeCatchLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_CATCH_LBRACE = value;}

  public boolean isSpaceBeforeFinallyLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_FINALLY_LBRACE;
  }

  public void setSpaceBeforeFinallyLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_FINALLY_LBRACE = value;}

  public boolean isSpaceBeforeSynchronizedLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_SYNCHRONIZED_LBRACE;
  }

  public void setSpaceBeforeSynchronizedLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_SYNCHRONIZED_LBRACE = value;}

  public boolean isSpaceBeforeArrayInitializerLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE;
  }

  public void setSpaceBeforeArrayInitializerLeftBrace(boolean value) {getCommonSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = value;}

  public boolean isSpaceBeforeAnnotationArrayInitializerLeftBrace() {
    return getCommonSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE;
  }

  public void setSpaceBeforeAnnotationArrayInitializerLeftBrace(boolean value) {
    getCommonSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = value;
  }

  public boolean isSpaceBeforeElseKeyword() {
    return getCommonSettings().SPACE_BEFORE_ELSE_KEYWORD;
  }

  public void setSpaceBeforeElseKeyword(boolean value) {getCommonSettings().SPACE_BEFORE_ELSE_KEYWORD = value;}

  public boolean isSpaceBeforeWhileKeyword() {
    return getCommonSettings().SPACE_BEFORE_WHILE_KEYWORD;
  }

  public void setSpaceBeforeWhileKeyword(boolean value) {getCommonSettings().SPACE_BEFORE_WHILE_KEYWORD = value;}

  public boolean isSpaceBeforeCatchKeyword() {
    return getCommonSettings().SPACE_BEFORE_CATCH_KEYWORD;
  }

  public void setSpaceBeforeCatchKeyword(boolean value) {getCommonSettings().SPACE_BEFORE_CATCH_KEYWORD = value;}

  public boolean isSpaceBeforeFinallyKeyword() {
    return getCommonSettings().SPACE_BEFORE_FINALLY_KEYWORD;
  }

  public void setSpaceBeforeFinallyKeyword(boolean value) {getCommonSettings().SPACE_BEFORE_FINALLY_KEYWORD = value;}

  public boolean isSpaceBeforeQuest() {
    return getCommonSettings().SPACE_BEFORE_QUEST;
  }

  public void setSpaceBeforeQuest(boolean value) {getCommonSettings().SPACE_BEFORE_QUEST = value;}

  public boolean isSpaceAfterQuest() {
    return getCommonSettings().SPACE_AFTER_QUEST;
  }

  public void setSpaceAfterQuest(boolean value) {getCommonSettings().SPACE_AFTER_QUEST = value;}

  public boolean isSpaceBeforeColon() {
    return getCommonSettings().SPACE_BEFORE_COLON;
  }

  public void setSpaceBeforeColon(boolean value) {getCommonSettings().SPACE_BEFORE_COLON = value;}

  public boolean isSpaceAfterColon() {
    return getCommonSettings().SPACE_AFTER_COLON;
  }

  public void setSpaceAfterColon(boolean value) {getCommonSettings().SPACE_AFTER_COLON = value;}

  public boolean isSpaceBeforeTypeParameterList() {
    return getCommonSettings().SPACE_BEFORE_TYPE_PARAMETER_LIST;
  }

  public void setSpaceBeforeTypeParameterList(boolean value) {getCommonSettings().SPACE_BEFORE_TYPE_PARAMETER_LIST = value;}

  public WrapType getCallParametersWrap() {
    return intToWrapType(getCommonSettings().CALL_PARAMETERS_WRAP);
  }

  public void setCallParametersWrap(WrapType value) {getCommonSettings().CALL_PARAMETERS_WRAP = wrapTypeToInt(value);}

  public boolean isPreferParametersWrap() {
    return getCommonSettings().PREFER_PARAMETERS_WRAP;
  }

  public void setPreferParametersWrap(boolean value) {getCommonSettings().PREFER_PARAMETERS_WRAP = value;}

  public boolean isCallParametersLeftParenOnNextLine() {
    return getCommonSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
  }

  public void setCallParametersLeftParenOnNextLine(boolean value) {getCommonSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = value;}

  public boolean isCallParametersRightParenOnNextLine() {
    return getCommonSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
  }

  public void setCallParametersRightParenOnNextLine(boolean value) {getCommonSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = value;}

  public WrapType getMethodParametersWrap() {
    return intToWrapType(getCommonSettings().METHOD_PARAMETERS_WRAP);
  }

  public void setMethodParametersWrap(WrapType value) {getCommonSettings().METHOD_PARAMETERS_WRAP = wrapTypeToInt(value);}

  public boolean isMethodParametersLeftParenOnNextLine() {
    return getCommonSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
  }

  public void setMethodParametersLeftParenOnNextLine(boolean value) {getCommonSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = value;}

  public boolean isMethodParametersRightParenOnNextLine() {
    return getCommonSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE;
  }

  public void setMethodParametersRightParenOnNextLine(boolean value) {getCommonSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = value;}

  public WrapType getResourceListWrap() {
    return intToWrapType(getCommonSettings().RESOURCE_LIST_WRAP);
  }

  public void setResourceListWrap(WrapType value) {getCommonSettings().RESOURCE_LIST_WRAP = wrapTypeToInt(value);}

  public boolean isResourceListLeftParenOnNextLine() {
    return getCommonSettings().RESOURCE_LIST_LPAREN_ON_NEXT_LINE;
  }

  public void setResourceListLeftParenOnNextLine(boolean value) {getCommonSettings().RESOURCE_LIST_LPAREN_ON_NEXT_LINE = value;}

  public boolean isResourceListRightParenOnNextLine() {
    return getCommonSettings().RESOURCE_LIST_RPAREN_ON_NEXT_LINE;
  }

  public void setResourceListRightParenOnNextLine(boolean value) {getCommonSettings().RESOURCE_LIST_RPAREN_ON_NEXT_LINE = value;}

  public WrapType getExtendsListWrap() {
    return intToWrapType(getCommonSettings().EXTENDS_LIST_WRAP);
  }

  public void setExtendsListWrap(WrapType value) {getCommonSettings().EXTENDS_LIST_WRAP = wrapTypeToInt(value);}

  public WrapType getThrowsListWrap() {
    return intToWrapType(getCommonSettings().THROWS_LIST_WRAP);
  }

  public void setThrowsListWrap(WrapType value) {getCommonSettings().THROWS_LIST_WRAP = wrapTypeToInt(value);}

  public WrapType getExtendsKeywordWrap() {
    return intToWrapType(getCommonSettings().EXTENDS_KEYWORD_WRAP);
  }

  public void setExtendsKeywordWrap(WrapType value) {getCommonSettings().EXTENDS_KEYWORD_WRAP = wrapTypeToInt(value);}

  public WrapType getThrowsKeywordWrap() {
    return intToWrapType(getCommonSettings().THROWS_KEYWORD_WRAP);
  }

  public void setThrowsKeywordWrap(WrapType value) {getCommonSettings().THROWS_KEYWORD_WRAP = wrapTypeToInt(value);}

  public WrapType getMethodCallChainWrap() {
    return intToWrapType(getCommonSettings().METHOD_CALL_CHAIN_WRAP);
  }

  public void setMethodCallChainWrap(WrapType value) {getCommonSettings().METHOD_CALL_CHAIN_WRAP = wrapTypeToInt(value);}

  public boolean isWrapFirstMethodInCallChain() {
    return getCommonSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN;
  }

  public void setWrapFirstMethodInCallChain(boolean value) {getCommonSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = value;}

  public boolean isParenthesesExpressionLeftParenWrap() {
    return getCommonSettings().PARENTHESES_EXPRESSION_LPAREN_WRAP;
  }

  public void setParenthesesExpressionLeftParenWrap(boolean value) {getCommonSettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = value;}

  public boolean isParenthesesExpressionRightParenWrap() {
    return getCommonSettings().PARENTHESES_EXPRESSION_RPAREN_WRAP;
  }

  public void setParenthesesExpressionRightParenWrap(boolean value) {getCommonSettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = value;}

  public WrapType getBinaryOperationWrap() {
    return intToWrapType(getCommonSettings().BINARY_OPERATION_WRAP);
  }

  public void setBinaryOperationWrap(WrapType value) {getCommonSettings().BINARY_OPERATION_WRAP = wrapTypeToInt(value);}

  public boolean isBinaryOperationSignOnNextLine() {
    return getCommonSettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE;
  }

  public void setBinaryOperationSignOnNextLine(boolean value) {getCommonSettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE = value;}

  public WrapType getTernaryOperationWrap() {
    return intToWrapType(getCommonSettings().TERNARY_OPERATION_WRAP);
  }

  public void setTernaryOperationWrap(WrapType value) {getCommonSettings().TERNARY_OPERATION_WRAP = wrapTypeToInt(value);}

  public boolean isTernaryOperationSignsOnNextLine() {
    return getCommonSettings().TERNARY_OPERATION_SIGNS_ON_NEXT_LINE;
  }

  public void setTernaryOperationSignsOnNextLine(boolean value) {getCommonSettings().TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = value;}

  public boolean isModifierListWrap() {
    return getCommonSettings().MODIFIER_LIST_WRAP;
  }

  public void setModifierListWrap(boolean value) {getCommonSettings().MODIFIER_LIST_WRAP = value;}

  public boolean isKeepSimpleBlocksInOneLine() {
    return getCommonSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
  }

  public void setKeepSimpleBlocksInOneLine(boolean value) {getCommonSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = value;}

  public boolean isKeepSimpleMethodsInOneLine() {
    return getCommonSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE;
  }

  public void setKeepSimpleMethodsInOneLine(boolean value) {getCommonSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = value;}

  public boolean isKeepSimpleLambdasInOneLine() {
    return getCommonSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE;
  }

  public void setKeepSimpleLambdasInOneLine(boolean value) {getCommonSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = value;}

  public boolean isKeepSimpleClassesInOneLine() {
    return getCommonSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE;
  }

  public void setKeepSimpleClassesInOneLine(boolean value) {getCommonSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = value;}

  public boolean isKeepMultipleExpressionsInOneLine() {
    return getCommonSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE;
  }

  public void setKeepMultipleExpressionsInOneLine(boolean value) {getCommonSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = value;}

  public WrapType getForStatementWrap() {
    return intToWrapType(getCommonSettings().FOR_STATEMENT_WRAP);
  }

  public void setForStatementWrap(WrapType value) {getCommonSettings().FOR_STATEMENT_WRAP = wrapTypeToInt(value);}

  public boolean isForStatementLeftParenOnNextLine() {
    return getCommonSettings().FOR_STATEMENT_LPAREN_ON_NEXT_LINE;
  }

  public void setForStatementLeftParenOnNextLine(boolean value) {getCommonSettings().FOR_STATEMENT_LPAREN_ON_NEXT_LINE = value;}

  public boolean isForStatementRightParenOnNextLine() {
    return getCommonSettings().FOR_STATEMENT_RPAREN_ON_NEXT_LINE;
  }

  public void setForStatementRightParenOnNextLine(boolean value) {getCommonSettings().FOR_STATEMENT_RPAREN_ON_NEXT_LINE = value;}

  public WrapType getArrayInitializerWrap() {
    return intToWrapType(getCommonSettings().ARRAY_INITIALIZER_WRAP);
  }

  public void setArrayInitializerWrap(WrapType value) {getCommonSettings().ARRAY_INITIALIZER_WRAP = wrapTypeToInt(value);}

  public boolean isArrayInitializerLeftBraceOnNextLine() {
    return getCommonSettings().ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE;
  }

  public void setArrayInitializerLeftBraceOnNextLine(boolean value) {getCommonSettings().ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = value;}

  public boolean isArrayInitializerRightBraceOnNextLine() {
    return getCommonSettings().ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE;
  }

  public void setArrayInitializerRightBraceOnNextLine(boolean value) {getCommonSettings().ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = value;}

  public WrapType getAssignmentWrap() {
    return intToWrapType(getCommonSettings().ASSIGNMENT_WRAP);
  }

  public void setAssignmentWrap(WrapType value) {getCommonSettings().ASSIGNMENT_WRAP = wrapTypeToInt(value);}

  public boolean isPlaceAssignmentSignOnNextLine() {
    return getCommonSettings().PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE;
  }

  public void setPlaceAssignmentSignOnNextLine(boolean value) {getCommonSettings().PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = value;}

  public WrapType getLabeledStatementWrap() {
    return intToWrapType(getCommonSettings().LABELED_STATEMENT_WRAP);
  }

  public void setLabeledStatementWrap(WrapType value) {getCommonSettings().LABELED_STATEMENT_WRAP = wrapTypeToInt(value);}

  public boolean isWrapComments() {
    return getCommonSettings().WRAP_COMMENTS;
  }

  public void setWrapComments(boolean value) {getCommonSettings().WRAP_COMMENTS = value;}

  public WrapType getAssertStatementWrap() {
    return intToWrapType(getCommonSettings().ASSERT_STATEMENT_WRAP);
  }

  public void setAssertStatementWrap(WrapType value) {getCommonSettings().ASSERT_STATEMENT_WRAP = wrapTypeToInt(value);}

  public boolean isAssertStatementColonOnNextLine() {
    return getCommonSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE;
  }

  public void setAssertStatementColonOnNextLine(boolean value) {getCommonSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = value;}

  public int getIfBraceForce() {
    return getCommonSettings().IF_BRACE_FORCE;
  }

  public void setIfBraceForce(int value) {getCommonSettings().IF_BRACE_FORCE = value;}

  public int getDoWhileBraceForce() {
    return getCommonSettings().DOWHILE_BRACE_FORCE;
  }

  public void setDoWhileBraceForce(int value) {getCommonSettings().DOWHILE_BRACE_FORCE = value;}

  public int getWhileBraceForce() {
    return getCommonSettings().WHILE_BRACE_FORCE;
  }

  public void setWhileBraceForce(int value) {getCommonSettings().WHILE_BRACE_FORCE = value;}

  public int getForBraceForce() {
    return getCommonSettings().FOR_BRACE_FORCE;
  }

  public void setForBraceForce(int value) {getCommonSettings().FOR_BRACE_FORCE = value;}

  public boolean isWrapLongLines() {
    return getCommonSettings().WRAP_LONG_LINES;
  }

  public void setWrapLongLines(boolean value) {getCommonSettings().WRAP_LONG_LINES = value;}

  public WrapType getMethodAnnotationWrap() {
    return intToWrapType(getCommonSettings().METHOD_ANNOTATION_WRAP);
  }

  public void setMethodAnnotationWrap(WrapType value) {getCommonSettings().METHOD_ANNOTATION_WRAP = wrapTypeToInt(value);}

  public WrapType getClassAnnotationWrap() {
    return intToWrapType(getCommonSettings().CLASS_ANNOTATION_WRAP);
  }

  public void setClassAnnotationWrap(WrapType value) {getCommonSettings().CLASS_ANNOTATION_WRAP = wrapTypeToInt(value);}

  public WrapType getFieldAnnotationWrap() {
    return intToWrapType(getCommonSettings().FIELD_ANNOTATION_WRAP);
  }

  public void setFieldAnnotationWrap(WrapType value) {getCommonSettings().FIELD_ANNOTATION_WRAP = wrapTypeToInt(value);}

  public WrapType getParameterAnnotationWrap() {
    return intToWrapType(getCommonSettings().PARAMETER_ANNOTATION_WRAP);
  }

  public void setParameterAnnotationWrap(WrapType value) {getCommonSettings().PARAMETER_ANNOTATION_WRAP = wrapTypeToInt(value);}

  public WrapType getVariableAnnotationWrap() {
    return intToWrapType(getCommonSettings().VARIABLE_ANNOTATION_WRAP);
  }

  public void setVariableAnnotationWrap(WrapType value) {getCommonSettings().VARIABLE_ANNOTATION_WRAP = wrapTypeToInt(value);}

  public boolean isSpaceBeforeAnotationParameterList() {
    return getCommonSettings().SPACE_BEFORE_ANOTATION_PARAMETER_LIST;
  }

  public void setSpaceBeforeAnotationParameterList(boolean value) {getCommonSettings().SPACE_BEFORE_ANOTATION_PARAMETER_LIST = value;}

  public boolean isSpaceWithinAnnotationParentheses() {
    return getCommonSettings().SPACE_WITHIN_ANNOTATION_PARENTHESES;
  }

  public void setSpaceWithinAnnotationParentheses(boolean value) {getCommonSettings().SPACE_WITHIN_ANNOTATION_PARENTHESES = value;}

  public WrapType getEnumConstantsWrap() {
    return intToWrapType(getCommonSettings().ENUM_CONSTANTS_WRAP);
  }

  public void setEnumConstantsWrap(WrapType value) {getCommonSettings().ENUM_CONSTANTS_WRAP = wrapTypeToInt(value);}

  public String getFieldNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX;
  }

  public void setFieldNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX = value;}

  public String getStaticFieldNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).STATIC_FIELD_NAME_PREFIX;
  }

  public void setStaticFieldNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).STATIC_FIELD_NAME_PREFIX = value;}

  public String getParameterNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).PARAMETER_NAME_PREFIX;
  }

  public void setParameterNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).PARAMETER_NAME_PREFIX = value;}

  public String getLocalVariableNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).LOCAL_VARIABLE_NAME_PREFIX;
  }

  public void setLocalVariableNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).LOCAL_VARIABLE_NAME_PREFIX = value;}

  public String getTestNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).TEST_NAME_PREFIX;
  }

  public void setTestNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).TEST_NAME_PREFIX = value;}

  public String getSubclassNamePrefix() {
    return getCustomSettings(JavaCodeStyleSettings.class).SUBCLASS_NAME_PREFIX;
  }

  public void setSubclassNamePrefix(String value) {getCustomSettings(JavaCodeStyleSettings.class).SUBCLASS_NAME_PREFIX = value;}

  public String getFieldNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_SUFFIX;
  }

  public void setFieldNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_SUFFIX = value;}

  public String getStaticFieldNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).STATIC_FIELD_NAME_SUFFIX;
  }

  public void setStaticFieldNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).STATIC_FIELD_NAME_SUFFIX = value;}

  public String getParameterNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).PARAMETER_NAME_SUFFIX;
  }

  public void setParameterNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).PARAMETER_NAME_SUFFIX = value;}

  public String getLocalVariableNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).LOCAL_VARIABLE_NAME_SUFFIX;
  }

  public void setLocalVariableNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).LOCAL_VARIABLE_NAME_SUFFIX = value;}

  public String getTestNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).TEST_NAME_SUFFIX;
  }

  public void setTestNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).TEST_NAME_SUFFIX = value;}

  public String getSubclassNameSuffix() {
    return getCustomSettings(JavaCodeStyleSettings.class).SUBCLASS_NAME_SUFFIX;
  }

  public void setSubclassNameSuffix(String value) {getCustomSettings(JavaCodeStyleSettings.class).SUBCLASS_NAME_SUFFIX = value;}

  public boolean isPreferLongerNames() {
    return getCustomSettings(JavaCodeStyleSettings.class).PREFER_LONGER_NAMES;
  }

  public void setPreferLongerNames(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).PREFER_LONGER_NAMES = value;}

  public boolean isGenerateFinalLocals() {
    return getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS;
  }

  public void setGenerateFinalLocals(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS = value;}

  public boolean isGenerateFinalParameters() {
    return getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
  }

  public void setGenerateFinalParameters(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS = value;}

  public String getVisibility() {
    return getCustomSettings(JavaCodeStyleSettings.class).VISIBILITY;
  }

  public void setVisibility(String value) {getCustomSettings(JavaCodeStyleSettings.class).VISIBILITY = value;}

  // TODO: Implement FIELD_TYPE_TO_NAME getter manually (unsupported type).

  // TODO: Implement FIELD_TYPE_TO_NAME setter manually (unsupported type).

  // TODO: Implement STATIC_FIELD_TYPE_TO_NAME getter manually (unsupported type).

  // TODO: Implement STATIC_FIELD_TYPE_TO_NAME setter manually (unsupported type).

  // TODO: Implement PARAMETER_TYPE_TO_NAME getter manually (unsupported type).

  // TODO: Implement PARAMETER_TYPE_TO_NAME setter manually (unsupported type).

  // TODO: Implement LOCAL_VARIABLE_TYPE_TO_NAME getter manually (unsupported type).

  // TODO: Implement LOCAL_VARIABLE_TYPE_TO_NAME setter manually (unsupported type).
  public boolean isUseExternalAnnotations() {
    return getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS;
  }

  public void setUseExternalAnnotations(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = value;}

  public boolean isInsertOverrideAnnotation() {
    return getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION;
  }

  public void setInsertOverrideAnnotation(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION = value;
  }

  public boolean isRepeatSynchronized() {
    return getCustomSettings(JavaCodeStyleSettings.class).REPEAT_SYNCHRONIZED;
  }

  public void setRepeatSynchronized(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).REPEAT_SYNCHRONIZED = value;}

  public boolean isReplaceInstanceOf() {
    return getCustomSettings(JavaCodeStyleSettings.class).REPLACE_INSTANCEOF;
  }

  public void setReplaceInstanceOf(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).REPLACE_INSTANCEOF = value;}

  public boolean isReplaceCast() {
    return getCustomSettings(JavaCodeStyleSettings.class).REPLACE_CAST;
  }

  public void setReplaceCast(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).REPLACE_CAST = value;}

  public boolean isReplaceNullCheck() {
    return getCustomSettings(JavaCodeStyleSettings.class).REPLACE_NULL_CHECK;
  }

  public void setReplaceNullCheck(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).REPLACE_NULL_CHECK = value;}

  public boolean isSpacesWithinAngleBrackets() {
    return getCustomSettings(JavaCodeStyleSettings.class).SPACES_WITHIN_ANGLE_BRACKETS;
  }

  public void setSpacesWithinAngleBrackets(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).SPACES_WITHIN_ANGLE_BRACKETS = value;
  }

  public boolean isSpaceAfterClosingAngleBracketInTypeArgument() {
    return getCustomSettings(JavaCodeStyleSettings.class).SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT;
  }

  public void setSpaceAfterClosingAngleBracketInTypeArgument(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = value;
  }

  public boolean isSpaceBeforeOpeningAngleBracketInTypeParameter() {
    return getCustomSettings(JavaCodeStyleSettings.class).SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER;
  }

  public void setSpaceBeforeOpeningAngleBracketInTypeParameter(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = value;
  }

  public boolean isSpaceAroundTypeBoundsInTypeParameters() {
    return getCustomSettings(JavaCodeStyleSettings.class).SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS;
  }

  public void setSpaceAroundTypeBoundsInTypeParameters(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = value;
  }

  public boolean isDoNotWrapAfterSingleAnnotation() {
    return getCustomSettings(JavaCodeStyleSettings.class).DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION;
  }

  public void setDoNotWrapAfterSingleAnnotation(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = value;
  }

  public WrapType getAnnotationParameterWrap() {
    return intToWrapType(getCustomSettings(JavaCodeStyleSettings.class).ANNOTATION_PARAMETER_WRAP);
  }

  public void setAnnotationParameterWrap(WrapType value) {
    getCustomSettings(JavaCodeStyleSettings.class).ANNOTATION_PARAMETER_WRAP = wrapTypeToInt(value);
  }

  public boolean isAlignMultilineAnnotationParameters() {
    return getCustomSettings(JavaCodeStyleSettings.class).ALIGN_MULTILINE_ANNOTATION_PARAMETERS;
  }

  public void setAlignMultilineAnnotationParameters(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).ALIGN_MULTILINE_ANNOTATION_PARAMETERS = value;
  }

  public int getBlankLinesAroundInitializer() {
    return getCustomSettings(JavaCodeStyleSettings.class).BLANK_LINES_AROUND_INITIALIZER;
  }

  public void setBlankLinesAroundInitializer(int value) {
    getCustomSettings(JavaCodeStyleSettings.class).BLANK_LINES_AROUND_INITIALIZER = value;
  }

  public int getClassNamesInJavadoc() {
    return getCustomSettings(JavaCodeStyleSettings.class).CLASS_NAMES_IN_JAVADOC;
  }

  public void setClassNamesInJavadoc(int value) {getCustomSettings(JavaCodeStyleSettings.class).CLASS_NAMES_IN_JAVADOC = value;}

  public boolean isLayoutStaticImportsSeparately() {
    return getCustomSettings(JavaCodeStyleSettings.class).LAYOUT_STATIC_IMPORTS_SEPARATELY;
  }

  public void setLayoutStaticImportsSeparately(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).LAYOUT_STATIC_IMPORTS_SEPARATELY = value;
  }

  public boolean isUseFqClassNames() {
    return getCustomSettings(JavaCodeStyleSettings.class).USE_FQ_CLASS_NAMES;
  }

  public void setUseFqClassNames(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).USE_FQ_CLASS_NAMES = value;}

  public boolean isUseSingleClassImports() {
    return getCustomSettings(JavaCodeStyleSettings.class).USE_SINGLE_CLASS_IMPORTS;
  }

  public void setUseSingleClassImports(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).USE_SINGLE_CLASS_IMPORTS = value;}

  public boolean isInsertInnerClassImports() {
    return getCustomSettings(JavaCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS;
  }

  public void setInsertInnerClassImports(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS = value;}

  public int getClassCountToUseImportOnDemand() {
    return getCustomSettings(JavaCodeStyleSettings.class).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  public void setClassCountToUseImportOnDemand(int value) {
    getCustomSettings(JavaCodeStyleSettings.class).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  public int getNamesCountToUseImportOnDemand() {
    return getCustomSettings(JavaCodeStyleSettings.class).NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  public void setNamesCountToUseImportOnDemand(int value) {
    getCustomSettings(JavaCodeStyleSettings.class).NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  // TODO: Implement PACKAGES_TO_USE_IMPORT_ON_DEMAND getter manually (unsupported type).

  // TODO: Implement PACKAGES_TO_USE_IMPORT_ON_DEMAND setter manually (unsupported type).

  // TODO: Implement IMPORT_LAYOUT_TABLE getter manually (unsupported type).

  // TODO: Implement IMPORT_LAYOUT_TABLE setter manually (unsupported type).
  public boolean isEnableJavadocFormatting() {
    return getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING;
  }

  public void setEnableJavadocFormatting(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING = value;}

  public boolean isJavaDocAlignParamComments() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_ALIGN_PARAM_COMMENTS;
  }

  public void setJavaDocAlignParamComments(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_ALIGN_PARAM_COMMENTS = value;}

  public boolean isJavaDocAlignExceptionComments() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_ALIGN_EXCEPTION_COMMENTS;
  }

  public void setJavaDocAlignExceptionComments(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_ALIGN_EXCEPTION_COMMENTS = value;
  }

  public boolean isJavaDocAddBlankAfterParmComments() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_PARM_COMMENTS;
  }

  public void setJavaDocAddBlankAfterParmComments(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_PARM_COMMENTS = value;
  }

  public boolean isJavaDocAddBlankAfterReturn() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_RETURN;
  }

  public void setJavaDocAddBlankAfterReturn(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_RETURN = value;
  }

  public boolean isJavaDocAddBlankAfterDescription() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_DESCRIPTION;
  }

  public void setJavaDocAddBlankAfterDescription(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_ADD_BLANK_AFTER_DESCRIPTION = value;
  }

  public boolean isJavaDocPAtEmptyLines() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_P_AT_EMPTY_LINES;
  }

  public void setJavaDocPAtEmptyLines(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_P_AT_EMPTY_LINES = value;}

  public boolean isJavaDocKeepInvalidTags() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_INVALID_TAGS;
  }

  public void setJavaDocKeepInvalidTags(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_INVALID_TAGS = value;}

  public boolean isJavaDocKeepEmptyLines() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_LINES;
  }

  public void setJavaDocKeepEmptyLines(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_LINES = value;}

  public boolean isJavaDocDoNotWrapOneLineComments() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_DO_NOT_WRAP_ONE_LINE_COMMENTS;
  }

  public void setJavaDocDoNotWrapOneLineComments(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = value;
  }

  public boolean isJavaDocUseThrowsNotException() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_USE_THROWS_NOT_EXCEPTION;
  }

  public void setJavaDocUseThrowsNotException(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_USE_THROWS_NOT_EXCEPTION = value;
  }

  public boolean isJavaDocKeepEmptyParameter() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_PARAMETER;
  }

  public void setJavaDocKeepEmptyParameter(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_PARAMETER = value;}

  public boolean isJavaDocKeepEmptyException() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_EXCEPTION;
  }

  public void setJavaDocKeepEmptyException(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_EXCEPTION = value;}

  public boolean isJavaDocKeepEmptyReturn() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_RETURN;
  }

  public void setJavaDocKeepEmptyReturn(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_RETURN = value;}

  public boolean isJavaDocLeadingAsterisksAreEnabled() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_LEADING_ASTERISKS_ARE_ENABLED;
  }

  public void setJavaDocLeadingAsterisksAreEnabled(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_LEADING_ASTERISKS_ARE_ENABLED = value;
  }

  public boolean isJavaDocPreserveLineFeeds() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_PRESERVE_LINE_FEEDS;
  }

  public void setJavaDocPreserveLineFeeds(boolean value) {getCustomSettings(JavaCodeStyleSettings.class).JD_PRESERVE_LINE_FEEDS = value;}

  public boolean isJavaDocParamDescriptionOnNewLine() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_PARAM_DESCRIPTION_ON_NEW_LINE;
  }

  public void setJavaDocParamDescriptionOnNewLine(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_PARAM_DESCRIPTION_ON_NEW_LINE = value;
  }

  public boolean isJavaDocIndentOnContinuation() {
    return getCustomSettings(JavaCodeStyleSettings.class).JD_INDENT_ON_CONTINUATION;
  }

  public void setJavaDocIndentOnContinuation(boolean value) {
    getCustomSettings(JavaCodeStyleSettings.class).JD_INDENT_ON_CONTINUATION = value;
  }

}
