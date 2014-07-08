/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.CommentUtil;
import com.intellij.codeInsight.actions.MultiCaretCodeInsightActionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.InjectedCaret;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommentByLineCommentHandler extends MultiCaretCodeInsightActionHandler {
  private Project                                         myProject;
  private CodeStyleManager                                myCodeStyleManager;

  private final List<Block> myBlocks = new ArrayList<Block>();

  @Override
  // first pass - adjacent carets are grouped into blocks
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull Caret caret, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    myProject = project;
    file = file.getViewProvider().getPsi(file.getViewProvider().getBaseLanguage());

    PsiElement context = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);

    if (context != null && (context.textContains('\'') || context.textContains('\"'))) {
      String s = context.getText();
      if (StringUtil.startsWith(s, "\"") || StringUtil.startsWith(s, "\'")) {
        file = context.getContainingFile();
        editor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
        caret = caret instanceof InjectedCaret ? ((InjectedCaret)caret).getDelegate() : caret;
      }
    }

    Document document = editor.getDocument();
    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
      return;
    }

    boolean hasSelection = caret.hasSelection();
    int startOffset = caret.getSelectionStart();
    int endOffset = caret.getSelectionEnd();

    FoldRegion fold = editor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    if (fold != null && fold.shouldNeverExpand() && fold.getStartOffset() == startOffset && fold.getEndOffset() == endOffset) {
      // Foldings that never expand are automatically selected, so the fact it is selected must not interfere with commenter's logic
      hasSelection = false;
    }

    if (document.getTextLength() == 0) return;

    while (true) {
      int lastLineEnd = document.getLineEndOffset(document.getLineNumber(endOffset));
      FoldRegion collapsedAt = editor.getFoldingModel().getCollapsedRegionAtOffset(lastLineEnd);
      if (collapsedAt != null) {
        final int regionEndOffset = collapsedAt.getEndOffset();
        if (regionEndOffset <= endOffset) {
          break;
        }
        endOffset = regionEndOffset;
      }
      else {
        break;
      }
    }

    int startLine = document.getLineNumber(startOffset);
    int endLine = document.getLineNumber(endOffset);

    if (endLine > startLine && document.getLineStartOffset(endLine) == endOffset) {
      endLine--;
    }

    Block lastBlock = myBlocks.isEmpty() ? null : myBlocks.get(myBlocks.size() - 1);
    Block currentBlock;
    if (lastBlock == null || lastBlock.editor != editor || lastBlock.psiFile != file || endLine < (lastBlock.startLine - 1)) {
      currentBlock = new Block();
      currentBlock.editor = editor;
      currentBlock.psiFile = file;
      currentBlock.endLine = endLine;
      myBlocks.add(currentBlock);
    }
    else {
      currentBlock = lastBlock;
    }
    currentBlock.carets.add(caret);
    currentBlock.startLine = startLine;

    boolean wholeLinesSelected = !hasSelection ||
                                 startOffset == document.getLineStartOffset(document.getLineNumber(startOffset)) &&
                                 endOffset == document.getLineEndOffset(document.getLineNumber(endOffset - 1)) + 1;
    boolean startingNewLineComment = !hasSelection
                                     && isLineEmpty(document, document.getLineNumber(startOffset))
                                     && !Comparing.equal(IdeActions.ACTION_COMMENT_LINE,
                                                         ActionManagerEx.getInstanceEx().getPrevPreformedActionId());
    currentBlock.caretUpdate = startingNewLineComment ? CaretUpdate.PUT_AT_COMMENT_START :
                               !hasSelection ? CaretUpdate.SHIFT_DOWN :
                               wholeLinesSelected ? CaretUpdate.RESTORE_SELECTION : null;
    }

  @Override
  public void postInvoke() {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.comment.line");

    myCodeStyleManager = CodeStyleManager.getInstance(myProject);

    // second pass - determining whether we need to comment or to uncomment
    boolean allLinesCommented = true;
    for (Block block : myBlocks) {
      int startLine = block.startLine;
      int endLine = block.endLine;
      Document document = block.editor.getDocument();
      PsiFile psiFile = block.psiFile;

      block.startOffsets = new int[endLine - startLine + 1];
      block.endOffsets = new int[endLine - startLine + 1];
      block.commenters = new Commenter[endLine - startLine + 1];
      block.commenterStateMap = new THashMap<SelfManagingCommenter, CommenterDataHolder>();
      CharSequence chars = document.getCharsSequence();

      boolean singleline = startLine == endLine;
      int offset = document.getLineStartOffset(startLine);
      offset = CharArrayUtil.shiftForward(chars, offset, " \t");

      int endOffset = CharArrayUtil.shiftBackward(chars, document.getLineEndOffset(endLine), " \t\n");

      block.blockSuitableCommenter = getBlockSuitableCommenter(psiFile, offset, endOffset);
      block.commentWithIndent =
        !CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(psiFile.getLanguage()).LINE_COMMENT_AT_FIRST_COLUMN;

      for (int line = startLine; line <= endLine; line++) {
        Commenter commenter = block.blockSuitableCommenter != null ? block.blockSuitableCommenter : findCommenter(block.editor, psiFile, line);
        if (commenter == null || commenter.getLineCommentPrefix() == null
                                 && (commenter.getBlockCommentPrefix() == null || commenter.getBlockCommentSuffix() == null)) {
          block.skip = true;
          break;
        }

        if (commenter instanceof SelfManagingCommenter && block.commenterStateMap.get(commenter) == null) {
          final SelfManagingCommenter selfManagingCommenter = (SelfManagingCommenter)commenter;
          CommenterDataHolder state = selfManagingCommenter.createLineCommentingState(startLine, endLine, document, psiFile);
          if (state == null) state = SelfManagingCommenter.EMPTY_STATE;
          block.commenterStateMap.put(selfManagingCommenter, state);
        }

        block.commenters[line - startLine] = commenter;
        if (!isLineCommented(block, line, commenter) && (singleline || !isLineEmpty(document, line))) {
          allLinesCommented = false;
          if (commenter instanceof IndentedCommenter) {
            final Boolean value = ((IndentedCommenter)commenter).forceIndentedLineComment();
            if (value != null) {
              block.commentWithIndent = value;
            }
          }
          break;
        }
      }
    }
    boolean moveCarets = true;
    for (Block block : myBlocks) {
      if (block.carets.size() > 1 && block.startLine != block.endLine) {
        moveCarets = false;
        break;
      }
    }
    // third pass - actual change
    for (Block block : myBlocks) {
      if (!block.skip) {
        if (!allLinesCommented) {
          if (!block.commentWithIndent) {
            doDefaultCommenting(block);
          }
          else {
            doIndentCommenting(block);
          }
        }
        else {
          for (int line = block.endLine; line >= block.startLine; line--) {
            uncommentLine(block, line);
          }
        }
      }

      if (!moveCarets || block.caretUpdate == null) {
        continue;
      }
      Document document = block.editor.getDocument();
      for (Caret caret : block.carets) {
        switch (block.caretUpdate) {
          case PUT_AT_COMMENT_START:
            final Commenter commenter = block.commenters[0];
            if (commenter != null) {
              String prefix;
              if (commenter instanceof SelfManagingCommenter) {
                prefix = ((SelfManagingCommenter)commenter).getCommentPrefix(block.startLine,
                                                                             document,
                                                                             block.commenterStateMap.get((SelfManagingCommenter)commenter));
                if (prefix == null) prefix = ""; // TODO
              }
              else {
                prefix = commenter.getLineCommentPrefix();
                if (prefix == null) prefix = commenter.getBlockCommentPrefix();
              }

              int lineStart = document.getLineStartOffset(block.startLine);
              lineStart = CharArrayUtil.shiftForward(document.getCharsSequence(), lineStart, " \t");
              lineStart += prefix.length();
              lineStart = CharArrayUtil.shiftForward(document.getCharsSequence(), lineStart, " \t");
              if (lineStart > document.getTextLength()) lineStart = document.getTextLength();
              caret.moveToOffset(lineStart);
            }
            break;
          case SHIFT_DOWN:
            // Don't tweak caret position if we're already located on the last document line.
            LogicalPosition position = caret.getLogicalPosition();
            if (position.line < document.getLineCount() - 1) {
              int verticalShift = 1 + block.editor.getSoftWrapModel().getSoftWrapsForLine(position.line).size()
                                  - position.softWrapLinesOnCurrentLogicalLine;
              caret.moveCaretRelatively(0, verticalShift, false, true);
            }
            break;
          case RESTORE_SELECTION:
            caret.setSelection(document.getLineStartOffset(document.getLineNumber(caret.getSelectionStart())), caret.getSelectionEnd());
        }
      }
    }
  }

  private static Commenter getBlockSuitableCommenter(final PsiFile file, int offset, int endOffset) {
    final Language languageSuitableForCompleteFragment;
    if (offset >= endOffset) {  // we are on empty line
      PsiElement element = file.findElementAt(offset);
      if (element != null) languageSuitableForCompleteFragment = element.getParent().getLanguage();
      else languageSuitableForCompleteFragment = null;
    }
    else {
      languageSuitableForCompleteFragment = PsiUtilBase.reallyEvaluateLanguageInRange(offset, endOffset, file);
    }


    Commenter blockSuitableCommenter =
      languageSuitableForCompleteFragment == null ? LanguageCommenters.INSTANCE.forLanguage(file.getLanguage()) : null;
    if (blockSuitableCommenter == null && file.getFileType() instanceof CustomSyntaxTableFileType) {
      blockSuitableCommenter = new Commenter() {
        final SyntaxTable mySyntaxTable = ((CustomSyntaxTableFileType)file.getFileType()).getSyntaxTable();

        @Override
        @Nullable
        public String getLineCommentPrefix() {
          return mySyntaxTable.getLineComment();
        }

        @Override
        @Nullable
        public String getBlockCommentPrefix() {
          return mySyntaxTable.getStartComment();
        }

        @Override
        @Nullable
        public String getBlockCommentSuffix() {
          return mySyntaxTable.getEndComment();
        }

        @Override
        public String getCommentedBlockCommentPrefix() {
          return null;
        }

        @Override
        public String getCommentedBlockCommentSuffix() {
          return null;
        }
      };
    }

    return blockSuitableCommenter;
  }

  private static boolean isLineEmpty(Document document, final int line) {
    final CharSequence chars = document.getCharsSequence();
    int start = document.getLineStartOffset(line);
    int end = Math.min(document.getLineEndOffset(line), document.getTextLength() - 1);
    for (int i = start; i <= end; i++) {
      if (!Character.isWhitespace(chars.charAt(i))) return false;
    }
    return true;
  }

  private static boolean isLineCommented(Block block, final int line, final Commenter commenter) {
    boolean commented;
    int lineEndForBlockCommenting = -1;
    Document document = block.editor.getDocument();
    int lineStart = document.getLineStartOffset(line);
    CharSequence chars = document.getCharsSequence();
    lineStart = CharArrayUtil.shiftForward(chars, lineStart, " \t");

    if (commenter instanceof SelfManagingCommenter) {
      final SelfManagingCommenter selfManagingCommenter = (SelfManagingCommenter)commenter;
      commented = selfManagingCommenter.isLineCommented(line, lineStart, document, block.commenterStateMap.get(selfManagingCommenter));
    }
    else {
      String prefix = commenter.getLineCommentPrefix();

      if (prefix != null) {
        commented = CharArrayUtil.regionMatches(chars, lineStart, StringUtil.trimTrailing(prefix));
      }
      else {
        prefix = commenter.getBlockCommentPrefix();
        String suffix = commenter.getBlockCommentSuffix();
        final int textLength = document.getTextLength();
        lineEndForBlockCommenting = document.getLineEndOffset(line);
        if (lineEndForBlockCommenting == textLength) {
          final int shifted = CharArrayUtil.shiftBackward(chars, textLength - 1, " \t");
          if (shifted < textLength - 1) lineEndForBlockCommenting = shifted;
        }
        else {
          lineEndForBlockCommenting = CharArrayUtil.shiftBackward(chars, lineEndForBlockCommenting, " \t");
        }
        commented = lineStart == lineEndForBlockCommenting && block.startLine != block.endLine ||
                    CharArrayUtil.regionMatches(chars, lineStart, prefix)
                    && CharArrayUtil.regionMatches(chars, lineEndForBlockCommenting - suffix.length(), suffix);
      }
    }

    if (commented) {
      block.startOffsets[line - block.startLine] = lineStart;
      block.endOffsets[line - block.startLine] = lineEndForBlockCommenting;
    }

    return commented;
  }

  @Nullable
  private static Commenter findCommenter(Editor editor, PsiFile file, final int line) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter();
    }

    Document document = editor.getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line) - 1;
    final CharSequence charSequence = document.getCharsSequence();
    lineStartOffset = CharArrayUtil.shiftForward(charSequence, lineStartOffset, " \t");
    lineEndOffset = CharArrayUtil.shiftBackward(charSequence, lineEndOffset < 0 ? 0 : lineEndOffset, " \t");
    final Language lineStartLanguage = PsiUtilCore.getLanguageAtOffset(file, lineStartOffset);
    final Language lineEndLanguage = PsiUtilCore.getLanguageAtOffset(file, lineEndOffset);
    return CommentByBlockCommentHandler.getCommenter(file, editor, lineStartLanguage, lineEndLanguage);
  }

  private Indent computeMinIndent(Editor editor, PsiFile psiFile, int line1, int line2, FileType fileType) {
    Document document = editor.getDocument();
    Indent minIndent = CommentUtil.getMinLineIndent(myProject, document, line1, line2, fileType);
    if (line1 > 0) {
      int commentOffset = getCommentStart(editor, psiFile, line1 - 1);
      if (commentOffset >= 0) {
        int lineStart = document.getLineStartOffset(line1 - 1);
        String space = document.getCharsSequence().subSequence(lineStart, commentOffset).toString();
        Indent indent = myCodeStyleManager.getIndent(space, fileType);
        minIndent = minIndent != null ? indent.min(minIndent) : indent;
      }
    }
    if (minIndent == null) {
      minIndent = myCodeStyleManager.zeroIndent();
    }
    return minIndent;
  }

  private static int getCommentStart(Editor editor, PsiFile psiFile, int line) {
    int offset = editor.getDocument().getLineStartOffset(line);
    CharSequence chars = editor.getDocument().getCharsSequence();
    offset = CharArrayUtil.shiftForward(chars, offset, " \t");
    final Commenter commenter = findCommenter(editor, psiFile, line);
    if (commenter == null) return -1;
    String prefix = commenter.getLineCommentPrefix();
    if (prefix == null) prefix = commenter.getBlockCommentPrefix();
    if (prefix == null) return -1;
    return CharArrayUtil.regionMatches(chars, offset, prefix) ? offset : -1;
  }

  public void doDefaultCommenting(final Block block) {
    final Document document = block.editor.getDocument();
    DocumentUtil.executeInBulk(
      document, block.endLine - block.startLine >= Registry.intValue("comment.by.line.bulk.lines.trigger"), new Runnable() {
      @Override
      public void run() {
        for (int line = block.endLine; line >= block.startLine; line--) {
          int offset = document.getLineStartOffset(line);
          commentLine(block, line, offset);
        }
      }
    });
  }

  private void doIndentCommenting(final Block block) {
    final Document document = block.editor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final FileType fileType = block.psiFile.getFileType();
    final Indent minIndent = computeMinIndent(block.editor, block.psiFile, block.startLine, block.endLine, fileType);

    DocumentUtil.executeInBulk(
      document, block.endLine - block.startLine > Registry.intValue("comment.by.line.bulk.lines.trigger"), new Runnable() {
        @Override
        public void run() {
          for (int line = block.endLine; line >= block.startLine; line--) {
            int lineStart = document.getLineStartOffset(line);
            int offset = lineStart;
            final StringBuilder buffer = new StringBuilder();
            while (true) {
              String space = buffer.toString();
              Indent indent = myCodeStyleManager.getIndent(space, fileType);
              if (indent.isGreaterThan(minIndent) || indent.equals(minIndent)) break;
              char c = chars.charAt(offset);
              if (c != ' ' && c != '\t') {
                String newSpace = myCodeStyleManager.fillIndent(minIndent, fileType);
                document.replaceString(lineStart, offset, newSpace);
                offset = lineStart + newSpace.length();
                break;
              }
              buffer.append(c);
              offset++;
            }
            commentLine(block, line, offset);
          }
        }
      });
  }

  private static void uncommentRange(Document document, int startOffset, int endOffset, @NotNull Commenter commenter) {
    final String commentedSuffix = commenter.getCommentedBlockCommentSuffix();
    final String commentedPrefix = commenter.getCommentedBlockCommentPrefix();
    final String prefix = commenter.getBlockCommentPrefix();
    final String suffix = commenter.getBlockCommentSuffix();
    if (prefix == null || suffix == null) {
      return;
    }
    if (endOffset >= suffix.length() && CharArrayUtil.regionMatches(document.getCharsSequence(), endOffset - suffix.length(), suffix)) {
      document.deleteString(endOffset - suffix.length(), endOffset);
      endOffset = document.getTextLength();
    }
    if (commentedPrefix != null && commentedSuffix != null) {
      CommentByBlockCommentHandler.commentNestedComments(document, new TextRange(startOffset, endOffset), commenter);
    }
    document.deleteString(startOffset, startOffset + prefix.length());
  }

  private static void uncommentLine(Block block, int line) {
    Document document = block.editor.getDocument();
    Commenter commenter = block.commenters[line - block.startLine];
    if (commenter == null) commenter = findCommenter(block.editor, block.psiFile, line);
    if (commenter == null) return;

    final int startOffset = block.startOffsets[line - block.startLine];

    if (commenter instanceof SelfManagingCommenter) {
      final SelfManagingCommenter selfManagingCommenter = (SelfManagingCommenter)commenter;
      selfManagingCommenter.uncommentLine(line, startOffset, document, block.commenterStateMap.get(selfManagingCommenter));
      return;
    }

    final int endOffset = block.endOffsets[line - block.startLine];
    if (startOffset == endOffset) {
      return;
    }
    String prefix = commenter.getLineCommentPrefix();
    if (prefix != null) {
      CharSequence chars = document.getCharsSequence();

      if (commenter instanceof CommenterWithLineSuffix) {
        CommenterWithLineSuffix commenterWithLineSuffix = (CommenterWithLineSuffix)commenter;
        String suffix = commenterWithLineSuffix.getLineCommentSuffix();


        int theEnd = endOffset > 0 ? endOffset : document.getLineEndOffset(line);
        while (theEnd > startOffset && Character.isWhitespace(chars.charAt(theEnd - 1))) {
          theEnd--;
        }


        String lineText = document.getText(new TextRange(startOffset, theEnd));
        if (lineText.indexOf(suffix) != -1) {
          int start = startOffset + lineText.indexOf(suffix);
          document.deleteString(start, start + suffix.length());
        }
      }

      boolean matchesTrimmed = false;
      boolean commented = CharArrayUtil.regionMatches(chars, startOffset, prefix) ||
                          (matchesTrimmed = prefix.endsWith(" ") && CharArrayUtil.regionMatches(chars, startOffset, prefix.trim()));
      assert commented;

      int charsToDelete = matchesTrimmed ? prefix.trim().length() : prefix.length();
      int theEnd = endOffset > 0 ? endOffset : chars.length();
      // if there's exactly one space after line comment prefix and before the text that follows in the same line, delete the space too
      if (startOffset + charsToDelete < theEnd - 1 && chars.charAt(startOffset + charsToDelete) == ' ') {
        if (startOffset + charsToDelete == theEnd - 2 || chars.charAt(startOffset + charsToDelete + 1) != ' ') {
          charsToDelete++;
        }
      }
      document.deleteString(startOffset, startOffset + charsToDelete);
      return;
    }
    String text = document.getCharsSequence().subSequence(startOffset, endOffset).toString();

    prefix = commenter.getBlockCommentPrefix();
    final String suffix = commenter.getBlockCommentSuffix();
    if (prefix == null || suffix == null) {
      return;
    }

    IntArrayList prefixes = new IntArrayList();
    IntArrayList suffixes = new IntArrayList();
    for (int position = 0; position < text.length(); ) {
      int prefixPos = text.indexOf(prefix, position);
      if (prefixPos == -1) {
        break;
      }
      prefixes.add(prefixPos);
      position = prefixPos + prefix.length();
      int suffixPos = text.indexOf(suffix, position);
      if (suffixPos == -1) {
        suffixPos = text.length() - suffix.length();
      }
      suffixes.add(suffixPos);
      position = suffixPos + suffix.length();
    }

    assert prefixes.size() == suffixes.size();

    for (int i = prefixes.size() - 1; i >= 0; i--) {
      uncommentRange(document, startOffset + prefixes.get(i), Math.min(startOffset + suffixes.get(i) + suffix.length(), endOffset), commenter);
    }
  }

  private static void commentLine(Block block, int line, int offset) {
    Commenter commenter = block.blockSuitableCommenter;
    Document document = block.editor.getDocument();
    if (commenter == null) commenter = findCommenter(block.editor, block.psiFile, line);
    if (commenter == null) return;
    if (commenter instanceof SelfManagingCommenter) {
      final SelfManagingCommenter selfManagingCommenter = (SelfManagingCommenter)commenter;
      selfManagingCommenter.commentLine(line, offset, document, block.commenterStateMap.get(selfManagingCommenter));
      return;
    }

    String prefix = commenter.getLineCommentPrefix();
    if (prefix != null) {
      if (commenter instanceof CommenterWithLineSuffix) {
        int endOffset = document.getLineEndOffset(line);
        endOffset = CharArrayUtil.shiftBackward(document.getCharsSequence(), endOffset, " \t");
        int shiftedStartOffset = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
        String lineSuffix = ((CommenterWithLineSuffix)commenter).getLineCommentSuffix();
        if (!CharArrayUtil.regionMatches(document.getCharsSequence(), shiftedStartOffset, prefix)) {
          if (!CharArrayUtil.regionMatches(document.getCharsSequence(), endOffset - lineSuffix.length(), lineSuffix)) {
            document.insertString(endOffset, lineSuffix);
          }
          document.insertString(offset, prefix);
        }
      }
      else {
        document.insertString(offset, prefix);
      }
    }
    else {
      prefix = commenter.getBlockCommentPrefix();
      String suffix = commenter.getBlockCommentSuffix();
      if (prefix == null || suffix == null) return;
      int endOffset = document.getLineEndOffset(line);
      if (endOffset == offset && block.startLine != block.endLine) return;
      final int textLength = document.getTextLength();
      final CharSequence chars = document.getCharsSequence();
      offset = CharArrayUtil.shiftForward(chars, offset, " \t");
      if (endOffset == textLength) {
        final int shifted = CharArrayUtil.shiftBackward(chars, textLength - 1, " \t");
        if (shifted < textLength - 1) endOffset = shifted;
      }
      else {
        endOffset = CharArrayUtil.shiftBackward(chars, endOffset, " \t");
      }
      if (endOffset < offset ||
          offset == textLength - 1 && line != document.getLineCount() - 1) {
        return;
      }
      final String text = chars.subSequence(offset, endOffset).toString();
      final IntArrayList prefixes = new IntArrayList();
      final IntArrayList suffixes = new IntArrayList();
      final String commentedSuffix = commenter.getCommentedBlockCommentSuffix();
      final String commentedPrefix = commenter.getCommentedBlockCommentPrefix();
      for (int position = 0; position < text.length(); ) {
        int nearestPrefix = text.indexOf(prefix, position);
        if (nearestPrefix == -1) {
          nearestPrefix = text.length();
        }
        int nearestSuffix = text.indexOf(suffix, position);
        if (nearestSuffix == -1) {
          nearestSuffix = text.length();
        }
        if (Math.min(nearestPrefix, nearestSuffix) == text.length()) {
          break;
        }
        if (nearestPrefix < nearestSuffix) {
          prefixes.add(nearestPrefix);
          position = nearestPrefix + prefix.length();
        }
        else {
          suffixes.add(nearestSuffix);
          position = nearestSuffix + suffix.length();
        }
      }
      if (!(commentedSuffix == null && !suffixes.isEmpty() && offset + suffixes.get(suffixes.size() - 1) + suffix.length() >= endOffset)) {
        document.insertString(endOffset, suffix);
      }
      int nearestPrefix = prefixes.size() - 1;
      int nearestSuffix = suffixes.size() - 1;
      while (nearestPrefix >= 0 || nearestSuffix >= 0) {
        if (nearestSuffix == -1 || nearestPrefix != -1 && prefixes.get(nearestPrefix) > suffixes.get(nearestSuffix)) {
          final int position = prefixes.get(nearestPrefix);
          nearestPrefix--;
          if (commentedPrefix != null) {
            document.replaceString(offset + position, offset + position + prefix.length(), commentedPrefix);
          }
          else if (position != 0) {
            document.insertString(offset + position, suffix);
          }
        }
        else {
          final int position = suffixes.get(nearestSuffix);
          nearestSuffix--;
          if (commentedSuffix != null) {
            document.replaceString(offset + position, offset + position + suffix.length(), commentedSuffix);
          }
          else if (offset + position + suffix.length() < endOffset) {
            document.insertString(offset + position + suffix.length(), prefix);
          }
        }
      }
      if (!(commentedPrefix == null && !prefixes.isEmpty() && prefixes.get(0) == 0)) {
        document.insertString(offset, prefix);
      }
    }
  }

  private static class Block {
    private Editor editor;
    private PsiFile psiFile;
    private List<Caret> carets = new ArrayList<Caret>();
    private int startLine;
    private int endLine;
    private int[] startOffsets;
    private int[] endOffsets;
    private Commenter blockSuitableCommenter;
    private Commenter[] commenters;
    private Map<SelfManagingCommenter, CommenterDataHolder> commenterStateMap;
    private boolean commentWithIndent;
    private CaretUpdate caretUpdate;
    private boolean skip;
  }

  private enum CaretUpdate {
    PUT_AT_COMMENT_START, SHIFT_DOWN, RESTORE_SELECTION
  }
}
