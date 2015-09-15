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
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModelXmlReader;
import com.intellij.formatting.TestBlock;
import com.intellij.formatting.TestFormattingModel;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.formatter.common.NewLineBlocksIterator;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Iterator;

public class NewLineBlocksIteratorTest extends AbstractNewLineBlocksIteratorTest {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() +
           "/psi/autodetect/";
  }

  public void testSimpleFileNewBlockOffsetDetection() {
    configureByFile(getFileName() + ".java");

    int[] newLineBlocksStartOffsets = new int[] {
       0, 3, 30, 60, 82, 107, 136, 154, 184, 220, 226, 232, 237
    };

    checkStartOffsets(newLineBlocksStartOffsets);
  }

  public void testDoNotReverseBlocks() {
    configureByFile(getFileName() + ".xml");

    int[] newLineBlocksStartOffsets = new int[] {
      0, 39, 48, 118, 177, 235, 243
    };

    checkStartOffsets(newLineBlocksStartOffsets);
  }
  
  public void testFirstBlockOnNewLineNotStartsIt() throws IOException, JDOMException {
    String text = "var x = r'''\n" +
                  "''';";
    
    Iterator<Block> it = newIteratorFromTestFormattingModel(text);
    checkStartOffsets(new int[] {0}, it);
  }
  
  
  public void testBigFileWithOnlyErrorElements_DoNotProduceSOE() {
    configureByFile(getFileName() + ".java");
    Iterator<Block> iterator = newLineBlockIterator();
    while (iterator.hasNext()) {
      iterator.next();
    }
  }
  
  protected Iterator<Block> newIteratorFromTestFormattingModel(String text) throws IOException, JDOMException {
    TestFormattingModel model = new TestFormattingModel(text);
    Document document = model.getDocument();
    TestBlock block = new FormattingModelXmlReader(model).readTestBlock(getTestDataPath(), getFileName() + ".xml");
    return new NewLineBlocksIterator(block, document);
  }
}
