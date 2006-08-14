package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

public class CommentByLineCommentHandler implements CodeInsightActionHandler {
  private Project myProject;
  private PsiFile myFile;
  private Document myDocument;
  private int myStartOffset;
  private int myEndOffset;
  private int myLine1;
  private int myLine2;
  private int[] myStartOffsets;
  private int[] myEndOffsets;
  private Commenter[] myCommenters;
  private CodeStyleManager myCodeStyleManager;

  public void invoke(Project project, Editor editor, PsiFile file) {
    myProject = project;
    myFile = file;
    myDocument = editor.getDocument();

    if (!myFile.isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(myDocument, project)) {
        return;
      }
    }

    PsiDocumentManager.getInstance(project).commitDocument(myDocument);

    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.comment.line");

    //myCodeInsightSettings = (CodeInsightSettings)ApplicationManager.getApplication().getComponent(CodeInsightSettings.class);
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);

    final SelectionModel selectionModel = editor.getSelectionModel();

    boolean hasSelection = selectionModel.hasSelection();
    myStartOffset = selectionModel.getSelectionStart();
    myEndOffset = selectionModel.getSelectionEnd();

    if (myDocument.getTextLength() == 0) return;

    int lastLineEnd = myDocument.getLineEndOffset(myDocument.getLineNumber(myEndOffset));
    FoldRegion collapsedAt = editor.getFoldingModel().getCollapsedRegionAtOffset(lastLineEnd);
    if (collapsedAt != null) {
      myEndOffset = Math.max(myEndOffset, collapsedAt.getEndOffset());
    }

    boolean wholeLinesSelected = !hasSelection || (
      myStartOffset == myDocument.getLineStartOffset(myDocument.getLineNumber(myStartOffset)) &&
      myEndOffset == myDocument.getLineEndOffset(myDocument.getLineNumber(myEndOffset - 1)) + 1);

    boolean startingNewLineComment = !hasSelection && isLineEmpty(myDocument.getLineNumber(myStartOffset)) && !Comparing
      .equal(IdeActions.ACTION_COMMENT_LINE, ActionManagerEx.getInstanceEx().getPrevPreformedActionId());
    doComment();

    if (startingNewLineComment) {
      final Commenter commenter = myCommenters[0];
      if (commenter != null) {
        String prefix = commenter.getLineCommentPrefix();
        if (prefix == null) prefix = commenter.getBlockCommentPrefix();
        int lineStart = myDocument.getLineStartOffset(myLine1);
        lineStart = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), lineStart, " \t");
        lineStart += prefix.length();
        if (lineStart < myDocument.getTextLength() && myDocument.getCharsSequence().charAt(lineStart) == ' ') lineStart++;
        editor.getCaretModel().moveToOffset(lineStart);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
    else {
      if (!hasSelection) {
        editor.getCaretModel().moveCaretRelatively(0, 1, false, false, true);
      }
      else {
        if (wholeLinesSelected) {
          selectionModel.setSelection(myStartOffset, selectionModel.getSelectionEnd());
        }
      }
    }
  }

  private boolean isLineEmpty(final int line) {
    final CharSequence chars = myDocument.getCharsSequence();
    int start = myDocument.getLineStartOffset(line);
    int end = Math.min(myDocument.getLineEndOffset(line), myDocument.getTextLength() - 1);
    for (int i = start; i <= end; i++) {
      if (!Character.isWhitespace(chars.charAt(i))) return false;
    }
    return true;
  }

  public boolean startInWriteAction() {
    return true;
  }

  private void doComment() {
    myLine1 = myDocument.getLineNumber(myStartOffset);
    myLine2 = myDocument.getLineNumber(myEndOffset);

    if (myLine2 > myLine1 && myDocument.getLineStartOffset(myLine2) == myEndOffset) {
      myLine2--;
    }

    myStartOffsets = new int[myLine2 - myLine1 + 1];
    myEndOffsets = new int[myLine2 - myLine1 + 1];
    myCommenters = new Commenter[myLine2 - myLine1 + 1];
    boolean allLineCommented = true;
    CharSequence chars = myDocument.getCharsSequence();

    boolean singleline = myLine1 == myLine2;

    for (int line = myLine1; line <= myLine2; line++) {
      final Commenter commenter = findCommenter(line);
      if (commenter == null) return;

      if (commenter.getLineCommentPrefix() == null &&
          (commenter.getBlockCommentPrefix() == null || commenter.getBlockCommentSuffix() == null)) {
        return;
      }
      myCommenters[line - myLine1] = commenter;
      if (!isLineCommented(line, chars, commenter) && (singleline || !isLineEmpty(line))) {
        allLineCommented = false;
        break;
      }
    }

    if (!allLineCommented) {
      if (CodeStyleSettingsManager.getSettings(myProject).LINE_COMMENT_AT_FIRST_COLUMN) {
        doDefaultCommenting();
      }
      else {
        doIndentCommenting();
      }
    }
    else {
      for (int line = myLine2; line >= myLine1; line--) {
        int offset1 = myStartOffsets[line - myLine1];
        int offset2 = myEndOffsets[line - myLine1];
        if (offset1 == offset2) continue;
        Commenter commenter = myCommenters[line - myLine1];
        String prefix = commenter.getBlockCommentPrefix();
        if (prefix == null || !myDocument.getText().substring(offset1, myDocument.getTextLength()).startsWith(prefix)) {
          prefix = commenter.getLineCommentPrefix();
        }
        final String suffix = commenter.getBlockCommentSuffix();
        if (prefix != null && suffix != null) {
          final int suffixLen = suffix.length();
          final int prefixLen = prefix.length();
          if (offset2 >= 0) {
            if (!CharArrayUtil.regionMatches(chars, offset1 + prefixLen, prefix)) {
              myDocument.deleteString(offset2 - suffixLen, offset2);
            }
          }
          if (offset1 >= 0) {
            for (int i = offset2 - suffixLen - 1; i > offset1 + prefixLen; --i) {
              if (CharArrayUtil.regionMatches(chars, i, suffix)) {
                myDocument.deleteString(i, i + suffixLen);
              }
              else if (CharArrayUtil.regionMatches(chars, i, prefix)) {
                myDocument.deleteString(i, i + prefixLen);
              }
            }
            myDocument.deleteString(offset1, offset1 + prefixLen);
          }
        }
      }
    }
  }

  private boolean isLineCommented(final int line, final CharSequence chars, final Commenter commenter) {
    String prefix = commenter.getLineCommentPrefix();
    int lineStart = myDocument.getLineStartOffset(line);
    lineStart = CharArrayUtil.shiftForward(chars, lineStart, " \t");
    boolean commented;
    if (prefix != null) {
      commented = CharArrayUtil.regionMatches(chars, lineStart, prefix);
      if (commented) {
        myStartOffsets[line - myLine1] = lineStart;
        myEndOffsets[line - myLine1] = -1;
      }
    }
    else {
      prefix = commenter.getBlockCommentPrefix();
      String suffix = commenter.getBlockCommentSuffix();
      final int textLength = myDocument.getTextLength();
      int lineEnd = myDocument.getLineEndOffset(line);
      if (lineEnd == textLength) {
        final int shifted = CharArrayUtil.shiftBackward(chars, textLength - 1, " \t");
        if (shifted < textLength - 1) lineEnd = shifted;
      }
      else {
        lineEnd = CharArrayUtil.shiftBackward(chars, lineEnd, " \t");
      }
      commented = (lineStart == lineEnd && myLine1 != myLine2) || (CharArrayUtil.regionMatches(chars, lineStart, prefix) &&
                                                                   CharArrayUtil.regionMatches(chars, lineEnd - suffix.length(), suffix));
      if (commented) {
        myStartOffsets[line - myLine1] = lineStart;
        myEndOffsets[line - myLine1] = lineEnd;
      }
    }
    return commented;
  }

  @Nullable
  private Commenter findCommenter(final int line) {
    final FileType fileType = myFile.getFileType();
    if (fileType instanceof CustomFileType) {
      return ((CustomFileType)fileType).getCommenter();
    }

    int offset = myDocument.getLineStartOffset(line);
    offset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), offset, " \t");
    Language language = PsiUtil.getLanguageAtOffset(myFile, offset);
    language = CommentByBlockCommentHandler.evaluateLanguageInRange(offset, myDocument.getLineEndOffset(line),myFile, language);
    return language.getCommenter();
  }

  private Indent computeMinIndent(int line1, int line2, CharSequence chars, CodeStyleManager codeStyleManager, FileType fileType) {
    Indent minIndent = CodeInsightUtil.getMinLineIndent(myProject, myDocument, line1, line2, fileType);
    if (line1 > 0) {
      int commentOffset = getCommentStart(line1 - 1);
      if (commentOffset >= 0) {
        int lineStart = myDocument.getLineStartOffset(line1 - 1);
        String space = chars.subSequence(lineStart, commentOffset).toString();
        Indent indent = codeStyleManager.getIndent(space, fileType);
        minIndent = minIndent != null ? indent.min(minIndent) : indent;
      }
    }
    if (minIndent == null) {
      minIndent = codeStyleManager.zeroIndent();
    }
    return minIndent;
  }

  private int getCommentStart(int line) {
    int offset = myDocument.getLineStartOffset(line);
    CharSequence chars = myDocument.getCharsSequence();
    offset = CharArrayUtil.shiftForward(chars, offset, " \t");
    final Commenter commenter = findCommenter(line);
    if (commenter == null) return -1;
    String prefix = commenter.getLineCommentPrefix();
    if (prefix == null) prefix = commenter.getBlockCommentPrefix();
    if (prefix == null) return -1;
    return CharArrayUtil.regionMatches(chars, offset, prefix) ? offset : -1;
  }

  public void doDefaultCommenting() {
    for (int line = myLine2; line >= myLine1; line--) {
      int offset = myDocument.getLineStartOffset(line);
      commentLine(line, offset);
    }
  }

  private void doIndentCommenting() {
    CharSequence chars = myDocument.getCharsSequence();
    final FileType fileType = myFile.getFileType();
    Indent minIndent = computeMinIndent(myLine1, myLine2, chars, myCodeStyleManager, fileType);

    for (int line = myLine2; line >= myLine1; line--) {
      int lineStart = myDocument.getLineStartOffset(line);
      int offset = lineStart;
      final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
      try {
        while (true) {
          String space = buffer.toString();
          Indent indent = myCodeStyleManager.getIndent(space, fileType);
          if (indent.isGreaterThan(minIndent) || indent.equals(minIndent)) break;
          char c = chars.charAt(offset);
          if (c != ' ' && c != '\t') {
            String newSpace = myCodeStyleManager.fillIndent(minIndent, fileType);
            myDocument.replaceString(lineStart, offset, newSpace);
            offset = lineStart + newSpace.length();
            break;
          }
          buffer.append(c);
          offset++;
        }
      }
      finally {
        StringBuilderSpinAllocator.dispose(buffer);
      }
      commentLine(line, offset);
    }
  }

  private void commentLine(int line, int offset) {
    final Commenter commenter = findCommenter(line);
    if (commenter == null) return;
    String prefix = commenter.getLineCommentPrefix();
    if (prefix != null) {
      myDocument.insertString(offset, prefix);
    }
    else {
      prefix = commenter.getBlockCommentPrefix();
      String suffix = commenter.getBlockCommentSuffix();
      if (prefix == null || suffix == null) return;
      int endOffset = myDocument.getLineEndOffset(line);
      if (endOffset == offset && myLine1 != myLine2) return;
      final int textLength = myDocument.getTextLength();
      final CharSequence chars = myDocument.getCharsSequence();
      offset = CharArrayUtil.shiftForward(chars, offset, " \t");
      if (endOffset == textLength) {
        final int shifted = CharArrayUtil.shiftBackward(chars, textLength - 1, " \t");
        if (shifted < textLength - 1) endOffset = shifted;
      }
      else {
        endOffset = CharArrayUtil.shiftBackward(chars, endOffset, " \t");
      }
      final int suffixLen = suffix.length();
      final int prefixLen = prefix.length();
      if (endOffset - offset > prefixLen + suffixLen) {
        boolean insertPrefix = false;
        int i = endOffset - suffixLen;
        while (i >= offset) {
          if (insertPrefix) {
            if (CharArrayUtil.regionMatches(chars, i, prefix)) {
              i -= suffixLen;
              insertPrefix = false;
              continue;
            }
            if (CharArrayUtil.regionMatches(chars, i, suffix)) {
              myDocument.insertString(i + suffixLen, prefix);
            }
          }
          else {
            if (!CharArrayUtil.regionMatches(chars, i, suffix)) {
              myDocument.insertString(i + suffixLen, suffix);
            }
            insertPrefix = true;
          }
          --i;
        }
        myDocument.insertString(offset, prefix);
      }
      else {
        myDocument.insertString(endOffset, suffix);
        myDocument.insertString(offset, prefix);
      }
    }
  }
}
