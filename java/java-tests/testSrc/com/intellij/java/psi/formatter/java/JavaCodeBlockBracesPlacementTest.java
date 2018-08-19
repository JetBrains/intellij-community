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

    String before = "try {} catch (Exception e) {\n" +
                    "      System.out.println(\"AA!\");\n" +
                    "}";

    String endOfLine = "try {} catch (Exception e) {\n" +
                       "    System.out.println(\"AA!\");\n" +
                       "}";

    String nextLine = "try {} catch (Exception e)\n" +
                      "{\n" +
                      "    System.out.println(\"AA!\");\n" +
                      "}";

    String nextLineShifted = "try {} catch (Exception e)\n" +
                             "    {\n" +
                             "    System.out.println(\"AA!\");\n" +
                             "    }";

    String nextLineShiftedEach = "try {} catch (Exception e)\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AA!\");\n" +
                                 "    }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleCatchBlock() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    String before = "try {\n" +
                    "        System.out.println(\"AA!\");\n" +
                    "} catch (Exception e) { System.out.println(\"AA!\"); }";

    String endOfLine = "try {\n" +
                   "    System.out.println(\"AA!\");\n" +
                   "} catch (Exception e) { System.out.println(\"AA!\"); }";

    String nextLine = "try\n" +
                             "{\n" +
                             "    System.out.println(\"AA!\");\n" +
                             "} catch (Exception e) { System.out.println(\"AA!\"); }";

    String nextLineShifted = "try\n" +
                                    "    {\n" +
                                    "    System.out.println(\"AA!\");\n" +
                                    "    } catch (Exception e) { System.out.println(\"AA!\"); }";

    String nextLineShiftedEach = "try\n" +
                                        "    {\n" +
                                        "        System.out.println(\"AA!\");\n" +
                                        "    } catch (Exception e) { System.out.println(\"AA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleCatchAndTryBlock() {
    String before = "try {} catch (Exception e) {      System.out.println(\"AA!\"); }";
    String after = "try {} catch (Exception e) { System.out.println(\"AA!\"); }";
    String afterExpanded = "try {\n" +
                           "} catch (Exception e) {\n" +
                           "    System.out.println(\"AA!\");\n" +
                           "}";

    doMethodTest(before, afterExpanded);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  public void testDoWhileStatement() {
    String before = "do {\n" +
                    "        System.out.println(\"AAA!\");\n" +
                    "} while (1 == 1);";

    String endOfLine = "do {\n" +
                       "    System.out.println(\"AAA!\");\n" +
                       "} while (1 == 1);";

    String nextLine = "do\n" +
                      "{\n" +
                      "    System.out.println(\"AAA!\");\n" +
                      "} while (1 == 1);";

    String nextLineShifted = "do\n" +
                             "    {\n" +
                             "    System.out.println(\"AAA!\");\n" +
                             "    } while (1 == 1);";

    String nextLineShiftedEach = "do\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AAA!\");\n" +
                                 "    } while (1 == 1);";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleDoWhileStatement() {
    String before = "do {     System.out.println(\"AAA!\"); } while (1 == 1);";
    String after = "do { System.out.println(\"AAA!\"); } while (1 == 1);";

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testForEachStatement() {
    String before = "for (String arg : args) {\n" +
                    "        System.out.println(\"AAA!\");\n" +
                    "}";

    String endOfLine = "for (String arg : args) {\n" +
                       "    System.out.println(\"AAA!\");\n" +
                       "}";

    String nextLine = "for (String arg : args)\n" +
                      "{\n" +
                      "    System.out.println(\"AAA!\");\n" +
                      "}";

    String nextLineShifted = "for (String arg : args)\n" +
                             "    {\n" +
                             "    System.out.println(\"AAA!\");\n" +
                             "    }";

    String nextLineShiftedEach = "for (String arg : args)\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AAA!\");\n" +
                                 "    }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleForEachStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    String before = "for (String arg : args) {     System.out.println(\"AAA!\"); }";
    String after = "for (String arg : args) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testForStatement() {
    String before = "for (int i = 0; i < 10; i = i + 3) {\n" +
                    "        System.out.println(\"AAA!\");\n" +
                    "}\n";

    String endOfLine = "for (int i = 0; i < 10; i = i + 3) {\n" +
                       "    System.out.println(\"AAA!\");\n" +
                       "}\n";

    String nextLine = "for (int i = 0; i < 10; i = i + 3)\n" +
                      "{\n" +
                      "    System.out.println(\"AAA!\");\n" +
                      "}\n";

    String nextLineShifted = "for (int i = 0; i < 10; i = i + 3)\n" +
                             "    {\n" +
                             "    System.out.println(\"AAA!\");\n" +
                             "    }\n";

    String nextLineShiftedEach = "for (int i = 0; i < 10; i = i + 3)\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AAA!\");\n" +
                                 "    }\n";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleForStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    String before = "for (int i = 0; i < 10; i = i + 3) {    System.out.println(\"AAA!\"); }";
    String after = "for (int i = 0; i < 10; i = i + 3) { System.out.println(\"AAA!\"); }";

    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }


  public void testSwitchStatement() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    String before = "switch (timeUnit) {\n" +
                    "        case DAYS:\n" +
                    "           break;\n" +
                    "   default:\n" +
                    "     break;\n" +
                    "}";

    String endOfLine = "switch (timeUnit) {\n" +
                       "    case DAYS:\n" +
                       "        break;\n" +
                       "    default:\n" +
                       "        break;\n" +
                       "}";

    String nextLine = "switch (timeUnit)\n" +
                      "{\n" +
                      "    case DAYS:\n" +
                      "        break;\n" +
                      "    default:\n" +
                      "        break;\n" +
                      "}";

    String nextLineShifted = "switch (timeUnit)\n" +
                             "    {\n" +
                             "    case DAYS:\n" +
                             "        break;\n" +
                             "    default:\n" +
                             "        break;\n" +
                             "    }";

    String nextLineShiftedEach = "switch (timeUnit)\n" +
                                 "    {\n" +
                                 "        case DAYS:\n" +
                                 "            break;\n" +
                                 "        default:\n" +
                                 "            break;\n" +
                                 "    }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSynchronizedStatement() {
    String before = "synchronized (this) {\n" +
                    "       System.out.println(\"AAA!\");\n" +
                    "}";

    String endOfLine = "synchronized (this) {\n" +
                       "    System.out.println(\"AAA!\");\n" +
                       "}";

    String nextLine = "synchronized (this)\n" +
                      "{\n" +
                      "    System.out.println(\"AAA!\");\n" +
                      "}";

    String nextLineShifted = "synchronized (this)\n" +
                             "    {\n" +
                             "    System.out.println(\"AAA!\");\n" +
                             "    }";

    String nextLineShiftedEach = "synchronized (this)\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AAA!\");\n" +
                                 "    }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleSynchronizedStatement() {
    String before = "synchronized (this) {     System.out.println(\"AAA!\"); }";
    String after = "synchronized (this) { System.out.println(\"AAA!\"); }";

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, after, after, after, after);
  }

  public void testWhileStatement() {
    String before = "while (true) {\n" +
                    "        System.out.println(\"AAA\");\n" +
                    "}";

    String endOfLine = "while (true) {\n" +
                       "    System.out.println(\"AAA\");\n" +
                       "}";

    String nextLine = "while (true)\n" +
                      "{\n" +
                      "    System.out.println(\"AAA\");\n" +
                      "}";

    String nextLineShifted = "while (true)\n" +
                             "    {\n" +
                             "    System.out.println(\"AAA\");\n" +
                             "    }";

    String nextLineShiftedEach = "while (true)\n" +
                                 "    {\n" +
                                 "        System.out.println(\"AAA\");\n" +
                                 "    }";

    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);

    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatterWithDifferentBraceStyles(before, endOfLine, nextLine, nextLineShifted, nextLineShiftedEach);
  }

  public void testSimpleWhileStatement() {
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
