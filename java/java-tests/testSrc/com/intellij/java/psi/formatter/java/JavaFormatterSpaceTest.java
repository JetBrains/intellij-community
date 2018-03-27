// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;

/**
 * Is intended to hold specific java formatting tests for 'spacing' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 29, 2010
 */
public class JavaFormatterSpaceTest extends AbstractJavaFormatterTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(LightPlatformTestCase.getProject()).setLanguageLevel(LanguageLevel.JDK_X);
  }

  public void testSpacingBetweenTypeParameters() {
    // Implied by IDEADEV-3666
    getSettings().SPACE_AFTER_COMMA = true;

    doTextTest("class Foo {\n" + "Map<String,String> map() {}\n" + "}",
               "class Foo {\n" + "    Map<String, String> map() {\n" + "    }\n" + "}");
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testDoNotPlaceStatementsOnOneLineIfFirstEndsWithSingleLineComment() {
    getSettings().KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;
    getSettings().KEEP_LINE_BREAKS = false;
    String before = "public class Reproduce {\n" +
                    "    public void start() {\n" +
                    "        if (true)\n" +
                    "            return; // comment\n" +
                    "        final int count = 5;\n" +
                    "        for (int i = 0; i < count; i++) {\n" +
                    "            System.out.println(\"AAA!\");\n" +
                    "            System.out.println(\"BBB!\"); // ololol\n" +
                    "            System.out.println(\"asda\"); /* ololo */\n" +
                    "            System.out.println(\"booo\");\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";
    String after = "public class Reproduce {\n" +
                   "    public void start() {\n" +
                   "        if (true) return; // comment\n" +
                   "        final int count = 5; for (int i = 0; i < count; i++) {\n" +
                   "            System.out.println(\"AAA!\"); System.out.println(\"BBB!\"); // ololol\n" +
                   "            System.out.println(\"asda\"); /* ololo */ System.out.println(\"booo\");\n" +
                   "        }\n" +
                   "    }\n" +
                   "}";
    doTextTest(before, after);
  }

  public void testSpaceBeforeAnnotationParamArray() {
    // Inspired by IDEA-24329
    getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    String text =
      "@SuppressWarnings( {\"ALL\"})\n" +
      "public class FormattingTest {\n" +
      "}";

    // Don't expect the space to be 'ate'
    doTextTest(text, text);
  }

  public void testCommaInTypeArguments() {
    // Inspired by IDEA-31681
    getSettings().SPACE_AFTER_COMMA = false;

    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = false;
    String initial =
      "interface TestInterface<A,B> {\n" +
      "\n" +
      "    <X,Y> void foo(X x,Y y);\n" +
      "}\n" +
      "\n" +
      "public class FormattingTest implements TestInterface<String,Integer> {\n" +
      "\n" +
      "    public <X,Y> void foo(X x,Y y) {\n" +
      "        Map<String,Integer> map = new HashMap<String,Integer>();\n" +
      "    }\n" +
      "}";

    doTextTest(initial, initial); // Don't expect the comma to be inserted

    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
    String formatted =
      "interface TestInterface<A,B> {\n" +
      "\n" +
      "    <X,Y> void foo(X x,Y y);\n" +
      "}\n" +
      "\n" +
      "public class FormattingTest implements TestInterface<String, Integer> {\n" +
      "\n" +
      "    public <X,Y> void foo(X x,Y y) {\n" +
      "        Map<String, Integer> map = new HashMap<String, Integer>();\n" +
      "    }\n" +
      "}";
    doTextTest(initial, formatted); // Expect the comma to be inserted between type arguments
  }

  public void testUnaryOperators() {
    // Inspired by IDEA-52127
    getSettings().SPACE_AROUND_UNARY_OPERATOR = false;

    String initial =
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        int i = 1;\n" +
      "        System.out.println(-i);\n" +
      "        System.out.println(+i);\n" +
      "        System.out.println(++i);\n" +
      "        System.out.println(i++);\n" +
      "        System.out.println(--i);\n" +
      "        System.out.println(i--);\n" +
      "        boolean b = true;\n" +
      "        System.out.println(!b);\n" +
      "    }\n" +
      "}";

    doTextTest(initial, initial); // Don't expect spaces to be inserted after unary operators

    getSettings().SPACE_AROUND_UNARY_OPERATOR = true;
    String formatted =
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        int i = 1;\n" +
      "        System.out.println(- i);\n" +
      "        System.out.println(+ i);\n" +
      "        System.out.println(++ i);\n" +
      "        System.out.println(i++);\n" +
      "        System.out.println(-- i);\n" +
      "        System.out.println(i--);\n" +
      "        boolean b = true;\n" +
      "        System.out.println(! b);\n" +
      "    }\n" +
      "}";
    doTextTest(initial, formatted); // Expect spaces to be inserted after unary operators
  }

  @SuppressWarnings("unused")
  public void _testJavadocMethodParams() {
    // Inspired by IDEA-42167
    // Disabled because the contents of the {@code tag} is not necessarily Java code and
    // therefore it's incorrect to modify it when formatting
    getSettings().SPACE_AFTER_COMMA = false;

    String initial =
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test,   new      Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}";

    // Expect single space to left between 'new' and Object[0].
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test,new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");

    // Expect space to be inserted between ',' and 'new'.
    getSettings().SPACE_AFTER_COMMA = true;
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test, new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");

    // Expect space to be inserted between 'test' and ','.
    getSettings().SPACE_BEFORE_COMMA = true;
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test , new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");
  }

  public void testSpaceWithArrayBrackets() {
    // Inspired by IDEA-58510
    getSettings().SPACE_WITHIN_BRACKETS = true;
    doMethodTest(
      "int[] i = new int[1]\n" +
      "i[0] = 1;\n" +
      "int[] i2 = new int[]{1}",
      "int[] i = new int[ 1 ]\n" +
      "i[ 0 ] = 1;\n" +
      "int[] i2 = new int[]{1}"
    );
  }

  public void testSpaceBeforeElse() {
    // Inspired by IDEA-58068
    getSettings().ELSE_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_ELSE_KEYWORD = false;
    doMethodTest(
      "if (true) {\n" +
      "} else {\n" +
      "}",
      "if (true) {\n" +
      "}else {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_ELSE_KEYWORD = true;
    doMethodTest(
      "if (true) {\n" +
      "}else {\n" +
      "}",
      "if (true) {\n" +
      "} else {\n" +
      "}"
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
      "try {\n" +
      "} catch (Exception e) {\n" +
      "}",
      "try {\n" +
      "}catch (Exception e) {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_CATCH_KEYWORD = true;
    doMethodTest(
      "try {\n" +
      "}catch (Exception e) {\n" +
      "}",
      "try {\n" +
      "} catch (Exception e) {\n" +
      "}"
    );
  }

  public void testSpaceBeforeFinally() {
    // Inspired by IDEA-58068
    getSettings().FINALLY_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = false;
    doMethodTest(
      "try {\n" +
      "} finally {\n" +
      "}",
      "try {\n" +
      "}finally {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = true;
    doMethodTest(
      "try {\n" +
      "}finally {\n" +
      "}",
      "try {\n" +
      "} finally {\n" +
      "}"
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
                 "try { } catch (E1 | E2 e) { }");

    getSettings().SPACE_AROUND_BITWISE_OPERATORS = false;
    doMethodTest("try { } catch (E1 | E2 e) { }",
                 "try { } catch (E1|E2 e) { }");
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
                 "try (AutoCloseable r = null) { }");

    getSettings().SPACE_BEFORE_TRY_PARENTHESES = false;
    getSettings().SPACE_BEFORE_TRY_LBRACE = false;
    doMethodTest("try (AutoCloseable r = null) { }",
                 "try(AutoCloseable r = null){ }");
  }

  public void testSpacesWithinResourceList() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = false;
    doMethodTest("try (  R r = null  ) { }",
                 "try (R r = null) { }");
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (  R r1 = null    ; R r2 = null; ) { }",
                 "try (R r1 = null;R r2 = null;) { }");

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = true;
    doMethodTest("try (R r = null) { }",
                 "try ( R r = null ) { }");
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try ( R r1 = null; R r2 = null; ) { }");
  }

  public void testSpacesBetweenResources() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_BEFORE_SEMICOLON = false;
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try (R r1 = null; R r2 = null;) { }");

    getSettings().SPACE_BEFORE_SEMICOLON = true;
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (R r1 = null;   R r2 = null;) { }",
                 "try (R r1 = null ;R r2 = null ;) { }");
  }

  public void testSpacesInResourceAssignment() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
    doMethodTest("try (R r=null) { }",
                 "try (R r = null) { }");

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = false;
    doMethodTest("try (R r =  null) { }",
                 "try (R r=null) { }");
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
      "actions.add(new Action(this) {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "});",
      "actions.add (new Action (this) {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "});"
    );
  }

  public void testBeforeAnnotationArrayInitializer() {
    // Inspired by IDEA-72317
    getSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;
    getSettings().SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = true;

    doClassTest(
      "@SuppressWarnings({\"HardCodedStringLiteral\"})\n" +
      "void test() {\n" +
      "    int[] data = new int[] {1, 2, 3};\n" +
      "}",
      "@SuppressWarnings( {\"HardCodedStringLiteral\"})\n" +
      "void test() {\n" +
      "    int[] data = new int[]{1, 2, 3};\n" +
      "}"
    );
  }

  public void testBetweenParenthesesOfNoArgsMethod() {
    // Inspired by IDEA-74751
    getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;
    getSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = true;
    doClassTest(
      "void test() {\n" +
      "    foo();\n" +
      "}",
      "void test( ) {\n" +
      "    foo();\n" +
      "}"
    );

    getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    getSettings().SPACE_WITHIN_METHOD_PARENTHESES = false;
    getSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;
    doClassTest(
      "void test() {\n" +
      "    foo();\n" +
      "}",
      "void test() {\n" +
      "    foo( );\n" +
      "}"
    );
  }

  public void testIncompleteCastExpression() {
    // Inspired by IDEA-75043.
    String text = "void test(int i) {\n" +
                  "    (() i)\n" +
                  "}";
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
      "class C<@TA(1)T> {\n" +
      "    L<@TA(2)A> f = (@TA(3)  A) new @TA(4)  A() {\n" +
      "        void m(@TA(6) int  @TA(7)[] p) {\n" +
      "        }\n" +
      "    };\n" +
      "}",

      "class C<@TA(1) T> {\n" +
      "    L<@TA(2) A> f = (@TA(3) A) new @TA(4) A() {\n" +
      "        void m(@TA(6) int @TA(7) [] p) {\n" +
      "        }\n" +
      "    };\n" +
      "}"
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
      "new Object() {\n" +
      "boolean one, two;\n" +
      "};",
      "new Object() {\n" +
      "    boolean one, two;\n" +
      "};"
    );
  }

  public void testCommentBetweenAnnotationAndModifierList() {
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;
    doClassTest("@Override\n" +
                "//FIX me this stupid stuff\n" +
                "public void run() {\n" +
                "        int a = 2;\n" +
                "}",

                "@Override\n" +
                "//FIX me this stupid stuff\n" +
                "public void run() {\n" +
                "    int a = 2;\n" +
                "}");
  }

  public void testSpace_BeforeSemicolon_InsideFor() {
    getSettings().SPACE_BEFORE_SEMICOLON = true;
    doMethodTest(
      "int i = 0;\n" +
      "for (; i < 10 ; i++) {\n" +
      "}\n",
      "int i = 0;\n" +
      "for ( ; i < 10 ; i++) {\n" +
      "}\n"
    );
  }

  public void testSpace_BeforeSemicolon_InsideFor_IfSpacesWithinForIsOn() {
    getSettings().SPACE_WITHIN_FOR_PARENTHESES = true;
    doMethodTest(
      "int i = 0;\n" +
      "for (; i < 10 ; i++) {\n" +
      "}\n",
      "int i = 0;\n" +
      "for ( ; i < 10; i++ ) {\n" +
      "}\n"
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
    doTextTest("enum SampleEnum {\n" +
               "    @Annotation(\"1\")ONE,\n" +
               "    @Annotation(\"2\")TWO,\n" +
               "    @Annotation THREE\n" +
               "}",
               "enum SampleEnum {\n" +
               "    @Annotation(\"1\") ONE,\n" +
               "    @Annotation(\"2\") TWO,\n" +
               "    @Annotation THREE\n" +
               "}");
  }

  public void testSpaceBeforeComma() {
    getSettings().SPACE_BEFORE_COMMA = true;
    doClassTest(
      "public void main(String[] args, String xxx) {\n" +
      "  foo(100, 200);\n" +
      "}",
      "public void main(String[] args , String xxx) {\n" +
      "    foo(100 , 200);\n" +
      "}");
  }

  public void testSpacingAroundVarKeyword() {
    doMethodTest("for (  var  path  :  paths) ;", "for (var path : paths) ;");
    doMethodTest("try ( @A  var  r  =  open()) { }", "try (@A var r = open()) {\n}");
  }
}