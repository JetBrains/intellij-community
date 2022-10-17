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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.java.LegacyChainedMethodCallsBlockBuilder;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold java formatting indentation-specific tests.
 *
 * @author Denis Zhdanov
 */
public class JavaFormatterIndentationTest extends AbstractJavaFormatterTest {

  public void testClassInitializationBlockIndentation() {
    // Checking that initialization block body is correctly indented.
    doMethodTest(
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
    doTextTest("""
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
    doTextTest(
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

  public void testNestedMethodsIndentation() {
    // Inspired by IDEA-43962

    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4;

    doMethodTest(
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

  public void testShiftedChainedIfElse() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    getSettings().ELSE_ON_NEW_LINE = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;
    doMethodTest(
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

  public void testAlignedSubBlockIndentation() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8;

    // Inspired by IDEA-54671
    doTextTest(
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

  public void testEnumIndentation() throws IncorrectOperationException {
    // Inspired by IDEADEV-2840
    doTextTest("""
                 enum Xyz {
                 FOO,
                 BAR,
                 }""", """
                 enum Xyz {
                     FOO,
                     BAR,
                 }""");
  }

  public void testFirstColumnComment() throws IncorrectOperationException {
    // Inspired by IDEADEV-14116
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest("""
                 class Foo{
                 private int foo;     // this is a foo
                 }""",
               """
                 class Foo {
                     private int foo;     // this is a foo
                 }""");
  }

  public void testCaseFromSwitch() throws IncorrectOperationException {
    // Inspired by IDEADEV-22920
    getSettings().INDENT_CASE_FROM_SWITCH = false;
    doTextTest(
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

  public void testBinaryExpressionsWithRelativeIndents() {
    // Inspired by IDEA-21795
    getIndentOptions().USE_RELATIVE_INDENTS = true;
    getIndentOptions().CONTINUATION_INDENT_SIZE = 4;

    doTextTest(
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
  
  public void testBracesShiftedOnNextLineOnMethodWithJavadoc() {
    // Inspired by IDEA-62997
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    
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
    
    doClassTest(precededByJavadoc, precededByJavadoc);
    doClassTest(precededBySingleLineComment, precededBySingleLineComment);
    doClassTest(precededByMultiLineComment, precededByMultiLineComment);
  }
  
  public void testAnonymousClassInstancesAsMethodCallArguments() {
    // Inspired by IDEA-65987
    
    doMethodTest(
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
    
    doMethodTest(
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
    
    doMethodTest(
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

  public void testAnonymousClassInstancesAsAlignedMethodCallArguments() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doMethodTest(
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

    doMethodTest(
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

    doMethodTest(
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

  public void testAnonymousClassesOnSameLineAtMethodCallExpression() {
    doMethodTest(
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
    doMethodTest(text, text);
  }

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
    doMethodTest(text, text);
  }

  public void testAlignmentMultipleParamsWithAnonymousClass_PassedAsMethodParams() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = """
      test(1000,
           new Runnable() {
               @Override
               public void run() {
                   System.out.println("BBB");
               }
           }
      );""";
    doMethodTest(text, text);
  }

  public void testAlignmentMultipleAnonymousClassesOnNewLines() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
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
    doMethodTest(text, text);
  }

  public void testEnforceChildrenIndent_OfAnonymousClasses_IfAnyOfParamsIsLocatedOnNewLine() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
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
    doMethodTest(text, text);
  }

  public void testPackagePrivateAnnotation() {
    // Inspired by IDEA-67294
    
    String text =
      """
        @Retention(RUNTIME)
        @Target({FIELD, PARAMETER, METHOD})
        @interface MyAnnotation {

        }""";
    doTextTest(text, text);
  }

  public void testIncompleteMethodCall() {
    // Inspired by IDEA-79836.

    doMethodTest(
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

  public void testCStyleCommentIsNotMoved() {
    // IDEA-87087
    doClassTest(
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

  public void testMultilineCommentAtFileStart() {
    // IDEA-90860
    String text =
      """

        /*
         * comment
         */

        class Test {
        }""";
    doTextTest(text, text);
  }

  public void testMultilineCommentAndTabsIndent() {
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
    
    getIndentOptions().USE_TAB_CHARACTER = true;
    doTextTest(initial, expected);
  }

  public void testLambdaIndentation() {
    String before = """
      Runnable r = () ->
      {
          System.out.println("olo");
      };""";
    doMethodTest(before, before);
  }
  
  public void testAnnotatedParameters() {
    // it is supposed that this
    getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER = true;
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().RIGHT_MARGIN = 120;
    getSettings().WRAP_LONG_LINES = true;
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
    
    doTextTest(before, after);
  }

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
    doTextTest(before, after);
  }


  public void testKeepBuilderMethodsIndents() {
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().BUILDER_METHODS = "wrap,flowPanel,widget,builder,end";
    getSettings().KEEP_BUILDER_METHODS_INDENTS = true;

    doTextTest(
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

  public void testIdea158691() {
    doMethodTest(
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

  public void testIdea274755() {
    getSettings().getIndentOptions().USE_RELATIVE_INDENTS = true;
    doMethodTest(
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

  public void testIdea274778() {
    CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getIndentOptions();
    indentOptions.INDENT_SIZE = 3;
    indentOptions.CONTINUATION_INDENT_SIZE = 3;
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    RegistryValue pre212compat = Registry.get(LegacyChainedMethodCallsBlockBuilder.COMPATIBILITY_KEY);
    try {
      pre212compat.setValue(true);
      doTextTest(
        """
          class Foo {
          void foo() {
          LOG.error(DetailsMessage.of(
          "TITLE",
          "LONG MESSAGE TEXT...")
          .with("value", value));
          }
          }""",

        """
          class Foo {
             void foo() {
                LOG.error(DetailsMessage.of(
                   "TITLE",
                   "LONG MESSAGE TEXT...")
                                        .with("value", value));
             }
          }"""
      );
    }
    finally {
      pre212compat.setValue(false);
    }
  }
}
