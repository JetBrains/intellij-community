package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public interface FormattingModel {
  
  @NotNull Block getRootBlock();
  @NotNull FormattingDocumentModel getDocumentModel();
  /**
   * @param textRange
   * @param whiteSpace
   */     
  void replaceWhiteSpace(TextRange textRange, String whiteSpace);

  TextRange shiftIndentInsideRange(TextRange range, int indent);

  void commitChanges();
}
