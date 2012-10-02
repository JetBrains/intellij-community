/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.ChildRoleBase;

public class ChildRole {
  public static final int PACKAGE_STATEMENT = 1; // in FILE
  public static final int IMPORT_LIST = 2; // in FILE
  public static final int CLASS = 3; // in FILE, CLASS
  public static final int FIELD = 4; // in CLASS
  public static final int METHOD = 5; // in CLASS
  public static final int CLASS_INITIALIZER = 6; // in CLASS
  public static final int DOC_COMMENT = 7; // in CLASS, FIELD, METHOD
  public static final int MODIFIER_LIST = 8; // in CLASS, FIELD, METHOD, CLASS_INITIALIZER, PARAMETER, LOCAL_VARIABLE
  public static final int NAME = 9; // in CLASS, FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, NAME_VALUE_PAIR
  public static final int TYPE = 10; // in FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_CAST_EXPRESSION, INSTANCEOF_EXPRESSION, CLASS_OBJECT_ACCESS_EXPRESSION
  public static final int CLASS_OR_INTERFACE_KEYWORD = 11; // in CLASS
  public static final int EXTENDS_LIST = 12; // in CLASS
  public static final int IMPLEMENTS_LIST = 13; // in CLASS
  public static final int PARAMETER_LIST = 14; // in METHOD, ANNOTATION
  public static final int PARAMETER = 15; // in PARAMETER_LIST, CATCH_SECTION
  public static final int THROWS_LIST = 16; // in METHOD
  public static final int METHOD_BODY = 17; // in METHOD, CLASS_INITIALIZER
  public static final int LBRACE = 18; // in CLASS, CODE_BLOCK, ARRAY_INITIALIZER_EXPRESSION
  public static final int RBRACE = 19; // in CLASS, CODE_BLOCK, ARRAY_INITIALIZER_EXPRESSION
  public static final int INITIALIZER_EQ = 20; // in FIELD, LOCAL_VARIABLE
  public static final int INITIALIZER = 21; // in FIELD, LOCAL_VARIABLE
  public static final int CLOSING_SEMICOLON = 22; // in FIELD, METHOD, LOCAL_VARIABLE, DO_WHILE_STATEMENT,
                                                  // THROW_STATEMENT, RETURN_STATEMENT, BREAK_STATEMENT, CONTINUE_STATEMENT,
                                                  // EXPRESSION_LIST_STATEMENT, EXPRESSION_STATEMENT, PACKAGE_STATEMENT,
                                                  // IMPORT_STATEMENT, ASSERT_STATEMENT
  public static final int COMMA = 23; // in PARAMETER_LIST, EXTENDS_LIST, IMPLEMENTS_LIST, THROWS_LIST, EXPRESSION_LIST, ARRAY_INITIALIZER_EXPRESSION, JSP_IMPORT_VALUE, DECLARATION_STATEMENT
  public static final int LPARENTH = 24; // in PARAMETER_LIST, IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, DO_WHILE_STATEMENT, SWITCH_STATEMENT, PARENTHESIZED_EXPRESSION, TYPE_CAST_EXPRESSION, SYNCHRONIZED_STATEMENT
  public static final int RPARENTH = 25; // in PARAMETER_LIST, IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, DO_WHILE_STATEMENT, SWITCH_STATEMENT, PARENTHESIZED_EXPRESSION, TYPE_CAST_EXPRESSION, SYNCHRONIZED_STATEMENT
  public static final int EXTENDS_KEYWORD = 26; // in EXTENDS_LIST
  public static final int IMPLEMENTS_KEYWORD = 27; // in IMPLEMENTS_LIST
  public static final int THROWS_KEYWORD = 28; // in THROWS_LIST
  public static final int REFERENCE_IN_LIST = 29; // in EXTENDS_LIST, IMPLEMENTS_LIST, THROWS_LIST
  public static final int IF_KEYWORD = 30; // in IF_STATEMENT
  public static final int ELSE_KEYWORD = 31; // in IF_STATEMENT
  public static final int CONDITION = 32; // in IF_STATEMENT, WHILE_STATEMENT, DO_WHILE_STATEMENT, FOR_STATEMENT, CONDITIONAL_EXPRESSION, ASSERT_STATEMENT
  public static final int THEN_BRANCH = 33; // in IF_STATEMENT
  public static final int ELSE_BRANCH = 34; // in IF_STATEMENT
  public static final int WHILE_KEYWORD = 35; // in WHILE_STATEMENT, DO_WHILE_STATEMENT
  public static final int DO_KEYWORD = 36; // in DO_WHILE_STATEMENT
  public static final int FOR_KEYWORD = 37; // in FOR_STATEMENT, FOREACH_STATEMENT
  public static final int LOOP_BODY = 38; // in WHILE_STATEMENT, DO_WHILE_STATEMENT, FOR_STATEMENT, FOREACH_STATEMENT
  public static final int FOR_INITIALIZATION = 39; // in FOR_STATEMENT
  public static final int FOR_UPDATE = 40; // in FOR_STATEMENT
  public static final int FOR_SEMICOLON = 41; // in FOR_STATEMENT
  public static final int SWITCH_KEYWORD = 42; // in SWITCH_STATEMENT
  public static final int SWITCH_EXPRESSION = 43; // in SWITCH_STATEMENT
  public static final int SWITCH_BODY = 44; // in SWITCH_STATEMENT
  public static final int TRY_KEYWORD = 45; // in TRY_STATEMENT
  public static final int CATCH_KEYWORD = 46; // in CATCH_SECTION
  public static final int FINALLY_KEYWORD = 47; // in TRY_STATEMENT
  public static final int TRY_BLOCK = 48; // in TRY_STATEMENT
  public static final int CATCH_BLOCK = 49; // in CATCH_SECTION
  public static final int CATCH_BLOCK_PARAMETER_LPARENTH = 50; // in CATCH_SECTION
  public static final int CATCH_BLOCK_PARAMETER_RPARENTH = 51; // in CATCH_SECTION
  public static final int FINALLY_BLOCK = 52; // in TRY_STATEMENT
  public static final int REFERENCE_NAME = 53; // in JAVA_CODE_REFERENCE, REFERENCE_EXPRESSION
  public static final int QUALIFIER = 54; // in JAVA_CODE_REFERENCE, REFERENCE_EXPRESSION, THIS_EXPRESSION, SUPER_EXPRESSION, NEW_EXPRESSION
  public static final int DOT = 55; // in JAVA_CODE_REFERENCE, REFERENCE_EXPRESSION, CLASS_OBJECT_ACCESS_EXPRESSION, THIS_EXPRESSION, SUPER_EXPRESSION, NEW_EXPRESSION
  public static final int THROW_KEYWORD = 57; // in THROW_STATEMENT
  public static final int EXCEPTION = 58; // in THROW_STATEMENT
  public static final int EXPRESSION_IN_LIST = 59; // in EXPRESSION_LIST. ARRAY_INITIALIZER_EXPRESSION
  public static final int BLOCK = 60; // in BLOCK_STATEMENT, SYNCHRONIZED_STATEMENT, CATCH_SECTION
  public static final int LOPERAND = 61; // in ASSIGNMENT_EXPRESSION, BINARY_EXPRESSION
  public static final int ROPERAND = 62; // in ASSIGNMENT_EXPRESSION, BINARY_EXPRESSION
  public static final int OPERATION_SIGN = 63; // in ASSIGNMENT_EXPRESSION, BINARY_EXPRESSION
  public static final int EXPRESSION = 64; // in EXPRESSION_STATEMENT, PARENTHESIZED_EXPRESSION
  public static final int RETURN_KEYWORD = 65; // in RETURN_STATEMENT
  public static final int RETURN_VALUE = 66; // in RETURN_STATEMENT
  public static final int OPERAND = 67; // in /*PREFIX_EXPRESSION, POSTFIX_EXPRESSION*/, TYPE_CAST_EXPRESSION, INSTANCEOF_EXPRESSION
  public static final int INSTANCEOF_KEYWORD = 68; // in INSTANCEOF_EXPRESSION
  public static final int NEW_KEYWORD = 69; // in NEW_EXPRESSION
  public static final int ANONYMOUS_CLASS = 70; // in NEW_EXPRESSION
  public static final int TYPE_REFERENCE = 71; // in NEW_EXPRESSION
  public static final int TYPE_KEYWORD = 72; // in NEW_EXPRESSION, TYPE
  public static final int ARGUMENT_LIST = 73; // in METHOD_CALL_EXPRESSION, NEW_EXPRESSION, ANONYMOUS_CLASS
  public static final int LBRACKET = 74; // in NEW_EXPRESSION, ARRAY_ACCESS_EXPRESSION, TYPE
  public static final int RBRACKET = 75; // in NEW_EXPRESSION, ARRAY_ACCESS_EXPRESSION, TYPE
  public static final int ARRAY_DIMENSION = 76; // in NEW_EXPRESSION
  public static final int ARRAY_INITIALIZER = 77; // in NEW_EXPRESSION
  public static final int BASE_CLASS_REFERENCE = 78; // in ANONYMOUS_CLASS
  public static final int SYNCHRONIZED_KEYWORD = 79; // in SYNCHRONIZED_STATEMENT
  public static final int LOCK = 80; // in SYNCHRONIZED_STATEMENT
  public static final int BREAK_KEYWORD = 81; // in BREAK_STATEMENT
  public static final int CONTINUE_KEYWORD = 82; // in CONTINUE_STATEMENT
  public static final int LABEL = 83; // in BREAK_STATEMENT, CONTINUE_STATEMENT
  public static final int CASE_KEYWORD = 84; // in SWITCH_LABEL_STATEMENT
  public static final int DEFAULT_KEYWORD = 85; // in SWITCH_LABEL_STATEMENT
  public static final int CASE_EXPRESSION = 86; // in SWITCH_LABEL_STATEMENT
  public static final int COLON = 87; // in SWITCH_LABEL_STATEMENT, LABELED_STATEMENT, CONDITIONAL_EXPRESSION, ASSERT_STATEMENT
  public static final int ARRAY = 88; // in ARRAY_ACCESS_EXPRESSION
  public static final int INDEX = 89; // in ARRAY_ACCESS_EXPRESSION
  public static final int CLASS_KEYWORD = 90; // in CLASS_OBJECT_ACCESS_EXPRESSION
  public static final int METHOD_EXPRESSION = 91; // in METHOD_CALL_EXPRESSION
  public static final int EXPRESSION_LIST = 92; // in EXPRESSION_LIST_STATEMENT
  public static final int LABEL_NAME = 93; // in LABELED_STATEMENT
  public static final int STATEMENT = 94; // in LABELED_STATEMENT
  public static final int THIS_KEYWORD = 95; // in THIS_EXPRESSION
  public static final int SUPER_KEYWORD = 96; // in SUPER_EXPRESSION
  public static final int IMPORT_KEYWORD = 98; // in IMPORT_STATEMENT
  public static final int IMPORT_REFERENCE = 99; // in IMPORT_STATEMENT
  public static final int IMPORT_ON_DEMAND_DOT = 100; // in IMPORT_STATEMENT
  public static final int IMPORT_ON_DEMAND_ASTERISK = 101; // in IMPORT_STATEMENT
  public static final int PACKAGE_KEYWORD = 102; // in PACKAGE_STATEMENT
  public static final int PACKAGE_REFERENCE = 103; // in PACKAGE_STATEMENT
  public static final int DOC_TAG = 104; // in DOC_COMMENT
  public static final int DOC_TAG_NAME = 105; // in DOC_TAG, DOC_INLINE_TAG
  public static final int DOC_CONTENT = 106; // in DOC_COMMENT, DOC_TAG, DOC_INLINE_TAG
  public static final int DOC_COMMENT_ASTERISKS = 107; // in DOC_COMMENT, DOC_TAG
  public static final int DOC_INLINE_TAG_START = 108; // in DOC_INLINE_TAG
  public static final int DOC_INLINE_TAG_END = 109; // in DOC_INLINE_TAG
  public static final int DOC_COMMENT_START = 110; // in DOC_COMMENT
  public static final int DOC_COMMENT_END = 111; // in DOC_COMMENT
  public static final int THEN_EXPRESSION = 112; // in CONDITIONAL_EXPRESSION
  public static final int ELSE_EXPRESSION = 113; // in CONDITIONAL_EXPRESSION
  public static final int QUEST = 114; // in CONDITIONAL_EXPRESSION
  public static final int ASSERT_KEYWORD = 116; // in ASSERT_STATEMENT
  public static final int ASSERT_DESCRIPTION = 117; // in ASSERT_DESCRIPTION
  public static final int CLASS_REFERENCE = 119; // in TYPE, ANNOTATION
  public static final int TYPE_IN_REFERENCE_PARAMETER_LIST = 120; // in REFERENCE_PARAMETER_LIST
  public static final int LT_IN_TYPE_LIST = 121;
  public static final int GT_IN_TYPE_LIST = 122;
  public static final int AMPERSAND_IN_BOUNDS_LIST = 123;

  public static final int FOR_ITERATED_VALUE = 124; // in FOREACH_STATEMENT
  public static final int FOR_ITERATION_PARAMETER = 125; // in FOREACH_STATEMENT

  public static final int ENUM_CONSTANT_LIST_DELIMITER = 126; // in CLASS

  public static final int DOC_TAG_VALUE = 242;

  public static final int TYPE_PARAMETER_IN_LIST = 244;
  public static final int TYPE_PARAMETER_LIST = 245;
  public static final int REFERENCE_PARAMETER_LIST = 246;
  public static final int AT = 247; // in CLASS
  public static final int ANNOTATION_DEFAULT_VALUE = 248;  //in ANNOTATION_METHOD
  public static final int ANNOTATION_VALUE = 249;  // in NAME_VALUE_PAIR, ANNOTATION_ARRAY_INITIALIZER
  public static final int ANNOTATION = 250;  // in MODIFIER_LIST
  public static final int CATCH_SECTION = 251; // in TRY_STATEMENT
  public static final int ARROW = 252; // in LAMBDA STATEMENT
  public static final int DOUBLE_COLON = 253; // in METHOD_REF

  private ChildRole() {
  }

  public static boolean isUnique(int role) {
    switch(role){
      default:
        return true;

      case ChildRoleBase.NONE:
      case CLASS:
      case FIELD:
      case METHOD:
      case PARAMETER:
      case CLASS_INITIALIZER:
      case COMMA:
      case REFERENCE_IN_LIST:
      case EXPRESSION_IN_LIST:
      case ARRAY_DIMENSION:
      case TYPE_PARAMETER_IN_LIST:
      case ANNOTATION_VALUE:
        return false;
    }
  }
}
