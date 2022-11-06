// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

import static com.intellij.formatting.FormatterTestUtils.Action.REFORMAT_WITH_CONTEXT;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * {@code Project Settings - Code Style - Alignment and Braces}).
 *
 * @author Denis Zhdanov
 */
public class JavaFormatterAlignmentTest extends AbstractJavaFormatterTest {

  public void testChainedMethodsAlignment() {
    // Inspired by IDEA-30369
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testMethodAndChainedField() {
    // Inspired by IDEA-79806

    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest(
      "Holder.INSTANCE\n" +
      "                .foo();",
      "Holder.INSTANCE\n" +
      "        .foo();"
    );
  }

  public void testChainedMethodWithComments() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest("""
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

  public void testChainedMethodWithBlockComment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTextTest("""
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

  public void testMultipleMethodAnnotationsCommentedInTheMiddle() {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;

    // Inspired by IDEA-53942
    doTextTest(
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

  public void testTernaryOperator() {
    // Inspired by IDEADEV-13018
    getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doMethodTest("int i = a ? x\n" + ": y;", "int i = a ? x\n" + "          : y;");
  }

  public void testMethodCallArgumentsAndSmartTabs() throws IncorrectOperationException {
    // Inspired by IDEADEV-20144.
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    doTextTest("""
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

  public void testArrayInitializer() throws IncorrectOperationException {
    // Inspired by IDEADEV-16136
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest(
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

  public void testMethodBrackets() {
    // Inspired by IDEA-53013
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    getSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
    getSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doClassTest(
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
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = false;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    String method =
      """
        executeCommand(new Command<Boolean>() {
            public Boolean run() throws ExecutionException {
                return doInterrupt();
            }
        });""";
    doMethodTest(method, method);
  }

  public void testFieldInColumnsAlignment() {
    // Inspired by IDEA-55147
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    getSettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTextTest(
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

  public void testTabsAndFieldsInColumnsAlignment() {
    // Inspired by IDEA-56242
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    getIndentOptions().USE_TAB_CHARACTER = true;

    doTextTest(
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

  public void testDoNotAlignIfNotEnabled() {
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = false;
    doTextTest(
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

  public void testAnnotatedAndNonAnnotatedFieldsInColumnsAlignment() {
    // Inspired by IDEA-60237

    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest(
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

  public void testAlignThrowsKeyword() {
    // Inspired by IDEA-63820

    getSettings().ALIGN_THROWS_KEYWORD = true;
    doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      """
        public void test()
        throws Exception {
        }"""
    );

    getSettings().ALIGN_THROWS_KEYWORD = false;
    doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      """
        public void test()
                throws Exception {
        }"""
    );
  }

  public void testAlignResourceList() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().ALIGN_MULTILINE_RESOURCES = true;
    doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "     MyResource r2 = null) {}");

    getSettings().ALIGN_MULTILINE_RESOURCES = false;
    doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "        MyResource r2 = null) {}");
  }


  public void testChainedMethodCallsAfterFieldsChain_WithAlignment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      """
        a.current.current.current.getThis()
                                 .getThis()
                                 .getThis();"""
    );

    doMethodTest(
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

    doMethodTest(onlyMethodCalls, formatedMethodCalls);
  }

  public void testChainedMethodCallsAfterFieldsChain_WithoutAlignment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      """
        a.current.current.current.getThis()
                .getThis()
                .getThis();"""
    );
  }

  public void testChainedMethodCalls_WithChopDownIfLongOption() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM; // it's equal to "Chop down if long"
    getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis().getThis();";
    doMethodTest(
      before,
      """
        a.current.current.getThis()
                         .getThis()
                         .getThis()
                         .getThis()
                         .getThis();"""
    );

    getSettings().RIGHT_MARGIN = 80;
    doMethodTest(before, before);
  }

  public void testChainedMethodCalls_WithWrapIfNeededOption() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis();";

    doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "        .getThis().getThis();"
    );

    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;

    doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "                 .getThis().getThis();"
    );

    getSettings().RIGHT_MARGIN = 75;
    doMethodTest(before, before);
  }

  public void testAlignMethodCalls_PassedAsParameters_InMethodCall() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doMethodTest(
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

  public void testLocalVariablesAlignment() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "int a = 2;\n" +
      "String myString = \"my string\"",
      "int    a        = 2;\n" +
      "String myString = \"my string\""
    );
  }

  public void testAlignOnlyDeclarationStatements() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void testAlignFieldDeclarations() {
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doClassTest(
      """
        char a = '2';
        int aaaaa = 3;
        String b;""",
      """
        char   a     = '2';
        int    aaaaa = 3;
        String b;""");
  }

  public void testAlignVarDeclarations() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      """
        char a = '2';
        int aaaaa = 3;
        String b;""",
      """
        char   a     = '2';
        int    aaaaa = 3;
        String b;""");
  }

  public void testDoNotAlignWhenBlankLine() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      """
        int a = 2;

        String myString = "my string\"""",
      """
        int a = 2;

        String myString = "my string\""""
    );
  }

  public void testDoNotAlignWhenGroupInterrupted() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void testDoNotAlignMultiDeclarations() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "  int a, b = 2;\n" +
      "String myString = \"my string\"",
      "int    a, b     = 2;\n" +
      "String myString = \"my string\""
    );
  }

  public void testDoNotAlignMultilineParams() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    doMethodTest(
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

    doMethodTest(
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

    doMethodTest(
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

  public void testDoNotAlign_IfFirstMultiline() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    doMethodTest(
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

  public void testAlign_InMethod() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doClassTest(
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

    doClassTest(
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

  public void test_Shift_All_AlignedParameters() {
    myLineRange = new TextRange(2, 2);
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTextTest(
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

  public void test_Align_UnselectedField_IfNeeded() {
    myLineRange = new TextRange(2, 2);
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest(
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

  public void test_Align_UnselectedVariable_IfNeeded() {
    myLineRange = new TextRange(3, 3);
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doTextTest(
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

  public void test_Align_ConsecutiveVars_InsideIfBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void test_Align_ConsecutiveVars_InsideForBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void test_Align_ConsecutiveVars_InsideTryBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void test_Align_ConsecutiveVars_InsideCodeBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
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

  public void test_AlignComments_BetweenChainedMethodCalls() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest(
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

  public void test_AlignComments_2() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doClassTest(
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

  public void test_AlignSubsequentOneLineMethods() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    getSettings().ALIGN_SUBSEQUENT_SIMPLE_METHODS = true;
    doTextTest(
      """
        public class Test {

            public void testSuperDuperFuckerMother() { System.out.println("AAA"); }

            public void testCounterMounter() { System.out.println("XXXX"); }

        }""",
      """
        public class Test {

            public void testSuperDuperFuckerMother() { System.out.println("AAA"); }

            public void testCounterMounter()         { System.out.println("XXXX"); }

        }"""
    );
  }

  public void test_alignAssignments() {
    getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    doTextTest(
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

  public void test_alignMultilineAssignments() {
    getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    doTextTest(
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


  public void test_alignMultilineAssignmentsMixedWithDeclaration() {
    getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doTextTest(
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

  public void test_alignAssignmentsFields() {
    getSettings().ALIGN_CONSECUTIVE_ASSIGNMENTS = true;
    doTextTest(
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

  public void test_alignMultilineTextBlock() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true;
    doTextTest(
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

  public void testAlign() {
    getJavaSettings().MULTI_CATCH_TYPES_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest(
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
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    getSettings().CALL_PARAMETERS_WRAP = 2;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doTextTest(
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
}