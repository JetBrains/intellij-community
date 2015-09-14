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

import com.intellij.JavaTestUtil;
import com.intellij.formatting.FormattingModelXmlReader;
import com.intellij.formatting.TestBlock;
import com.intellij.formatting.TestFormattingModel;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class FormatterBasedLineInfoBuilderTest extends LightPlatformCodeInsightTestCase {
  
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() +
           "/psi/autodetect/";
  }
  
  public void testKotlinComment() throws IOException, JDOMException {
    String text = "/**\n" +
                  " *\n" +
                  " */";

    List<LineIndentInfo> infos = getLineInfos(text);
    assertLinesWithNormalIndent(infos, 0);
  }
  
  public void testXmlContinuationWithoutFirst() throws IOException, JDOMException {
    String text = "<idea-plugin>\n" +
                  "    <name/>\n" +
                  "    <id/>\n" +
                  "</idea-plugin>";

    List<LineIndentInfo> infos = getLineInfos(text);
    assertLinesWithNormalIndent(infos, 4);
  }

  private static void assertLinesWithNormalIndent(List<LineIndentInfo> infos, int expected) {
    long linesWithNormalIndent = infos.stream().filter(LineIndentInfo::isLineWithNormalIndent).count();
    assertEquals(expected, linesWithNormalIndent);
  }

  private List<LineIndentInfo> getLineInfos(String text) throws IOException, JDOMException {
    String file = getTestName(false) + ".xml";
    TestFormattingModel model = new TestFormattingModel(text);
    Document document = model.getDocument();
    TestBlock block = new FormattingModelXmlReader(model).readTestBlock(getTestDataPath(), file);
    FormatterBasedLineIndentInfoBuilder builder = new FormatterBasedLineIndentInfoBuilder(document, block);
    return builder.build();
  }
}