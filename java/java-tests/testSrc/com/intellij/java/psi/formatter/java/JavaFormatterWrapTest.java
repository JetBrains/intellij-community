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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * Is intended to hold specific java formatting tests for 'wrapping' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 29, 2010 4:06:15 PM
 */
public class JavaFormatterWrapTest extends AbstractJavaFormatterTest {
  @SuppressWarnings("SpellCheckingInspection")
  public void testWrappingAnnotationArrayParameters() {
    getSettings().RIGHT_MARGIN = 80;
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
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

  public void testAnnotationParamValueExceedingRightMargin() {
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
      "    @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment =" +
      " \"some long comment that goes longer that right margin 012345678901234567890\")\n" +
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
      "    @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment =" +
      " \"some long comment that goes longer that right margin 012345678901234567890\")\n" +
      "    public class Test {\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "}");
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testEnumConstantsWrapping() {
    // Inspired by IDEA-54667
    getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
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

  public void testEnumCommentsWrapping() {
    // Inspired by IDEA-130575
    getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTextTest(
      "public enum Test {\n" +
      "\n" +
      "  TEST1(\"test\"),//comment 1\n" +
      "  TEST2(\"test\");//comment 2\n" +
      "\n" +
      "  private String value;\n" +
      "\n" +
      "  Test(String value) {\n" +
      "    this.value = value;\n" +
      "  }\n" +
      "\n" +
      "  public String getValue() {\n" +
      "    return value;\n" +
      "  }\n" +
      "\n" +
      "}",
      "public enum Test {\n" +
      "\n" +
      "    TEST1(\"test\"),//comment 1\n" +
      "    TEST2(\"test\");//comment 2\n" +
      "\n" +
      "    private String value;\n" +
      "\n" +
      "    Test(String value) {\n" +
      "        this.value = value;\n" +
      "    }\n" +
      "\n" +
      "    public String getValue() {\n" +
      "        return value;\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }

  public void testIDEA123074() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    String before = "final GeoZone geoZone1 = new GeoZone(APPROACHING, new Polygon(point(\"0.0\", \"0.0\"), point(\"10.0\", \"0.0\")," +
                    "point(\"10.0\", \"10.0\"), point(\"0.0\", \"10.0\")));";
    String after = "final GeoZone geoZone1 = new GeoZone(APPROACHING,\n" +
                   "        new Polygon(point(\"0.0\",\n" +
                   "                \"0.0\"),\n" +
                   "                point(\"10.0\",\n" +
                   "                        \"0.0\"),\n" +
                   "                point(\"10.0\",\n" +
                   "                        \"10.0\"),\n" +
                   "                point(\"0.0\",\n" +
                   "                        \"10.0\")));";
    doMethodTest(before, after);
  }

  public void testMethodAnnotationFollowedBySingleLineComment() {
    // Inspired by IDEA-22808
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    String text =
      "@Test//my_comment\n" +
      "public void foo() {\n" +
      "}";

    // Expecting the code to be left as-is
    doClassTest(text, text);
  }

  public void testWrapCompoundStringLiteralThatEndsAtRightMargin() {
    // Inspired by IDEA-82398
    getSettings().RIGHT_MARGIN = 30;
    getSettings().WRAP_LONG_LINES = true;

    final String text = "class Test {\n" +
                        "    String s = \"first line \" +\n" +
                        "            +\"second line\";\n" +
                        "}";
    doTextTest(text, text);
  }

  public void testWrapLongLine() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 50;
    getSettings().WRAP_LONG_LINES = true;

    doTextTest(
      "class TestClass {\n" +
      "    // Single line comment that is long enough to exceed right margin\n" +
      "    /* Multi line comment that is long enough to exceed right margin*/\n" +
      "    /**\n" +
      "      Javadoc comment that is long enough to exceed right margin" +
      "     */\n" +
      "     public String s = \"this is a string that is long enough to be wrapped\"\n" +
      "}",
      "class TestClass {\n" +
      "    // Single line comment that is long enough \n" +
      "    // to exceed right margin\n" +
      "    /* Multi line comment that is long enough \n" +
      "    to exceed right margin*/\n" +
      "    /**\n" +
      "     * Javadoc comment that is long enough to \n" +
      "     * exceed right margin\n" +
      "     */\n" +
      "    public String s = \"this is a string that is\" +\n" +
      "            \" long enough to be wrapped\"\n" +
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
      "\t//This is a \n" +
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
      "    //This is a \n" +
      "    // comment\n" +
      "    //This is another comment\n" +
      "}"
    );
  }

  public void testWrapMethodAnnotationBeforeParams() {
    // Inspired by IDEA-59536
    getSettings().RIGHT_MARGIN = 90;
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doClassTest(
        "@SuppressWarnings({\"SomeInspectionIWantToIgnore\"}) public void doSomething(int x, int y) {}",
        "@SuppressWarnings({\"SomeInspectionIWantToIgnore\"})\n" +
        "public void doSomething(int x, int y) {\n}"
    );
  }

  public void testMultipleExpressionInSameLine() {
    // Inspired by IDEA-64975.

    getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;
    doMethodTest(
      "int i = 1; int j = 2;",
      "int i = 1; int j = 2;"
    );

    getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;
    doMethodTest(
        "int i = 1; int j = 2;",
        "int i = 1;\n" +
        "int j = 2;"
    );
  }

  public void testIncompleteFieldAndAnnotationWrap() {
    // Inspired by IDEA-64725

    getSettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doClassTest(
        "@NotNull Comparable<String>",
        "@NotNull Comparable<String>"
    );
  }

  public void testResourceListWrap() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().RIGHT_MARGIN = 40;
    getSettings().RESOURCE_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doMethodTest("try (MyResource r1 = null; MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "     MyResource r2 = null) { }");

    getSettings().RESOURCE_LIST_LPAREN_ON_NEXT_LINE = true;
    getSettings().RESOURCE_LIST_RPAREN_ON_NEXT_LINE = true;
    doMethodTest("try (MyResource r1 = null; MyResource r2 = null) { }",
                 "try (\n" +
                 "        MyResource r1 = null;\n" +
                 "        MyResource r2 = null\n" +
                 ") { }");
  }

  public void testLineLongEnoughToExceedAfterFirstWrapping() {
    // Inspired by IDEA-103624
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    // No wrapping for now
    doMethodTest(
      "test(1,\n" +
      "     2,\n" +
      "     MyTestClass.loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod());\n" +
      "int i = 1;\n" +
      "int j = 2;",
      "test(1,\n" +
      "     2,\n" +
      "     MyTestClass.loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod());\n" +
      "int i = 1;\n" +
      "int j = 2;"
    );
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testNoUnnecessaryWrappingIsPerformedForLongLine() {
    // Inspired by IDEA-103624
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text =
      "test(1,\n" +
      "     2,\n" +
      "     Test.\n" +
      "             loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod()\n" +
      ");\n" +
      "int i = 1;\n" +
      "int j = 2;";
    doMethodTest(text, text);
  }

  public void testEnforceIndentMethodCallParamWrap() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 140;
    getSettings().PREFER_PARAMETERS_WRAP = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    String before = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, " +
                    "String.format(\"Could not process annotations: %s%n%s\", e.toString(), writer.toString()));";

    String afterFirstReformat = "processingEnv.getMessenger().printMessage(\n" +
                                "        Diagnostic.Kind.ERROR, String.format(\n" +
                                "        \"Could not process annotations: %s%n%s\",\n" +
                                "        e.toString(),\n" +
                                "        writer.toString()\n" +
                                ")\n" +
                                ");";

    String after = "processingEnv.getMessenger().printMessage(\n" +
                   "        Diagnostic.Kind.ERROR, String.format(\n" +
                   "                \"Could not process annotations: %s%n%s\",\n" +
                   "                e.toString(),\n" +
                   "                writer.toString()\n" +
                   "        )\n" +
                   ");";

    doMethodTest(afterFirstReformat, after);

    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    doMethodTest(before,
                 "processingEnv.getMessenger().printMessage(\n" +
                 "        Diagnostic.Kind.ERROR,\n" +
                 "        String.format(\"Could not process annotations: %s%n%s\", e.toString(), writer.toString())\n" +
                 ");");

    String literal = "\"" + StringUtil.repeatSymbol('A', 128) + "\"";
    before = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, call(" + literal + "));\n";
    after = "processingEnv.getMessenger().printMessage(\n" +
            "        Diagnostic.Kind.ERROR,\n" +
            "        call(" + literal + ")\n" +
            ");\n";

    doMethodTest(before, after);
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testDoNotWrapMethodsWithMethodCallAsParameters() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 140;
    getSettings().PREFER_PARAMETERS_WRAP = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    String before = "   processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage());";
    String after = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage());";

    doMethodTest(before, after);

    before = "   processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage(loooooooooooooooooongParamName));";
    after = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage(loooooooooooooooooongParamName));";

    doMethodTest(before, after);
  }

  public void testFieldAnnotationWithoutModifier() {
    doClassTest("@NotNull String myFoo = null;", "@NotNull\nString myFoo = null;");
  }

  public void testTypeAnnotationsInModifierList() {
    getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;

    String prefix =
      "import java.lang.annotation.*;\n\n" +
      "//@formatter:off\n" +
      "@interface A { }\n" +
      "@Target({ElementType.TYPE_USE}) @interface TA { int value() default 0; }\n" +
      "//@formatter:on\n\n";

    doTextTest(
        prefix + "interface C {\n" +
        "    @TA(0)String m();\n" +
        "    @A  @TA(1)  @TA(2)String m();\n" +
        "    @A  public  @TA String m();\n" +
        "}",

        prefix + "interface C {\n" +
        "    @TA(0) String m();\n\n" +
        "    @A\n" +
        "    @TA(1) @TA(2) String m();\n\n" +
        "    @A\n" +
        "    public @TA String m();\n" +
        "}");
  }

  public void testKeepSingleFieldAnnotationOnSameLine() {
    getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = true;
    doClassTest(
        "@NotNull public String result = \"OK\"\n" +
        "@NotNull String newResult = \"OK\"\n" +
        "@NotNull\n" +
        "@Deprecated public String bad = \"bad\"",

        "@NotNull public String result = \"OK\"\n" +
        "@NotNull String newResult = \"OK\"\n" +
        "@NotNull\n" +
        "@Deprecated\n" +
        "public String bad = \"bad\""
    );
  }

  public void testMoveSingleAnnotationOnSameLine() {
    getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = true;
    getSettings().KEEP_LINE_BREAKS = false;
    doClassTest(
      "@NotNull\n" +
      "public String test = \"tst\";\n" +
      "String ok = \"ok\";\n",
      "@NotNull public String test = \"tst\";\n" +
      "String ok = \"ok\";\n"
    );
  }

  public void test_Wrap_On_Method_Parameter_Declaration() {
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doClassTest(
        "      public static void main(String[] args) {\n" +
        "    boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;\n" +
        "    soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText(\"syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt\"));\n" +
        "}",
        "public static void main(String[] args) {\n" +
        "    boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;\n" +
        "    soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText(\"syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt\"));\n" +
        "}"
    );

    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doClassTest(
        "      public static void main(String[] args) {\n" +
        "    boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;\n" +
        "    soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText(\"syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt\"));\n" +
        "}",
        "public static void main(String[] args) {\n" +
        "    boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;\n" +
        "    soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa,\n" +
        "            (v) -> v.setText(\"syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt\"));\n" +
        "}"
    );
  }

  public void test_Do_Not_Wrap_On_Nested_Call_Arguments_If_Not_Needed() {
    getSettings().PREFER_PARAMETERS_WRAP = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doMethodTest("call(aaaaaaaaaaabbbbbbbbbbbbsdfsdfsdfsdfsdfsdfsdfb, 1 + call(111111111, 32213123123, 123123123123, 234234234234324234234));",
                 "call(aaaaaaaaaaabbbbbbbbbbbbsdfsdfsdfsdfsdfsdfsdfb,\n" +
                 "        1 + call(111111111, 32213123123, 123123123123, 234234234234324234234));");
  }

  public void test_PlaceOnNewLineParenth_DoNotWork_IfLineNotExceedsRightMargin() {
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    doMethodTest("run(new Runnable() {\n" +
                 "public void run() {\n" +
                 "}\n" +
                 "});",
                 "run(new Runnable() {\n" +
                 "    public void run() {\n" +
                 "    }\n" +
                 "});");

    doMethodTest("run(() -> {\n" +
                 "int a = 2;\n" +
                 "});",
                 "run(() -> {\n" +
                 "    int a = 2;\n" +
                 "});");
  }

  public void test_WrapIfLong_ActivatesPlaceNewLineAfterParenthesis() {
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccccccccccccccccccccccc\");",
                 "fuun(\n" +
                 "        \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                 "        \"cccccccccccccccccccccccccccccccccc\");");

  }

  public void test_WrapIfLong_On_Second_Parameter_ActivatesPlaceNewLineAfterParenthesis() {
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 "fuun(\n" +
                 "        \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                 "        \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");");
  }

  public void test_LParen_OnNextLine_IfWrapped() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 "fuun(\n" +
                 "        \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                 "        \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                 "        \"cccccccccccccc\");");


    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 "fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                 "        \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                 "        \"cccccccccccccc\");");

  }

  public void test_RParen_OnNextLine_IfWrapped() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 "fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                 "        \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                 "        \"cccccccccccccc\"" +
                 "\n);");


    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 "fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                 "        \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                 "        \"cccccccccccccc\");");

  }

  public void test_ChainedCalls_FirstOnNewLine() {
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    doMethodTest(
      "obj.call().call().call().call();",
      "obj\n" +
      "        .call()\n" +
      "        .call()\n" +
      "        .call()\n" +
      "        .call();"
    );

    doMethodTest(
      "call().call().call().call();",
      "call()\n" +
      "        .call()\n" +
      "        .call()\n" +
      "        .call();"
    );

    doMethodTest(
      "nestedCall(call().call().call().call());",
      "nestedCall(call()\n" +
      "        .call()\n" +
      "        .call()\n" +
      "        .call());"
    );
  }

  public void test_ChainedCalls_NoWrapOnSingleCall() {
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    doMethodTest(
      "obj.call(    )",
      "obj.call()"
    );
  }
  
  public void test_TryWithResource_NextLineIfWrapped() {
    getSettings().RESOURCE_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_RESOURCES = false;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doMethodTest(
      "try (Foo foo = createAFoo(); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo)) {\n" +
      "    useThem();\n"                                                                                                                             +
      "}",
      "try (Foo foo = createAFoo(); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo);\n" +
      "        Bar bar = createABar(foo))\n" +
      "{\n" +
      "    useThem();\n"                                                                                                                             +
      "}"
    );
  }
  
  public void test_KeepSimpleLambdasInOneLine() {
    getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    doMethodTest("      execute(  () -> {});", 
                 "execute(() -> {});");
    
    getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    doMethodTest("execute(() -> {});", 
                 "execute(() -> {\n" +
                 "});");
  }


  public void testDisableWrapLongLinesInFormatterMarkers() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;
    getSettings().RIGHT_MARGIN = 80;
    
    doTextTest(
      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    @Override\n" +
      "    public boolean equals(Object obj) {\n" +
      "\n" +
      "        String direction = \" \";\n" +
      "        String humanId = \" \";\n" +
      "        String instrument = \" \";\n" +
      "        String price = \" \";\n" +
      "        String quantity = \" \";\n" +
      "        String stopLoss = \" \";\n" +
      "        String thatsDirection = \"\";\n" +
      "\n" +
      "        return 0 < 0\n" +
      "                + (direction != null && thatsDirection != null ? direction.equalsIgnoreCase(thatsDirection)          ? 1 : -100 : 0)\n" +
      "                // @formatter:off\n" +
      "                + (humanId      != null && thatsDirection  != null ? humanId.equalsIgnoreCase(thatsDirection)        ? 1 : -100 : 0)\n" +
      "                + (instrument   != null && thatsDirection  != null ? instrument.equals(thatsDirection)               ? 1 : -100 : 0)\n" +
      "                + (price        != null && thatsDirection  != null ? price.equals(thatsDirection)                    ? 1 : -100 : 0)\n" +
      "                // @formatter:on\n" +
      "                + (quantity     != null && thatsDirection  != null ? quantity.equals(thatsDirection)                 ? 1 : -100 : 0)\n" +
      "                + (stopLoss     != null && thatsDirection  != null ? stopLoss.equals(thatsDirection)                 ? 1 : -100 : 0);\n" +
      "    }\n" +
      "}",
      
      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    @Override\n" +
      "    public boolean equals(Object obj) {\n" +
      "\n" +
      "        String direction = \" \";\n" +
      "        String humanId = \" \";\n" +
      "        String instrument = \" \";\n" +
      "        String price = \" \";\n" +
      "        String quantity = \" \";\n" +
      "        String stopLoss = \" \";\n" +
      "        String thatsDirection = \"\";\n" +
      "\n" +
      "        return 0 < 0\n" +
      "                + (direction != null && thatsDirection != null ?\n" +
      "                direction.equalsIgnoreCase(thatsDirection) ? 1 : -100 : 0)\n" +
      "                // @formatter:off\n" +
      "                + (humanId      != null && thatsDirection  != null ? humanId.equalsIgnoreCase(thatsDirection)        ? 1 : -100 : 0)\n" +
      "                + (instrument   != null && thatsDirection  != null ? instrument.equals(thatsDirection)               ? 1 : -100 : 0)\n" +
      "                + (price        != null && thatsDirection  != null ? price.equals(thatsDirection)                    ? 1 : -100 : 0)\n" +
      "                // @formatter:on\n" +
      "                + (quantity != null && thatsDirection != null ?\n" +
      "                quantity.equals(thatsDirection) ? 1 : -100 : 0)\n" +
      "                + (stopLoss != null && thatsDirection != null ?\n" +
      "                stopLoss.equals(thatsDirection) ? 1 : -100 : 0);\n" +
      "    }\n" +
      "}"
    );
  }

  public void testNoImportWrap() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      "package com.company;\n" +
      "\n" +
      "import com.company.subpackage.TestClassOne;\n" +
      "import com.company.subpackage.TestClassTwo;\n" +
      "\n" +
      "public class Test {\n" +
      "    void foo() {\n" +
      "        TestClassOne testClassOne = new TestClassOne();\n" +
      "        TestClassTwo testClassTwo = new TestClassTwo();\n" +
      "    }\n" +
      "}",

      "package com.company;\n" +
      "\n" +
      "import com.company.subpackage.TestClassOne;\n" +
      "import com.company.subpackage.TestClassTwo;\n" +
      "\n" +
      "public class Test {\n" +
      "    void foo() {\n" +
      "        TestClassOne testClassOne =\n" +
      "                new TestClassOne();\n" +
      "        TestClassTwo testClassTwo =\n" +
      "                new TestClassTwo();\n" +
      "    }\n" +
      "}"
    );
  }

  public void testNoWrapOnEllipsis() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      "package org.example.sandbox;\n" +
      "\n" +
      "public class Test {\n" +
      "    void test(String a, String... b) {\n" +
      "    }\n" +
      "}",

      "package org.example.sandbox;\n" +
      "\n" +
      "public class Test {\n" +
      "    void test(String a,\n" +
      "              String... b) {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testNoWrapInDouble() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      "package org.example.sandbox;\n" +
      "\n" +
      "public class Test {\n" +
      "    void test() {\n" +
      "        double doubleNumber = 12.456;\n" +
      "    }\n" +
      "}",

      "package org.example.sandbox;\n" +
      "\n" +
      "public class Test {\n" +
      "    void test() {\n" +
      "        double doubleNumber =\n" +
      "                12.456;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea186225() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 100;
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;
    getIndentOptions().CONTINUATION_INDENT_SIZE = 4;

    doTextTest(
      "interface Test {\n" +
      "    @SuppressWarnings(\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\" + \" \"\n" +
      "        + \"xxxxxxxxxxxxxxxxxxx\")\n" +
      "    void test();\n" +
      "}",

      "interface Test {\n" +
      "    @SuppressWarnings(\n" +
      "        \"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\" + \" \"\n" +
      "            + \"xxxxxxxxxxxxxxxxxxx\")\n" +
      "    void test();\n" +
      "}"
    );
  }

  public void testIdea110902() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 50;

    doTextTest(
      "public class Main {\n" +
      "\n" +
      "    /**\n" +
      "     * {@link #authenticationCompleted(android.app.Activity, int, int, android.content.Intent)}\n" +
      "     *\n" +
      "     * @param args\n" +
      "     */\n" +
      "    public static void main(String[] args) {\n" +
      "    }\n" +
      "}",

      "public class Main {\n" +
      "\n" +
      "    /**\n" +
      "     * {@link #authenticationCompleted(android.app.Activity,\n" +
      "     * int, int, android.content.Intent)}\n" +
      "     *\n" +
      "     * @param args\n" +
      "     */\n" +
      "    public static void main(String[] args) {\n" +
      "    }\n" +
      "}"
    );
  }
}
