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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * Is intended to hold specific java formatting tests for 'wrapping' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 29, 2010 4:06:15 PM
 */
public class JavaFormatterWrapTest extends AbstractJavaFormatterTest {

  public void testWrappingAnnotationArrayParameters() throws Exception {
    getSettings().RIGHT_MARGIN = 80;
    getSettings().ARRAY_INITIALIZER_WRAP = CodeStyleSettings.WRAP_AS_NEEDED;
    doTextTest(
      "@AttributeOverrides( { @AttributeOverride(name = \"id\", column = @Column(name = \"recovery_id\"))," +
      "@AttributeOverride(name = \"transactionReference\", column = @Column(name = \"deal_reference\"))," +
      "@AttributeOverride(name = \"eventDate\", column = @Column(name = \"recovery_date\"))," +
      "@AttributeOverride(name = \"amount\", column = @Column(name = \"recovery_amount\"))," +
      "@AttributeOverride(name = \"currency\", column = @Column(name = \"local_currency\"))," +
      "@AttributeOverride(name = \"exchangeRate\", column = @Column(name = \"exchange_rate\"))," +
      "@AttributeOverride(name = \"exchangeRateDate\", column = @Column(name = \"recovery_date\", insertable = false, updatable = false))," +
      "@AttributeOverride(name = \"exchangeRateAlterationJustification\", column = @Column(name = \"exchange_rate_justification\"))," +
      "@AttributeOverride(name = \"systemExchangeRate\", column = @Column(name = \"system_exchange_rate\")) })\n" +
      "class Foo {\n" +
      "}",
      
      "@AttributeOverrides({\n" +
      "        @AttributeOverride(name = \"id\", column = @Column(name = \"recovery_id\")),\n" +
      "        @AttributeOverride(name = \"transactionReference\", column = @Column(name = \"deal_reference\")),\n" +
      "        @AttributeOverride(name = \"eventDate\", column = @Column(name = \"recovery_date\")),\n" +
      "        @AttributeOverride(name = \"amount\", column = @Column(name = \"recovery_amount\")),\n" +
      "        @AttributeOverride(name = \"currency\", column = @Column(name = \"local_currency\")),\n" +
      "        @AttributeOverride(name = \"exchangeRate\", column = @Column(name = \"exchange_rate\")),\n" +
      "        @AttributeOverride(name = \"exchangeRateDate\", column = @Column(name = \"recovery_date\", insertable = false, updatable = false)),\n" +
      "        @AttributeOverride(name = \"exchangeRateAlterationJustification\", column = @Column(name = \"exchange_rate_justification\")),\n" +
      "        @AttributeOverride(name = \"systemExchangeRate\", column = @Column(name = \"system_exchange_rate\"))})\n" +
      "class Foo {\n" +
      "}"
    );
  }

  public void testAnnotationParamValueExceedingRightMargin() throws Exception {
    // Inspired by IDEA-18051
    getSettings().RIGHT_MARGIN = 80;
    doTextTest(
      "package formatting;\n" +
      "\n" +
      "public class EnumInAnnotationFormatting {\n" +
      "\n" +
      "    public enum TheEnum {\n" +
      "\n" +
      "        FIRST,\n" +
      "        SECOND,\n" +
      "        THIRD,\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "    public @interface TheAnnotation {\n" +
      "\n" +
      "        TheEnum[] value();\n" +
      "\n" +
      "        String comment();\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "    @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment = \"some long comment that goes longer that right margin 012345678901234567890\")\n" +
      "    public class Test {\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "}",
      "package formatting;\n" +
      "\n" +
      "public class EnumInAnnotationFormatting {\n" +
      "\n" +
      "    public enum TheEnum {\n" +
      "\n" +
      "        FIRST,\n" +
      "        SECOND,\n" +
      "        THIRD,\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "    public @interface TheAnnotation {\n" +
      "\n" +
      "        TheEnum[] value();\n" +
      "\n" +
      "        String comment();\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "    @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment = \"some long comment that goes longer that right margin 012345678901234567890\")\n" +
      "    public class Test {\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "}");
  }

  public void testEnumConstantsWrapping() {
    // Inspired by IDEA-54667
    getSettings().ENUM_CONSTANTS_WRAP = CodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 80;

    // Don't expect the constants to be placed on new line.
    doTextTest(
      "enum Test {FIRST, SECOND}",
      "enum Test {FIRST, SECOND}"
    );

    // Expect not only enum constants to be wrapped but line break inside enum-level curly braces as well.
    doTextTest(
      "enum Test {FIRST, SECOND, THIIIIIIIIIIIIIIIIIRRDDDDDDDDDDDDDD, FOURTHHHHHHHHHHHHHHHH}",

      "enum Test {\n" +
      "    FIRST, SECOND, THIIIIIIIIIIIIIIIIIRRDDDDDDDDDDDDDD, FOURTHHHHHHHHHHHHHHHH\n" +
      "}"
    );
  }

  public void testMethodAnnotationFollowedBySingleLineComment() throws Exception {
    // Inspired by IDEA-22808
    getSettings().METHOD_ANNOTATION_WRAP = CodeStyleSettings.WRAP_ALWAYS;

    String text =
      "@Test//mycomment\n" +
      "public void foo() {\n" +
      "}";
    
    // Expecting the code to be left as-is
    doClassTest(text, text);
  }

  public void testWrapLongLine() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 50;
    getSettings().WRAP_LONG_LINES = true;

    doTextTest(
      "class TestClass {\n" +
      "    // Single line comment that exceeds right margin\n" +
      "    /* Multi line comment that exceeds right margin*/\n" +
      "    /**\n" +
      "      Javadoc comment that exceeds right margin" +
      "     */\n" +
      "     public String s = \"this is a long string to be wrapped\"\n" +
      "}",
      "class TestClass {\n" +
      "    // Single line comment that exceeds right\n" +
      "    // margin\n" +
      "    /* Multi line comment that exceeds right\n" +
      "    margin*/\n" +
      "    /**\n" +
      "     * Javadoc comment that exceeds right\n" +
      "     * margin\n" +
      "     */\n" +
      "    public String s = \"this is a long string to\" +\n" +
      "            \" be wrapped\"\n" +
      "}"
    );
  }

  public void testWrapLongLineWithTabs() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 20;
    getSettings().WRAP_LONG_LINES = true;
    getIndentOptions().USE_TAB_CHARACTER = true;
    getIndentOptions().TAB_SIZE = 4;

    doTextTest(
      "class TestClass {\n" +
      "\t \t   //This is a comment\n" +
      "}",
      "class TestClass {\n" +
      "\t//This is a\n" +
      "\t// comment\n" +
      "}"
    );
  }

  public void testWrapLongLineWithSelection() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 20;
    getSettings().WRAP_LONG_LINES = true;

    String initial =
      "class TestClass {\n" +
      "    //This is a comment\n" +
      "    //This is another comment\n" +
      "}";

    int start = initial.indexOf("//");
    int end = initial.indexOf("comment");
    myTextRange = new TextRange(start, end);
    doTextTest(initial, initial);

    myLineRange = new TextRange(1, 1);
    doTextTest(
      initial,
      "class TestClass {\n" +
      "    //This is a\n" +
      "    // comment\n" +
      "    //This is another comment\n" +
      "}"
    );
  }
}
