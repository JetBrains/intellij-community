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

import com.intellij.formatting.*;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.common.NewLineBlocksIterator;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Iterator;

public abstract class AbstractNewLineBlocksIteratorTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  protected String getFileName() {
    return getTestName(true);
  }

  protected void checkStartOffsets(int[] newLineStartOffset) {
    checkStartOffsets(newLineStartOffset, newLineBlockIterator());
  }
  
  protected void checkStartOffsets(int[] newLineStartOffsets, Iterator<Block> iterator) {
    int i = 0;
    while (iterator.hasNext()) {
      Block next = iterator.next();
      Assert.assertTrue("Extra new line block found: " + next.getTextRange(), i < newLineStartOffsets.length);
      Assert.assertEquals("Block start offset do not match ", newLineStartOffsets[i++], next.getTextRange().getStartOffset());
    }
    Assert.assertEquals("Not detected new line block start offset ", i, newLineStartOffsets.length);
  }

  @NotNull
  protected static Iterator<Block> newLineBlockIterator() {
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(myFile);
    Assert.assertNotNull(builder);

    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
    FormattingModel model = builder.createModel(myFile, settings);

    Block root = model.getRootBlock();
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(myFile);
    Assert.assertNotNull(document);

    return new NewLineBlocksIterator(root, document);
  }

}
