/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.diagnostic.LogMessageEx;
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

    LogMessageEx.error(LOG, messageBuffer.toString(), buffer.toString());
  }

}