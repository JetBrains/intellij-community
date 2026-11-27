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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

@TestApplication
public class JavaCodeBlockBracesPlacementTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testSimpleTryBlock() {
    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

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

  @Test
  public void testSimpleCatchBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;

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

  @Test
  public void testSimpleCatchAndTryBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    String before = "try {} catch (Exception e) {      System.out.println(\"AA!\"); }";
    String after = "try {} catch (Exception e) {System.out.println(\"AA!\");}";
    String afterExpanded = """
      try {
      } catch (Exception e) {
          System.out.println("AA!");
      }""";

    formatter.doMethodTest(before, afterExpanded);

    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  @Test
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

    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
  public void testSimpleDoWhileStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    String before = "do {     System.out.println(\"AAA!\"); } while (1 == 1);";
    String after = "do { System.out.println(\"AAA!\"); } while (1 == 1);";

    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  @Test
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

    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
  public void testSimpleForEachStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;

    String before = "for (String arg : args) {     System.out.println(\"AAA!\"); }";
    String after = "for (String arg : args) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  @Test
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

    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
  public void testSimpleForStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;

    String before = "for (int i = 0; i < 10; i = i + 3) {    System.out.println(\"AAA!\"); }";
    String after = "for (int i = 0; i < 10; i = i + 3) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  @Test
  public void testSwitchStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

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

    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
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

    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
  public void testSimpleSynchronizedStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    String before = "synchronized (this) {     System.out.println(\"AAA!\"); }";
    String after = "synchronized (this) { System.out.println(\"AAA!\"); }";

    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  @Test
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

    formatterFixture.get().getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  @Test
  public void testSimpleWhileStatement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    String before = "while (true) {    System.out.println(\"AAA\"); }";
    String after = "while (true) { System.out.println(\"AAA\"); }";

    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
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
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().BRACE_STYLE = braceStyle;
    formatter.doMethodTest(before, after);
  }

  @Test
  public void testSwitchExpression() {
    String before = """
      var x = switch (foo) {
      		case "bar" ->
      			{
      				yield "2000";
      			}
      		default -> "n/a";
      	};""";

    String endOfLine = """
      var x = switch (foo) {
          case "bar" -> {
              yield "2000";
          }
          default -> "n/a";
      };""";

    String nextLine = """
      var x = switch (foo)
      {
          case "bar" ->
          {
              yield "2000";
          }
          default -> "n/a";
      };""";

    String nextLineShifted = """
      var x = switch (foo)
          {
          case "bar" ->
              {
              yield "2000";
              }
          default -> "n/a";
          };""";

    String nextLineShiftedEach = """
      var x = switch (foo)
          {
              case "bar" ->
                  {
                      yield "2000";
                  }
              default -> "n/a";
          };""";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted,
                                           nextLineShiftedEach);
  }
}
