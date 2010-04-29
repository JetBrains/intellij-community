/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

/**
 * Is intended to hold specific java formatting tests for 'blank lines' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:33:00 PM
 */
public class JavaFormattingBlankLinesTest extends AbstractJavaFormattingTest {

  public void testBlankLinesAroundClassInitializationBlock() throws Exception {
    getSettings().BLANK_LINES_AROUND_METHOD = 3;
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

}
