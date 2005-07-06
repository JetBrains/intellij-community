package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

public interface FormattingDocumentModel {
  int getLineNumber(int offset);
  int getLineStartOffset(int line);
  CharSequence getText(final TextRange textRange);
  int getTextLength();
}
