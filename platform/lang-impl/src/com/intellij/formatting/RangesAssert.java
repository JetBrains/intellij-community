// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.impl.DebugUtil;

import java.util.List;

class RangesAssert {
  private static final Logger LOG = Logger.getInstance(RangesAssert.class);
  
  public void assertInvalidRanges(final int startOffset, final int newEndOffset, FormattingDocumentModel model, String message) {
    final StringBuilder buffer = new StringBuilder();

    int minOffset = Math.max(Math.min(startOffset, newEndOffset), 0);
    int maxOffset = Math.min(Math.max(startOffset, newEndOffset), model.getTextLength());

    final StringBuilder messageBuffer =  new StringBuilder();
    messageBuffer.append(message);
    if (model instanceof FormattingDocumentModelImpl) {
      messageBuffer.append(" in #").append(((FormattingDocumentModelImpl)model).getFile().getLanguage().getDisplayName());
    }
    messageBuffer.append(" #formatter");
    messageBuffer.append("\nRange: [").append(startOffset).append(",").append(newEndOffset).append("], ")
                 .append("text fragment: [").append(minOffset).append(",").append(maxOffset).append("] - '")
                 .append(model.getText(new TextRange(minOffset, maxOffset))).append("'\n");

    buffer.append("File text:(").append(model.getTextLength()).append(")\n'");
    buffer.append(model.getText(new TextRange(0, model.getTextLength())).toString());
    buffer.append("'\n");
    buffer.append("model (").append(model.getClass()).append("): ").append(model);

    if (model instanceof FormattingDocumentModelImpl) {
      final FormattingDocumentModelImpl modelImpl = (FormattingDocumentModelImpl)model;
      buffer.append("Psi Tree:\n");
      final PsiFile file = modelImpl.getFile();
      final List<PsiFile> roots = file.getViewProvider().getAllFiles();
      for (PsiFile root : roots) {
        buffer.append("Root ");
        DebugUtil.treeToBuffer(buffer, root.getNode(), 0, false, true, true, true);
      }
      buffer.append('\n');
    }

    LOG.error(messageBuffer.toString(), new Throwable(), AttachmentFactory.createContext(buffer));
  }
}