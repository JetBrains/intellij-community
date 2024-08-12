// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.ide.highlighter.FileTypeRegistrar;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Commenter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class StandardFileTypeRegistrar implements FileTypeRegistrar {
  @Override
  public void initFileType(final @NotNull FileType fileType) {
    if (fileType instanceof AbstractFileType) {
      init((AbstractFileType)fileType);
    }
  }

  private static void init(final @NotNull AbstractFileType abstractFileType) {
    SyntaxTable table = abstractFileType.getSyntaxTable();

    if (!StringUtil.isEmpty(table.getStartComment()) && !StringUtil.isEmpty(table.getEndComment()) ||
        !StringUtil.isEmpty(table.getLineComment())) {
      abstractFileType.setCommenter(new MyCommenter(abstractFileType));
    }

    TypedHandler.registerQuoteHandler(abstractFileType, new CustomFileTypeQuoteHandler());
  }

  private static final class MyCommenter implements Commenter {
    private final CustomSyntaxTableFileType myAbstractFileType;

    MyCommenter(final CustomSyntaxTableFileType abstractFileType) {

      myAbstractFileType = abstractFileType;
    }

    @Override
    public String getLineCommentPrefix() {
      return myAbstractFileType.getSyntaxTable().getLineComment();
    }

    @Override
    public String getBlockCommentPrefix() {
      return myAbstractFileType.getSyntaxTable().getStartComment();
    }

    @Override
    public String getBlockCommentSuffix() {
      return myAbstractFileType.getSyntaxTable().getEndComment();
    }

    @Override
    public String getCommentedBlockCommentPrefix() {
      return null;
    }

    @Override
    public String getCommentedBlockCommentSuffix() {
      return null;
    }
  }
}
