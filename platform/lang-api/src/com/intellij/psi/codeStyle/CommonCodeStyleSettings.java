/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Common code style settings can be used by several programming languages. Each language may have its own
 * instance of <code>CommonCodeStyleSettings</code>.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettings {


  public CommonCodeStyleSettings clone(CodeStyleSettings parentSettings) {
    CommonCodeStyleSettings commonSettings = new CommonCodeStyleSettings();
    copyPublicFields(this, commonSettings);
    return commonSettings;
  }

  protected static void copyPublicFields(Object from, Object to) {
    assert from != to;
    copyFields(to.getClass().getDeclaredFields(), from, to);
    Class superClass = to.getClass().getSuperclass();
    if (superClass != null) {
      copyFields(superClass.getDeclaredFields(), from, to);
    }
  }

  private static void copyFields(Field[] fields, Object from, Object to) {
    for (Field field : fields) {
      if (isPublic(field) && !isFinal(field)) {
        try {
          copyFieldValue(from, to, field);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static void copyFieldValue(final Object from, Object to, final Field field)
    throws IllegalAccessException {
    Class<?> fieldType = field.getType();
    if (fieldType.isPrimitive()) {
      field.set(to, field.get(from));
    }
    else if (fieldType.equals(String.class)) {
      field.set(to, field.get(from));
    }
    else {
      throw new RuntimeException("Field not copied " + field.getName());
    }
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }



//----------------- GENERAL --------------------

  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;

  public boolean KEEP_LINE_BREAKS = true;

  /**
   * Controls END_OF_LINE_COMMENT's and C_STYLE_COMMENT's
   */
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  public boolean INSERT_FIRST_SPACE_IN_LINE = true;

  /**
   * Keep "if (..) ...;" (also while, for)
   * Does not control "if (..) { .. }"
   */
  public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;

//----------------- BLANK LINES --------------------

  /**
   * Keep up to this amount of blank lines between declarations
   */
  public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;

  /**
   * Keep up to this amount of blank lines in code
   */
  public int KEEP_BLANK_LINES_IN_CODE = 2;

  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;

  public int BLANK_LINES_BEFORE_PACKAGE = 0;
  public int BLANK_LINES_AFTER_PACKAGE = 1;
  public int BLANK_LINES_BEFORE_IMPORTS = 1;
  public int BLANK_LINES_AFTER_IMPORTS = 1;

  public int BLANK_LINES_AROUND_CLASS = 1;
  public int BLANK_LINES_AROUND_FIELD = 0;
  public int BLANK_LINES_AROUND_METHOD = 1;
  public int BLANK_LINES_BEFORE_METHOD_BODY = 0;

  public int BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0;
  public int BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1;


  public int BLANK_LINES_AFTER_CLASS_HEADER = 0;
  public int BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
  //public int BLANK_LINES_BETWEEN_CASE_BLOCKS;


//----------------- BRACES & INDENTS --------------------

  /**
   * <PRE>
   * 1.
   * if (..) {
   * body;
   * }
   * 2.
   * if (..)
   * {
   * body;
   * }
   * 3.
   * if (..)
   * {
   * body;
   * }
   * 4.
   * if (..)
   * {
   * body;
   * }
   * 5.
   * if (long-condition-1 &&
   * long-condition-2)
   * {
   * body;
   * }
   * if (short-condition) {
   * body;
   * }
   * </PRE>
   */

  public static final int END_OF_LINE = 1;
  public static final int NEXT_LINE = 2;
  public static final int NEXT_LINE_SHIFTED = 3;
  public static final int NEXT_LINE_SHIFTED2 = 4;
  public static final int NEXT_LINE_IF_WRAPPED = 5;

  public int BRACE_STYLE = 1;
  public int CLASS_BRACE_STYLE = 1;
  public int METHOD_BRACE_STYLE = 1;

  /**
   * Defines if 'flying geese' style should be used for curly braces formatting, e.g. if we want to format code like
   * <p/>
   * <pre>
   *     class Test {
   *         {
   *             System.out.println();
   *         }
   *     }
   * </pre>
   * to
   * <pre>
   *     class Test { {
   *         System.out.println();
   *     } }
   * </pre>
   */
  public boolean USE_FLYING_GEESE_BRACES = false;

  /**
   * Defines number of white spaces between curly braces in case of {@link #USE_FLYING_GEESE_BRACES 'flying geese'} style usage.
   */
  public int FLYING_GEESE_BRACES_GAP = 1;

  public boolean DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false;

  /**
   * <PRE>
   * "}
   * else"
   * or
   * "} else"
   * </PRE>
   */
  public boolean ELSE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * while"
   * or
   * "} while"
   * </PRE>
   */
  public boolean WHILE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * catch"
   * or
   * "} catch"
   * </PRE>
   */
  public boolean CATCH_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * finally"
   * or
   * "} finally"
   * </PRE>
   */
  public boolean FINALLY_ON_NEW_LINE = false;

  public boolean INDENT_CASE_FROM_SWITCH = true;

  public boolean SPECIAL_ELSE_IF_TREATMENT = true;

  /**
   * Indicates if long sequence of chained method calls should be aligned.
   * <p/>
   * E.g. if statement like <code>'foo.bar().bar().bar();'</code> should be reformatted to the one below if,
   * say, last <code>'bar()'</code> call exceeds right margin. The code looks as follows after reformatting
   * if this property is <code>true</code>:
   * <p/>
   * <pre>
   *     foo.bar().bar()
   *        .bar();
   * </pre>
   */
  public boolean ALIGN_MULTILINE_CHAINED_METHODS = false;
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  public boolean ALIGN_MULTILINE_FOR = true;
  public boolean INDENT_WHEN_CASES = true;

  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;

  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  public boolean ALIGN_MULTILINE_METHOD_BRACKETS = false;
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;

//----------------- Group alignments ---------------

  /**
   * Specifies if subsequent fields/variables declarations and initialisations should be aligned in columns like below:
   * int start = 1;
   * int end   = 10;
   */
  public boolean ALIGN_GROUP_FIELDS_VARIABLES = false;

  public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;


//----------------- SPACES --------------------

  /**
   * Controls =, +=, -=, etc
   */
  public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;

  /**
   * Controls &&, ||
   */
  public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;

  /**
   * Controls ==, !=
   */
  public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;

  /**
   * Controls <, >, <=, >=
   */
  public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;

  /**
   * Controls &, |, ^
   */
  public boolean SPACE_AROUND_BITWISE_OPERATORS = true;

  /**
   * Controls +, -
   */
  public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;

  /**
   * Controls *, /, %
   */
  public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;

  /**
   * Controls <<. >>, >>>
   */
  public boolean SPACE_AROUND_SHIFT_OPERATORS = true;

  public boolean SPACE_AROUND_UNARY_OPERATOR = false;

  public boolean SPACE_AFTER_COMMA = true;
  public boolean SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
  public boolean SPACE_BEFORE_COMMA = false;
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement

  /**
   * "( expr )"
   * or
   * "(expr)"
   */
  public boolean SPACE_WITHIN_PARENTHESES = false;

  /**
   * "f( expr )"
   * or
   * "f(expr)"
   */
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f( int param )"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;

  /**
   * "if( expr )"
   * or
   * "if(expr)"
   */
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;

  /**
   * "while( expr )"
   * or
   * "while(expr)"
   */
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;

  /**
   * "for( int i = 0; i < 10; i++ )"
   * or
   * "for(int i = 0; i < 10; i++)"
   */
  public boolean SPACE_WITHIN_FOR_PARENTHESES = false;

  /**
   * "catch( Exception e )"
   * or
   * "catch(Exception e)"
   */
  public boolean SPACE_WITHIN_CATCH_PARENTHESES = false;

  /**
   * "switch( expr )"
   * or
   * "switch(expr)"
   */
  public boolean SPACE_WITHIN_SWITCH_PARENTHESES = false;

  /**
   * "synchronized( expr )"
   * or
   * "synchronized(expr)"
   */
  public boolean SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false;

  /**
   * "( Type )expr"
   * or
   * "(Type)expr"
   */
  public boolean SPACE_WITHIN_CAST_PARENTHESES = false;

  /**
   * "[ expr ]"
   * or
   * "[expr]"
   */
  public boolean SPACE_WITHIN_BRACKETS = false;

  /**
   * "int X[] { 1, 3, 5 }"
   * or
   * "int X[] {1, 3, 5}"
   */
  public boolean SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;

  public boolean SPACE_AFTER_TYPE_CAST = true;

  /**
   * "f (x)"
   * or
   * "f(x)"
   */
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f (int param)"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;

  /**
   * "if (...)"
   * or
   * "if(...)"
   */
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;

  /**
   * "while (...)"
   * or
   * "while(...)"
   */
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;

  /**
   * "for (...)"
   * or
   * "for(...)"
   */
  public boolean SPACE_BEFORE_FOR_PARENTHESES = true;

  /**
   * "catch (...)"
   * or
   * "catch(...)"
   */
  public boolean SPACE_BEFORE_CATCH_PARENTHESES = true;

  /**
   * "switch (...)"
   * or
   * "switch(...)"
   */
  public boolean SPACE_BEFORE_SWITCH_PARENTHESES = true;

  /**
   * "synchronized (...)"
   * or
   * "synchronized(...)"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = true;

  /**
   * "class A {"
   * or
   * "class A{"
   */
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;

  /**
   * "void f() {"
   * or
   * "void f(){"
   */
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;

  /**
   * "if (...) {"
   * or
   * "if (...){"
   */
  public boolean SPACE_BEFORE_IF_LBRACE = true;

  /**
   * "else {"
   * or
   * "else{"
   */
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;

  /**
   * "while (...) {"
   * or
   * "while (...){"
   */
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;

  /**
   * "for (...) {"
   * or
   * "for (...){"
   */
  public boolean SPACE_BEFORE_FOR_LBRACE = true;

  /**
   * "do {"
   * or
   * "do{"
   */
  public boolean SPACE_BEFORE_DO_LBRACE = true;

  /**
   * "switch (...) {"
   * or
   * "switch (...){"
   */
  public boolean SPACE_BEFORE_SWITCH_LBRACE = true;

  /**
   * "try {"
   * or
   * "try{"
   */
  public boolean SPACE_BEFORE_TRY_LBRACE = true;

  /**
   * "catch (...) {"
   * or
   * "catch (...){"
   */
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;

  /**
   * "finally {"
   * or
   * "finally{"
   */
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;

  /**
   * "synchronized (...) {"
   * or
   * "synchronized (...){"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;

  /**
   * "new int[] {"
   * or
   * "new int[]{"
   */
  public boolean SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;


  public boolean SPACE_BEFORE_ELSE_KEYWORD = true;
  public boolean SPACE_BEFORE_WHILE_KEYWORD = true;
  public boolean SPACE_BEFORE_CATCH_KEYWORD = true;
  public boolean SPACE_BEFORE_FINALLY_KEYWORD = true;

  public boolean SPACE_BEFORE_QUEST = true;
  public boolean SPACE_AFTER_QUEST = true;
  public boolean SPACE_BEFORE_COLON = true;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LIST = false;

  //----------------- WRAPPING ---------------------------
  
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MAGIN = false;

  public static final int DO_NOT_WRAP = 0x00;
  public static final int WRAP_AS_NEEDED = 0x01;
  public static final int WRAP_ALWAYS = 0x02;
  public static final int WRAP_ON_EVERY_ITEM = 0x04;

  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean PREFER_PARAMETERS_WRAP = false;
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;

  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;

  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;

  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;

  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;

  public boolean MODIFIER_LIST_WRAP = false;

  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;

  public int FOR_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
  public boolean FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;

  public int ARRAY_INITIALIZER_WRAP = DO_NOT_WRAP;
  public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;

  public int ASSIGNMENT_WRAP = DO_NOT_WRAP;
  public boolean PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;

  public int LABELED_STATEMENT_WRAP = WRAP_ALWAYS;

  public boolean WRAP_COMMENTS = false;

  public int ASSERT_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;

  // BRACE FORCING
  public static final int DO_NOT_FORCE = 0x00;
  public static final int FORCE_BRACES_IF_MULTILINE = 0x01;
  public static final int FORCE_BRACES_ALWAYS = 0x03;

  public int IF_BRACE_FORCE = DO_NOT_FORCE;
  public int DOWHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int WHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int FOR_BRACE_FORCE = DO_NOT_FORCE;

  //-------------- Annotation formatting settings-------------------------------------------

  public int METHOD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int CLASS_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int FIELD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int PARAMETER_ANNOTATION_WRAP = DO_NOT_WRAP;
  public int VARIABLE_ANNOTATION_WRAP = DO_NOT_WRAP;

  public boolean SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false;
  public boolean SPACE_WITHIN_ANNOTATION_PARENTHESES = false;

  //----------------------------------------------------------------------------------------


  //-------------------------Enums----------------------------------------------------------
  public int ENUM_CONSTANTS_WRAP = DO_NOT_WRAP;
  

}
