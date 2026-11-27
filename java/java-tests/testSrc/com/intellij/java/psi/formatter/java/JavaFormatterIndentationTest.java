// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold java formatting indentation-specific tests.
 */
@TestApplication
public class JavaFormatterIndentationTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testClassInitializationBlockIndentation() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Checking that initialization block body is correctly indented.
    formatter.doMethodTest(
      """
        checking(new Expectations() {{
        one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining("a-c")));
        }});""",
      """
        checking(new Expectations() {{
            one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining("a-c")));
        }});"""
    );

    // Checking that closing curly brace of initialization block that is not the first block on a line is correctly indented.
    formatter.doTextTest("""
                 class Class {
                     private Type field; {
                     }
                 }""",
               """
                 class Class {
                     private Type field;

                     {
                     }
                 }""");
    formatter.doTextTest(
      """
        class T {
            private final DecimalFormat fmt = new DecimalFormat(); {
                fmt.setGroupingUsed(false);
                fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
            }
        }""",
      """
        class T {
            private final DecimalFormat fmt = new DecimalFormat();

            {
                fmt.setGroupingUsed(false);
                fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
            }
        }"""
    );
  }

  @Test
  public void testNestedMethodsIndentation() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-43962

    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4;

    formatter.doMethodTest(
      """
        BigDecimal.ONE
              .add(BigDecimal.ONE
                .add(BigDecimal.ONE
                .add(BigDecimal.ONE
                .add(BigDecimal.ONE
        .add(BigDecimal.ONE
         .add(BigDecimal.ONE
          .add(BigDecimal.ONE
         .add(BigDecimal.ONE
                .add(BigDecimal.ONE)))))))));""",
      """
        BigDecimal.ONE
            .add(BigDecimal.ONE
                .add(BigDecimal.ONE
                    .add(BigDecimal.ONE
                        .add(BigDecimal.ONE
                            .add(BigDecimal.ONE
                                .add(BigDecimal.ONE
                                    .add(BigDecimal.ONE
                                        .add(BigDecimal.ONE
                                            .add(BigDecimal.ONE)))))))));"""
    );
  }

  @Test
  public void testShiftedChainedIfElse() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    formatter.getSettings().ELSE_ON_NEW_LINE = true;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;
    formatter.doMethodTest(
      """
        long a = System.currentTimeMillis();
            if (a == 0){
           }else if (a > 1){
          }else if (a > 2){
         }else if (a > 3){
             }else if (a > 4){
              }else if (a > 5){
               }else{
                }""",
      """
        long a = System.currentTimeMillis();
        if (a == 0)
            {
            }
        else if (a > 1)
            {
            }
        else if (a > 2)
            {
            }
        else if (a > 3)
            {
            }
        else if (a > 4)
            {
            }
        else if (a > 5)
            {
            }
        else
            {
            }"""
    );
  }

  @Test
  public void testAlignedSubBlockIndentation() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8;

    // Inspired by IDEA-54671
    formatter.doTextTest(
      """
        class Test {
            public void foo() {
                test(11
                             + 12
                             + 13,
                     21
                             + 22
                             + 23
                )    }
        }""",

      """
        class Test {
            public void foo() {
                test(11
                             + 12
                             + 13,
                     21
                             + 22
                             + 23
                )
            }
        }"""
    );
  }

  @Test
  public void testEnumIndentation() throws IncorrectOperationException {
    // Inspired by IDEADEV-2840
    formatterFixture.get().doTextTest("""
                 enum Xyz {
                 FOO,
                 BAR,
                 }""", """
                 enum Xyz {
                     FOO,
                     BAR,
                 }""");
  }

  @Test
  public void testFirstColumnComment() throws IncorrectOperationException {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-14116
    formatter.getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    formatter.doTextTest("""
                 class Foo{
                 private int foo;     // this is a foo
                 }""",
               """
                 class Foo {
                     private int foo;     // this is a foo
                 }""");
  }

  @Test
  public void testCaseFromSwitch() throws IncorrectOperationException {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-22920
    formatter.getSettings().INDENT_CASE_FROM_SWITCH = false;
    formatter.doTextTest(
      """
        class Foo{
        void foo () {
        switch(someValue) {
         // This comment is correctly not-indented
         case 1:
            doSomething();
            break;

         // This comment should not be indented, but it is
         case 2:
            doSomethingElse();
            break;
        }
        }
        }""",

      """
        class Foo {
            void foo() {
                switch (someValue) {
                // This comment is correctly not-indented
                case 1:
                    doSomething();
                    break;

                // This comment should not be indented, but it is
                case 2:
                    doSomethingElse();
                    break;
                }
            }
        }""");
  }

  @Test
  public void testBinaryExpressionsWithRelativeIndents() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-21795
    formatter.getIndentOptions().USE_RELATIVE_INDENTS = true;
    formatter.getIndentOptions().CONTINUATION_INDENT_SIZE = 4;

    formatter.doTextTest(
      """
        public class FormattingTest {

            public boolean test1(int a, int b, int c, int d) {
                return a == 1 &&
              b == 2;
            }

            public boolean multilineSignOnCurrent(int a, int b, int c, int d) {
                return a == 0 &&
                                          (b == 0 ||
             c == 0) &&
          d == 0;
            }

            public boolean multilineSignOnNext(int a, int b, int c, int d) {
                return a == 0
               && (b == 0
                                             || c == 0)
           && d == 0;
            }

            public boolean expectedMultilineSignOnNext(int a, int b, int c, int d) {
                return a == 0
            && (b == 0
             || c == 0)
                               && d == 0;
            }
        }""",

      """
        public class FormattingTest {

            public boolean test1(int a, int b, int c, int d) {
                return a == 1 &&
                           b == 2;
            }

            public boolean multilineSignOnCurrent(int a, int b, int c, int d) {
                return a == 0 &&
                           (b == 0 ||
                                c == 0) &&
                           d == 0;
            }

            public boolean multilineSignOnNext(int a, int b, int c, int d) {
                return a == 0
                           && (b == 0
                                   || c == 0)
                           && d == 0;
            }

            public boolean expectedMultilineSignOnNext(int a, int b, int c, int d) {
                return a == 0
                           && (b == 0
                                   || c == 0)
                           && d == 0;
            }
        }"""
    );
  }
  
  @Test
  public void testBracesShiftedOnNextLineOnMethodWithJavadoc() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-62997
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    
    String precededByJavadoc =
      """
        /**
         * test
         */
        public int getFoo()
            {
            return foo;
            }""";
    
    String precededBySingleLineComment =
      """
        // test
        public int getFoo()
            {
            return foo;
            }""";

    String precededByMultiLineComment =
      """
        /*
        test
        */
        public int getFoo()
            {
            return foo;
            }""";
    
    formatter.doClassTest(precededByJavadoc, precededByJavadoc);
    formatter.doClassTest(precededBySingleLineComment, precededBySingleLineComment);
    formatter.doClassTest(precededByMultiLineComment, precededByMultiLineComment);
  }
  
  @Test
  public void testAnonymousClassInstancesAsMethodCallArguments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-65987
    
    formatter.doMethodTest(
      """
        foo("long string as the first argument", new Runnable() {
        public void run() {                        \s
        }                                       \s
        },                                           \s
        new Runnable() {                        \s
        public void run() {                \s
        }                                         \s
        }                                            \s
        );                                                      \s""",
      """
        foo("long string as the first argument", new Runnable() {
                    public void run() {
                    }
                },
                new Runnable() {
                    public void run() {
                    }
                }
        );"""
    );
    
    formatter.doMethodTest(
      """
        foo(1,
        2, new Runnable() {
        @Override
        public void run() {
        }
        });""",
      """
        foo(1,
                2, new Runnable() {
                    @Override
                    public void run() {
                    }
                });"""
    );
    
    formatter.doMethodTest(
      """
        foo(new Runnable() {
        @Override
        public void run() {
        }
        },
        new Runnable() {
        @Override
        public void run() {
        }
        });""",
      """
        foo(new Runnable() {
                @Override
                public void run() {
                }
            },
                new Runnable() {
                    @Override
                    public void run() {
                    }
                });"""
    );
  }

  @Test
  public void testAnonymousClassInstancesAsAlignedMethodCallArguments() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    formatter.doMethodTest(
      """
        foo(new Runnable() {
        @Override
        public void run() {
        }
        },
        new Runnable() {
        @Override
        public void run() {
        }
        });""",
      """
        foo(new Runnable() {
                @Override
                public void run() {
                }
            },
            new Runnable() {
                @Override
                public void run() {
                }
            });"""
    );

    formatter.doMethodTest(
      """
        foo(123456789, new Runnable() {
        @Override
        public void run() {
        }
        },
        new Runnable() {
        @Override
        public void run() {
        }
        });""",
      """
        foo(123456789, new Runnable() {
                @Override
                public void run() {
                }
            },
            new Runnable() {
                @Override
                public void run() {
                }
            });"""
    );

    formatter.doMethodTest(
      """
        foo(new Runnable() {
        @Override
        public void run() {
        }}, 1, 2);""",
      """
        foo(new Runnable() {
            @Override
            public void run() {
            }
        }, 1, 2);"""
    );
  }

  @Test
  public void testAnonymousClassesOnSameLineAtMethodCallExpression() {
    formatterFixture.get().doMethodTest(
      """
        foo(new Runnable() {
                public void run() {
                }
            }, new Runnable() {
                       public void run() {
                       }
                      });""",
      """
        foo(new Runnable() {
            public void run() {
            }
        }, new Runnable() {
            public void run() {
            }
        });"""
    );
  }

  @Test
  public void testAlignMultipleAnonymousClasses_PassedAsMethodParameters() {
    String text = """
      test(new Runnable() {
          @Override
          public void run() {
              System.out.println("AAA!");
          }
      }, new Runnable() {
          @Override
          public void run() {
              System.out.println("BBB!");
          }
      });
      """;
    formatterFixture.get().doMethodTest(text, text);
  }

  @Test
  public void testAlignmentAdditionalParamsWithMultipleAnonymousClasses_PassedAsMethodParameters() {
    String text = """
      foo(1221, new Runnable() {
          @Override
          public void run() {
              System.out.println("A");
          }
      }, new Runnable() {
          @Override
          public void run() {
              System.out.println("BB");
          }
      });""";
    formatterFixture.get().doMethodTest(text, text);
  }

  @Test
  public void testAlignmentMultipleParamsWithAnonymousClass_PassedAsMethodParams() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = """
      test(1000,
           new Runnable() {
               @Override
               public void run() {
                   System.out.println("BBB");
               }
           }
      );""";
    formatter.doMethodTest(text, text);
  }

  @Test
  public void testAlignmentMultipleAnonymousClassesOnNewLines() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = """
      test(1000,
           new Runnable() {
               @Override
               public void run() {
                   System.out.println("BBB");
               }
           },
           new Runnable() {
               @Override
               public void run() {
                   System.out.println("BBB");
               }
           }
      );""";
    formatter.doMethodTest(text, text);
  }

  @Test
  public void testEnforceChildrenIndent_OfAnonymousClasses_IfAnyOfParamsIsLocatedOnNewLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = """
      test("Suuuuuuuuuuuuuuuuuper loooooooooooong string",
           "Next loooooooooooooooooooooong striiiiiiiiiiing", new Runnable() {
                  @Override
                  public void run() {

                  }
              }, new Runnable() {
                  @Override
                  public void run() {

                  }
              }
      );
      """;
    formatter.doMethodTest(text, text);
  }

  @Test
  public void testPackagePrivateAnnotation() {
    // Inspired by IDEA-67294
    
    String text =
      """
        @Retention(RUNTIME)
        @Target({FIELD, PARAMETER, METHOD})
        @interface MyAnnotation {

        }""";
    formatterFixture.get().doTextTest(text, text);
  }

  @Test
  public void testIncompleteMethodCall() {
    // Inspired by IDEA-79836.

    formatterFixture.get().doMethodTest(
      """
        test(new Runnable() {
                 public void run() {
                 }
             }, new Runnable() {
                 public void run() {
                 }
             }, )""",
      """
        test(new Runnable() {
            public void run() {
            }
        }, new Runnable() {
            public void run() {
            }
        }, )"""
    );
  }

  @Test
  public void testCStyleCommentIsNotMoved() {
    // IDEA-87087
    formatterFixture.get().doClassTest(
      """
                        /*
                           this is a c-style comment
                         */
                   // This is a line comment\
        """,
      """
                    /*
                       this is a c-style comment
                     */
        // This is a line comment"""
    );
  }

  @Test
  public void testMultilineCommentAtFileStart() {
    // IDEA-90860
    String text =
      """

        /*
         * comment
         */

        class Test {
        }""";
    formatterFixture.get().doTextTest(text, text);
  }

  @Test
  public void testMultilineCommentAndTabsIndent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // IDEA-91703
    String initial =
      """
        \t/*
        \t\t* comment
        \t */
        class Test {
        }""";

    String expected =
      """
        /*
         * comment
         */
        class Test {
        }""";
    
    formatter.getIndentOptions().USE_TAB_CHARACTER = true;
    formatter.doTextTest(initial, expected);
  }

  @Test
  public void testLambdaIndentation() {
    String before = """
      Runnable r = () ->
      {
          System.out.println("olo");
      };""";
    formatterFixture.get().doMethodTest(before, before);
  }
  
  @Test
  public void testAnnotatedParameters() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // it is supposed that this
    formatter.getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER = true;
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.getSettings().RIGHT_MARGIN = 120;
    formatter.getSettings().WRAP_LONG_LINES = true;
    String before = """
      public class Formatting {
        @RequestMapping(value = "/", method = GET)
        public HttpEntity<String> helloWorld(@RequestParam("name") String name, @PageableDefault(page = 0, size = 10) Pageable pageable) {
          // I'd expect the line above to be indented by 4 spaces
          return ResponseEntity.ok("Hello " + name);
        }
      }""";
    String after = """
      public class Formatting {
          @RequestMapping(value = "/", method = GET)
          public HttpEntity<String> helloWorld(@RequestParam("name") String name,
                                               @PageableDefault(page = 0, size = 10) Pageable pageable) {
              // I'd expect the line above to be indented by 4 spaces
              return ResponseEntity.ok("Hello " + name);
          }
      }""";
    
    formatter.doTextTest(before, after);
  }

  @Test
  public void testTextBlock() {
    String before = """
      class Formatting {
          void test() {
              String block = ""\"
                                  \s
       text
      block""\";
          }
      }""";

    String after = """
      class Formatting {
          void test() {
              String block = ""\"
                     \s
                       text
                      block""\";
          }
      }""";
    formatterFixture.get().doTextTest(before, after);
  }


  @Test
  public void testKeepBuilderMethodsIndents() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.getSettings().BUILDER_METHODS = "wrap,flowPanel,widget,builder,end";
    formatter.getSettings().KEEP_BUILDER_METHODS_INDENTS = true;

    formatter.doTextTest(
      """
        class Test {
            public static void main(String[] args) {
                PanelBuilder.wrap(getCenterPanel(), "review-view")
                    .flowPanel("sidebar-offset")   //content
                      .widget(myReviewHints)
                      .flowPanel("island")          //changes island
                        .flowPanel("pane-toolbar pane-toolbar_island clearfix") //paneToolbar
                          .flowPanel("pane-toolbar__left pane-toolbar__left_header") //paneToolbarLeft
                            .widget(reviewStateLabel(reviewDescriptorSignal))
                            .widget(reviewIdLabel(reviewDescriptorSignal))
                            .builder(reviewTitle(projectDescriptor, reviewDescriptorSignal))
                          .end()
                        .end()
                        .flowPanel("revision-files-standalone") // review changes view
                          .widget(myChangesListView)
                        .end()
                      .end()
                      .widget(myReviewFeedView)
                    .end();
            }
        }""",

      """
        class Test {
            public static void main(String[] args) {
                PanelBuilder.wrap(getCenterPanel(), "review-view")
                        .flowPanel("sidebar-offset")   //content
                          .widget(myReviewHints)
                          .flowPanel("island")          //changes island
                            .flowPanel("pane-toolbar pane-toolbar_island clearfix") //paneToolbar
                              .flowPanel("pane-toolbar__left pane-toolbar__left_header") //paneToolbarLeft
                                .widget(reviewStateLabel(reviewDescriptorSignal))
                                .widget(reviewIdLabel(reviewDescriptorSignal))
                                .builder(reviewTitle(projectDescriptor, reviewDescriptorSignal))
                              .end()
                            .end()
                            .flowPanel("revision-files-standalone") // review changes view
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
  public void testIdea158691() {
    formatterFixture.get().doMethodTest(
      """
        context.start(
                    first,
                    second)
                    .setPriority(1)
                    .build();""",

      """
        context.start(
                        first,
                        second)
                .setPriority(1)
                .build();"""
    );
  }

  @Test
  public void testIdea274755() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().getIndentOptions().USE_RELATIVE_INDENTS = true;
    formatter.doMethodTest(
      """
        public class Test {
        void test() {
            final var command = CreateUpload.builder()
        .identityId(userId)
              .iotId(iotId)
        .build();
           }
        }""",

      """
        public class Test {
            void test() {
                final var command = CreateUpload.builder()
                                            .identityId(userId)
                                            .iotId(iotId)
                                            .build();
            }
        }"""
    );
  }

  @Test
  public void testDanglingDotInMethodCallChain() {
    formatterFixture.get().doMethodTest(
      """
        foo()
        .bar()
        .
        baz();""",
      """
        foo()
                .bar()
                .
                baz();"""
    );
  }

  @Test
  public void testDotAtLineEndInMethodCallChain() {
    formatterFixture.get().doMethodTest(
      """
        foo().
        bar().
        baz();""",
      """
        foo().
                bar().
                baz();"""
    );
  }
}
