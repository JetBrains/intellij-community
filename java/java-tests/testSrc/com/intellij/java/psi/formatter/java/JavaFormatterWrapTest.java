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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for 'wrapping' settings.
 */
@SuppressWarnings("SpellCheckingInspection")
@TestApplication
public class JavaFormatterWrapTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testWrappingAnnotationArrayParameters() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 80;
    formatter.getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.doTextTest(
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

  @Test
  public void testAnnotationParamValueExceedingRightMargin() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-18051
    formatter.getSettings().RIGHT_MARGIN = 80;
    formatter.doTextTest(
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
  @Test
  public void testEnumConstantsWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54667
    formatter.getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().RIGHT_MARGIN = 80;

    // Don't expect the constants to be placed on new line.
    formatter.doTextTest(
      "enum Test {FIRST, SECOND}",
      "enum Test {FIRST, SECOND}"
    );

    // Expect not only enum constants to be wrapped but line break inside enum-level curly braces as well.
    formatter.doTextTest(
      "enum Test {FIRST, SECOND, THIIIIIIIIIIIIIIIIIRRDDDDDDDDDDDDDD, FOURTHHHHHHHHHHHHHHHH}",

      """
        enum Test {
            FIRST, SECOND, THIIIIIIIIIIIIIIIIIRRDDDDDDDDDDDDDD, FOURTHHHHHHHHHHHHHHHH
        }"""
    );
  }

  @Test
  public void testEnumCommentsWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-130575
    formatter.getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.doTextTest(
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

  @Test
  public void testEnumConstantsMixedWithCommentsWrapping() {
    // IDEA-180049

    // Expect comments to be wrapped
    formatterFixture.get().doTextTest(
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

  @Test
  public void testIDEA123074() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
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
    formatter.doMethodTest(before, after);
  }

  @Test
  public void testMethodAnnotationFollowedBySingleLineComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-22808
    formatter.getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    String text =
      """
        @Test//my_comment
        public void foo() {
        }""";

    // Expecting the code to be left as-is
    formatter.doClassTest(text, text);
  }

  @Test
  public void testClassAnnotationsWithoutModifier() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-178795
    formatter.getSettings().CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    // Expecting the code to be left as-is
    String text = """
      @Test
      class MyClass {
      }""";
    formatter.doTextTest(text, text);
  }

  @Test
  public void testClassAnnotationsWithoutModifierSameLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-178795
    formatter.getSettings().CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    // Expecting the code to be left as-is
    String text = "@Test class MyClass {\n" +
                  "}";
    formatter.doTextTest(text, text);
  }

  @Test
  public void testWrapCompoundStringLiteralThatEndsAtRightMargin() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-82398
    formatter.getSettings().RIGHT_MARGIN = 30;
    formatter.getSettings().WRAP_LONG_LINES = true;

    final String text = """
      class Test {
          String s = "first line " +
                  +"second line";
      }""";
    formatter.doTextTest(text, text);
  }

  @Test
  public void testWrapLongLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-55782
    formatter.getSettings().RIGHT_MARGIN = 50;
    formatter.getSettings().WRAP_LONG_LINES = true;

    formatter.doTextTest(
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

  @Test
  public void testWrapLongLineWithTabs() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-55782
    formatter.getSettings().RIGHT_MARGIN = 20;
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getIndentOptions().USE_TAB_CHARACTER = true;
    formatter.getIndentOptions().TAB_SIZE = 4;

    formatter.doTextTest(
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

  @Test
  public void testWrapLongLineWithSelection() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-55782
    formatter.getSettings().RIGHT_MARGIN = 20;
    formatter.getSettings().WRAP_LONG_LINES = true;

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

    formatter.setLineRange(new TextRange(1, 1));
    formatter.doTextTest(
      initial,
      """
        class TestClass {
            //This is a\s
            // comment
            //This is another comment
        }"""
    );
  }

  @Test
  public void testWrapMethodAnnotationBeforeParams() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-59536
    formatter.getSettings().RIGHT_MARGIN = 90;
    formatter.getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    formatter.doClassTest(
        "@SuppressWarnings({\"SomeInspectionIWantToIgnore\"}) public void doSomething(int x, int y) {}",
        """
          @SuppressWarnings({"SomeInspectionIWantToIgnore"})
          public void doSomething(int x, int y) {
          }"""
    );
  }

  @Test
  public void testMultipleExpressionInSameLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-64975.

    formatter.getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;
    formatter.doMethodTest(
      "int i = 1; int j = 2;",
      "int i = 1; int j = 2;"
    );

    formatter.getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;
    formatter.doMethodTest(
        "int i = 1; int j = 2;",
        "int i = 1;\n" +
        "int j = 2;"
    );
  }

  @Test
  public void testIncompleteFieldAndAnnotationWrap() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-64725

    formatter.getSettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.doClassTest(
        "@NotNull Comparable<String>",
        "@NotNull Comparable<String>"
    );
  }

  @Test
  public void testResourceListWrap() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().RIGHT_MARGIN = 40;
    formatter.getSettings().RESOURCE_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.doMethodTest("try (MyResource r1 = null; MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "     MyResource r2 = null) {}");

    formatter.getSettings().RESOURCE_LIST_LPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().RESOURCE_LIST_RPAREN_ON_NEXT_LINE = true;
    formatter.doMethodTest("try (MyResource r1 = null; MyResource r2 = null) { }",
                 """
                   try (
                           MyResource r1 = null;
                           MyResource r2 = null
                   ) {}""");
  }

  @Test
  public void testLineLongEnoughToExceedAfterFirstWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-103624
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    // No wrapping for now
    formatter.doMethodTest(
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
  @Test
  public void testNoUnnecessaryWrappingIsPerformedForLongLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-103624
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text =
      """
        test(1,
             2,
             Test.
                     loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooongMethod()
        );
        int i = 1;
        int j = 2;""";
    formatter.doMethodTest(text, text);
  }

  @Test
  public void testEnforceIndentMethodCallParamWrap() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 140;
    formatter.getSettings().PREFER_PARAMETERS_WRAP = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

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

    formatter.doMethodTest(afterFirstReformat, after);

    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.doMethodTest(before,
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

    formatter.doMethodTest(before, after);
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testDoNotWrapMethodsWithMethodCallAsParameters() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 140;
    formatter.getSettings().PREFER_PARAMETERS_WRAP = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    String before = "   processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage());";
    String after = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage());";

    formatter.doMethodTest(before, after);

    before = "   processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage(loooooooooooooooooongParamName));";
    after = "processingEnv.getMessenger().printMessage(Diagnostic.Kind.ERROR, getMessage(loooooooooooooooooongParamName));";

    formatter.doMethodTest(before, after);
  }

  @Test
  public void testFieldAnnotationWithoutModifier() {
    formatterFixture.get().doClassTest("@NotNull String myFoo = null;", "@NotNull\nString myFoo = null;");
  }

  @Test
  public void testTypeAnnotationsInModifierList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;

    String prefix =
      """
        import java.lang.annotation.*;

        //@formatter:off
        @interface A { }
        @Target({ElementType.TYPE_USE}) @interface TA { int value() default 0; }
        //@formatter:on

        """;

    formatter.doTextTest(
        prefix + "interface C {\n" +
        "    @TA(0)String m();\n" +
        "    @A  @TA(1)  @TA(2)String m();\n" +
        "    @A  public  @TA String m();\n" +
        "}",

        prefix + "interface C {\n" +
        "    @TA(0)\n" +
        "    String m();\n\n" +
        "    @A\n" +
        "    @TA(1)\n" +
        "    @TA(2)\n" +
        "    String m();\n\n" +
        "    @A\n" +
        "    public @TA String m();\n" +
        "}");
  }

  @Test
  public void testKeepSingleFieldAnnotationOnSameLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = true;
    formatter.doClassTest(
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

  @Test
  public void testMoveSingleAnnotationOnSameLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = true;
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.doClassTest(
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

  @Test
  public void test_Wrap_On_Method_Parameter_Declaration() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.doClassTest(
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

    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.doClassTest(
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

  @Test
  public void test_Do_Not_Wrap_On_Nested_Call_Arguments_If_Not_Needed() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().PREFER_PARAMETERS_WRAP = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    formatter.doMethodTest("call(aaaaaaaaaaabbbbbbbbbbbbsdfsdfsdfsdfsdfsdfsdfb, 1 + call(111111111, 32213123123, 123123123123, 234234234234324234234));",
                 "call(aaaaaaaaaaabbbbbbbbbbbbsdfsdfsdfsdfsdfsdfsdfb,\n" +
                 "        1 + call(111111111, 32213123123, 123123123123, 234234234234324234234));");
  }

  @Test
  public void test_PlaceOnNewLineParenth_DoNotWork_IfLineNotExceedsRightMargin() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    formatter.doMethodTest("""
                   run(new Runnable() {
                   public void run() {
                   }
                   });""",
                 """
                   run(new Runnable() {
                       public void run() {
                       }
                   });""");

    formatter.doMethodTest("""
                   run(() -> {
                   int a = 2;
                   });""",
                 """
                   run(() -> {
                       int a = 2;
                   });""");
  }

  @Test
  public void test_WrapIfLong_ActivatesPlaceNewLineAfterParenthesis() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccccccccccccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccccccccccccccccccccccc");""");

  }

  @Test
  public void test_WrapIfLong_On_Second_Parameter_ActivatesPlaceNewLineAfterParenthesis() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccc");""");
  }

  @Test
  public void test_LParen_OnNextLine_IfWrapped() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;

    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun(
                           "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");


    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");

  }

  @Test
  public void test_RParen_OnNextLine_IfWrapped() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc"
                   );""");


    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    formatter.doMethodTest("fuun(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccccccc\");",
                 """
                   fuun("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                           "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                           "cccccccccccccc");""");

  }

  @Test
  public void test_ChainedCalls_FirstOnNewLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    formatter.doMethodTest(
      "obj.call().call().call().call();",
      """
        obj
                .call()
                .call()
                .call()
                .call();"""
    );

    formatter.doMethodTest(
      "call().call().call().call();",
      """
        call()
                .call()
                .call()
                .call();"""
    );

    formatter.doMethodTest(
      "nestedCall(call().call().call().call());",
      """
        nestedCall(call()
                .call()
                .call()
                .call());"""
    );
  }

  @Test
  public void test_ChainedCalls_NoWrapOnSingleCall() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    formatter.doMethodTest(
      "obj.call(    )",
      "obj.call()"
    );
  }
  
  @Test
  public void test_TryWithResource_NextLineIfWrapped() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RESOURCE_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().ALIGN_MULTILINE_RESOURCES = false;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.doMethodTest(
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
  
  @Test
  public void test_KeepSimpleLambdasInOneLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.doMethodTest("      execute(  () -> {});", 
                 "execute(() -> {});");
    
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.doMethodTest("execute(() -> {});", 
                 "execute(() -> {\n" +
                 "});");
  }


  @Test
  public void testDisableWrapLongLinesInFormatterMarkers() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;
    formatter.getSettings().RIGHT_MARGIN = 80;
    
    formatter.doTextTest(
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

  @Test
  public void testNoImportWrap() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;

    formatter.doTextTest(
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

  @Test
  public void testNoWrapOnEllipsis() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 35;

    formatter.doTextTest(
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

  @Test
  public void testNoWrapInDouble() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 35;

    formatter.doTextTest(
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

  @Test
  public void testIdea186225() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 100;
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;
    formatter.getIndentOptions().CONTINUATION_INDENT_SIZE = 4;

    formatter.doTextTest(
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

  @Test
  public void testIdea110902() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().WRAP_COMMENTS = true;
    formatter.getSettings().RIGHT_MARGIN = 50;

    formatter.doTextTest(
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

  @Test
  public void testBuilderMethods() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BUILDER_METHODS = "flowPanel,widget,wrap,builder,end";
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    formatter.doTextTest(
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

  @Test
  public void testIdea248594() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.doTextTest(
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

  @Test
  public void testWrapMixedBuilderAndNonBuilderChainedCalls() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BUILDER_METHODS = "start,addInt,addText,end";
    formatter.getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = true;

    formatter.doTextTest(
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


  @Test
  public void testIdea189817_noWrap() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;

    formatter.doTextTest(
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

  @Test
  public void testIdea189817_wrapAfterUrl() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;

    formatter.doTextTest(
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

  @Test
  public void testIdea186208() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().WRAP_FIRST_METHOD_IN_CALL_CHAIN = false;
    formatter.getSettings().RIGHT_MARGIN = 60;

    formatter.doClassTest(
      "final ImmutableMap<String, String> map = ImmutableMap.of(\"content\", \"value\", \"content\", \"value\",\"content\", \"value\"," +
      "\"content\", \"value\",\"content\", \"value\",\"content\", \"value\");",

      """
        final ImmutableMap<String, String> map = ImmutableMap.of(
                "content", "value", "content", "value",
                "content", "value", "content", "value",
                "content", "value", "content", "value");"""
    );
  }

  @Test
  public void testSwitchLabelBracesWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    formatter.doClassTest(
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

  @Test
  public void testExprInSwitchWrapping() {
    formatterFixture.get().doClassTest(
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

  @Test
  public void testPermitsListWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;

    formatter.doTextTest(
      "class A permits Class1, Class2, Class3, Class4, Class5 {}",

      """
        class A permits Class1, Class2,
                Class3, Class4, Class5 {
        }"""
    );
  }

  @Test
  public void testDeconstructionPatternWrappingNotAligned() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WRAP_LONG_LINES = true;
    formatter.getSettings().RIGHT_MARGIN = 40;
    formatter.getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getJavaSettings().ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS = false;

    formatter.doMethodTest(
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

  @Test
  public void testCaseLabelElementListEnum() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 50;
    formatter.getSettings().SWITCH_EXPRESSIONS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    formatter.doClassTest("""
          boolean isWeekend(DayOfWeek day) {
                return switch (day) {
                    case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> false;
                    case SATURDAY, SUNDAY -> true;
                };
          }""", """
          boolean isWeekend(DayOfWeek day) {
              return switch (day) {
                  case MONDAY,
                       TUESDAY,
                       WEDNESDAY,
                       THURSDAY,
                       FRIDAY -> false;
                  case SATURDAY, SUNDAY -> true;
              };
          }""");
  }

  @Test
  public void testCaseLabelElementListConditionalExpression() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 30;
    formatter.getSettings().SWITCH_EXPRESSIONS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    formatter.doClassTest("""
          boolean isMagicNumber(Integer b) {
              return switch (b) {     // Exhaustive!
                  case true ? 10 : 20, false ? 11 : 12 -> true;
                  default -> false;
              };
          }""", """
          boolean isMagicNumber(Integer b) {
              return switch (b) {     // Exhaustive!
                  case true ? 10 : 20,
                       false ? 11 : 12 ->
                          true;
                  default -> false;
              };
          }""");
  }

  @Test
  public void testCaseLabelElementListNumbers() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 30;
    formatter.getSettings().SWITCH_EXPRESSIONS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    formatter.doClassTest("""
          int getMagicNumber(Integer b) {
              return switch (b) {     // Exhaustive!
                  case 10, 100, 1000, 10000 -> 1;
                  default -> -1;
              };
          }""", """
          int getMagicNumber(Integer b) {
              return switch (b) {     // Exhaustive!
                  case 10,
                       100,
                       1000,
                       10000 -> 1;
                  default -> -1;
              };
          }""");
  }
}
