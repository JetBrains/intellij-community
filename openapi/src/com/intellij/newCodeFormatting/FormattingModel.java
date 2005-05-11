package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public interface FormattingModel {
  
  @NotNull Block getRootBlock();
  @NotNull FormattingDocumentModel getDocumentModel();
  /**
   * @return new text range length for block after the white space
   * @throws IncorrectOperationException
   * @param textRange
   * @param whiteSpace
   * @param blockLength - length of the block after white space 
   */     
  int replaceWhiteSpace(TextRange textRange, String whiteSpace, final int blockLength) throws IncorrectOperationException;
}
