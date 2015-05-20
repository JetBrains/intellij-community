/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.List;
import java.util.Set;


public class ContinuationIndentDetectorTest extends TestCase {
  private ContinuationIndentDetector myContinuationIndentDetector;
  private List<Integer> myLineStartOffsets;

  public void configure(String text) {
    myContinuationIndentDetector = new ContinuationIndentDetector(text);
    myLineStartOffsets = calculateLineStartOffsets(text);
  }

  public void testContinuationIndentInsideParenth() {
    configure(
      "test(\n" +
      "         \"aaaaa\" + \n" +
      "         \"bbbbb\");\n" +
      "int a = 2;"
    );

    doCheckLinesWithContinuationIndents(1, 2);
  }

  public void testContinuationIndent_IfBracesWithinStringLiteral() {
    configure(
      "tester(\"{\",\n" +
      "       \"welcome\",\n" +
      "       \"my own\",\n" +
      "       \"string literal\")\n"
    );

    doCheckLinesWithContinuationIndents(1, 2, 3);
  }

  public void testNoContinuationIndent_OnLineStartingWithRightParenth() {
    configure(
      "test(\n" +
      "         \"aaaaa\" + \n" +
      "         \"bbbbb\"\n" +
      ");"
    );

    doCheckLinesWithContinuationIndents(1, 2, 3);
  }

  public void testNoContinuationIndents_BetweenBraces() {
    configure(
      "import java.lang.Override;\n" +
      "import java.lang.Runnable;\n" +
      "\n" +
      "class T {\n" +
      "  \n" +
      "  void test() {\n" +
      "    int a;\n" +
      "    int b;\n" +
      "    Runnable runnable = new Runnable() {\n" +
      "      @Override\n" +
      "      public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  \n" +
      "}"
    );
    doCheckLinesWithContinuationIndents();
  }

  public void testMixedBraces() {
    configure(
      "import java.lang.Runnable;\n" +
      "\n" +
      "class R {\n" +
      "\n" +
      "\n" +
      "  void test() {\n" +
      "    kuu(new Runnable() {\n" +
      "          @Override\n" +
      "          public void run() {\n" +
      "          }\n" +
      "        },\n" +
      "        new Runnable() {\n" +
      "          @Override\n" +
      "          public void run() {\n" +
      "          }\n" +
      "        }\n" +
      "    );\n" +
      "  }\n" +
      "\n" +
      "  void kuu(Runnable a, Runnable b) {\n" +
      "  }\n" +
      "}"
    );
    doCheckLinesWithContinuationIndents(11, 16);
  }

  public void testClosingBracesAtLineStart() {
    configure("class Test {\n" +
              "  public static void main(String[] args) {\n" +
              "    tree.put(\"Web Development\", Pair.create(PlatformImplIcons.WebDevelopment, Arrays.asList(\n" +
              "        \"HTML:HtmlTools\",\n" +
              "        \"com.intellij.css\",\n" +
              "        \"JavaScript:JavaScript,JSIntentionPowerPack\",\n" +
              "        \"com.jetbrains.restClient\"\n" +
              "    )));\n" +
              "  }\n" +
              "}");
    doCheckLinesWithContinuationIndents(3, 4, 5, 6, 7);
  }

  public void testInsideParenth() {
    configure("@State(\n" +
              "  name = \"AndroidRemoteDataBaseManager\",\n" +
              "  storages = {\n" +
              "    @Storage(\n" +
              "      file = StoragePathMacros.APP_CONFIG + \"/androidRemoteDatabases.xml\"\n" +
              "    )}\n" +
              ")\n" +
              "class T {}");
    doCheckLinesWithContinuationIndents(1, 2, 4, 5, 6);
  }

  private void doCheckLinesWithContinuationIndents(Integer... linesWithContinuationIndents) {
    Set<Integer> continuationLines = ContainerUtil.newHashSet(linesWithContinuationIndents);

    for (int currentLine = 0; currentLine < myLineStartOffsets.size(); currentLine++) {
      Integer lineStartOffset = myLineStartOffsets.get(currentLine);
      boolean isContinuation = myContinuationIndentDetector.isContinuationIndent();
      if (continuationLines.contains(currentLine)) {
        assertTrue("Line " +  currentLine + " should start with continuation indent", isContinuation);
      }
      else {
        assertTrue("Line " + currentLine + " should not start with continuation indent" ,!isContinuation);
      }
      myContinuationIndentDetector.feedLineStartingAt(lineStartOffset);
    }
  }

  private List<Integer> calculateLineStartOffsets(String text) {
    List<Integer> lineStartOffsets = ContainerUtil.newArrayList();
    lineStartOffsets.add(0);

    int lineStart = 0;
    int lineFeed;
    while ((lineFeed = text.indexOf('\n', lineStart)) > 0) {
      lineStart = lineFeed + 1;
      lineStartOffsets.add(lineStart);
    }

    return lineStartOffsets;
  }
}