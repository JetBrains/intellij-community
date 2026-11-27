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
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.engine.FormatterEngineTestsKt;
import com.intellij.formatting.engine.TestData;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.NewLineBlocksIterator;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class NewLineBlocksIteratorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, Collections::emptyList,
                               LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/psi/autodetect/"));

  @Test
  public void testSimpleFileNewBlockOffsetDetection() {
    daemonFixture.get().configureByFile(getFileName() + ".java");

    int[] newLineBlocksStartOffsets = new int[] {
       0, 3, 30, 60, 82, 107, 136, 154, 184, 220, 226, 232, 237
    };

    ReadAction.runBlocking(() -> checkStartOffsets(newLineBlocksStartOffsets));
  }

  @Test
  public void testDoNotReverseBlocks() {
    daemonFixture.get().configureByFile(getFileName() + ".xml");

    int[] newLineBlocksStartOffsets = new int[] {
      0, 39, 48, 118, 177, 235, 243
    };

    ReadAction.runBlocking(() -> checkStartOffsets(newLineBlocksStartOffsets));
  }
  
  @Test
  public void testFirstBlockOnNewLineNotStartsIt() {
    String text = "[]varx []=r'''\n" +
                  "'''";

    TestData data = FormatterEngineTestsKt.extractFormattingTestData(text);
    Document document = EditorFactory.getInstance().createDocument(data.getTextToFormat());
    NewLineBlocksIterator iterator = new NewLineBlocksIterator(data.getRootBlock(), document);

    checkStartOffsets(new int[] {0}, iterator);
  }
  
  
  @Test
  public void testBigFileWithOnlyErrorElements_DoNotProduceSOE() {
    daemonFixture.get().configureByFile(getFileName() + ".java");
    ReadAction.runBlocking(() -> {
      Iterator<Block> iterator = newLineBlockIterator();
      while (iterator.hasNext()) {
        iterator.next();
      }
    });
  }

  private @NotNull String getFileName() {
    return daemonFixture.get().getFixture().getTestName(true);
  }

  private void checkStartOffsets(int[] newLineStartOffset) {
    checkStartOffsets(newLineStartOffset, newLineBlockIterator());
  }

  private static void checkStartOffsets(int[] newLineStartOffsets, Iterator<Block> iterator) {
    int i = 0;
    while (iterator.hasNext()) {
      Block next = iterator.next();
      assertTrue(i < newLineStartOffsets.length, "Extra new line block found: " + next.getTextRange());
      assertEquals(newLineStartOffsets[i++], next.getTextRange().getStartOffset(), "Block start offset do not match ");
    }
    assertEquals(i, newLineStartOffsets.length, "Not detected new line block start offset ");
  }

  private @NotNull Iterator<Block> newLineBlockIterator() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    PsiFile file = myFixture.getFile();
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    assertNotNull(builder);

    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    FormattingModel model = builder.createModel(FormattingContext.create(file, settings));

    Block root = model.getRootBlock();
    Document document = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(file);
    assertNotNull(document);

    return new NewLineBlocksIterator(root, document);
  }

}
