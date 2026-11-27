// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.autodetect.FormatterBasedLineIndentInfoBuilder;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.psi.autodetect.IndentAutoDetectionTestUtil.doTestIndentSize;
import static com.intellij.psi.autodetect.IndentAutoDetectionTestUtil.doTestMaxUsedIndent;
import static com.intellij.psi.autodetect.IndentAutoDetectionTestUtil.doTestTabsUsed;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaAutoDetectIndentTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData/psi/autodetect/");

  @Test
  public void testNotIndentedComment() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 3);
  }

  @Test
  public void testContinuationIndents_DoNotCount() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 2);
  }

  @Test
  public void testContinuationIndent_JsonLiteral() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 4);
  }

  @Test
  public void testContinuationIndents_InMethodParameters_DoNotCount() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 4);
  }

  @Test
  public void testBigFileWithIndent2() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 2);
  }

  @Test
  public void testBigFileWithIndent8() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 8);
  }

  @Test
  public void testBigFileWithIndent4() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 4);
  }

  @Test
  public void testFileWithTabs() {
    doTestTabsUsed(codeInsightFixture.get(), getFileNameWithExtension());
  }

  @Test
  public void testSimpleIndent() {
    doTestMaxUsedIndent(codeInsightFixture.get(), getFileNameWithExtension(), 2, 6);
  }
  
  @Test
  public void testTabsAndIndents() {
    doTestIndentSize(codeInsightFixture.get(), getFileNameWithExtension(), 4);
  }

  @Test
  public void testManyComments() {
    doTestMaxUsedIndent(codeInsightFixture.get(), getFileNameWithExtension(), 2, 6);
  }

  @Test
  public void testManyZeroRelativeIndent() {
    doTestMaxUsedIndent(codeInsightFixture.get(), getFileNameWithExtension(), 2);
  }

  @Test
  public void testSmallFileWithIndent8() {
    doTestMaxUsedIndent(codeInsightFixture.get(), getFileNameWithExtension(), 8);
  }

  @Test
  public void testSmallFileWithTabs() {
    doTestTabsUsed(codeInsightFixture.get(), getFileNameWithExtension());
  }

  @Test
  public void testNoIndentsUseLanguageSpecificSettings() {
    CommonCodeStyleSettings.IndentOptions options = new CommonCodeStyleSettings.IndentOptions();
    options.USE_TAB_CHARACTER = true;

    doTestTabsUsed(codeInsightFixture.get(), getFileNameWithExtension(), options);
  }
  
  @Test
  public void testSpacesInSimpleClass() {
    doTestLineToIndentMapping(
      """
        public class A {
            public void test() {
              int a = 2;
            }
            public void a() {
            }
        }""",
      0, 4, 6, 4, 4, 4, 0
    );
  }

  @Test
  public void testComplexIndents() {
    doTestLineToIndentMapping(
      """
        class Test
        {
          int a;
          int b;
          public void test() {
            int c;
          }
          public void run() {
            Runnable runnable = new Runnable() {
              @Override
              public void run() {
                System.out.println("Hello!");
              }
            };
          }
        }""",
      0, 0, 2, 2, 2, 4, 2, 2, 4, 6, 6, 8, 6, 4, 2, 0
    );
  }

  private @NotNull String getFileNameWithExtension() {
    return codeInsightFixture.get().getTestName(true) + ".java";
  }

  private void doTestLineToIndentMapping(@NotNull String text, int... spacesForLine) {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    PsiFile file = fixture.configureByText("x.java", text);
    List<LineIndentInfo> list = ReadAction.computeBlocking(() -> {
      Document document = PsiDocumentManager.getInstance(fixture.getProject()).getDocument(file);
      FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

      assertNotNull(document);
      assertNotNull(builder);

      FormattingModel model =
        builder.createModel(FormattingContext.create(file, CodeStyle.getSettings(fixture.getProject())));
      Block block = model.getRootBlock();
      return new FormatterBasedLineIndentInfoBuilder(document, block, null).build();
    });

    assertEquals(list.size(), spacesForLine.length);
    for (int i = 0; i < spacesForLine.length; i++) {
      int indentSize = list.get(i).getIndentSize();
      assertEquals(spacesForLine[i], indentSize, "Mismatch on line " + i);
    }
  }
}
