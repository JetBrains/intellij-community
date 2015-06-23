/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfoBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

public class JavaAutoDetectIndentTest extends AbstractIndentAutoDetectionTest {

  @NotNull
  @Override
  protected String getFileNameWithExtension() {
    return getTestName(true) + ".java";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() +
           "/psi/autodetect/";
  }

  public void testNotIndentedComment() {
    doTestIndentSize(3);
  }

  public void testContinuationIndents_DoNotCount() {
    doTestIndentSize(2);
  }

  public void testContinuationIndent_JsonLiteral() {
    doTestIndentSize(4);
  }

  public void testContinuationIndents_InMethodParameters_DoNotCount() {
    doTestIndentSize(4);
  }

  public void testBigFileWithIndent2() {
    doTestIndentSize(2);
  }

  public void testBigFileWithIndent8() {
    doTestIndentSize(8);
  }

  public void testBigFileWithIndent4() {
    doTestIndentSize(4);
  }

  public void testFileWithTabs() {
    doTestTabsUsed();
  }

  public void testSimpleIndent() {
    doTestMaxUsedIndent(2, 6);
  }

  public void testManyComments() {
    doTestMaxUsedIndent(2, 6);
  }

  public void testManyZeroRelativeIndent() {
    doTestMaxUsedIndent(2);
  }

  public void testSmallFileWithIndent8() {
    doTestMaxUsedIndent(8);
  }

  public void testSmallFileWithTabs() {
    doTestTabsUsed();
  }

  public void testNoIndentsUseLanguageSpecificSettings() {
    CommonCodeStyleSettings.IndentOptions options = new CommonCodeStyleSettings.IndentOptions();
    options.USE_TAB_CHARACTER = true;

    doTestTabsUsed(options);
  }

  public void testSpacesToNumbers() throws Exception {
    String text = "     i\n" +
                  "    a\n" +
                  "          t\n";
    doTestLineToIndentMapping(text, 5, 4, 10);
  }

  public void testEmptyLines() throws Exception {
    doTestLineToIndentMapping("     \n\n\n", -1, -1, -1);
  }

  public void testSpacesInSimpleClass() {
    doTestLineToIndentMapping(
      "public class A {\n" +
      "\n" +
      "    public void test() {\n" +
      "      int a = 2;\n" +
      "    }\n" +
      "\n" +
      "    public void a() {\n" +
      "    }\n" +
      "}",
      0, -1, 4, 6, 4, -1, 4, 4, 0
    );
  }

  public void testComplexIndents() {
    doTestLineToIndentMapping(
      "class Test\n" +
      "{\n" +
      "  int a;\n" +
      "  int b;\n" +
      "  \n" +
      "  public void test() {\n" +
      "    int c;\n" +
      "  }\n" +
      "  \n" +
      "  public void run() {\n" +
      "    Runnable runnable = new Runnable() {\n" +
      "      @Override\n" +
      "      public void run() {\n" +
      "        System.out.println(\"Hello!\");\n" +
      "      }\n" +
      "    };\n" +
      "  }\n" +
      "}",
      0, 0, 2, 2, -1, 2, 4, 2, -1, 2, 4, 6, 6, 8, 6, 4, 2, 0
    );
  }

  private static void doTestLineToIndentMapping(@NotNull CharSequence text, int... spacesForLine) {
    List<LineIndentInfo> list = new LineIndentInfoBuilder(text, PlainTextLanguage.INSTANCE).build();
    Assert.assertEquals(list.size(), spacesForLine.length);
    for (int i = 0; i < spacesForLine.length; i++) {
      int indentSize = list.get(i).getIndentSize();
      Assert.assertEquals("Mismatch on line " + i, spacesForLine[i], indentSize);
    }
  }
}
