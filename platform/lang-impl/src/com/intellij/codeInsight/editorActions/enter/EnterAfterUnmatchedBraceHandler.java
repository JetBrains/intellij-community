/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class EnterAfterUnmatchedBraceHandler extends EnterHandlerDelegateAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler");

  @Override
  public Result preprocessEnter(@NotNull final PsiFile file,
                                @NotNull final Editor editor,
                                @NotNull final Ref<Integer> caretOffsetRef,
                                @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext,
                                final EditorActionHandler originalHandler) {

    int caretOffset = caretOffsetRef.get();
    if (!CodeInsightSettings.getInstance().INSERT_BRACE_ON_ENTER) return Result.Continue;

    int maxRBraceCount = getUnmatchedLBracesNumberBefore(editor, caretOffset, file.getFileType());
    if (maxRBraceCount <= 0) return Result.Continue;

    insertRBraces(file, editor, caretOffset,
                  getRBraceOffset(file, editor, caretOffset),
                  adjustRBraceCountForPosition(editor, caretOffset, maxRBraceCount));

    return Result.DefaultForceIndent;
  }

  /**
   * Calculates the precise number of '}' that have be inserted by handler.
   *
   * @param editor      target editor
   * @param caretOffset target caret offset
   * @param maxRBraceCount the maximum number of '}' for insert at position, it always positive
   * @return number of '}' that has to be inserted by handler, it has to positive
   */
  protected int adjustRBraceCountForPosition(@NotNull final Editor editor, int caretOffset, int maxRBraceCount) {
    assert maxRBraceCount > 0;

    CharSequence text = editor.getDocument().getCharsSequence();
    int bracesToInsert = 0;
    for (int i = caretOffset - 1; i >= 0 && bracesToInsert < maxRBraceCount; --i) {
      char c = text.charAt(i);
      if (c == '{') {
        bracesToInsert++;
      }
      else if (breakOn(c)) {
        break;
      }
    }
    return Math.max(bracesToInsert, 1);
  }

  protected boolean breakOn(char ch) {
    switch (ch) {
      case ' ':
      case '\n':
      case '\t':
        return false;
    }
    return true;
  }

  /**
   * Calculates the position for insertion of one or more '}'.
   *
   * @param file        target PSI file
   * @param editor      target editor
   * @param caretOffset target caret offset
   * @return the position between <code>caretOffset</code> and the end of file
   */
  protected int getRBraceOffset(@NotNull final PsiFile file, @NotNull final Editor editor, int caretOffset) {
    CharSequence text = editor.getDocument().getCharsSequence();
    int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
    final int fileLength = text.length();
    if (offset < fileLength && ")];,%<?".indexOf(text.charAt(offset)) < 0) {
      offset = calculateOffsetToInsertClosingBrace(file, text, offset);
      //offset = CharArrayUtil.shiftForwardUntil(text, caretOffset, "\n");
    }
    return Math.min(offset, fileLength);
  }

  /**
   * Inserts the <code>rBracesCount</code> of '}' at the <code>rBracesInsertOffset</code> position and formats the code block.
   *
   * @param file                target PSI file
   * @param editor              target editor
   * @param caretOffset         target caret offset
   * @param rBracesInsertOffset target position to insert
   * @param rBracesCount        count of '}' to insert
   * @return true for success
   */
  protected void insertRBraces(@NotNull PsiFile file,
                               @NotNull Editor editor,
                               int caretOffset,
                               int rBracesInsertOffset,
                               int rBracesCount) {
    final Document document = editor.getDocument();
    document.insertString(rBracesInsertOffset, "\n" + StringUtil.repeatSymbol('}', rBracesCount));
    // We need to adjust indents of the text that will be moved, hence, need to insert preliminary line feed.
    // Example:
    //     if (test1()) {
    //     } else {<caret> if (test2()) {
    //         foo();
    //     }
    // We insert here '\n}' after 'foo();' and have the following:
    //     if (test1()) {
    //     } else { if (test2()) {
    //         foo();
    //         }
    //     }
    // That is formatted incorrectly because line feed between 'else' and 'if' is not inserted yet (whole 'if' block is indent anchor
    // to 'if' code block('{}')). So, we insert temporary line feed between 'if' and 'else', correct indent and remove that temporary
    // line feed.
    document.insertString(caretOffset, "\n");

    Project project = file.getProject();
    long stamp = document.getModificationStamp();
    boolean closingBraceIndentAdjusted;
    try {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      doAlignt(file, caretOffset, rBracesInsertOffset, rBracesCount);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      closingBraceIndentAdjusted = stamp != document.getModificationStamp();
      document.deleteString(caretOffset, caretOffset + 1);
    }

    // There is a possible case that formatter was unable to adjust line indent for the closing brace (that is the case for plain text
    // document for example). Hence, we're trying to do the manually.
    if (!closingBraceIndentAdjusted) {
      int line = document.getLineNumber(rBracesInsertOffset);
      StringBuilder buffer = new StringBuilder();
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);
      final CharSequence text = document.getCharsSequence();
      for (int i = start; i < end; i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t') {
          break;
        }
        else {
          buffer.append(c);
        }
      }
      if (buffer.length() > 0) {
        document.insertString(rBracesInsertOffset + 1, buffer);
      }
    }
  }

  protected void doAlignt(@NotNull PsiFile file, int caretOffset, int rBracesInsertOffset, int rBracesCount) {
    CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, new TextRange(caretOffset, rBracesInsertOffset + 2));
  }

  /**
   * Current handler inserts closing curly brace (right brace) if necessary. There is a possible case that it should be located
   * more than one line forward.
   * <p/>
   * <b>Example</b>
   * <pre>
   *     if (test1()) {
   *     } else {<caret> if (test2()) {
   *         foo();
   *     }
   * </pre>
   * <p/>
   * We want to get this after the processing:
   * <pre>
   *     if (test1()) {
   *     } else {
   *         if (test2()) {
   *             foo();
   *         }
   *     }
   * </pre>
   * I.e. closing brace should be inserted two lines below current caret line. Hence, we need to calculate correct offset
   * to use for brace inserting. This method is responsible for that.
   * <p/>
   * In essence it inspects PSI structure and finds PSE elements with the max length that starts at caret offset. End offset
   * of that element is used as an insertion point.
   *
   * @param file   target PSI file
   * @param text   text from the given file
   * @param offset target offset where line feed will be inserted
   * @return offset to use for inserting closing brace
   */
  protected int calculateOffsetToInsertClosingBrace(PsiFile file, CharSequence text, final int offset) {
    PsiElement element = PsiUtilCore.getElementAtOffset(file, offset);
    ASTNode node = element.getNode();
    if (node != null && node.getElementType() == TokenType.WHITE_SPACE) {
      return CharArrayUtil.shiftForwardUntil(text, offset, "\n");
    }
    for (PsiElement parent = element.getParent(); parent != null; parent = parent.getParent()) {
      ASTNode parentNode = parent.getNode();
      if (parentNode == null || parentNode.getStartOffset() != offset) {
        break;
      }
      element = parent;
    }
    if (element.getTextOffset() != offset) {
      return CharArrayUtil.shiftForwardUntil(text, offset, "\n");
    }
    return doCalcOffset(element);
  }

  protected int doCalcOffset(PsiElement element) {
    return element.getTextRange().getEndOffset();
  }

  public static boolean isAfterUnmatchedLBrace(Editor editor, int offset, FileType fileType) {
    return getUnmatchedLBracesNumberBefore(editor, offset, fileType) > 0;
  }

  /**
   * Calculates number of unmatched left braces before the given offset.
   *
   * @param editor   target editor
   * @param offset   target offset
   * @param fileType target file type
   * @return number of unmatched braces before the given offset;
   * negative value if it's not possible to perform the calculation or if there are no unmatched left braces before
   * the given offset
   */
  protected static int getUnmatchedLBracesNumberBefore(Editor editor, int offset, FileType fileType) {
    if (offset == 0) {
      return -1;
    }
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '{') {
      return -1;
    }

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);

    if (!braceMatcher.isLBraceToken(iterator, chars, fileType) || !braceMatcher.isStructuralBrace(iterator, chars, fileType)) {
      return -1;
    }

    Language language = iterator.getTokenType().getLanguage();

    iterator = highlighter.createIterator(0);
    int lBracesBeforeOffset = 0;
    int lBracesAfterOffset = 0;
    int rBracesBeforeOffset = 0;
    int rBracesAfterOffset = 0;
    for (; !iterator.atEnd(); iterator.advance()) {
      IElementType tokenType = iterator.getTokenType();
      if (!tokenType.getLanguage().equals(language) || !braceMatcher.isStructuralBrace(iterator, chars, fileType)) {
        continue;
      }

      boolean beforeOffset = iterator.getStart() < offset;

      if (braceMatcher.isLBraceToken(iterator, chars, fileType)) {
        if (beforeOffset) {
          lBracesBeforeOffset++;
        }
        else {
          lBracesAfterOffset++;
        }
      }
      else if (braceMatcher.isRBraceToken(iterator, chars, fileType)) {
        if (beforeOffset) {
          rBracesBeforeOffset++;
        }
        else {
          rBracesAfterOffset++;
        }
      }
    }

    return lBracesBeforeOffset - rBracesBeforeOffset - (rBracesAfterOffset - lBracesAfterOffset);
  }
}
