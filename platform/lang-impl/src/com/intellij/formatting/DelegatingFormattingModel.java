// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;


public final class DelegatingFormattingModel implements FormattingModelEx {
  private final FormattingModel myBaseModel;
  private final Block myRootBlock;

  public DelegatingFormattingModel(FormattingModel model, Block block) {
    myBaseModel = model;
    myRootBlock = block;
  }

  @Override
  public @NotNull Block getRootBlock() {
    return myRootBlock;
  }

  @Override
  public @NotNull FormattingDocumentModel getDocumentModel() {
    return myBaseModel.getDocumentModel();
  }

  @Override
  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    return myBaseModel.replaceWhiteSpace(textRange, whiteSpace);
  }

  @Override
  public TextRange replaceWhiteSpace(TextRange textRange, ASTNode nodeAfter, String whiteSpace) {
    if (myBaseModel instanceof FormattingModelEx) {
      return ((FormattingModelEx) myBaseModel).replaceWhiteSpace(textRange, nodeAfter, whiteSpace);
    }
    return myBaseModel.replaceWhiteSpace(textRange, whiteSpace);
  }

  @Override
  public TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent) {
    return myBaseModel.shiftIndentInsideRange(node, range, indent);
  }

  @Override
  public void commitChanges() {
    myBaseModel.commitChanges();
  }
}
