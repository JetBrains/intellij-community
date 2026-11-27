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

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for 'blank lines' settings.
 */
@TestApplication
public class JavaFormatterBlankLinesTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testBlankLinesAroundClassInitializationBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BLANK_LINES_AROUND_METHOD = 3;
    formatter.getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    formatter.doTextTest(
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

  @Test
  public void testBlankLinesAroundClassMethods() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-19408
    formatter.getSettings().BLANK_LINES_AROUND_METHOD = 3;

    formatter.doTextTest(
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

  @Test
  public void testBlankLinesAroundEnumMethods() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-19408
    formatter.getSettings().BLANK_LINES_AROUND_METHOD = 2;

    formatter.doTextTest(
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

  @Test
  public void testInitializationBlockAndInnerClass() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-21191
    formatter.getSettings().BLANK_LINES_AROUND_CLASS = 3;

    formatter.doTextTest(
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
  
  @Test
  public void testInnerClasses() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-21191
    formatter.getSettings().BLANK_LINES_AROUND_CLASS = 3;

    formatter.doTextTest(
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

  @Test
  public void testTopLevelClasses() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-21191
    formatter.getSettings().BLANK_LINES_AROUND_CLASS = 3;

    formatter.doTextTest(
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

  @Test
  public void testBlankLinesBetweenAbstractMethods() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54668
    formatter.getSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0;
    formatter.getSettings().BLANK_LINES_AROUND_METHOD = 1;

    formatter.doTextTest(
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

  @Test
  public void testAroundClassHeader() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54746
    formatter.getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 2;
    formatter.getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 1;
    formatter.doTextTest(
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

  @Test
  public void testAfterAnonymousClassWhereCodeBlockStartsWithComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-66583
    formatter.getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
    
    String textWithWhiteSpaceBetweenCommentAndLbrace =
      """
        Object object = new Object() { // comment breaks "blank line after anonymous class header"
            @Override
            public String toString() {
                return super.toString();
            }
        };""";
    formatter.doMethodTest(textWithWhiteSpaceBetweenCommentAndLbrace, textWithWhiteSpaceBetweenCommentAndLbrace);

    String textWithoutWhiteSpaceBetweenCommentAndLbrace =
      """
        Object object = new Object() {// comment breaks "blank line after anonymous class header"
            @Override
            public String toString() {
                return super.toString();
            }
        };""";
    formatter.doMethodTest(textWithoutWhiteSpaceBetweenCommentAndLbrace, textWithoutWhiteSpaceBetweenCommentAndLbrace);
  }
  
  @Test
  public void testBeforeMethodBody() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54747
    formatter.getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    formatter.doTextTest(
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

  @Test
  public void testBeforeMethodBodyWithCodeBlockInside() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54747
    formatter.getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    formatter.doTextTest(
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

  @Test
  public void testIDEA126836() {
    formatterFixture.get().doTextTest(
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

  @Test
  public void testBlankLinesAfterClassHeaderWithComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 5;
    formatter.doTextTest(
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

  @Test
  public void testBlankLinesAroundInitializer() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    formatter.doTextTest(
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

  @Test
  public void testEnumBlankLines() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    formatter.getSettings().KEEP_BLANK_LINES_IN_CODE = 1;
    formatter.getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    formatter.doTextTest(
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

  @Test
  public void testOneLineEnumWithJavadoc() {
    formatterFixture.get().doTextTest(
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

  @Test
  public void testLinesBetweenPackageAndHeader() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 0;
    formatter.doTextTest(
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

  @Test
  public void testLinesBetweenPackageAndHeader2() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 2;
    formatter.getSettings().BLANK_LINES_BEFORE_PACKAGE = 1;
    formatter.doTextTest(
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

  @Test
  public void testEnumRbrace() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    formatter.getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    formatter.doTextTest(
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

  @Test
  public void testEnumRbrace2() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
    formatter.getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    formatter.doTextTest(
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
