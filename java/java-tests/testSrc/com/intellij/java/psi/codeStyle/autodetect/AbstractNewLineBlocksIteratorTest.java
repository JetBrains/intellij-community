// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
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
  protected Iterator<Block> newLineBlockIterator() {
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(getFile());
    Assert.assertNotNull(builder);

    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    FormattingModel model = builder.createModel(FormattingContext.create(getFile(), settings));

    Block root = model.getRootBlock();
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getFile());
    Assert.assertNotNull(document);

    return new NewLineBlocksIterator(root, document);
  }

}
