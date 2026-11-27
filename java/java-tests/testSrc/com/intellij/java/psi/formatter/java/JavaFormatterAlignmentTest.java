// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import static com.intellij.formatting.FormatterTestUtils.Action.REFORMAT_WITH_CONTEXT;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * {@code Project Settings - Code Style - Alignment and Braces}).
 */
@TestApplication
public class JavaFormatterAlignmentTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testChainedMethodsAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-30369
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8;
    formatter.doTest();
  }

  @Test
  public void testMethodAndChainedField() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-79806

    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doMethodTest(
      "Holder.INSTANCE\n" +
      "                .foo();",
      "Holder.INSTANCE\n" +
      "        .foo();"
    );
  }

  @Test
  public void testDanglingDotInAlignedMethodCallChain() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doMethodTest(
      """
        foo.bar()
        .
        baz();""",
      """
        foo.bar()
           .
           baz();"""
    );
  }

  @Test
  public void testChainedMethodWithComments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doMethodTest("""
                   AAAAA.b()
                   .c() // comment after line
                   .d()
                   .e();""",

                 """
                   AAAAA.b()
                        .c() // comment after line
                        .d()
                        .e();""");
  }

  @Test
  public void testChainedMethodWithBlockComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doTextTest("""
                 class X {
                     public void test() {
                         AAAAAA.b()
                 .c()
                 .d()
                           /* simple block comment */
                 .e();
                     }
                 }""",
               """
                 class X {
                     public void test() {
                         AAAAAA.b()
                               .c()
                               .d()
                               /* simple block comment */
                               .e();
                     }
                 }""");
  }

  @Test
  public void testMultipleMethodAnnotationsCommentedInTheMiddle() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;

    // Inspired by IDEA-53942
    formatter.doTextTest(
      """
        public class Test {
                  @Override
        //       @XmlElement(name = "Document", required = true, type = DocumentType.class)
               @XmlTransient
          void foo() {
        }
        }""",

      """
        public class Test {

            @Override
        //       @XmlElement(name = "Document", required = true, type = DocumentType.class)
            @XmlTransient
            void foo() {
            }
        }"""
    );
  }

  @Test
  public void testTernaryOperator() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-13018
    formatter.getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;

    formatter.doMethodTest("int i = a ? x\n" + ": y;", "int i = a ? x\n" + "          : y;");
  }

  @Test
  public void testMethodCallArgumentsAndSmartTabs() throws IncorrectOperationException {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-20144.
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    formatter.doTextTest("""
                 class Foo {
                     void foo() {
                         bar(new Object[] {
                             "hello1",
                             "hello2", add("hello3",
                                            "world")
                 });    }}""", """
                 class Foo {
                 \tvoid foo() {
                 \t\tbar(new Object[]{
                 \t\t\t\t"hello1",
                 \t\t\t\t"hello2", add("hello3",
                 \t\t\t\t              "world")
                 \t\t});
                 \t}
                 }""");
  }

  @Test
  public void testArrayInitializer() throws IncorrectOperationException {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-16136
    formatter.getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    formatter.doTextTest(
      """
        @SuppressWarnings({"UseOfSystemOutOrSystemErr", "AssignmentToCollectionOrArrayFieldFromParameter", "ReturnOfCollectionOrArrayField"})
        public class Some {
        }""",
      """
        @SuppressWarnings({"UseOfSystemOutOrSystemErr",
                           "AssignmentToCollectionOrArrayFieldFromParameter",
                           "ReturnOfCollectionOrArrayField"})
        public class Some {
        }""");
  }

  @Test
  public void testMethodBrackets() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-53013
    formatter.getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    formatter.getSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS = true;
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    formatter.doClassTest(
      """
        public void foo(int i,
                          int j) {
        }

          public void bar() {
            foo(1,
                2);
          }""",

      """
        public void foo(int i,
                        int j
                       ) {
        }

        public void bar() {
            foo(1,
                2
               );
        }"""
    );

    // Inspired by IDEA-55306
    formatter.getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = false;
    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    String method =
      """
        executeCommand(new Command<Boolean>() {
            public Boolean run() throws ExecutionException {
                return doInterrupt();
            }
        });""";
    formatter.doMethodTest(method, method);
  }

  @Test
  public void testFieldInColumnsAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-55147
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.getSettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.getSettings().VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    formatter.doTextTest(
      """
        public class FormattingTest {

            int start = 1;
            double end = 2;

            int i2 = 1;
            double dd2,
                dd3 = 2;

            // asd
            char ccc3 = 'a';
            double ddd31, ddd32 = 1;

            private
            final String s4 = "";
            private
            transient int i4 = 1;

            private final String s5 = "xxx";
            private transient int iiii5 = 1;
            /*sdf*/
            @MyAnnotation(value = 1, text = 2) float f5 = 1;
        }""",

      """
        public class FormattingTest {

            int    start = 1;
            double end   = 2;

            int    i2   = 1;
            double dd2,
                    dd3 = 2;

            // asd
            char   ccc3         = 'a';
            double ddd31, ddd32 = 1;

            private
            final     String s4 = "";
            private
            transient int    i4 = 1;

            private final                      String s5    = "xxx";
            private transient                  int    iiii5 = 1;
            /*sdf*/
            @MyAnnotation(value = 1, text = 2) float  f5    = 1;
        }"""
    );
  }

  @Test
  public void testTabsAndFieldsInColumnsAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-56242
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.getIndentOptions().USE_TAB_CHARACTER = true;

    formatter.doTextTest(
      """
        public class Test {
        \tprivate Long field2 = null;
        \tprivate final Object field1 = null;
        \tprivate int i = 1;
        }""",

      """
        public class Test {
        \tprivate       Long   field2 = null;
        \tprivate final Object field1 = null;
        \tprivate       int    i      = 1;
        }"""
    );
  }

  @Test
  public void testDoNotAlignIfNotEnabled() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = false;
    formatter.doTextTest(
      """
        public class Test {
        private Long field2 = null;
        private final Object field1 = null;
        private int i = 1;
        }""",

      """
        public class Test {
            private Long field2 = null;
            private final Object field1 = null;
            private int i = 1;
        }"""
    );
  }

  @Test
  public void testAnnotatedAndNonAnnotatedFieldsInColumnsAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-60237

    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.doTextTest(
      """
        public class Test {
            @Id
            private final String name;
            @Column(length = 2 * 1024 * 1024 /* 2 MB */)
            private String value;
            private boolean required;
            private String unsetValue;
        }""",

      """
        public class Test {
            @Id
            private final String  name;
            @Column(length = 2 * 1024 * 1024 /* 2 MB */)
            private       String  value;
            private       boolean required;
            private       String  unsetValue;
        }"""
    );
  }

  @Test
  public void testAlignThrowsKeyword() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-63820

    formatter.getSettings().ALIGN_THROWS_KEYWORD = true;
    formatter.doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      """
        public void test()
        throws Exception {
        }"""
    );

    formatter.getSettings().ALIGN_THROWS_KEYWORD = false;
    formatter.doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      """
        public void test()
                throws Exception {
        }"""
    );
  }

  @Test
  public void testAlignResourceList() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    formatter.getSettings().ALIGN_MULTILINE_RESOURCES = true;
    formatter.doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "     MyResource r2 = null) {}");

    formatter.getSettings().ALIGN_MULTILINE_RESOURCES = false;
    formatter.doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "        MyResource r2 = null) {}");
  }


  @Test
  public void testChainedMethodCallsAfterFieldsChain_WithAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      """
        a.current.current.current.getThis()
                                 .getThis()
                                 .getThis();"""
    );

    formatter.doMethodTest(
      "a.current.current.current.getThis().getThis().getThis().current.getThis().getThis().getThis().getThis();",

      """
        a.current.current.current.getThis()
                                 .getThis()
                                 .getThis().current.getThis()
                                                   .getThis()
                                                   .getThis()
                                                   .getThis();"""
    );


    String onlyMethodCalls = "getThis().getThis().getThis();";
    String formatedMethodCalls = "getThis().getThis()\n" +
                                 "         .getThis();";

    formatter.doMethodTest(onlyMethodCalls, formatedMethodCalls);
  }

  @Test
  public void testChainedMethodCallsAfterFieldsChain_WithoutAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      """
        a.current.current.current.getThis()
                .getThis()
                .getThis();"""
    );
  }

  @Test
  public void testChainedMethodCalls_WithChopDownIfLongOption() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM; // it's equal to "Chop down if long"
    formatter.getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis().getThis();";
    formatter.doMethodTest(
      before,
      """
        a.current.current.getThis()
                         .getThis()
                         .getThis()
                         .getThis()
                         .getThis();"""
    );

    formatter.getSettings().RIGHT_MARGIN = 80;
    formatter.doMethodTest(before, before);
  }

  @Test
  public void testChainedMethodCalls_WithWrapIfNeededOption() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis();";

    formatter.doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "        .getThis().getThis();"
    );

    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;

    formatter.doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "                 .getThis().getThis();"
    );

    formatter.getSettings().RIGHT_MARGIN = 75;
    formatter.doMethodTest(before, before);
  }

  @Test
  public void testAlignMethodCalls_PassedAsParameters_InMethodCall() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    formatter.doMethodTest(
      """
        test(call1(),
                     call2(),
                                call3());
        """,
      """
        test(call1(),
             call2(),
             call3());
        """
    );
  }

  @Test
  public void testLocalVariablesAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      "int a = 2;\n" +
      "String myString = \"my string\"",
      "int    a        = 2;\n" +
      "String myString = \"my string\""
    );
  }

  @Test
  public void testAlignOnlyDeclarationStatements() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
              String s;
           int a = 2;
        s = "abs";
        long stamp = 12;""",
      """
        String s;
        int    a = 2;
        s = "abs";
        long stamp = 12;"""
    );
  }

  @Test
  public void testAlignFieldDeclarations() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.doClassTest(
      """
        char a = '2';
        int aaaaa = 3;
        String b;""",
      """
        char   a     = '2';
        int    aaaaa = 3;
        String b;""");
  }

  @Test
  public void testAlignFieldsInAnonymousClass() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.doMethodTest(
      """
        (new Thread() {
                    private int a = 1;
                    private String abc = "alphabet";
                
                    @Override
                    public void run() {
                        super.run();
                    }
                }).start();""",
      """
        (new Thread() {
            private int    a   = 1;
            private String abc = "alphabet";
        
            @Override
            public void run() {
                super.run();
            }
        }).start();""");
  }

  @Test
  public void testAlignVarDeclarations() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
        char a = '2';
        int aaaaa = 3;
        String b;""",
      """
        char   a     = '2';
        int    aaaaa = 3;
        String b;""");
  }

  @Test
  public void testDoNotAlignWhenBlankLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
        int a = 2;

        String myString = "my string\"""",
      """
        int a = 2;

        String myString = "my string\""""
    );
  }

  @Test
  public void testDoNotAlignWhenGroupInterrupted() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
        int a = 2;
        System.out.println("hi!")
        String myString = "my string\"""",
      """
        int a = 2;
        System.out.println("hi!")
        String myString = "my string\""""
    );
  }

  @Test
  public void testDoNotAlignMultiDeclarations() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      "  int a, b = 2;\n" +
      "String myString = \"my string\"",
      "int    a, b     = 2;\n" +
      "String myString = \"my string\""
    );
  }

  @Test
  public void testDoNotAlignMultilineParams() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    formatter.doMethodTest(
      """
        int a = 12;
          Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };""",

      """
        int a = 12;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };"""
    );

    formatter.doMethodTest(
      """
           Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };
        int c = 12;""",

      """
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };
        int c = 12;"""
    );

    formatter.doMethodTest(
      """
            int ac = 99;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };
        int c = 12;""",

      """
        int ac = 99;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("AAA!");
            }
        };
        int c = 12;"""
    );
  }

  @Test
  public void testDoNotAlign_IfFirstMultiline() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    formatter.doMethodTest(
      """
        int
               i = 0;
        int[] a = new int[]{1, 2, 0x0052, 0x0053, 0x0054};
        int var1 = 1;
        int var2 = 2;""",

      """
        int
                i = 0;
        int[] a    = new int[]{1, 2, 0x0052, 0x0053, 0x0054};
        int   var1 = 1;
        int   var2 = 2;"""
    );
  }

  @Test
  public void testAlign_InMethod() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doClassTest(
      """
        public void run() {

                        int a = 2;
                    String superString = "";

             test(call1(), call2(), call3());
            }""",

      """
        public void run() {

            int    a           = 2;
            String superString = "";

            test(call1(), call2(), call3());
        }"""
    );

    formatter.doClassTest(
      """
        public void run() {

                test(call1(), call2(), call3());

                int a = 2;
                     String superString = "";
        }""",
      """
        public void run() {

            test(call1(), call2(), call3());

            int    a           = 2;
            String superString = "";
        }""");
  }

  @Test
  public void test_Shift_All_AlignedParameters() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.setLineRange(new TextRange(2, 2));
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    formatter.doTextTest(
      REFORMAT_WITH_CONTEXT,
      """
        public class Test {

            public void fooooo(String foo,
                            String booo,
                            String kakadoo) {

            }

        }""",

      """
        public class Test {

            public void fooooo(String foo,
                               String booo,
                               String kakadoo) {

            }

        }"""
    );
  }

  @Test
  public void test_Align_UnselectedField_IfNeeded() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.setLineRange(new TextRange(2, 2));
    formatter.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.doTextTest(
      REFORMAT_WITH_CONTEXT,
      """
        public class Test {
            public int    i = 1;
            public String iiiiiiiiii = 2;
        }""",
      """
        public class Test {
            public int    i          = 1;
            public String iiiiiiiiii = 2;
        }"""
    );
  }

  @Test
  public void test_Align_UnselectedVariable_IfNeeded() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.setLineRange(new TextRange(3, 3));
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doTextTest(
      REFORMAT_WITH_CONTEXT,
      """
        public class Test {
            public void test() {
                int s = 2;
                String sssss = 3;
            }
        }""",
      """
        public class Test {
            public void test() {
                int    s     = 2;
                String sssss = 3;
            }
        }"""
    );
  }

  @Test
  public void test_Align_ConsecutiveVars_InsideIfBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
        if (a > 2) {
        int a=2;
        String name="Yarik";
        }
        """,
      """
        if (a > 2) {
            int    a    = 2;
            String name = "Yarik";
        }
        """
    );
  }

  @Test
  public void test_Align_ConsecutiveVars_InsideForBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
            for (int i = 0; i < 10; i++) {
              int a=2;
              String name="Xa";
            }
        """,
      """
        for (int i = 0; i < 10; i++) {
            int    a    = 2;
            String name = "Xa";
        }
        """
    );
  }

  @Test
  public void test_Align_ConsecutiveVars_InsideTryBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
            try {
              int x = getX();
              String name = "Ha";
            }
            catch (IOException exception) {
              int y = 12;
              String test = "Test";
            }
            finally {
              int z = 12;
              String zzzz = "pnmhd";
            }
        """,
      """
        try {
            int    x    = getX();
            String name = "Ha";
        } catch (IOException exception) {
            int    y    = 12;
            String test = "Test";
        } finally {
            int    z    = 12;
            String zzzz = "pnmhd";
        }
        """
    );
  }

  @Test
  public void test_Align_ConsecutiveVars_InsideCodeBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doMethodTest(
      """
            System.out.println("AAAA");
            int a = 2;
           \s
            {
              int x=2;
              String name=3;
            }
        """,
      """
        System.out.println("AAAA");
        int a = 2;

        {
            int    x    = 2;
            String name = 3;
        }
        """
    );
  }

  @Test
  public void test_AlignComments_BetweenChainedMethodCalls() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doMethodTest(
      """
        ActionBarPullToRefresh.from(getActivity())
                // Mark the ListView as pullable
                .theseChildrenArePullable(eventsListView)
                        // Set the OnRefreshListener
                .listener(this)
                        // Use the AbsListView delegate for StickyListHeadersListView
                .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())
                        // Finally commit the setup to our PullToRefreshLayout
                .setup(mPullToRefreshLayout);""",
      """
        ActionBarPullToRefresh.from(getActivity())
                              // Mark the ListView as pullable
                              .theseChildrenArePullable(eventsListView)
                              // Set the OnRefreshListener
                              .listener(this)
                              // Use the AbsListView delegate for StickyListHeadersListView
                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())
                              // Finally commit the setup to our PullToRefreshLayout
                              .setup(mPullToRefreshLayout);"""
    );
  }

  @Test
  public void test_AlignComments_2() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.doClassTest(
      """
        public String returnWithBuilder2() {
            return MoreObjects
                .toStringHelper(this)
                .add("value", value)
                           // comment
                .toString();
          }""",
      """
        public String returnWithBuilder2() {
            return MoreObjects
                    .toStringHelper(this)
                    .add("value", value)
                    // comment
                    .toString();
        }"""
    );
  }

  @Test
  public void test_AlignSubsequentOneLineMethods() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.getSettings().ALIGN_SUBSEQUENT_SIMPLE_METHODS = true;
    formatter.doTextTest(
      """
        public class Test {

            public void testSuperDuperLongName() { System.out.println("AAA"); }

            public void testCounterMounter() { System.out.println("XXXX"); }

        }""",
      """
        public class Test {

            public void testSuperDuperLongName() { System.out.println("AAA"); }

            public void testCounterMounter()     { System.out.println("XXXX"); }

        }"""
    );
  }

  @Test
  public void test_alignAssignments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    formatter.doTextTest(
      """
        public class Test {
          void foo(int a, int xyz) {
            a = 9999;
            xyz = 1;
          }
        }""",
      """
        public class Test {
            void foo(int a, int xyz) {
                a   = 9999;
                xyz = 1;
            }
        }"""
    );
  }

  @Test
  public void test_alignMultilineAssignments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    formatter.getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    formatter.doTextTest(
      """
        public class Test {
          void foo(int a, int xyz) {
            a = 9999;
            xyz = a =\s
            a = 12;
          }
        }""",
      """
        public class Test {
            void foo(int a, int xyz) {
                a   = 9999;
                xyz = a =
                a   = 12;
            }
        }"""
    );
  }


  @Test
  public void test_alignMultilineAssignmentsMixedWithDeclaration() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    formatter.getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.doTextTest(
      """
        public class Test {
          void foo(int a, int xyz, int bc) {
            bc = 9999;
            a = 9999;
            int basdf = 1234;
            int as = 3;
            xyz = a =\s
            a = 12;
          }
        }""",
      """
        public class Test {
            void foo(int a, int xyz, int bc) {
                bc = 9999;
                a  = 9999;
                int basdf = 1234;
                int as    = 3;
                xyz = a =
                a   = 12;
            }
        }"""
    );
  }

  @Test
  public void test_alignAssignmentsFields() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    formatter.doTextTest(
      """
        public class Test {
          void foo(A a, int xyz) {
            a.bar = 9999;
            xyz = 1;
          }
        }""",
      """
        public class Test {
            void foo(A a, int xyz) {
                a.bar = 9999;
                xyz   = 1;
            }
        }"""
    );
  }

  @Test
  public void test_alignMultilineTextBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true;
    formatter.doTextTest(
      """
        public class Test {
            void foo() {
                String block = ""\"
          text
          block
         ""\";
            }
        }""",
      """
        public class Test {
            void foo() {
                String block = ""\"
                                text
                                block
                               ""\";
            }
        }"""
    );
  }

  @Test
  public void testAlign() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().MULTI_CATCH_TYPES_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doTextTest(
      """
        public class Main {

            public static void main(String[] args) {
              try {
               \s
              } catch (FooException | BarException | FooBarException | FooBarFooException | BarBarFooException | BarFooFooException e) {
               \s
              }
            }
        }""",

      """
        public class Main {

            public static void main(String[] args) {
                try {

                } catch (FooException |
                         BarException |
                         FooBarException |
                         FooBarFooException |
                         BarBarFooException |
                         BarFooFooException e) {

                }
            }
        }"""
    );
  }

  @SuppressWarnings("unused")
  public void _testIdea199677() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    formatter.getSettings().CALL_PARAMETERS_WRAP = 2;
    formatter.getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    formatter.doTextTest(
      """
        public class Main {

            public static void main(String[] args) {
                int one               = 1;
                int a_million_dollars = 1000000;

                doSomething(one, a_million_dollars);
            }

            private static void doSomething(int one, int two) {
            }

        }""",

      """
        public class Main {

            public static void main(String[] args) {
                int one               = 1;
                int a_million_dollars = 1000000;

                doSomething(
                        one,
                        a_million_dollars
                );
            }

            private static void doSomething(int one, int two) {
            }

        }"""
    );
  }

  @Test
  public void testChainedMethodCallsInTernaryExpression_AlignMethodChain() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doMethodTest(
      """
        boolean unknown = true;
        final var someVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongVariableName = unknown ? "string".chars().map(integer -> integer + 1).map(integer -> integer - 1).sorted().peek(System.out::println).count() : 0;""",
      """
        boolean unknown = true;
        final var someVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongVariableName = unknown ? "string".chars()
                                                                                                               .map(integer -> integer + 1)
                                                                                                               .map(integer -> integer - 1)
                                                                                                               .sorted()
                                                                                                               .peek(System.out::println)
                                                                                                               .count() : 0;"""
    );
  }

  @Test
  public void testChainedMethodCallsInTernaryExpression_NoAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doMethodTest(
      """
        boolean unknown = true;
        final var someVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongVariableName = unknown ? "string".chars().map(integer -> integer + 1).map(integer -> integer - 1).sorted().peek(System.out::println).count() : 0;""",
      """
        boolean unknown = true;
        final var someVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongVariableName = unknown ? "string".chars()
                .map(integer -> integer + 1)
                .map(integer -> integer - 1)
                .sorted()
                .peek(System.out::println)
                .count() : 0;"""
    );
  }
}