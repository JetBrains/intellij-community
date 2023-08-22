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
 */
@SuppressWarnings("SpellCheckingInspection")
public class JavaFormatterWrapTest extends AbstractJavaFormatterTest {
  public void testWrappingAnnotationArrayParameters() {
    getSettings().RIGHT_MARGIN = 80;
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTextTest(
      """
        @AttributeOverrides( { @AttributeOverride(name = "id", column = @Column(name = "recovery_id")),@AttributeOverride(name = "transactionReference", column = @Column(name = "deal_reference")),@AttributeOverride(name = "eventDate", column = @Column(name = "recovery_date")),@AttributeOverride(name = "amount", column = @Column(name = "recovery_amount")),@AttributeOverride(name = "currency", column = @Column(name = "local_currency")),@AttributeOverride(name = "exchangeRate", column = @Column(name = "exchange_rate")),@AttributeOverride(name = "exchangeRateDate", column = @Column(name = "recovery_date", insertable = false, updatable = false)),@AttributeOverride(name = "exchangeRateAlterationJustification", column = @Column(name = "exchange_rate_justification")),@AttributeOverride(name = "systemExchangeRate", column = @Column(name = "system_exchange_rate")) })
        class Foo {
        }""",

      """
        @AttributeOverrides({
                @AttributeOverride(name = "id", column = @Column(name = "recovery_id")),
                @AttributeOverride(name = "transactionReference", column = @Column(name = "deal_reference")),
                @AttributeOverride(name = "eventDate", column = @Column(name = "recovery_date")),
                @AttributeOverride(name = "amount", column = @Column(name = "recovery_amount")),
                @AttributeOverride(name = "currency", column = @Column(name = "local_currency")),
                @AttributeOverride(name = "exchangeRate", column = @Column(name = "exchange_rate")),
                @AttributeOverride(name = "exchangeRateDate", column = @Column(name = "recovery_date", insertable = false, updatable = false)),
                @AttributeOverride(name = "exchangeRateAlterationJustification", column = @Column(name = "exchange_rate_justification")),
                @AttributeOverride(name = "systemExchangeRate", column = @Column(name = "system_exchange_rate"))})
        class Foo {
        }"""
    );
  }

  public void testAnnotationParamValueExceedingRightMargin() {
    // Inspired by IDEA-18051
    getSettings().RIGHT_MARGIN = 80;
    doTextTest(
      """
        package formatting;

        public class EnumInAnnotationFormatting {

            public enum TheEnum {

                FIRST,
                SECOND,
                THIRD,

            }

            public @interface TheAnnotation {

                TheEnum[] value();

                String comment();

            }


            @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment = "some long comment that goes longer that right margin 012345678901234567890")
            public class Test {

            }

        }""",
      """
        package formatting;

        public class EnumInAnnotationFormatting {

            public enum TheEnum {

                FIRST,
                SECOND,
                THIRD,

            }

            public @interface TheAnnotation {

                TheEnum[] value();

                String comment();

            }


            @TheAnnotation(value = {TheEnum.FIRST, TheEnum.SECOND}, comment = "some long comment that goes longer that right margin 012345678901234567890")
            public class Test {

            }

        }""");
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

      """
        enum Test {
            FIRST, SECOND, THIIIIIIIIIIIIIIIIIRRDDDDDDDDDDDDDD, FOURTHHHHHHHHHHHHHHHH
        }"""
    );
  }

  public void testEnumCommentsWrapping() {
    // Inspired by IDEA-130575
    getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTextTest(
      """
        public enum Test {

          TEST1("test"),//comment 1
          TEST2("test");//comment 2

          private String value;

          Test(String value) {
            this.value = value;
          }

          public String getValue() {
            return value;
          }

        }""",
      """
        public enum Test {

            TEST1("test"),//comment 1
            TEST2("test");//comment 2

            private String value;

            Test(String value) {
                this.value = value;
            }

            public String getValue() {
                return value;
            }

        }"""
    );
  }

  public void testEnumConstantsMixedWithCommentsWrapping() {
    // IDEA-180049

    // Expect comments to be wrapped
    doTextTest(
      """
        public enum FormatTest {
            FOO, /**
            * some description
            */ BAR, BAZ;
        }""",
      """
        public enum FormatTest {
            FOO,
            /**
             * some description
             */
            BAR, BAZ;
        }"""
    );
  }

  public void testIDEA123074() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    String before = "final GeoZone geoZone1 = new GeoZone(APPROACHING, new Polygon(point(\"0.0\", \"0.0\"), point(\"10.0\", \"0.0\")," +
                    "point(\"10.0\", \"10.0\"), point(\"0.0\", \"10.0\")));";
    String after = """
      final GeoZone geoZone1 = new GeoZone(APPROACHING,
              new Polygon(point("0.0",
                      "0.0"),
                      point("10.0",
                              "0.0"),
                      point("10.0",
                              "10.0"),
                      point("0.0",
                              "10.0")));""";
    doMethodTest(before, after);
  }

  public void testMethodAnnotationFollowedBySingleLineComment() {
    // Inspired by IDEA-22808
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    String text =
      """
        @Test//my_comment
        public void foo() {
        }""";

    // Expecting the code to be left as-is
    doClassTest(text, text);
  }

  public void testClassAnnotationsWithoutModifier() {
    // Inspired by IDEA-178795
    getSettings().CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().KEEP_LINE_BREAKS = true;

    // Expecting the code to be left as-is
    String text = """
      @Test
      class MyClass {
      }""";
    doTextTest(text, text);
  }

  public void testClassAnnotationsWithoutModifierSameLine() {
    // Inspired by IDEA-178795
    getSettings().CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().KEEP_LINE_BREAKS = true;

    // Expecting the code to be left as-is
    String text = "@Test class MyClass {\n" +
                  "}";
    doTextTest(text, text);
  }

  public void testWrapCompoundStringLiteralThatEndsAtRightMargin() {
    // Inspired by IDEA-82398
    getSettings().RIGHT_MARGIN = 30;
    getSettings().WRAP_LONG_LINES = true;

    final String text = """
      class Test {
          String s = "first line " +
                  +"second line";
      }""";
    doTextTest(text, text);
  }

  public void testWrapLongLine() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 50;
    getSettings().WRAP_LONG_LINES = true;

    doTextTest(
      """
        class TestClass {
            // Single line comment that is long enough to exceed right margin
            /* Multi line comment that is long enough to exceed right margin*/
            /**
              Javadoc comment that is long enough to exceed right margin     */
             public String s = "this is a string that is long enough to be wrapped"
        }""",
      """
        class TestClass {
            // Single line comment that is long enough\s
            // to exceed right margin
            /* Multi line comment that is long enough\s
            to exceed right margin*/
            /**
             * Javadoc comment that is long enough to\s
             * exceed right margin
             */
            public String s = "this is a string that is" +
                    " long enough to be wrapped"
        }"""
    );
  }

  public void testWrapLongLineWithTabs() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 20;
    getSettings().WRAP_LONG_LINES = true;
    getIndentOptions().USE_TAB_CHARACTER = true;
    getIndentOptions().TAB_SIZE = 4;

    doTextTest(
      """
        class TestClass {
        \t \t   //This is a comment
        }""",
      """
        class TestClass {
        \t//This is a\s
        \t// comment
        }"""
    );
  }

  public void testWrapLongLineWithSelection() {
    // Inspired by IDEA-55782
    getSettings().RIGHT_MARGIN = 20;
    getSettings().WRAP_LONG_LINES = true;

    String initial =
      """
        class TestClass {
            //This is a comment
            //This is another comment
        }""";

    //int start = initial.indexOf("//");
    //int end = initial.indexOf("comment");
    //myTextRange = new TextRange(start, end);
    //doTextTest(initial, initial);

    myLineRange = new TextRange(1, 1);
    doTextTest(
      initial,
      """
        class TestClass {
            //This is a\s
            // comment
            //This is another comment
        }"""
    );
  }

  public void testWrapMethodAnnotationBeforeParams() {
    // Inspired by IDEA-59536
    getSettings().RIGHT_MARGIN = 90;
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doClassTest(
        "@SuppressWarnings({\"SomeInspectionIWantToIgnore\"}) public void doSomething(int x, int y) {}",
        """
          @SuppressWarnings({"SomeInspectionIWantToIgnore"})
          public void doSomething(int x, int y) {
          }"""
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
                 "     MyResource r2 = null) {}");

    getSettings().RESOURCE_LIST_LPAREN_ON_NEXT_LINE = true;
    getSettings().RESOURCE_LIST_RPAREN_ON_NEXT_LINE = true;
    doMethodTest("try (MyResource r1 = null; MyResource r2 = null) { }",
                 """
                   try (
                           MyResource r1 = null;
                           MyResource r2 = null
                   ) {}""");
  }

  public void testLineLongEnoughToExceedAfterFirstWrapping() {
    // Inspired by IDEA-103624
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    // No wrapping for now
    doMethodTest(
      """
        test(1,
             2,
             MyTestClass.loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod());
        int i = 1;
        int j = 2;""",
      """
        test(1,
             2,
             MyTestClass.loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod());
        int i = 1;
        int j = 2;"""
    );
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testNoUnnecessaryWrappingIsPerformedForLongLine() {
    // Inspired by IDEA-103624
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text =
      """
        test(1,
             2,
             Test.
                     loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod()
        );
        int i = 1;
        int j = 2;""";
    doMethodTest(text, text);
  }

  public void testEnforceIndentMethodCallParamWrap() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 140;
    getSettings().PREFER_PARAMETERS_WRAP = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    String before = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, " +
                    "String.format(\"Could not process annotations: %s%n%s\", e.toString(), writer.toString()));";

    String afterFirstReformat = """
      processingEnv.getMessenger().printMessage(
              Diagnostic.Kind.ERROR, String.format(
              "Could not process annotations: %s%n%s",
              e.toString(),
              writer.toString()
      )
      );""";

    String after = """
      processingEnv.getMessenger().printMessage(
              Diagnostic.Kind.ERROR, String.format(
                      "Could not process annotations: %s%n%s",
                      e.toString(),
                      writer.toString()
              )
      );""";

    doMethodTest(afterFirstReformat, after);

    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    doMethodTest(before,
                 """
                   processingEnv.getMessenger().printMessage(
                           Diagnostic.Kind.ERROR,
                           String.format("Could not process annotations: %s%n%s", e.toString(), writer.toString())
                   );""");

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
      """
        import java.lang.annotation.*;

        //@formatter:off
        @interface A { }
        @Target({ElementType.TYPE_USE}) @interface TA { int value() default 0; }
        //@formatter:on

        """;

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
      """
        @NotNull public String result = "OK"
        @NotNull String newResult = "OK"
        @NotNull
        @Deprecated public String bad = "bad\"""",

      """
        @NotNull public String result = "OK"
        @NotNull String newResult = "OK"
        @NotNull
        @Deprecated
        public String bad = "bad\""""
    );
  }

  public void testMoveSingleAnnotationOnSameLine() {
    getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = true;
    getSettings().KEEP_LINE_BREAKS = false;
    doClassTest(
      """
        @NotNull
        public String test = "tst";
        String ok = "ok";
        """,
      """
        @NotNull public String test = "tst";
        String ok = "ok";
        """
    );
  }

  public void test_Wrap_On_Method_Parameter_Declaration() {
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doClassTest(
      """
              public static void main(String[] args) {
            boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;
            soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText("syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt"));
        }""",
      """
        public static void main(String[] args) {
            boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;
            soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText("syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt"));
        }"""
    );

    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doClassTest(
      """
              public static void main(String[] args) {
            boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;
            soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa, (v) -> v.setText("syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt"));
        }""",
      """
        public static void main(String[] args) {
            boolean ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa = false;
            soo.ifTrue(ssuuuuuuuuuuuuuuuuuuupaaaaaaaaaaaaa,
                    (v) -> v.setText("syyycuuuuuuuuurrrrrrrrrrrrrrennnnnnnnnnnnnnnnnnnnnt"));
        }"""
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

    doMethodTest("""
                   run(new Runnable() {
                   public void run() {
                   }
                   });""",
                 """
                   run(new Runnable() {
                       public void run() {
                       }
                   });""");

    doMethodTest("""
                   run(() -> {
                   int a = 2;
                   });""",
                 """
                   run(() -> {
                       int a = 2;
                   });""");
  }

  public void test_WrapIfLong_ActivatesPlaceNewLineAfterParenthesis() {
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccccccccccccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccccccccccccccccccccccc");""");

  }

  public void test_WrapIfLong_On_Second_Parameter_ActivatesPlaceNewLineAfterParenthesis() {
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccc");""");
  }

  public void test_LParen_OnNextLine_IfWrapped() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");


    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");

  }

  public void test_RParen_OnNextLine_IfWrapped() {
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc"
                   );""");


    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");

  }

  public void test_ChainedCalls_FirstOnNewLine() {
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    doMethodTest(
      "obj.call().call().call().call();",
      """
        obj
                .call()
                .call()
                .call()
                .call();"""
    );

    doMethodTest(
      "call().call().call().call();",
      """
        call()
                .call()
                .call()
                .call();"""
    );

    doMethodTest(
      "nestedCall(call().call().call().call());",
      """
        nestedCall(call()
                .call()
                .call()
                .call());"""
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
      """
        try (Foo foo = createAFoo(); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo)) {
            useThem();
        }""",
      """
        try (Foo foo = createAFoo(); Bar bar = createABar(foo); Bar bar = createABar(foo); Bar bar = createABar(foo);
                Bar bar = createABar(foo))
        {
            useThem();
        }"""
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
      """
        package com.acme;

        public class Test {
            @Override
            public boolean equals(Object obj) {

                String direction = " ";
                String humanId = " ";
                String instrument = " ";
                String price = " ";
                String quantity = " ";
                String stopLoss = " ";
                String thatsDirection = "";

                return 0 < 0
                        + (direction != null && thatsDirection != null ? direction.equalsIgnoreCase(thatsDirection)          ? 1 : -100 : 0)
                        // @formatter:off
                + (humanId      != null && thatsDirection  != null ? humanId.equalsIgnoreCase(thatsDirection)        ? 1 : -100 : 0)
                + (instrument   != null && thatsDirection  != null ? instrument.equals(thatsDirection)               ? 1 : -100 : 0)
                + (price        != null && thatsDirection  != null ? price.equals(thatsDirection)                    ? 1 : -100 : 0)
                // @formatter:on
                        + (quantity     != null && thatsDirection  != null ? quantity.equals(thatsDirection)                 ? 1 : -100 : 0)
                        + (stopLoss     != null && thatsDirection  != null ? stopLoss.equals(thatsDirection)                 ? 1 : -100 : 0);
            }
        }""",

      """
        package com.acme;

        public class Test {
            @Override
            public boolean equals(Object obj) {

                String direction = " ";
                String humanId = " ";
                String instrument = " ";
                String price = " ";
                String quantity = " ";
                String stopLoss = " ";
                String thatsDirection = "";

                return 0 < 0
                        + (direction != null && thatsDirection != null ?
                        direction.equalsIgnoreCase(thatsDirection) ? 1 : -100 : 0)
                        // @formatter:off
                + (humanId      != null && thatsDirection  != null ? humanId.equalsIgnoreCase(thatsDirection)        ? 1 : -100 : 0)
                + (instrument   != null && thatsDirection  != null ? instrument.equals(thatsDirection)               ? 1 : -100 : 0)
                + (price        != null && thatsDirection  != null ? price.equals(thatsDirection)                    ? 1 : -100 : 0)
                // @formatter:on
                        + (quantity != null && thatsDirection != null ?
                        quantity.equals(thatsDirection) ? 1 : -100 : 0)
                        + (stopLoss != null && thatsDirection != null ?
                        stopLoss.equals(thatsDirection) ? 1 : -100 : 0);
            }
        }"""
    );
  }

  public void testNoImportWrap() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      """
        package com.company;

        import com.company.subpackage.TestClassOne;
        import com.company.subpackage.TestClassTwo;

        public class Test {
            void foo() {
                TestClassOne testClassOne = new TestClassOne();
                TestClassTwo testClassTwo = new TestClassTwo();
            }
        }""",

      """
        package com.company;

        import com.company.subpackage.TestClassOne;
        import com.company.subpackage.TestClassTwo;

        public class Test {
            void foo() {
                TestClassOne testClassOne =
                        new TestClassOne();
                TestClassTwo testClassTwo =
                        new TestClassTwo();
            }
        }"""
    );
  }

  public void testNoWrapOnEllipsis() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      """
        package org.example.sandbox;

        public class Test {
            void test(String a, String... b) {
            }
        }""",

      """
        package org.example.sandbox;

        public class Test {
            void test(String a,
                      String... b) {
            }
        }"""
    );
  }

  public void testNoWrapInDouble() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      """
        package org.example.sandbox;

        public class Test {
            void test() {
                double doubleNumber = 12.456;
            }
        }""",

      """
        package org.example.sandbox;

        public class Test {
            void test() {
                double doubleNumber =
                        12.456;
            }
        }"""
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
      """
        interface Test {
            @SuppressWarnings("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + " "
                + "xxxxxxxxxxxxxxxxxxx")
            void test();
        }""",

      """
        interface Test {
            @SuppressWarnings(
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + " "
                    + "xxxxxxxxxxxxxxxxxxx")
            void test();
        }"""
    );
  }

  public void testIdea110902() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 50;

    doTextTest(
      """
        public class Main {

            /**
             * {@link #authenticationCompleted(android.app.Activity, int, int, android.content.Intent)}
             *
             * @param args
             */
            public static void main(String[] args) {
            }
        }""",

      """
        public class Main {

            /**
             * {@link
             * #authenticationCompleted(android.app.Activity,
             * int, int, android.content.Intent)}
             *
             * @param args
             */
            public static void main(String[] args) {
            }
        }"""
    );
  }

  public void testBuilderMethods() {
    getSettings().BUILDER_METHODS = "flowPanel,widget,wrap,builder,end";
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTextTest(
      """
        class Test {
            public static void main(String[] args) {
                PanelBuilder.wrap(getCenterPanel(), "review-view").flowPanel("sidebar-offset").widget(myReviewHints).flowPanel("island").flowPanel("pane-toolbar pane-toolbar_island clearfix").flowPanel("pane-toolbar__left pane-toolbar__left_header").widget(reviewStateLabel(reviewDescriptorSignal)).widget(reviewIdLabel(reviewDescriptorSignal)).builder(reviewTitle(projectDescriptor, reviewDescriptorSignal)).end().end().flowPanel("revision-files-standalone").widget(myChangesListView).end().end().widget(myReviewFeedView).end();
            }
        }""",

      """
        class Test {
            public static void main(String[] args) {
                PanelBuilder.wrap(getCenterPanel(), "review-view")
                        .flowPanel("sidebar-offset")
                        .widget(myReviewHints)
                        .flowPanel("island")
                        .flowPanel("pane-toolbar pane-toolbar_island clearfix")
                        .flowPanel("pane-toolbar__left pane-toolbar__left_header")
                        .widget(reviewStateLabel(reviewDescriptorSignal))
                        .widget(reviewIdLabel(reviewDescriptorSignal))
                        .builder(reviewTitle(projectDescriptor, reviewDescriptorSignal))
                        .end()
                        .end()
                        .flowPanel("revision-files-standalone")
                        .widget(myChangesListView)
                        .end()
                        .end()
                        .widget(myReviewFeedView)
                        .end();
            }
        }"""
    );
  }

  public void testIdea248594() {
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTextTest(
      """
        public class Test {
            void foo() {
                String zozo = List.<String>of("titi", "toto", "tutu").stream().filter(it -> it.contains("i"))
                        .findAny().<String>map(it -> it.replaceFirst("t", "l")).orElse("zozoggrezgzee");
            }
        }""",

      """
        public class Test {
            void foo() {
                String zozo = List.<String>of("titi", "toto", "tutu")
                        .stream()
                        .filter(it -> it.contains("i"))
                        .findAny()
                        .<String>map(it -> it.replaceFirst("t", "l"))
                        .orElse("zozoggrezgzee");
            }
        }"""
    );
  }

  public void testWrapMixedBuilderAndNonBuilderChainedCalls() {
    getSettings().BUILDER_METHODS = "start,addInt,addText,end";
    getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    doTextTest(
      """
        public class Test {

            void foo() {
                String result = this.nonBuilder().start().addInt().addText().end().toString();
            }
        }""",

      """
        public class Test {

            void foo() {
                String result = this.nonBuilder()
                        .start()
                        .addInt()
                        .addText()
                        .end().toString();
            }
        }"""
    );
  }


  public void testIdea189817_noWrap() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      """
        public class Cls {
         public void foo () {
         int x = 0; // See https://youtrack.jetbrains.com/issue/IDEA-189817#focus=Comments-27-2841120.0-0
          }
        }""",

      """
        public class Cls {
            public void foo() {
                int x = 0; // See https://youtrack.jetbrains.com/issue/IDEA-189817#focus=Comments-27-2841120.0-0
            }
        }"""
    );
  }

  public void testIdea189817_wrapAfterUrl() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      """
        public class Cls {
         public void foo () {
         int x = 0; // See https://youtrack.jetbrains.com/issue/IDEA-189817#focus=Comments-27-2841120.0-0 and other sources
          }
        }""",

      """
        public class Cls {
            public void foo() {
                int x = 0; // See https://youtrack.jetbrains.com/issue/IDEA-189817#focus=Comments-27-2841120.0-0
                // and other sources
            }
        }"""
    );
  }

  public void testIdea186208() {
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = false;
    getSettings().RIGHT_MARGIN = 60;

    doClassTest(
      "final ImmutableMap<String, String> map = ImmutableMap.of(\"content\", \"value\", \"content\", \"value\",\"content\", \"value\"," +
      "\"content\", \"value\",\"content\", \"value\",\"content\", \"value\");",

      """
        final ImmutableMap<String, String> map = ImmutableMap.of(
                "content", "value", "content", "value",
                "content", "value", "content", "value",
                "content", "value", "content", "value");"""
    );
  }

  public void testSwitchLabelBracesWrapping() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    doClassTest(
      """
        static boolean test(String name){
            switch (name){
                case "GET" -> {
                    return true;
                }
                case "POST" -> {
                    return false;
                }
            }
            return false;
        }""",

      """
        static boolean test(String name) {
            switch (name)
            {
                case "GET" ->
                {
                    return true;
                }
                case "POST" ->
                {
                    return false;
                }
            }
            return false;
        }"""
    );
  }

  public void testExprInSwitchWrapping() {
    doClassTest(
      """
          String process(E e) {
            return switch (e) {
              case LONG_NAME_A -> throw new IllegalStateException("long text long text long text long text long text long text  |  long text");
              case LONG_NAME_B -> "text";
            };
          }\
        """,

      """
        String process(E e) {
            return switch (e) {
                case LONG_NAME_A ->
                        throw new IllegalStateException("long text long text long text long text long text long text  |  long text");
                case LONG_NAME_B -> "text";
            };
        }"""
    );
  }

  public void testPermitsListWrapping() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      "class A permits Class1, Class2, Class3, Class4, Class5 {}",

      """
        class A permits Class1, Class2,
                Class3, Class4, Class5 {
        }"""
    );
  }

  public void testDeconstructionPatternWrappingNotAligned() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 40;
    getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getJavaSettings().ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS = false;

    doMethodTest(
      """
        switch (a) {
          case Rec(String s, int i) -> {}
        }""",

      """
        switch (a) {
            case Rec(
                    String s,
                    int i
            ) -> {
            }
        }"""
    );
  }
}
