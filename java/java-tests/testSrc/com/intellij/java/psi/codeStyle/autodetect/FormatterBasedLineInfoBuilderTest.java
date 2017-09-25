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

import com.intellij.JavaTestUtil;
import com.intellij.formatting.engine.FormatterEngineTestsKt;
import com.intellij.formatting.engine.TestData;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.psi.codeStyle.autodetect.FormatterBasedLineIndentInfoBuilder;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FormatterBasedLineInfoBuilderTest extends LightPlatformCodeInsightTestCase {
  
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() +
           "/psi/autodetect/";
  }
  
  public void testKotlinComment() {
    String text = "[i_cont]([i_norm]/**\n" +
                  " [i_norm]([i_space_1]*)\n" +
                  " [i_space_1]*/)";

    List<LineIndentInfo> infos = getLineInfos(text);
    assertLinesWithNormalIndent(infos, 0);
  }
  
  public void testXmlContinuationWithoutFirst() {
    String text = "[i_none]<idea-plugin>\n" +
                  "    [i_none]([i_norm]([i_none]<[i_none]name/>)\n" +
                  "             [i_norm]([i_none]([]<[i_none]id/>)))\n" +
                  "[i_none]</idea-plugin>";

    List<LineIndentInfo> infos = getLineInfos(text);
    assertLinesWithNormalIndent(infos, 4);
  }

  private static void assertLinesWithNormalIndent(List<LineIndentInfo> infos, int expected) {
    long linesWithNormalIndent = infos.stream().filter(LineIndentInfo::isLineWithNormalIndent).count();
    assertEquals(expected, linesWithNormalIndent);
  }

  private static List<LineIndentInfo> getLineInfos(String text) {
    TestData data = FormatterEngineTestsKt.extractFormattingTestData(text);
    Document document = EditorFactory.getInstance().createDocument(data.getTextToFormat());
    FormatterBasedLineIndentInfoBuilder builder = new FormatterBasedLineIndentInfoBuilder(document, data.getRootBlock(), null);
    return builder.build();
  }
}