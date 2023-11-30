// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class DummyFormattingModel implements FormattingModel {
  private final Block myRootBlock;
  private final FormattingDocumentModel myDocumentModel;

  DummyFormattingModel(@NotNull PsiElement element) {
    myRootBlock = new AbstractBlock(element.getNode(), null, null) {
      @Override
      protected List<Block> buildChildren() {
        return EMPTY;
      }

      @Override
      public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return null;
      }

      @Override
      public boolean isLeaf() {
        return true;
      }
    };

    Document document = FormattingDocumentModelImpl.createOn(element.getContainingFile()).getDocument();
    myDocumentModel = new FormattingDocumentModel() {
      @Override
      public int getLineNumber(int offset) {
        return document.getLineNumber(offset);
      }

      @Override
      public int getLineStartOffset(int line) {
        return document.getLineStartOffset(line);
      }

      @Override
      public @NotNull CharSequence getText(TextRange textRange) {
        return document.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
      }

      @Override
      public int getTextLength() {
        return document.getTextLength();
      }

      @Override
      public @NotNull Document getDocument() {
        return document;
      }

      @Override
      public boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset) {
        return false;
      }

      @Override
      public @NotNull CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                               int startOffset,
                                                               int endOffset,
                                                               ASTNode nodeAfter, boolean changedViaPsi) {
        return whiteSpaceText;
      }
    };
  }

  @Override
  public @NotNull Block getRootBlock() {
    return myRootBlock;
  }

  @Override
  public @NotNull FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  @Override
  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    return textRange;
  }

  @Override
  public TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent) {
    return range;
  }

  @Override
  public void commitChanges() {
  }
}
