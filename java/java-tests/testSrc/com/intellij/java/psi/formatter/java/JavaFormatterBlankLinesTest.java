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
 * @since Apr 27, 2010 6:33:00 PM
 */
public class JavaFormatterBlankLinesTest extends AbstractJavaFormatterTest {

  public void testBlankLinesAroundClassInitializationBlock() {
    getSettings().BLANK_LINES_AROUND_METHOD = 3;
    getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    doTextTest(
      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat();\n" +
      "    {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}",

      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat();\n" +
      "\n" +
      "\n" +
      "\n" +
      "    {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}");
  }

  public void testBlankLinesAroundClassMethods() {
    // Inspired by IDEA-19408
    getSettings().BLANK_LINES_AROUND_METHOD = 3;

    doTextTest(
      "class Test {\n" +
      "    public boolean flag1() {\n" +
      "        return false;\n" +
      "    }public boolean flag2() {\n" +
      "        return false;\n" +
      "    }public boolean flag3() {\n" +
      "        return false;\n" +
      "    }public boolean flag4() {\n" +
      "        return false;\n" +
      "    }\n" +
      "}",

      "class Test {\n" +
      "    public boolean flag1() {\n" +
      "        return false;\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    public boolean flag2() {\n" +
      "        return false;\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    public boolean flag3() {\n" +
      "        return false;\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    public boolean flag4() {\n" +
      "        return false;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testBlankLinesAroundEnumMethods() {
    // Inspired by IDEA-19408
    getSettings().BLANK_LINES_AROUND_METHOD = 2;

    doTextTest(
      "public enum Wrapping {\n" +
      "    WRAPPING {public boolean flag1() {\n" +
      "        return false;\n" +
      "    }public boolean flag2() {\n" +
      "        return false;\n" +
      "    }public boolean flag3() {\n" +
      "        return false;\n" +
      "    }public boolean flag4() {\n" +
      "        return false;\n" +
      "    }}\n" +
      "}",

      "public enum Wrapping {\n" +
      "    WRAPPING {\n" +
      "        public boolean flag1() {\n" +
      "            return false;\n" +
      "        }\n" +
      "\n" +
      "\n" +
      "        public boolean flag2() {\n" +
      "            return false;\n" +
      "        }\n" +
      "\n" +
      "\n" +
      "        public boolean flag3() {\n" +
      "            return false;\n" +
      "        }\n" +
      "\n" +
      "\n" +
      "        public boolean flag4() {\n" +
      "            return false;\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testInitializationBlockAndInnerClass() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      "public class FormattingTest {\n" +
      "    {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "    class MyInnerClass1 {\n" +
      "    }\n" +
      "    {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "    static {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "    class MyInnerClass2 {\n" +
      "    }\n" +
      "    static {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "    {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    class MyInnerClass1 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "\n" +
      "    static {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    class MyInnerClass2 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    static {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "}"
    );
  }
  
  public void testInnerClasses() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      "public class FormattingTest {\n" +
      "    class MyInnerClass1 {\n" +
      "    }\n" +
      "    class MyInnerClass2 {\n" +
      "    }\n" +
      "    static class MyInnerClass3 {\n" +
      "    }\n" +
      "    static class MyInnerClass4 {\n" +
      "    }\n" +
      "    class MyInnerClass5 {\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "    class MyInnerClass1 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    class MyInnerClass2 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    static class MyInnerClass3 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    static class MyInnerClass4 {\n" +
      "    }\n" +
      "\n" +
      "\n" +
      "\n" +
      "    class MyInnerClass5 {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testTopLevelClasses() {
    // Inspired by IDEA-21191
    getSettings().BLANK_LINES_AROUND_CLASS = 3;

    doTextTest(
      "class Class1 {\n" +
      "}\n" +
      "public class Class2 {\n" +
      "}\n" +
      "class Class3 {\n" +
      "}\n" +
      "class Class4 {\n" +
      "}",

      "class Class1 {\n" +
      "}\n" +
      "\n" +
      "\n" +
      "\n" +
      "public class Class2 {\n" +
      "}\n" +
      "\n" +
      "\n" +
      "\n" +
      "class Class3 {\n" +
      "}\n" +
      "\n" +
      "\n" +
      "\n" +
      "class Class4 {\n" +
      "}"
    );
  }

  public void testBlankLinesBetweenAbstractMethods() {
    // Inspired by IDEA-54668
    getSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0;
    getSettings().BLANK_LINES_AROUND_METHOD = 1;

    doTextTest(
      "abstract class Test {\n" +
      "    void test1() {\n" +
      "    }\n" +
      "    abstract void test2();\n" +
      "    void test3() {\n" +
      "    }\n" +
      "    void test4() {\n" +
      "    }\n" +
      "    abstract void test5();\n" +
      "    abstract void test6();\n" +
      "}",

      "abstract class Test {\n" +
      "    void test1() {\n" +
      "    }\n" +
      "\n" +
      "    abstract void test2();\n" +
      "\n" +
      "    void test3() {\n" +
      "    }\n" +
      "\n" +
      "    void test4() {\n" +
      "    }\n" +
      "\n" +
      "    abstract void test5();\n" +
      "    abstract void test6();\n" +
      "}"
    );
  }

  public void testAroundClassHeader() {
    // Inspired by IDEA-54746
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 2;
    getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 1;
    doTextTest(
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        Object buzz = new Object() {\n" +
      "            Object test = new Object();\n" +
      "        };\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "\n" +
      "\n" +
      "    public void foo() {\n" +
      "        Object buzz = new Object() {\n" +
      "\n" +
      "            Object test = new Object();\n" +
      "        };\n" +
      "    }\n" +
      "}"
    );
  }

  public void testAfterAnonymousClassWhereCodeBlockStartsWithComment() {
    // Inspired by IDEA-66583
    getSettings().BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
    
    String textWithWhiteSpaceBetweenCommentAndLbrace = 
      "Object object = new Object() { // comment breaks \"blank line after anonymous class header\"\n" +
      "    @Override\n" +
      "    public String toString() {\n" +
      "        return super.toString();\n" +
      "    }\n" +
      "};";
    doMethodTest(textWithWhiteSpaceBetweenCommentAndLbrace, textWithWhiteSpaceBetweenCommentAndLbrace);

    String textWithoutWhiteSpaceBetweenCommentAndLbrace =
      "Object object = new Object() {// comment breaks \"blank line after anonymous class header\"\n" +
      "    @Override\n" +
      "    public String toString() {\n" +
      "        return super.toString();\n" +
      "    }\n" +
      "};";
    doMethodTest(textWithoutWhiteSpaceBetweenCommentAndLbrace, textWithoutWhiteSpaceBetweenCommentAndLbrace);
  }
  
  public void testBeforeMethodBody() {
    // Inspired by IDEA-54747
    getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    doTextTest(
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "\n" +
      "\n" +
      "\n" +
      "        System.out.println(\"\");\n" +
      "    }\n" +
      "}"
    );
  }

  public void testBeforeMethodBodyWithCodeBlockInside() {
    // Inspired by IDEA-54747
    getSettings().BLANK_LINES_BEFORE_METHOD_BODY = 3;
    doTextTest(
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"\");\n" +
      "        try {\n" +
      "        } catch (Exception e) {\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "\n" +
      "\n" +
      "\n" +
      "        System.out.println(\"\");\n" +
      "        try {\n" +
      "        } catch (Exception e) {\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIDEA126836() {
    doTextTest(
      "public class JavaClass {  // comment\n" +
      "    public void doSomething() {\n" +
      "                int a = 3;\n" +
      "    }\n" +
      "}",
      "public class JavaClass {  // comment\n" +
      "    public void doSomething() {\n" +
      "        int a = 3;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testBlankLinesAfterClassHeaderWithComment() {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 5;
    doTextTest(
      "public class JavaClass {  // comment\n" +
      "    public void doSomething() {\n" +
      "                int a = 3;\n" +
      "    }\n" +
      "}",
      "public class JavaClass {  // comment\n" +
      "\n\n\n\n\n" +
      "    public void doSomething() {\n" +
      "        int a = 3;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testBlankLinesAroundInitializer() {
    getJavaSettings().BLANK_LINES_AROUND_INITIALIZER = 3;
    doTextTest(
      "public class JavaClass {\n" +
      "    int a = 3;\n" +
      "    {\n" +
      "        System.out.println(\"Hello\");\n" +
      "    }\n" +
      "\n" +
      "    public void test() {\n" +
      "    }\n" +
      "}",
      "public class JavaClass {\n" +
      "    int a = 3;\n" +
      "\n\n\n" +
      "    {\n" +
      "        System.out.println(\"Hello\");\n" +
      "    }\n" +
      "\n\n\n" +
      "    public void test() {\n" +
      "    }\n" +
      "}"
    );
  }
}
