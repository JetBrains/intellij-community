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
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.impl.DebugUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class RangesAssert {
  private static final Logger LOG = Logger.getInstance(RangesAssert.class);
  
  public void assertInvalidRanges(final int startOffset, final int newEndOffset, FormattingDocumentModel model, String message) {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("Invalid formatting blocks:").append(message).append("\n");
    buffer.append("Start offset:");
    buffer.append(startOffset);
    buffer.append(" end offset:");
    buffer.append(newEndOffset);
    buffer.append("\n");

    int minOffset = Math.max(Math.min(startOffset, newEndOffset) - 20, 0);
    int maxOffset = Math.min(Math.max(startOffset, newEndOffset) + 20, model.getTextLength());

    buffer.append("Affected text fragment:[").append(minOffset).append(",").append(maxOffset).append("] - '")
      .append(model.getText(new TextRange(minOffset, maxOffset))).append("'\n");

    final StringBuilder messageBuffer =  new StringBuilder();
    messageBuffer.append("Invalid ranges during formatting");
    if (model instanceof FormattingDocumentModelImpl) {
      messageBuffer.append(" in ").append(((FormattingDocumentModelImpl)model).getFile().getLanguage());
    }

    buffer.append("File text:(").append(model.getTextLength()).append(")\n'");
    buffer.append(model.getText(new TextRange(0, model.getTextLength())).toString());
    buffer.append("'\n");
    buffer.append("model (").append(model.getClass()).append("): ").append(model);

    Throwable currentThrowable = new Throwable();
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
      currentThrowable = makeLanguageStackTrace(currentThrowable, file);
    }

    LogMessageEx.error(LOG, messageBuffer.toString(), currentThrowable, buffer.toString());
  }
  
  private static Throwable makeLanguageStackTrace(@NotNull Throwable currentThrowable, @NotNull PsiFile file) {
    Throwable langThrowable = new Throwable();
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder == null) return currentThrowable;
    Class builderClass = builder.getClass();
    Class declaringClass = builderClass.getDeclaringClass();
    String guessedFileName = (declaringClass == null ? builderClass.getSimpleName() : declaringClass.getSimpleName())  + ".java";
    StackTraceElement ste = new StackTraceElement(builder.getClass().getName(), "createModel", guessedFileName, 1);
    StackTraceElement[] originalStackTrace = currentThrowable.getStackTrace();
    StackTraceElement[] modifiedStackTrace = new StackTraceElement[originalStackTrace.length + 1];
    System.arraycopy(originalStackTrace, 0, modifiedStackTrace, 1, originalStackTrace.length);
    modifiedStackTrace[0] = ste;
    langThrowable.setStackTrace(modifiedStackTrace);
    return langThrowable;
  }
  
}