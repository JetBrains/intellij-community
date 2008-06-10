package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.SyntaxTableCompletionData;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.highlighter.FileTypeRegistrator;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Commenter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;

public class StandardFileTypeRegistrator implements FileTypeRegistrator {
  public void initFileType(final FileType fileType) {
    if (fileType instanceof AbstractFileType) {
      init(((AbstractFileType)fileType));
    }
  }

  private static void init(final AbstractFileType abstractFileType) {
    SyntaxTable table = abstractFileType.getSyntaxTable();
    CompletionUtil.registerCompletionData(abstractFileType,new SyntaxTableCompletionData(table));

    if (!isEmpty(table.getStartComment()) && !isEmpty(table.getEndComment()) ||
        !isEmpty(table.getLineComment())) {
      abstractFileType.setCommenter(new MyCommenter(abstractFileType));
    }

    if (table.isHasBraces() || table.isHasBrackets() || table.isHasParens()) {
      BraceMatchingUtil.registerBraceMatcher(abstractFileType,new CustomFileTypeBraceMatcher());
    }

    TypedHandler.registerQuoteHandler(abstractFileType, new CustomFileTypeQuoteHandler());

  }

  private static class MyCommenter implements Commenter {
    private final AbstractFileType myAbstractFileType;

    public MyCommenter(final AbstractFileType abstractFileType) {

      myAbstractFileType = abstractFileType;
    }

    public String getLineCommentPrefix() {
      return myAbstractFileType.getSyntaxTable().getLineComment();
    }

    public static boolean isLineCommentPrefixOnZeroColumn() {
      return true;
    }

    public String getBlockCommentPrefix() {
      return myAbstractFileType.getSyntaxTable().getStartComment();
    }

    public String getBlockCommentSuffix() {
      return myAbstractFileType.getSyntaxTable().getEndComment();
    }
  }

  private static boolean isEmpty(String str) {
    return str==null || str.length() == 0;
  }

}
