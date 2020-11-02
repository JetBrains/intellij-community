// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.autodetect.FormatterBasedLineIndentInfoBuilder;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
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
  
  public void testTabsAndIndents() {
    doTestIndentSize(4);
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
  
  public void testSpacesInSimpleClass() {
    doTestLineToIndentMapping(
      "public class A {\n" +
      "    public void test() {\n" +
      "      int a = 2;\n" +
      "    }\n" +
      "    public void a() {\n" +
      "    }\n" +
      "}",
      0, 4, 6, 4, 4, 4, 0
    );
  }

  public void testComplexIndents() {
    doTestLineToIndentMapping(
      "class Test\n" +
      "{\n" +
      "  int a;\n" +
      "  int b;\n" +
      "  public void test() {\n" +
      "    int c;\n" +
      "  }\n" +
      "  public void run() {\n" +
      "    Runnable runnable = new Runnable() {\n" +
      "      @Override\n" +
      "      public void run() {\n" +
      "        System.out.println(\"Hello!\");\n" +
      "      }\n" +
      "    };\n" +
      "  }\n" +
      "}",
      0, 0, 2, 2, 2, 4, 2, 2, 4, 6, 6, 8, 6, 4, 2, 0
    );
  }

  private void doTestLineToIndentMapping(@NotNull String text, int... spacesForLine) {
    configureFromFileText("x.java", text);
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getFile());
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(getFile());

    Assert.assertNotNull(document);
    Assert.assertNotNull(builder);

    FormattingModel model =
      builder.createModel(FormattingContext.create(getFile(), CodeStyle.getSettings(getProject())));
    Block block = model.getRootBlock();
    List<LineIndentInfo> list = new FormatterBasedLineIndentInfoBuilder(document, block, null).build();

    Assert.assertEquals(list.size(), spacesForLine.length);
    for (int i = 0; i < spacesForLine.length; i++) {
      int indentSize = list.get(i).getIndentSize();
      Assert.assertEquals("Mismatch on line " + i, spacesForLine[i], indentSize);
    }
  }
}
