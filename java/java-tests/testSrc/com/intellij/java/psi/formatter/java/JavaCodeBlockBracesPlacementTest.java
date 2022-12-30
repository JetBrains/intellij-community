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

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class JavaCodeBlockBracesPlacementTest extends AbstractJavaFormatterTest {

  public void testSimpleTryBlock() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    String before = """
      try {} catch (Exception e) {
            System.out.println("AA!");
      }""";

    String endOfLine = """
      try {} catch (Exception e) {
          System.out.println("AA!");
      }""";

    String nextLine = """
      try {} catch (Exception e)
      {
          System.out.println("AA!");
      }""";

    String nextLineShifted = """
      try {} catch (Exception e)
          {
          System.out.println("AA!");
          }""";

    String nextLineShiftedEach = """
      try {} catch (Exception e)
          {
              System.out.println("AA!");
          }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleCatchBlock() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;

    String before = """
      try {
              System.out.println("AA!");
      } catch (Exception e) { System.out.println("AA!"); }""";

    String endOfLine = """
      try {
          System.out.println("AA!");
      } catch (Exception e) { System.out.println("AA!"); }""";

    String nextLine = """
      try
      {
          System.out.println("AA!");
      } catch (Exception e) { System.out.println("AA!"); }""";

    String nextLineShifted = """
      try
          {
          System.out.println("AA!");
          } catch (Exception e) { System.out.println("AA!"); }""";

    String nextLineShiftedEach = """
      try
          {
              System.out.println("AA!");
          } catch (Exception e) { System.out.println("AA!"); }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleCatchAndTryBlock() {
    String before = "try {} catch (Exception e) {      System.out.println(\"AA!\"); }";
    String after = "try {} catch (Exception e) {System.out.println(\"AA!\");}";
    String afterExpanded = """
      try {
      } catch (Exception e) {
          System.out.println("AA!");
      }""";

    doMethodTest(before, afterExpanded);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  public void testDoWhileStatement() {
    String before = """
      do {
              System.out.println("AAA!");
      } while (1 == 1);""";

    String endOfLine = """
      do {
          System.out.println("AAA!");
      } while (1 == 1);""";

    String nextLine = """
      do
      {
          System.out.println("AAA!");
      } while (1 == 1);""";

    String nextLineShifted = """
      do
          {
          System.out.println("AAA!");
          } while (1 == 1);""";

    String nextLineShiftedEach = """
      do
          {
              System.out.println("AAA!");
          } while (1 == 1);""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleDoWhileStatement() {
    getSettings().SPACE_WITHIN_BRACES = true;
    String before = "do {     System.out.println(\"AAA!\"); } while (1 == 1);";
    String after = "do { System.out.println(\"AAA!\"); } while (1 == 1);";

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testForEachStatement() {
    String before = """
      for (String arg : args) {
              System.out.println("AAA!");
      }""";

    String endOfLine = """
      for (String arg : args) {
          System.out.println("AAA!");
      }""";

    String nextLine = """
      for (String arg : args)
      {
          System.out.println("AAA!");
      }""";

    String nextLineShifted = """
      for (String arg : args)
          {
          System.out.println("AAA!");
          }""";

    String nextLineShiftedEach = """
      for (String arg : args)
          {
              System.out.println("AAA!");
          }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleForEachStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;

    String before = "for (String arg : args) {     System.out.println(\"AAA!\"); }";
    String after = "for (String arg : args) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testForStatement() {
    String before = """
      for (int i = 0; i < 10; i = i + 3) {
              System.out.println("AAA!");
      }
      """;

    String endOfLine = """
      for (int i = 0; i < 10; i = i + 3) {
          System.out.println("AAA!");
      }
      """;

    String nextLine = """
      for (int i = 0; i < 10; i = i + 3)
      {
          System.out.println("AAA!");
      }
      """;

    String nextLineShifted = """
      for (int i = 0; i < 10; i = i + 3)
          {
          System.out.println("AAA!");
          }
      """;

    String nextLineShiftedEach = """
      for (int i = 0; i < 10; i = i + 3)
          {
              System.out.println("AAA!");
          }
      """;

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleForStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;

    String before = "for (int i = 0; i < 10; i = i + 3) {    System.out.println(\"AAA!\"); }";
    String after = "for (int i = 0; i < 10; i = i + 3) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  public void testSwitchStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    String before = """
      switch (timeUnit) {
              case DAYS:
                 break;
         default:
           break;
      }""";

    String endOfLine = """
      switch (timeUnit) {
          case DAYS:
              break;
          default:
              break;
      }""";

    String nextLine = """
      switch (timeUnit)
      {
          case DAYS:
              break;
          default:
              break;
      }""";

    String nextLineShifted = """
      switch (timeUnit)
          {
          case DAYS:
              break;
          default:
              break;
          }""";

    String nextLineShiftedEach = """
      switch (timeUnit)
          {
              case DAYS:
                  break;
              default:
                  break;
          }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSynchronizedStatement() {
    String before = """
      synchronized (this) {
             System.out.println("AAA!");
      }""";

    String endOfLine = """
      synchronized (this) {
          System.out.println("AAA!");
      }""";

    String nextLine = """
      synchronized (this)
      {
          System.out.println("AAA!");
      }""";

    String nextLineShifted = """
      synchronized (this)
          {
          System.out.println("AAA!");
          }""";

    String nextLineShiftedEach = """
      synchronized (this)
          {
              System.out.println("AAA!");
          }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleSynchronizedStatement() {
    getSettings().SPACE_WITHIN_BRACES = true;
    String before = "synchronized (this) {     System.out.println(\"AAA!\"); }";
    String after = "synchronized (this) { System.out.println(\"AAA!\"); }";

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testWhileStatement() {
    String before = """
      while (true) {
              System.out.println("AAA");
      }""";

    String endOfLine = """
      while (true) {
          System.out.println("AAA");
      }""";

    String nextLine = """
      while (true)
      {
          System.out.println("AAA");
      }""";

    String nextLineShifted = """
      while (true)
          {
          System.out.println("AAA");
          }""";

    String nextLineShiftedEach = """
      while (true)
          {
              System.out.println("AAA");
          }""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleWhileStatement() {
    getSettings().SPACE_WITHIN_BRACES = true;
    String before = "while (true) {    System.out.println(\"AAA\"); }";
    String after = "while (true) { System.out.println(\"AAA\"); }";

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  private void checkFormatterWithDifferentBraceStyles(String before,
                                                      String endOfLine,
                                                      String nextLine,
                                                      String nextLineShifted,
                                                      String nextLineShiftedEach) {
    formatAndCheck(CommonCodeStyleSettings.END_OF_LINE, before, endOfLine);
    formatAndCheck(CommonCodeStyleSettings.NEXT_LINE, before, nextLine);
    formatAndCheck(CommonCodeStyleSettings.NEXT_LINE_SHIFTED, before, nextLineShifted);
    formatAndCheck(CommonCodeStyleSettings.NEXT_LINE_SHIFTED2, before, nextLineShiftedEach);
  }

  private void formatAndCheck(int braceStyle, String before, String after) {
    getSettings().BRACE_STYLE = braceStyle;
    doMethodTest(before, after);
  }
}
