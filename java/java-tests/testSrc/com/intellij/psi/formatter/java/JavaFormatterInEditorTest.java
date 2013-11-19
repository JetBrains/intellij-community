/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Is intended to test formatting in editor behavior, i.e. check how formatting affects things like caret position, selection etc.
 *
 * @author Denis Zhdanov
 * @since 6/1/11 6:17 PM
 */
public class JavaFormatterInEditorTest extends LightPlatformCodeInsightTestCase {

  public void testCaretPositionOnLongLineWrapping() throws IOException {
    // Inspired by IDEA-70242
    getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE).WRAP_LONG_LINES = true;
    getCurrentCodeStyleSettings().RIGHT_MARGIN = 40;
    doTest(
      "import static java.util.concurrent.atomic.AtomicInteger.*;\n" +
      "\n" +
      "class <caret>Test {\n" +
      "}",

      "import static java.util.concurrent\n" +
      "        .atomic.AtomicInteger.*;\n" +
      "\n" +
      "class <caret>Test {\n" +
      "}"
    );
  }

  public void testCaretPositionPreserved_WhenOnSameLineWithWhiteSpacesOnly() throws IOException {
    String text = "class Test {\n" +
                  "    void test() {\n" +
                  "         <caret>\n" +
                  "    }\n" +
                  "}";
    doTest(text, text);

    String before = "class Test {\n" +
                   "    void test() {\n" +
                   "         <caret>       \n" +
                   "    }\n" +
                   "}";
    doTest(before, text);
  }

  public void testCaretPositionPreserved_WhenSomeFormattingNeeded() throws IOException {
    String before = "public class Test {\n" +
                    "        int a;\n" +
                    "    \n" +
                    "    public static void main(String[] args) {\n" +
                    "                     <caret>\n" +
                    "    }\n" +
                    "\n" +
                    "    static final long j = 2;\n" +
                    "}";
    String after = "public class Test {\n" +
                   "    int a;\n" +
                   "\n" +
                   "    public static void main(String[] args) {\n" +
                   "                     <caret>\n" +
                   "    }\n" +
                   "\n" +
                   "    static final long j = 2;\n" +
                   "}";
    doTest(before, after);

    before = "public class Test {\n" +
             "        int a;\n" +
             "    \n" +
             "    public static void main(String[] args) {\n" +
             "                     <caret>           \n" +
             "    }\n" +
             "\n" +
             "    static final long j = 2;\n" +
             "}";
    doTest(before, after);
  }

  public void doTest(@NotNull String before, @NotNull String after) throws IOException {
    configureFromFileText(getTestName(false) + ".java", before);
    CodeStyleManager.getInstance(getProject()).reformatText(getFile(), 0, getEditor().getDocument().getTextLength());
    checkResultByText(after);
  }
}