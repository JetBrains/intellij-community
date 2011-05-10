package com.intellij.codeInsight.generation;

import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author vnikolaenko
 */
public abstract class ContextDependentCommenter implements Commenter, SelfManagingCommenter<ContextDependentCommenter.MyCommenterData> {
  @Override
  public String getLineCommentPrefix() {
    return "";
  }

  @Override
  public String getBlockCommentPrefix() {
    return "";
  }

  @Override
  public String getBlockCommentSuffix() {
    return "";
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return "";
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return "";
  }

  public abstract MyCommenterData createCommenterData(int startOffset, int endOffset, Document document,
                                                      PsiFile file, boolean isLineComment);

  @Override
  public MyCommenterData createLineCommentingState(int startLine,
                                                   int endLine,
                                                   @NotNull Document document,
                                                   @NotNull PsiFile file) {
    return createCommenterData(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine), document, file, true);
  }

  @Override
  public MyCommenterData createBlockCommentingState(int selectionStart,
                                                    int selectionEnd,
                                                    @NotNull Document document,
                                                    @NotNull PsiFile file) {
    return createCommenterData(selectionStart, selectionEnd, document, file, false);
  }

  @Override
  public void commentLine(int line, int offset, @NotNull Document document, @NotNull MyCommenterData data) {
    final int originalLineEndOffset = document.getLineEndOffset(line);
    document.insertString(originalLineEndOffset, data.getCommentSuffix());
    document.insertString(offset, data.getCommentPrefix());
  }

  @Override
  public void uncommentLine(int line, int offset, @NotNull Document document, @NotNull MyCommenterData data) {
    int rangeEnd = document.getLineEndOffset(line);

    final String commentSuffix = data.getCommentSuffix();
    document.deleteString(
      rangeEnd - commentSuffix.length(),
      rangeEnd);
    final String commentPrefix = data.getCommentPrefix();
    document.deleteString(
      offset,
      offset + commentPrefix.length());
  }

  @Override
  public boolean isLineCommented(int line,
                                 int offset,
                                 @NotNull Document document,
                                 @NotNull MyCommenterData data) {
    int rangeEnd = document.getLineEndOffset(line);

    boolean commented = true;

    final String commentSuffix = data.getCommentSuffix();
    if (!CharArrayUtil.regionMatches(document.getCharsSequence(),
                                     rangeEnd - commentSuffix.length(),
                                     commentSuffix
    )) {
      commented = false;
    }

    final String commentPrefix = data.getCommentPrefix();
    if (commented && !CharArrayUtil.regionMatches(
          document.getCharsSequence(),
          offset,
          commentPrefix
        )) {
      commented = false;
    }
    return commented;
  }

  @Override
  public String getCommentPrefix(int line, @NotNull Document document, @NotNull MyCommenterData data) {
    return data.getCommentPrefix();
  }

  @Override
  public TextRange getBlockCommentRange(int selectionStart,
                                        int selectionEnd,
                                        @NotNull Document document,
                                        @NotNull MyCommenterData data) {
    String commentSuffix = data.getCommentSuffix();
    String commentPrefix = data.getCommentPrefix();

    selectionStart = CharArrayUtil.shiftForward(document.getCharsSequence(), selectionStart, " \t\n");
    selectionEnd = CharArrayUtil.shiftBackward(document.getCharsSequence(), selectionEnd - 1, " \t\n") + 1;

    if (CharArrayUtil.regionMatches(document.getCharsSequence(), selectionEnd - commentSuffix.length(), commentSuffix) &&
        CharArrayUtil.regionMatches(document.getCharsSequence(), selectionStart, commentPrefix)) {
      return new TextRange(selectionStart, selectionEnd);
    }
    return null;
  }

  @Override
  public String getBlockCommentPrefix(int selectionStart,
                                      @NotNull Document document,
                                      @NotNull MyCommenterData data) {
    return data.getCommentPrefix();
  }

  @Override
  public String getBlockCommentSuffix(int selectionEnd,
                                      @NotNull Document document,
                                      @NotNull MyCommenterData data) {
    return data.getCommentSuffix();
  }

  @Override
  public void uncommentBlockComment(int startOffset, int endOffset, Document document, MyCommenterData data) {
    String commentSuffix = data.getCommentSuffix();
    String commentPrefix = data.getCommentPrefix();

    int startBlockLine = document.getLineNumber(startOffset);
    int endBlockLine = document.getLineNumber(endOffset);

    if (document.getCharsSequence().subSequence(document.getLineStartOffset(startBlockLine),
                                    document.getLineEndOffset(startBlockLine)).toString().matches("\\s*\\Q" + commentPrefix + "\\E\\s*")) {
      if (document.getCharsSequence().subSequence(document.getLineStartOffset(endBlockLine),
                                    document.getLineEndOffset(endBlockLine)).toString().matches("\\s*\\Q" + commentSuffix + "\\E\\s*")) {
        document.deleteString(document.getLineStartOffset(endBlockLine), document.getLineEndOffset(endBlockLine) + 1);
        document.deleteString(document.getLineStartOffset(startBlockLine), document.getLineEndOffset(startBlockLine) + 1);
        return;
      }
    }

    document.deleteString(endOffset - commentSuffix.length(), endOffset);
    document.deleteString(startOffset, startOffset + commentPrefix.length());
  }

  @NotNull
  @Override
  public TextRange insertBlockComment(int startOffset, int endOffset, Document document, MyCommenterData data) {
    int startLineNumber = document.getLineNumber(startOffset);
    int startLineStart = document.getLineStartOffset(startLineNumber);
    if (startLineStart == startOffset) {
      int endLineNumber = document.getLineNumber(endOffset);
      int endLineStart = document.getLineStartOffset(endLineNumber);
      if (endLineStart == endOffset) {
        String commentStart = data.getCommentPrefix() + "\n";
        String commentEnd = data.getCommentSuffix() + "\n";
        document.insertString(endOffset, commentEnd);
        document.insertString(startOffset,  commentStart);
        return new TextRange(startOffset, endOffset + commentStart.length() + commentEnd.length());
      }
    }
    document.insertString(endOffset, data.getCommentSuffix());
    document.insertString(startOffset, data.getCommentPrefix());
    return new TextRange(startOffset, endOffset + data.getCommentSuffix().length() + data.getCommentPrefix().length());
  }

  public abstract static class MyCommenterData extends CommenterDataHolder {
    public abstract String getCommentPrefix();
    public abstract String getCommentSuffix();
  }
}
