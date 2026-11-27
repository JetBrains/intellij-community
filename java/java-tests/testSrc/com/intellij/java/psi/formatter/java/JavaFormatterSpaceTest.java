// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for 'spacing' settings.
 */
@TestApplication
public class JavaFormatterSpaceTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @BeforeEach
  void setUp() {
    IdeaTestUtil.setProjectLanguageLevel(formatterFixture.get().getProject(), JavaTestUtil.getMaxRegisteredLanguageLevel());
  }

  @Test
  public void testSpacingBetweenTypeParameters() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Implied by IDEADEV-3666
    formatter.getSettings().SPACE_AFTER_COMMA = true;

    formatter.doTextTest("""
                 class Foo {
                 Map<String,String> map() {}
                 }""",
               """
                 class Foo {
                     Map<String, String> map() {
                     }
                 }""");
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testDoNotPlaceStatementsOnOneLineIfFirstEndsWithSingleLineComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    String before = """
      public class Reproduce {
          public void start() {
              if (true)
                  return; // comment
              final int count = 5;
              for (int i = 0; i < count; i++) {
                  System.out.println("AAA!");
                  System.out.println("BBB!"); // ololol
                  System.out.println("asda"); /* ololo */
                  System.out.println("booo");
              }
          }
      }""";
    String after = """
      public class Reproduce {
          public void start() {
              if (true) return; // comment
              final int count = 5; for (int i = 0; i < count; i++) {
                  System.out.println("AAA!"); System.out.println("BBB!"); // ololol
                  System.out.println("asda"); /* ololo */ System.out.println("booo");
              }
          }
      }""";
    formatter.doTextTest(before, after);
  }

  @Test
  public void testSpaceBeforeAnnotationParamArray() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-24329
    formatter.getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    String text =
      """
        @SuppressWarnings( {"ALL"})
        public class FormattingTest {
        }""";

    // Don't expect the space to be 'ate'
    formatter.doTextTest(text, text);
  }

  @Test
  public void testCommaInTypeArguments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-31681
    formatter.getSettings().SPACE_AFTER_COMMA = false;

    formatter.getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = false;
    String initial =
      """
        interface TestInterface<A,B> {

            <X,Y> void foo(X x,Y y);
        }

        public class FormattingTest implements TestInterface<String,Integer> {

            public <X,Y> void foo(X x,Y y) {
                Map<String,Integer> map = new HashMap<String,Integer>();
            }
        }""";

    formatter.doTextTest(initial, initial); // Don't expect the comma to be inserted

    formatter.getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
    String formatted =
      """
        interface TestInterface<A,B> {

            <X,Y> void foo(X x,Y y);
        }

        public class FormattingTest implements TestInterface<String, Integer> {

            public <X,Y> void foo(X x,Y y) {
                Map<String, Integer> map = new HashMap<String, Integer>();
            }
        }""";
    formatter.doTextTest(initial, formatted); // Expect the comma to be inserted between type arguments
  }

  @Test
  public void testUnaryOperators() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-52127
    formatter.getSettings().SPACE_AROUND_UNARY_OPERATOR = false;

    String initial =
      """
        public class FormattingTest {
            public void foo() {
                int i = 1;
                System.out.println(-i);
                System.out.println(+i);
                System.out.println(++i);
                System.out.println(i++);
                System.out.println(--i);
                System.out.println(i--);
                boolean b = true;
                System.out.println(!b);
            }
        }""";

    formatter.doTextTest(initial, initial); // Don't expect spaces to be inserted after unary operators

    formatter.getSettings().SPACE_AROUND_UNARY_OPERATOR = true;
    String formatted =
      """
        public class FormattingTest {
            public void foo() {
                int i = 1;
                System.out.println(- i);
                System.out.println(+ i);
                System.out.println(++ i);
                System.out.println(i++);
                System.out.println(-- i);
                System.out.println(i--);
                boolean b = true;
                System.out.println(! b);
            }
        }""";
    formatter.doTextTest(initial, formatted); // Expect spaces to be inserted after unary operators
  }

  @SuppressWarnings("unused")
  public void _testJavadocMethodParams() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-42167
    // Disabled because the contents of the {@code tag} is not necessarily Java code and
    // therefore it's incorrect to modify it when formatting
    formatter.getSettings().SPACE_AFTER_COMMA = false;

    String initial =
      """
        public class FormattingTest {
            /**
             * This is a convenience method for {@code doTest(test,   new      Object[0]);}
             */
            void doTest() {
            }
        }""";

    // Expect single space to left between 'new' and Object[0].
    formatter.doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test,new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");

    // Expect space to be inserted between ',' and 'new'.
    formatter.getSettings().SPACE_AFTER_COMMA = true;
    formatter.doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test, new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");

    // Expect space to be inserted between 'test' and ','.
    formatter.getSettings().SPACE_BEFORE_COMMA = true;
    formatter.doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test , new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");
  }

  @Test
  public void testSpaceWithArrayBrackets() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58510
    formatter.getSettings().SPACE_WITHIN_BRACKETS = true;
    formatter.doMethodTest(
      """
        int[] i = new int[1]
        i[0] = 1;
        int[] i2 = new int[]{1}""",
      """
        int[] i = new int[ 1 ]
        i[ 0 ] = 1;
        int[] i2 = new int[]{1}"""
    );
  }

  @Test
  public void testSpaceBeforeElse() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58068
    formatter.getSettings().ELSE_ON_NEW_LINE = false;

    formatter.getSettings().SPACE_BEFORE_ELSE_KEYWORD = false;
    formatter.doMethodTest(
      """
        if (true) {
        } else {
        }""",
      """
        if (true) {
        }else {
        }"""
    );

    formatter.getSettings().SPACE_BEFORE_ELSE_KEYWORD = true;
    formatter.doMethodTest(
      """
        if (true) {
        }else {
        }""",
      """
        if (true) {
        } else {
        }"""
    );
  }

  @Test
  public void testSpaceBeforeWhile() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58068
    formatter.getSettings().WHILE_ON_NEW_LINE = false;

    formatter.getSettings().SPACE_BEFORE_WHILE_KEYWORD = false;
    formatter.doMethodTest(
      "do {\n" +
      "} while (true);",
      "do {\n" +
      "}while (true);"
    );

    formatter.getSettings().SPACE_BEFORE_WHILE_KEYWORD = true;
    formatter.doMethodTest(
      "do {\n" +
      "}while (true);",
      "do {\n" +
      "} while (true);"
    );
  }


  @Test
  public void testSpaceBeforeCatch() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58068
    formatter.getSettings().CATCH_ON_NEW_LINE = false;

    formatter.getSettings().SPACE_BEFORE_CATCH_KEYWORD = false;
    formatter.doMethodTest(
      """
        try {
        } catch (Exception e) {
        }""",
      """
        try {
        }catch (Exception e) {
        }"""
    );

    formatter.getSettings().SPACE_BEFORE_CATCH_KEYWORD = true;
    formatter.doMethodTest(
      """
        try {
        }catch (Exception e) {
        }""",
      """
        try {
        } catch (Exception e) {
        }"""
    );
  }

  @Test
  public void testSpaceBeforeFinally() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58068
    formatter.getSettings().FINALLY_ON_NEW_LINE = false;

    formatter.getSettings().SPACE_BEFORE_FINALLY_KEYWORD = false;
    formatter.doMethodTest(
      """
        try {
        } finally {
        }""",
      """
        try {
        }finally {
        }"""
    );

    formatter.getSettings().SPACE_BEFORE_FINALLY_KEYWORD = true;
    formatter.doMethodTest(
      """
        try {
        }finally {
        }""",
      """
        try {
        } finally {
        }"""
    );
  }

  @Test
  public void testEmptyIterationAtFor() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-58293

    formatter.getSettings().SPACE_AFTER_SEMICOLON = true;
    formatter.getSettings().SPACE_WITHIN_FOR_PARENTHESES = false;

    formatter.doMethodTest(
      "for (   ; ;         )",
      "for (; ; )"
    );
  }

  @Test
  public void testSpacesInDisjunctiveType() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().CATCH_ON_NEW_LINE = false;

    formatter.getSettings().SPACE_AROUND_BITWISE_OPERATORS = true;
    formatter.doMethodTest("try { } catch (E1|E2 e) { }",
                 "try {} catch (E1 | E2 e) {}");

    formatter.getSettings().SPACE_AROUND_BITWISE_OPERATORS = false;
    formatter.doMethodTest("try { } catch (E1 | E2 e) { }",
                 "try {} catch (E1|E2 e) {}");
  }

  @Test
  public void testSpacesInsideLambda() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_AROUND_LAMBDA_ARROW = true;

    formatter.doMethodTest("()->{}",
                 "() -> {}");

    formatter.getSettings().SPACE_AROUND_LAMBDA_ARROW = false;
    formatter.doMethodTest("() -> {}",
                 "()->{}");
  }

  @Test
  public void testSpacesInsideMethodRef() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AROUND_METHOD_REF_DBL_COLON = true;

    //expr
    formatter.doMethodTest("Runnable r = this::foo",
                 "Runnable r = this :: foo");

    //class ref
    formatter.doMethodTest("Runnable r = Foo::foo",
                 "Runnable r = Foo :: foo");

    formatter.getSettings().SPACE_AROUND_METHOD_REF_DBL_COLON = false;
    formatter.doMethodTest("Runnable r = this::foo",
                 "Runnable r = this::foo");
  }

  @Test
  public void testSpacesBeforeResourceList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    formatter.getSettings().SPACE_BEFORE_TRY_PARENTHESES = true;
    formatter.getSettings().SPACE_BEFORE_TRY_LBRACE = true;
    formatter.doMethodTest("try(AutoCloseable r = null){ }",
                 "try (AutoCloseable r = null) {}");

    formatter.getSettings().SPACE_BEFORE_TRY_PARENTHESES = false;
    formatter.getSettings().SPACE_BEFORE_TRY_LBRACE = false;
    formatter.doMethodTest("try (AutoCloseable r = null) { }",
                 "try(AutoCloseable r = null){}");
  }

  @Test
  public void testSpacesWithinResourceList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    formatter.getSettings().SPACE_WITHIN_TRY_PARENTHESES = false;
    formatter.doMethodTest("try (  R r = null  ) { }",
                 "try (R r = null) {}");
    formatter.getSettings().SPACE_AFTER_SEMICOLON = false;
    formatter.doMethodTest("try (  R r1 = null    ; R r2 = null; ) { }",
                 "try (R r1 = null;R r2 = null;) {}");

    formatter.getSettings().SPACE_WITHIN_TRY_PARENTHESES = true;
    formatter.doMethodTest("try (R r = null) { }",
                 "try ( R r = null ) {}");
    formatter.getSettings().SPACE_AFTER_SEMICOLON = true;
    formatter.doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try ( R r1 = null; R r2 = null; ) {}");
  }

  @Test
  public void testSpacesBetweenResources() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    formatter.getSettings().SPACE_BEFORE_SEMICOLON = false;
    formatter.getSettings().SPACE_AFTER_SEMICOLON = true;
    formatter.doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try (R r1 = null; R r2 = null;) {}");

    formatter.getSettings().SPACE_BEFORE_SEMICOLON = true;
    formatter.getSettings().SPACE_AFTER_SEMICOLON = false;
    formatter.doMethodTest("try (R r1 = null;   R r2 = null;) { }",
                 "try (R r1 = null ;R r2 = null ;) {}");
  }

  @Test
  public void testSpacesInResourceAssignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    formatter.getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
    formatter.doMethodTest("try (R r=null) { }",
                 "try (R r = null) {}");

    formatter.getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = false;
    formatter.doMethodTest("try (R r =  null) { }",
                 "try (R r=null) {}");
  }

  @Test
  public void testBetweenMethodCallArguments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-71823
    formatter.getSettings().SPACE_AFTER_COMMA = false;

    formatter.doMethodTest(
      "foo(1, 2, 3);",
      "foo(1,2,3);"
    );
  }

  @Test
  public void testBeforeAnonymousClassConstructor() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-72321.
    formatter.getSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;

    formatter.doMethodTest(
      """
        actions.add(new Action(this) {
            public void run() {
            }
        });""",
      """
        actions.add (new Action (this) {
            public void run() {
            }
        });"""
    );
  }

  @Test
  public void testBeforeAnnotationArrayInitializer() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-72317
    formatter.getSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;
    formatter.getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    formatter.doClassTest(
      """
        @SuppressWarnings({"HardCodedStringLiteral"})
        void test() {
            int[] data = new int[] {1, 2, 3};
        }""",
      """
        @SuppressWarnings( {"HardCodedStringLiteral"})
        void test() {
            int[] data = new int[]{1, 2, 3};
        }"""
    );
  }

  @Test
  public void testBetweenParenthesesOfNoArgsMethod() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-74751
    formatter.getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    formatter.getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;
    formatter.getSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    formatter.getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = true;
    formatter.doClassTest(
      """
        void test() {
            foo();
        }""",
      """
        void test( ) {
            foo();
        }"""
    );

    formatter.getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    formatter.getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    formatter.getSettings().SPACE_WITHIN_METHOD_PARENTHESES = false;
    formatter.getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;
    formatter.doClassTest(
      """
        void test() {
            foo();
        }""",
      """
        void test() {
            foo( );
        }"""
    );
  }

  @Test
  public void testIncompleteCastExpression() {
    // Inspired by IDEA-75043.
    String text = """
      void test(int i) {
          (() i)
      }""";
    formatterFixture.get().doClassTest(text, text);
  }

  @Test
  public void testSpacesWithinAngleBrackets() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACES_WITHIN_ANGLE_BRACKETS = true;

    String beforeMethod = "static <      T             > void    fromArray(  T  [   ]   a ,   Collection<  T  >  c) {\n}";
    formatter.doClassTest(beforeMethod, "static < T > void fromArray(T[] a, Collection< T > c) {\n}");

    String beforeLocal = "Map<    String,   String     > map = new HashMap<   String,   String   >();";
    formatter.doMethodTest(beforeLocal, "Map< String, String > map = new HashMap< String, String >();");

    String beforeClass = "class  A <   U    > {\n}";
    formatter.doTextTest(beforeClass, "class A< U > {\n}");

    formatter.getJavaSettings().SPACES_WITHIN_ANGLE_BRACKETS = false;
    formatter.doMethodTest(beforeLocal, "Map<String, String> map = new HashMap<String, String>();");
    formatter.doClassTest(beforeMethod, "static <T> void fromArray(T[] a, Collection<T> c) {\n}");
    formatter.doTextTest(beforeClass, "class A<U> {\n}");
  }

  @Test
  public void testSpaceAfterClosingAngleBracket_InTypeArgument() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "Bar.<String, Integer>    mess(null);";

    formatter.getJavaSettings().SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = false;
    formatter.doMethodTest(before, "Bar.<String, Integer>mess(null);");

    formatter.getJavaSettings().SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = true;
    formatter.doMethodTest(before, "Bar.<String, Integer> mess(null);");
  }

  @Test
  public void testSpaceBeforeOpeningAngleBracket_InTypeParameter() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "class        A<T> {\n}";

    formatter.getJavaSettings().SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = false;
    formatter.doTextTest(before, "class A<T> {\n}");

    formatter.getJavaSettings().SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = true;
    formatter.doTextTest(before, "class A <T> {\n}");
  }

  @Test
  public void testSpaceAroundTypeBounds() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "public class     Foo<T extends Bar & Abba, U> {\n}";

    formatter.getJavaSettings().SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true;
    formatter.doTextTest(before, "public class Foo<T extends Bar & Abba, U> {\n}");

    formatter.getJavaSettings().SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = false;
    formatter.doTextTest(before, "public class Foo<T extends Bar&Abba, U> {\n}");
  }

  @Test
  public void testInnerTypeAnnotations() {
    formatterFixture.get().doTextTest(
      """
        class C<@TA(1)T> {
            L<@TA(2)A> f = (@TA(3)  A) new @TA(4)  A() {
                void m(@TA(6) int  @TA(7)[] p) {
                }
            };
        }""",

      """
        class C<@TA(1) T> {
            L<@TA(2) A> f = (@TA(3) A) new @TA(4) A() {
                void m(@TA(6) int @TA(7) [] p) {
                }
            };
        }"""
    );
  }

  @Test
  public void testClassObjectAccessExpression_BeforeDot() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "Test       \n         .class";

    formatter.getSettings().KEEP_LINE_BREAKS = true;
    formatter.doMethodTest(before, "Test\n        .class");

    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.doMethodTest(before, "Test.class");
  }

  @Test
  public void testClassObjectAccessExpression_AfterDot() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "Test.      \n     class";

    formatter.getSettings().KEEP_LINE_BREAKS = true;
    formatter.doMethodTest(before, "Test.\n        class");

    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.doMethodTest(before, "Test.class");
  }

  @Test
  public void testMultipleFieldDeclaration_InAnonymousClass() {
    formatterFixture.get().doMethodTest(
      """
        new Object() {
        boolean one, two;
        };""",
      """
        new Object() {
            boolean one, two;
        };"""
    );
  }

  @Test
  public void testCommentBetweenAnnotationAndModifierList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.getSettings().KEEP_FIRST_COLUMN_COMMENT = false;
    formatter.doClassTest("""
                  @Override
                  //FIX me this stupid stuff
                  public void run() {
                          int a = 2;
                  }""",

                """
                  @Override
                  //FIX me this stupid stuff
                  public void run() {
                      int a = 2;
                  }""");
  }

  @Test
  public void testSpace_BeforeSemicolon_InsideFor() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_BEFORE_SEMICOLON = true;
    formatter.doMethodTest(
      """
        int i = 0;
        for (; i < 10 ; i++) {
        }
        """,
      """
        int i = 0;
        for ( ; i < 10 ; i++) {
        }
        """
    );
  }

  @Test
  public void testSpace_BeforeSemicolon_InsideFor_IfSpacesWithinForIsOn() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_WITHIN_FOR_PARENTHESES = true;
    formatter.doMethodTest(
      """
        int i = 0;
        for (; i < 10 ; i++) {
        }
        """,
      """
        int i = 0;
        for ( ; i < 10; i++ ) {
        }
        """
    );
  }

  @Test
  public void testSpaceBeforeTypeArgumentList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_BEFORE_TYPE_PARAMETER_LIST = true;
    formatter.doMethodTest(
      "Map<Int, String> map = new HashMap<Int, String>();",
      "Map <Int, String> map = new HashMap <Int, String>();"
    );
    formatter.doMethodTest(
      "Bar.<Int, String>call();",
      "Bar. <Int, String>call();"
    );
  }

  @Test
  public void testKeepLineBreaksWorks_InsidePolyExpression() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.doMethodTest(
      "int x = (1 + 2 + 3) *\n" +
      "        (1 + 2 + 2) * (1 + 2);",
      "int x = (1 + 2 + 3) * (1 + 2 + 2) * (1 + 2);"
    );
  }

  @Test
  public void testKeepFinalOnLine() {
    formatterFixture.get().doClassTest(
      "public    static void bar(@NonNls final String[] args) {\n" +
      "}",
      "public static void bar(@NonNls final String[] args) {\n" +
      "}");
  }

  @Test
  public void testEnumAnnotations() {
    formatterFixture.get().doTextTest("""
                 enum SampleEnum {
                     @Annotation("1")ONE,
                     @Annotation("2")TWO,
                     @Annotation THREE
                 }""",
               """
                 enum SampleEnum {
                     @Annotation("1") ONE,
                     @Annotation("2") TWO,
                     @Annotation THREE
                 }""");
  }

  @Test
  public void testSpaceBeforeComma() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_BEFORE_COMMA = true;
    formatter.doClassTest(
      """
        public void main(String[] args, String xxx) {
          foo(100, 200);
        }""",
      """
        public void main(String[] args , String xxx) {
            foo(100 , 200);
        }""");
  }

  @Test
  public void testSpacingAroundVarKeyword() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.doMethodTest("for (  var  path :  paths) ;", "for (var path : paths) ;");
    formatter.doMethodTest("try ( @A  var  r  =  open()) { }", "try (@A var r = open()) {\n}");
  }

  @Test
  public void testSpacingBeforeColonInForeach() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACE_BEFORE_COLON_IN_FOREACH = false;
    formatter.doMethodTest("for (int i:arr) ;", "for (int i: arr) ;");
    formatter.getJavaSettings().SPACE_BEFORE_COLON_IN_FOREACH = true;
    formatter.doMethodTest("for (int i:arr) ;", "for (int i : arr) ;");
  }

  @Test
  public void testOneLineEnumSpacing() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACE_INSIDE_ONE_LINE_ENUM_BRACES = true;
    formatter.doTextTest("enum E {E1, E2}",
               "enum E { E1, E2 }");
  }

  @Test
  public void testSwitchLabelSpacing() {
    formatterFixture.get().doMethodTest("switch (i) { case\n1\n:break;\ndefault\n:break; }",
                 """
                   switch (i) {
                       case 1:
                           break;
                       default:
                           break;
                   }""");
  }

  @Test
  public void testSwitchLabeledRuleSpacing() {
    formatterFixture.get().doMethodTest("switch (i) { case\n1\n->\nfoo();\ncase\n2->{bar()};\ndefault->throw new Exception(); }",
                 """
                   switch (i) {
                       case 1 -> foo();
                       case 2 -> {
                           bar()
                       };
                       default -> throw new Exception();
                   }""");
  }

  @Test
  public void testMultiValueLabel() {
    formatterFixture.get().doMethodTest("switch(i) { case 1,2,  3: break; }",
                 """
                   switch (i) {
                       case 1, 2, 3:
                           break;
                   }""");
  }

  @Test
  public void testMultiValueLabeledRule() {
    formatterFixture.get().doMethodTest("switch(i) { case 1,2,  3 -> foo(); }",
                 """
                   switch (i) {
                       case 1, 2, 3 -> foo();
                   }""");
  }

  @Test
  public void testSwitchExpression() {
    formatterFixture.get().doMethodTest("String s = switch\n(i   ){default -> null;}",
                 """
                   String s = switch (i) {
                       default -> null;
                   }""");
  }

  @Test
  public void testBreakStatementSpacing() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    formatter.doMethodTest("""
                   String s = switch (i) {
                       case 1: break
                           label ;
                       case 3: break  label ;
                       case 4: break  ;
                   }""",
                 """
                   String s = switch (i) {
                       case 1: break label;
                       case 3: break label;
                       case 4: break;
                   }""");
  }

  @Test
  public void testYieldStatementSpacing() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    formatter.doMethodTest("""
                   String s = switch (i) {
                       case 0: yield(foo) ;
                       case 1: yield
                           42 ;
                       case 3: yield  label ;
                       case 4: yield  ;
                   }""",
                 """
                   String s = switch (i) {
                       case 0: yield (foo);
                       case 1: yield 42;
                       case 3: yield label;
                       case 4: yield ;
                   }""");
  }

  @Test
  public void testSpaceAfterCommaInRecordHeader() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AFTER_COMMA = true;
    formatter.doMethodTest("record R(String s,int i){}",
                 "record R(String s, int i) {\n" +
                 "}");
  }

  @Test
  public void testSpaceBeforeCommaInRecordHeader() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AFTER_COMMA = false;
    formatter.getSettings().SPACE_BEFORE_COMMA = true;
    formatter.doMethodTest("record R(String s,int i){}",
                 "record R(String s ,int i) {\n" +
                 "}");
  }

  @Test
  public void testSpaceBetweenGenericsAndName() {
    formatterFixture.get().doTextTest("record A(List<String> string){}",
               "record A(List<String> string) {\n" +
               "}");
  }

  @Test
  public void testSpaceWithinRecordHeader() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACE_WITHIN_RECORD_HEADER = true;
    formatter.doTextTest("record A(String string){}",
               "record A( String string ) {\n" +
               "}");
  }

  @Test
  public void testSpaceBetweenAnnotationAndType() {
    formatterFixture.get().doTextTest("record A(@Foo()String string) {}",
               "record A(@Foo() String string) {\n" +
               "}");
  }

  @Test
  public void testSpacesAroundRelationalOperators() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AROUND_RELATIONAL_OPERATORS = true;
    formatter.doMethodTest(
      """
        if (x >= 1 && y < 100) {
                 if (x<=5 && y>50) {
                    System.out.println("1..5");
                 }
              }""",

      """
        if (x >= 1 && y < 100) {
            if (x <= 5 && y > 50) {
                System.out.println("1..5");
            }
        }"""
    );
    formatter.getSettings().SPACE_AROUND_RELATIONAL_OPERATORS = false;
    formatter.doMethodTest(
      """
        if (x   >=   1 && y    <  100) {
                 if (x  <=  5 && y   >   50) {
                    System.out.println("1..5");
                 }
              }""",

      """
        if (x>=1 && y<100) {
            if (x<=5 && y>50) {
                System.out.println("1..5");
            }
        }"""
    );
  }

  @Test
  public void testIdea250968() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doClassTest(
      "public Long getId (){return id;}",

      "public Long getId() { return id; }"
    );
  }

  @Test
  public void testSnippet() {
    formatterFixture.get().doTextTest(
      """
        /**
         * {@snippet lang=java}
         **/
        class {}""",

      """
        /**
         * {@snippet lang = java}
         **/
        class {}""");
  }

  @Test
  public void testLeftShiftExpressionSpacing() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AROUND_SHIFT_OPERATORS = false;
    formatter.doMethodTest(
      "int x = a << 2;",
      "int x = a<<2;");
  }

  @Test
  public void testRightShiftExpressionSpacing() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_AROUND_SHIFT_OPERATORS = false;
    formatter.doMethodTest(
      "int x = a >> 2;",
      "int x = a>>2;");
  }

  @Test
  public void testNamedRecordPatternSpacing() {
    formatterFixture.get().doMethodTest(
      "return o instanceof Rec(int i)r",
      "return o instanceof Rec(int i) r");
  }

  @Test
  public void testDeconstructionPatternSpacing() {
    formatterFixture.get().doMethodTest(
      """
        switch (a) {
         case R  ( int x , String y  , char [ ] chs, List < A > list) -> {}
        }""",
      """
        switch (a) {
            case R(int x, String y, char[] chs, List<A> list) -> {
            }
        }""");
  }

  @Test
  public void testDeconstructionPatternSpacingBeforeComma() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_BEFORE_COMMA = true;
    formatter.doMethodTest(
      """
        switch (a) {
         case R  (int x,String y,char [ ] chs,List < A > list) -> {}
        }""",
      """
        switch (a) {
            case R(int x , String y , char[] chs , List<A> list) -> {
            }
        }""");
  }

  @Test
  public void testDeconstructionPatternSpacingWithin() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACE_WITHIN_DECONSTRUCTION_LIST = true;
    formatter.doMethodTest(
      """
        switch (a) {
         case R(int x,String y) -> {}
        }""",
      """
        switch (a) {
            case R( int x, String y ) -> {
            }
        }""");
  }

  @Test
  public void testDeconstructionPatternSpacingBefore() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().SPACE_BEFORE_DECONSTRUCTION_LIST = true;
    formatter.doMethodTest(
      """
        switch (a) {
         case R(int x,String y) -> {}
        }""",
      """
        switch (a) {
            case R (int x, String y) -> {
            }
        }""");
  }

  @Test
  public void testDeconstructionPatternNewLineAfterLpar() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN = true;
    formatter.getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doMethodTest(
      """
        switch (a) {
         case R(int x, String y) -> {}
        }""",
      """
        switch (a) {
            case R(
                    int x,
                    String y
            ) -> {
            }
        }""");
  }

  @Test
  public void testDeconstructionPatternNewLineBeforeRpar() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN = true;
    formatter.getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doMethodTest(
      """
        switch (a) {
         case R(int x, String y) -> {}
        }""",
      """
        switch (a) {
            case R(
                    int x,
                    String y
            ) -> {
            }
        }""");
  }

  @Test
  public void testSpaceBetweenWhenAndPattern() {
    formatterFixture.get().doMethodTest(
      """
        switch (xx) {
              case S(int x)when   true -> y();
        }""",
      """
        switch (xx) {
            case S(int x) when true -> y();
        }""");
  }

  @Test
  public void testEmptyCaseinSwitch() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 30;
    formatter.doMethodTest("""
                   switch (b) {
                       case   
                                        -> 1;
                       default -> -1;
                   }""",
                 """
                   switch (b) {
                       case -> 1;
                       default -> -1;
                   }"""
                 );
  }

  @Test
  public void testUnfinishedCaseinSwitch() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().RIGHT_MARGIN = 30;
    formatter.doMethodTest("""
                   switch (b) {
                       case 1,   
                                     -> 1;
                       default -> -1;
                   }""",
                 """
                   switch (b) {
                       case 1, -> 1;
                       default -> -1;
                   }"""
    );
  }

  @Test
  public void testForeachPatternInside() {
    formatterFixture.get().doMethodTest(
      "for (Rec(): foo)",
      "for (Rec() : foo)");
  }

  @Test
  public void testSemicolOnNewLineInLongCallChainWithWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getJavaSettings().WRAP_SEMICOLON_AFTER_CALL_CHAIN = true;

    formatter.doTextTest("""
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                         int i = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                     }
                 }
                 """,
               """
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1)
                             .reallyLongLongLongAddOperation(1)
                             .get()
                             ;
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1)
                                 .reallyLongLongLongAddOperation(1)
                                 .get()
                         ;
                                  
                         int i = new Summator().reallyLongLongLongAddOperation(1)
                                 .reallyLongLongLongAddOperation(1)
                                 .get()
                                 ;
                     }
                 }
                 """);

  }

  @Test
  public void testSemicolonOnNewLineInLongCallChainNoWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.getJavaSettings().WRAP_SEMICOLON_AFTER_CALL_CHAIN = true;
    formatter.doTextTest("""
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                         int i = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                     }
                 }
                 """,
               """
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                                  
                         int i = new Summator().reallyLongLongLongAddOperation(1).reallyLongLongLongAddOperation(1).get();
                     }
                 }
                 """);
  }

  @Test
  public void testSemicolonOnNewLineInShortCallChainWithWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getJavaSettings().WRAP_SEMICOLON_AFTER_CALL_CHAIN = true;
    formatter.doTextTest("""
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1).get();
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1).get();
                         
                         int i = new Summator().reallyLongLongLongAddOperation(1).get();
                     }
                 }
                 """,
               """
                 class Foo {
                     int x = new Summator().reallyLongLongLongAddOperation(1)
                             .get();
                                  
                     void foo() {
                         new Summator().reallyLongLongLongAddOperation(1)
                                 .get();
                                  
                         int i = new Summator().reallyLongLongLongAddOperation(1)
                                 .get();
                     }
                 }
                 """);
  }

  @Test
  public void testSemicolonOnNewLineInLongCallChainWithWrappingInForStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getJavaSettings().WRAP_SEMICOLON_AFTER_CALL_CHAIN = true;
    formatter.doMethodTest("""
                   for (int i = new Summator().add(1).add(1).get(); i < new Summator().add(1).add(10).get(); ++i) {}
                   """,
                 """
                 for (int i = new Summator().add(1)
                         .add(1)
                         .get(); i < new Summator().add(1)
                         .add(10)
                         .get(); ++i) {
                 }
                   """);
  }
}