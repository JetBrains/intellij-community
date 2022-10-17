// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * Is intended to hold specific java formatting tests for 'spacing' settings.
 *
 * @author Denis Zhdanov
 */
public class JavaFormatterSpaceTest extends AbstractJavaFormatterTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(JavaTestUtil.getMaxRegisteredLanguageLevel());
  }

  public void testSpacingBetweenTypeParameters() {
    // Implied by IDEADEV-3666
    getSettings().SPACE_AFTER_COMMA = true;

    doTextTest("""
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
  public void testDoNotPlaceStatementsOnOneLineIfFirstEndsWithSingleLineComment() {
    getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;
    getSettings().KEEP_LINE_BREAKS = false;
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
    doTextTest(before, after);
  }

  public void testSpaceBeforeAnnotationParamArray() {
    // Inspired by IDEA-24329
    getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    String text =
      """
        @SuppressWarnings( {"ALL"})
        public class FormattingTest {
        }""";

    // Don't expect the space to be 'ate'
    doTextTest(text, text);
  }

  public void testCommaInTypeArguments() {
    // Inspired by IDEA-31681
    getSettings().SPACE_AFTER_COMMA = false;

    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = false;
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

    doTextTest(initial, initial); // Don't expect the comma to be inserted

    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
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
    doTextTest(initial, formatted); // Expect the comma to be inserted between type arguments
  }

  public void testUnaryOperators() {
    // Inspired by IDEA-52127
    getSettings().SPACE_AROUND_UNARY_OPERATOR = false;

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

    doTextTest(initial, initial); // Don't expect spaces to be inserted after unary operators

    getSettings().SPACE_AROUND_UNARY_OPERATOR = true;
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
    doTextTest(initial, formatted); // Expect spaces to be inserted after unary operators
  }

  @SuppressWarnings("unused")
  public void _testJavadocMethodParams() {
    // Inspired by IDEA-42167
    // Disabled because the contents of the {@code tag} is not necessarily Java code and
    // therefore it's incorrect to modify it when formatting
    getSettings().SPACE_AFTER_COMMA = false;

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
    doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test,new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");

    // Expect space to be inserted between ',' and 'new'.
    getSettings().SPACE_AFTER_COMMA = true;
    doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test, new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");

    // Expect space to be inserted between 'test' and ','.
    getSettings().SPACE_BEFORE_COMMA = true;
    doTextTest(initial,
               """
                 public class FormattingTest {
                     /**
                      * This is a convenience method for {@code doTest(test , new Object[0]);}
                      */
                     void doTest() {
                     }
                 }""");
  }

  public void testSpaceWithArrayBrackets() {
    // Inspired by IDEA-58510
    getSettings().SPACE_WITHIN_BRACKETS = true;
    doMethodTest(
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

  public void testSpaceBeforeElse() {
    // Inspired by IDEA-58068
    getSettings().ELSE_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_ELSE_KEYWORD = false;
    doMethodTest(
      """
        if (true) {
        } else {
        }""",
      """
        if (true) {
        }else {
        }"""
    );

    getSettings().SPACE_BEFORE_ELSE_KEYWORD = true;
    doMethodTest(
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

  public void testSpaceBeforeWhile() {
    // Inspired by IDEA-58068
    getSettings().WHILE_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_WHILE_KEYWORD = false;
    doMethodTest(
      "do {\n" +
      "} while (true);",
      "do {\n" +
      "}while (true);"
    );

    getSettings().SPACE_BEFORE_WHILE_KEYWORD = true;
    doMethodTest(
      "do {\n" +
      "}while (true);",
      "do {\n" +
      "} while (true);"
    );
  }


  public void testSpaceBeforeCatch() {
    // Inspired by IDEA-58068
    getSettings().CATCH_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_CATCH_KEYWORD = false;
    doMethodTest(
      """
        try {
        } catch (Exception e) {
        }""",
      """
        try {
        }catch (Exception e) {
        }"""
    );

    getSettings().SPACE_BEFORE_CATCH_KEYWORD = true;
    doMethodTest(
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

  public void testSpaceBeforeFinally() {
    // Inspired by IDEA-58068
    getSettings().FINALLY_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = false;
    doMethodTest(
      """
        try {
        } finally {
        }""",
      """
        try {
        }finally {
        }"""
    );

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = true;
    doMethodTest(
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

  public void testEmptyIterationAtFor() {
    // Inspired by IDEA-58293

    getSettings().SPACE_AFTER_SEMICOLON = true;
    getSettings().SPACE_WITHIN_FOR_PARENTHESES = false;

    doMethodTest(
      "for (   ; ;         )",
      "for (; ; )"
    );
  }

  public void testSpacesInDisjunctiveType() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().CATCH_ON_NEW_LINE = false;

    getSettings().SPACE_AROUND_BITWISE_OPERATORS = true;
    doMethodTest("try { } catch (E1|E2 e) { }",
                 "try {} catch (E1 | E2 e) {}");

    getSettings().SPACE_AROUND_BITWISE_OPERATORS = false;
    doMethodTest("try { } catch (E1 | E2 e) { }",
                 "try {} catch (E1|E2 e) {}");
  }

  public void testSpacesInsideLambda() {
    getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    getSettings().SPACE_AROUND_LAMBDA_ARROW = true;

    doMethodTest("()->{}",
                 "() -> {}");

    getSettings().SPACE_AROUND_LAMBDA_ARROW = false;
    doMethodTest("() -> {}",
                 "()->{}");
  }

  public void testSpacesInsideMethodRef() {
    getSettings().SPACE_AROUND_METHOD_REF_DBL_COLON = true;

    //expr
    doMethodTest("Runnable r = this::foo",
                 "Runnable r = this :: foo");

    //class ref
    doMethodTest("Runnable r = Foo::foo",
                 "Runnable r = Foo :: foo");

    getSettings().SPACE_AROUND_METHOD_REF_DBL_COLON = false;
    doMethodTest("Runnable r = this::foo",
                 "Runnable r = this::foo");
  }

  public void testSpacesBeforeResourceList() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    getSettings().SPACE_BEFORE_TRY_PARENTHESES = true;
    getSettings().SPACE_BEFORE_TRY_LBRACE = true;
    doMethodTest("try(AutoCloseable r = null){ }",
                 "try (AutoCloseable r = null) {}");

    getSettings().SPACE_BEFORE_TRY_PARENTHESES = false;
    getSettings().SPACE_BEFORE_TRY_LBRACE = false;
    doMethodTest("try (AutoCloseable r = null) { }",
                 "try(AutoCloseable r = null){}");
  }

  public void testSpacesWithinResourceList() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = false;
    doMethodTest("try (  R r = null  ) { }",
                 "try (R r = null) {}");
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (  R r1 = null    ; R r2 = null; ) { }",
                 "try (R r1 = null;R r2 = null;) {}");

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = true;
    doMethodTest("try (R r = null) { }",
                 "try ( R r = null ) {}");
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try ( R r1 = null; R r2 = null; ) {}");
  }

  public void testSpacesBetweenResources() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_BEFORE_SEMICOLON = false;
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try (R r1 = null; R r2 = null;) {}");

    getSettings().SPACE_BEFORE_SEMICOLON = true;
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (R r1 = null;   R r2 = null;) { }",
                 "try (R r1 = null ;R r2 = null ;) {}");
  }

  public void testSpacesInResourceAssignment() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
    doMethodTest("try (R r=null) { }",
                 "try (R r = null) {}");

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = false;
    doMethodTest("try (R r =  null) { }",
                 "try (R r=null) {}");
  }

  public void testBetweenMethodCallArguments() {
    // Inspired by IDEA-71823
    getSettings().SPACE_AFTER_COMMA = false;

    doMethodTest(
      "foo(1, 2, 3);",
      "foo(1,2,3);"
    );
  }

  public void testBeforeAnonymousClassConstructor() {
    // Inspired by IDEA-72321.
    getSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;

    doMethodTest(
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

  public void testBeforeAnnotationArrayInitializer() {
    // Inspired by IDEA-72317
    getSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;
    getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    doClassTest(
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

  public void testBetweenParenthesesOfNoArgsMethod() {
    // Inspired by IDEA-74751
    getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;
    getSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = true;
    doClassTest(
      """
        void test() {
            foo();
        }""",
      """
        void test( ) {
            foo();
        }"""
    );

    getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    getSettings().SPACE_WITHIN_METHOD_PARENTHESES = false;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;
    doClassTest(
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

  public void testIncompleteCastExpression() {
    // Inspired by IDEA-75043.
    String text = """
      void test(int i) {
          (() i)
      }""";
    doClassTest(text, text);
  }

  public void testSpacesWithinAngleBrackets() {
    getJavaSettings().SPACES_WITHIN_ANGLE_BRACKETS = true;

    String beforeMethod = "static <      T             > void    fromArray(  T  [   ]   a ,   Collection<  T  >  c) {\n}";
    doClassTest(beforeMethod, "static < T > void fromArray(T[] a, Collection< T > c) {\n}");

    String beforeLocal = "Map<    String,   String     > map = new HashMap<   String,   String   >();";
    doMethodTest(beforeLocal, "Map< String, String > map = new HashMap< String, String >();");

    String beforeClass = "class  A <   U    > {\n}";
    doTextTest(beforeClass, "class A< U > {\n}");

    getJavaSettings().SPACES_WITHIN_ANGLE_BRACKETS = false;
    doMethodTest(beforeLocal, "Map<String, String> map = new HashMap<String, String>();");
    doClassTest(beforeMethod, "static <T> void fromArray(T[] a, Collection<T> c) {\n}");
    doTextTest(beforeClass, "class A<U> {\n}");
  }

  public void testSpaceAfterClosingAngleBracket_InTypeArgument() {
    String before = "Bar.<String, Integer>    mess(null);";

    getJavaSettings().SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = false;
    doMethodTest(before, "Bar.<String, Integer>mess(null);");

    getJavaSettings().SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = true;
    doMethodTest(before, "Bar.<String, Integer> mess(null);");
  }

  public void testSpaceBeforeOpeningAngleBracket_InTypeParameter() {
    String before = "class        A<T> {\n}";

    getJavaSettings().SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = false;
    doTextTest(before, "class A<T> {\n}");

    getJavaSettings().SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = true;
    doTextTest(before, "class A <T> {\n}");
  }

  public void testSpaceAroundTypeBounds() {
    String before = "public class     Foo<T extends Bar & Abba, U> {\n}";

    getJavaSettings().SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true;
    doTextTest(before, "public class Foo<T extends Bar & Abba, U> {\n}");

    getJavaSettings().SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = false;
    doTextTest(before, "public class Foo<T extends Bar&Abba, U> {\n}");
  }

  public void testInnerTypeAnnotations() {
    doTextTest(
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

  public void testClassObjectAccessExpression_BeforeDot() {
    String before = "Test       \n         .class";

    getSettings().KEEP_LINE_BREAKS = true;
    doMethodTest(before, "Test\n        .class");

    getSettings().KEEP_LINE_BREAKS = false;
    doMethodTest(before, "Test.class");
  }

  public void testClassObjectAccessExpression_AfterDot() {
    String before = "Test.      \n     class";

    getSettings().KEEP_LINE_BREAKS = true;
    doMethodTest(before, "Test.\n        class");

    getSettings().KEEP_LINE_BREAKS = false;
    doMethodTest(before, "Test.class");
  }

  public void testMultipleFieldDeclaration_InAnonymousClass() {
    doMethodTest(
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

  public void testCommentBetweenAnnotationAndModifierList() {
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;
    doClassTest("""
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

  public void testSpace_BeforeSemicolon_InsideFor() {
    getSettings().SPACE_BEFORE_SEMICOLON = true;
    doMethodTest(
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

  public void testSpace_BeforeSemicolon_InsideFor_IfSpacesWithinForIsOn() {
    getSettings().SPACE_WITHIN_FOR_PARENTHESES = true;
    doMethodTest(
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

  public void testSpaceBeforeTypeArgumentList() {
    getSettings().SPACE_BEFORE_TYPE_PARAMETER_LIST = true;
    doMethodTest(
      "Map<Int, String> map = new HashMap<Int, String>();",
      "Map <Int, String> map = new HashMap <Int, String>();"
    );
    doMethodTest(
      "Bar.<Int, String>call();",
      "Bar. <Int, String>call();"
    );
  }

  public void testKeepLineBreaksWorks_InsidePolyExpression() {
    getSettings().KEEP_LINE_BREAKS = false;
    doMethodTest(
      "int x = (1 + 2 + 3) *\n" +
      "        (1 + 2 + 2) * (1 + 2);",
      "int x = (1 + 2 + 3) * (1 + 2 + 2) * (1 + 2);"
    );
  }

  public void testKeepFinalOnLine() {
    doClassTest(
      "public    static void bar(@NonNls final String[] args) {\n" +
      "}",
      "public static void bar(@NonNls final String[] args) {\n" +
      "}");
  }

  public void testEnumAnnotations() {
    doTextTest("""
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

  public void testSpaceBeforeComma() {
    getSettings().SPACE_BEFORE_COMMA = true;
    doClassTest(
      """
        public void main(String[] args, String xxx) {
          foo(100, 200);
        }""",
      """
        public void main(String[] args , String xxx) {
            foo(100 , 200);
        }""");
  }

  public void testSpacingAroundVarKeyword() {
    doMethodTest("for (  var  path :  paths) ;", "for (var path : paths) ;");
    doMethodTest("try ( @A  var  r  =  open()) { }", "try (@A var r = open()) {\n}");
  }

  public void testSpacingBeforeColonInForeach() {
    getJavaSettings().SPACE_BEFORE_COLON_IN_FOREACH = false;
    doMethodTest("for (int i:arr) ;", "for (int i: arr) ;");
    getJavaSettings().SPACE_BEFORE_COLON_IN_FOREACH = true;
    doMethodTest("for (int i:arr) ;", "for (int i : arr) ;");
  }

  public void testOneLineEnumSpacing() {
    getJavaSettings().SPACE_INSIDE_ONE_LINE_ENUM_BRACES = true;
    doTextTest("enum E {E1, E2}",
               "enum E { E1, E2 }");
  }

  public void testSwitchLabelSpacing() {
    doMethodTest("switch (i) { case\n1\n:break;\ndefault\n:break; }",
                 """
                   switch (i) {
                       case 1:
                           break;
                       default:
                           break;
                   }""");
  }

  public void testSwitchLabeledRuleSpacing() {
    doMethodTest("switch (i) { case\n1\n->\nfoo();\ncase\n2->{bar()};\ndefault->throw new Exception(); }",
                 """
                   switch (i) {
                       case 1 -> foo();
                       case 2 -> {
                           bar()
                       };
                       default -> throw new Exception();
                   }""");
  }

  public void testMultiValueLabel() {
    doMethodTest("switch(i) { case 1,2,  3: break; }",
                 """
                   switch (i) {
                       case 1, 2, 3:
                           break;
                   }""");
  }

  public void testMultiValueLabeledRule() {
    doMethodTest("switch(i) { case 1,2,  3 -> foo(); }",
                 """
                   switch (i) {
                       case 1, 2, 3 -> foo();
                   }""");
  }

  public void testSwitchExpression() {
    doMethodTest("String s = switch\n(i   ){default -> null;}",
                 """
                   String s = switch (i) {
                       default -> null;
                   }""");
  }

  public void testBreakStatementSpacing() {
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    doMethodTest("""
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

  public void testYieldStatementSpacing() {
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    doMethodTest("""
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

  public void testSpaceAfterCommaInRecordHeader() {
    getSettings().SPACE_AFTER_COMMA = true;
    doMethodTest("record R(String s,int i){}",
                 "record R(String s, int i) {\n" +
                 "}");
  }

  public void testSpaceBeforeCommaInRecordHeader() {
    getSettings().SPACE_AFTER_COMMA = false;
    getSettings().SPACE_BEFORE_COMMA = true;
    doMethodTest("record R(String s,int i){}",
                 "record R(String s ,int i) {\n" +
                 "}");
  }

  public void testSpaceBetweenGenericsAndName() {
    doTextTest("record A(List<String> string){}",
                 "record A(List<String> string) {\n" +
                 "}");
  }

  public void testSpaceWithinRecordHeader() {
    getJavaSettings().SPACE_WITHIN_RECORD_HEADER = true;
    doTextTest("record A(String string){}",
               "record A( String string ) {\n" +
               "}");
  }

  public void testSpaceBetweenAnnotationAndType() {
    doTextTest("record A(@Foo()String string) {}",
               "record A(@Foo() String string) {\n" +
               "}");
  }

  public void testSpacesAroundRelationalOperators() {
    getSettings().SPACE_AROUND_RELATIONAL_OPERATORS = true;
    doMethodTest(
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
    getSettings().SPACE_AROUND_RELATIONAL_OPERATORS = false;
    doMethodTest(
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

  public void testIdea250968() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    doClassTest(
      "public Long getId (){return id;}",

      "public Long getId() { return id; }"
    );
  }

  public void testAndAndInGuardedPattern() {
    doClassTest(
      """
        void test(Object obj){
          switch(obj){
          case String s&&s.isEmpty()->System.out.println();
          }
        }
        """,

      """
        void test(Object obj) {
            switch (obj) {
                case String s && s.isEmpty() -> System.out.println();
            }
        }
        """);
  }

  public void testSnippet() {
    doTextTest(
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

  public void testLeftShiftExpressionSpacing() {
    getSettings().SPACE_AROUND_SHIFT_OPERATORS = false;
    doMethodTest(
      "int x = a << 2;",
      "int x = a<<2;");
  }

  public void testRightShiftExpressionSpacing() {
    getSettings().SPACE_AROUND_SHIFT_OPERATORS = false;
    doMethodTest(
      "int x = a >> 2;",
      "int x = a>>2;");
  }

  public void testNamedRecordPatternSpacing() {
    doMethodTest(
      "return o instanceof Rec(int i)r",
      "return o instanceof Rec(int i) r");
  }

  public void testDeconstructionPatternSpacing() {
    doMethodTest(
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

  public void testDeconstructionPatternSpacingBeforeComma() {
    getSettings().SPACE_BEFORE_COMMA = true;
    doMethodTest(
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

  public void testDeconstructionPatternSpacingWithin() {
    getJavaSettings().SPACE_WITHIN_DECONSTRUCTION_LIST = true;
    doMethodTest(
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

  public void testDeconstructionPatternSpacingBefore() {
    getJavaSettings().SPACE_BEFORE_DECONSTRUCTION_LIST = true;
    doMethodTest(
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

  public void testDeconstructionPatternNewLineAfterLpar() {
    getJavaSettings().NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN = true;
    getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doMethodTest(
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

  public void testDeconstructionPatternNewLineBeforeRpar() {
    getJavaSettings().RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN = true;
    getJavaSettings().DECONSTRUCTION_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doMethodTest(
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
}