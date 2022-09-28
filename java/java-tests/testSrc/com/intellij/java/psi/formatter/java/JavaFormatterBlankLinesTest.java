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

/**
 * Is intended to hold specific java formatting tests for 'blank lines' settings.
 *
 * @author Denis Zhdanov
 */
public class JavaFormatterBlankLinesTest extends AbstractJavaFormatterTest {

  public void testBlankLinesAroundClassInitializationBlock() {
    getSettings().BLANK_LINES_AROUND_METHOD = 3;
    getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    doTextTest(
      """
        class T {
            private final DecimalFormat fmt = new DecimalFormat();
            {
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
        }""");
  }

  public void testBlankLinesAroundClassMethods() {
    // Inspired by IDEA-19408
    getSettings().BLANK_LINES_AROUND_METHOD = 3;

    doTextTest(
      """
        class Test {
            public boolean flag1() {
                return false;
            }public boolean flag2() {
                return false;
            }public boolean flag3() {
                return false;
            }public boolean flag4() {
                return false;
            }
        }""",

      """
        class Test {
            public boolean flag1() {
                return false;
            }



            public boolean flag2() {
                return false;
            }



            public boolean flag3() {
                return false;
            }



            public boolean flag4() {
                return false;
            }
        }"""
    );
  }

  public void testBlankLinesAroundEnumMethods() {
    // Inspired by IDEA-19408
    getSettings().BLANK_LINES_AROUND_METHOD = 2;

    doTextTest(
      """
        public enum Wrapping {
            WRAPPING {public boolean flag1() {
                return false;
            }public boolean flag2() {
                return false;
            }public boolean flag3() {
                return false;
            }public boolean flag4() {
                return false;
            }}
        }""",

      """
        public enum Wrapping {
            WRAPPING {
                public boolean flag1() {
                    return false;
                }


                public boolean flag2() {
                    return false;
                }


                public boolean flag3() {
                    return false;
                }


                public boolean flag4() {
                    return false;
                }
            }
        }"""
    );
  }

  public void testInitializationBlockAndInnerClass() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      """
        public class FormattingTest {
            {
                System.out.println("");
            }
            class MyInnerClass1 {
            }
            {
                System.out.println("");
            }
            static {
                System.out.println("");
            }
            class MyInnerClass2 {
            }
            static {
                System.out.println("");
            }
        }""",

      """
        public class FormattingTest {
            {
                System.out.println("");
            }



            class MyInnerClass1 {
            }



            {
                System.out.println("");
            }

            static {
                System.out.println("");
            }



            class MyInnerClass2 {
            }



            static {
                System.out.println("");
            }
        }"""
    );
  }
  
  public void testInnerClasses() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      """
        public class FormattingTest {
            class MyInnerClass1 {
            }
            class MyInnerClass2 {
            }
            static class MyInnerClass3 {
            }
            static class MyInnerClass4 {
            }
            class MyInnerClass5 {
            }
        }""",

      """
        public class FormattingTest {
            class MyInnerClass1 {
            }



            class MyInnerClass2 {
            }



            static class MyInnerClass3 {
            }



            static class MyInnerClass4 {
            }



            class MyInnerClass5 {
            }
        }"""
    );
  }

  public void testTopLevelClasses() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      """
        class Class1 {
        }
        public class Class2 {
        }
        class Class3 {
        }
        class Class4 {
        }""",

      """
        class Class1 {
        }



        public class Class2 {
        }



        class Class3 {
        }



        class Class4 {
        }"""
    );
  }

  public void testBlankLinesBetweenAbstractMethods() {
    // Inspired by IDEA-54668
    getSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0;
    getSettings().BLANK_LINES_AROUND_METHOD = 1;

    doTextTest(
      """
        abstract class Test {
            void test1() {
            }
            abstract void test2();
            void test3() {
            }
            void test4() {
            }
            abstract void test5();
            abstract void test6();
        }""",

      """
        abstract class Test {
            void test1() {
            }

            abstract void test2();

            void test3() {
            }

            void test4() {
            }

            abstract void test5();
            abstract void test6();
        }"""
    );
  }

  public void testAroundClassHeader() {
    // Inspired by IDEA-54746
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 2;
    getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 1;
    doTextTest(
      """
        public class FormattingTest {
            public void foo() {
                Object buzz = new Object() {
                    Object test = new Object();
                };
            }
        }""",

      """
        public class FormattingTest {


            public void foo() {
                Object buzz = new Object() {

                    Object test = new Object();
                };
            }
        }"""
    );
  }

  public void testAfterAnonymousClassWhereCodeBlockStartsWithComment() {
    // Inspired by IDEA-66583
    getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
    
    String textWithWhiteSpaceBetweenCommentAndLbrace =
      """
        Object object = new Object() { // comment breaks "blank line after anonymous class header"
            @Override
            public String toString() {
                return super.toString();
            }
        };""";
    doMethodTest(textWithWhiteSpaceBetweenCommentAndLbrace, textWithWhiteSpaceBetweenCommentAndLbrace);

    String textWithoutWhiteSpaceBetweenCommentAndLbrace =
      """
        Object object = new Object() {// comment breaks "blank line after anonymous class header"
            @Override
            public String toString() {
                return super.toString();
            }
        };""";
    doMethodTest(textWithoutWhiteSpaceBetweenCommentAndLbrace, textWithoutWhiteSpaceBetweenCommentAndLbrace);
  }
  
  public void testBeforeMethodBody() {
    // Inspired by IDEA-54747
    getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    doTextTest(
      """
        public class FormattingTest {
            public void foo() {
                System.out.println("");
            }
        }""",

      """
        public class FormattingTest {
            public void foo() {



                System.out.println("");
            }
        }"""
    );
  }

  public void testBeforeMethodBodyWithCodeBlockInside() {
    // Inspired by IDEA-54747
    getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    doTextTest(
      """
        public class FormattingTest {
            public void foo() {
                System.out.println("");
                try {
                } catch (Exception e) {
                }
            }
        }""",

      """
        public class FormattingTest {
            public void foo() {



                System.out.println("");
                try {
                } catch (Exception e) {
                }
            }
        }"""
    );
  }

  public void testIDEA126836() {
    doTextTest(
      """
        public class JavaClass {  // comment
            public void doSomething() {
                        int a = 3;
            }
        }""",
      """
        public class JavaClass {  // comment
            public void doSomething() {
                int a = 3;
            }
        }"""
    );
  }

  public void testBlankLinesAfterClassHeaderWithComment() {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 5;
    doTextTest(
      """
        public class JavaClass {  // comment
            public void doSomething() {
                        int a = 3;
            }
        }""",
      """
        public class JavaClass {  // comment





            public void doSomething() {
                int a = 3;
            }
        }"""
    );
  }

  public void testBlankLinesAroundInitializer() {
    getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    doTextTest(
      """
        public class JavaClass {
            int a = 3;
            {
                System.out.println("Hello");
            }

            public void test() {
            }
        }""",
      """
        public class JavaClass {
            int a = 3;



            {
                System.out.println("Hello");
            }



            public void test() {
            }
        }"""
    );
  }

  public void testEnumBlankLines() {
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    getSettings().KEEP_BLANK_LINES_IN_CODE = 1;
    getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    doTextTest(
      """
        public enum SomeEnum {
          SOME;

          public void smth(){}


        }

        public abstract class SomeClass {
          public void smth(){}
        }""",
      """
        public enum SomeEnum {
            SOME;

            public void smth() {
            }
        }

        public abstract class SomeClass {
            public void smth() {
            }
        }"""
    );
  }

  public void testOneLineEnumWithJavadoc() {
    doTextTest(
      """
        /**
         *
         */
        enum Enum {A, B, C}""",
      """
        /**
         *
         */
        enum Enum {A, B, C}"""
    );
  }

  public void testLinesBetweenPackageAndHeader() {
    getSettings().KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 0;
    doTextTest(
      """
        /*
         * This is a sample file.
         */


        package com.intellij.samples;""",
      """
        /*
         * This is a sample file.
         */
        package com.intellij.samples;"""
    );
  }

  public void testLinesBetweenPackageAndHeader2() {
    getSettings().KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 2;
    getSettings().BLANK_LINES_BEFORE_PACKAGE = 1;
    doTextTest(
      """
        /*
         * This is a sample file.
         */
        package com.intellij.samples;""",
      """
        /*
         * This is a sample file.
         */

        package com.intellij.samples;"""
    );
  }

  public void testEnumRbrace() {
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    doTextTest(
      """
        enum Test {
          TEST1, TEST2, TEST3;


        }""",
      """
        enum Test {
            TEST1, TEST2, TEST3;


        }"""
    );
  }

  public void testEnumRbrace2() {
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
    getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    doTextTest(
      """
        enum Test {
          TEST1, TEST2, TEST3;


        }""",
      """
        enum Test {
            TEST1, TEST2, TEST3;
        }"""
    );
  }
}
