// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.IntStack;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class IndentsPass extends TextEditorHighlightingPass implements DumbAware {
  private static final Key<List<RangeHighlighter>> INDENT_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("INDENT_HIGHLIGHTERS_IN_EDITOR_KEY");
  private static final Key<Long> LAST_TIME_INDENTS_BUILT = Key.create("LAST_TIME_INDENTS_BUILT");

  private final Editor myEditor;
  private final PsiFile  myFile;

  private volatile List<TextRange> myRanges = Collections.emptyList();
  private volatile List<IndentGuideDescriptor> myDescriptors = Collections.emptyList();

  private static final CustomHighlighterRenderer RENDERER = new IndentGuideRenderer();

  public IndentsPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    super(project, editor.getDocument(), false);
    myEditor = editor;
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) return;

    myDescriptors = buildDescriptors();

    ArrayList<TextRange> ranges = new ArrayList<>();
    for (IndentGuideDescriptor descriptor : myDescriptors) {
      ProgressManager.checkCanceled();
      int endOffset =
        descriptor.endLine < myDocument.getLineCount() ? myDocument.getLineStartOffset(descriptor.endLine) : myDocument.getTextLength();
      ranges.add(new TextRange(myDocument.getLineStartOffset(descriptor.startLine), endOffset));
    }

    ranges.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
    myRanges = ranges;
  }

  private long nowStamp() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return -1;
    // include tab size into stamp to make sure indent guides are recalculated on tab size change
    return myDocument.getModificationStamp() ^ (((long)getTabSize()) << 24);
  }

  private int getTabSize() {
    return EditorUtil.getTabSize(myEditor);
  }

  @Override
  public void doApplyInformationToEditor() {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) return;

    List<RangeHighlighter> oldHighlighters = myEditor.getUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY);
    List<RangeHighlighter> newHighlighters = new ArrayList<>();
    MarkupModel mm = myEditor.getMarkupModel();

    int curRange = 0;

    if (oldHighlighters != null) {
      // after document change some range highlighters could have become invalid, or the order could have been broken
      oldHighlighters.sort(Comparator.comparing((RangeHighlighter h) -> !h.isValid())
                                     .thenComparing(Segment.BY_START_OFFSET_THEN_END_OFFSET));
      int curHighlight = 0;
      while (curRange < myRanges.size() && curHighlight < oldHighlighters.size()) {
        TextRange range = myRanges.get(curRange);
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        if (!highlighter.isValid()) break;

        int cmp = compare(range, highlighter);
        if (cmp < 0) {
          newHighlighters.add(createHighlighter(mm, range));
          curRange++;
        }
        else if (cmp > 0) {
          highlighter.dispose();
          curHighlight++;
        }
        else {
          newHighlighters.add(highlighter);
          curHighlight++;
          curRange++;
        }
      }

      for (; curHighlight < oldHighlighters.size(); curHighlight++) {
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        if (!highlighter.isValid()) break;
        highlighter.dispose();
      }
    }

    int startRangeIndex = curRange;
    DocumentUtil.executeInBulk(myDocument, myRanges.size() > 10000, () -> {
      for (int i = startRangeIndex; i < myRanges.size(); i++) {
        newHighlighters.add(createHighlighter(mm, myRanges.get(i)));
      }
    });


    myEditor.putUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY, newHighlighters);
    myEditor.putUserData(LAST_TIME_INDENTS_BUILT, nowStamp());
    myEditor.getIndentsModel().assumeIndents(myDescriptors);
  }

  private List<IndentGuideDescriptor> buildDescriptors() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return Collections.emptyList();

    IndentsCalculator calculator = new IndentsCalculator();
    calculator.calculate();
    int[] lineIndents = calculator.lineIndents;

    IntStack lines = new IntStack();
    IntStack indents = new IntStack();

    lines.push(0);
    indents.push(0);
    List<IndentGuideDescriptor> descriptors = new ArrayList<>();
    for (int line = 1; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      int curIndent = Math.abs(lineIndents[line]);

      while (!indents.empty() && curIndent <= indents.peek()) {
        ProgressManager.checkCanceled();
        int level = indents.pop();
        int startLine = lines.pop();
        if (level > 0) {
          for (int i = startLine; i < line; i++) {
            if (level != Math.abs(lineIndents[i])) {
              IndentGuideDescriptor descriptor = createDescriptor(level, startLine, line, lineIndents);
              if (IndentsPassFilterUtils.shouldShowIndentGuide(myEditor, descriptor)) descriptors.add(descriptor);
              break;
            }
          }
        }
      }

      int prevLine = line - 1;
      int prevIndent = Math.abs(lineIndents[prevLine]);

      if (curIndent - prevIndent > 1) {
        lines.push(prevLine);
        indents.push(prevIndent);
      }
    }

    while (!indents.empty()) {
      ProgressManager.checkCanceled();
      int level = indents.pop();
      int startLine = lines.pop();
      if (level > 0) {
        IndentGuideDescriptor descriptor = createDescriptor(level, startLine, myDocument.getLineCount(), lineIndents);
        if (IndentsPassFilterUtils.shouldShowIndentGuide(myEditor, descriptor)) descriptors.add(descriptor);
      }
    }
    return descriptors;
  }

  private IndentGuideDescriptor createDescriptor(int level, int startLine, int endLine, int[] lineIndents) {
    while (startLine > 0 && lineIndents[startLine] < 0) startLine--;
    int codeConstructStartLine = findCodeConstructStartLine(startLine);
    return new IndentGuideDescriptor(level, codeConstructStartLine, startLine, endLine);
  }

  private int findCodeConstructStartLine(int startLine) {
    Document document = myEditor.getDocument();
    CharSequence text = document.getImmutableCharSequence();
    int lineStartOffset = document.getLineStartOffset(startLine);
    int firstNonWsOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t");
    FileType type = PsiUtilBase.getPsiFileAtOffset(myFile, firstNonWsOffset).getFileType();
    Language language = PsiUtilCore.getLanguageAtOffset(myFile, firstNonWsOffset);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(type, language);
    HighlighterIterator iterator = myEditor.getHighlighter().createIterator(firstNonWsOffset);
    if (braceMatcher.isLBraceToken(iterator, text, type)) {
      int codeConstructStart = braceMatcher.getCodeConstructStart(myFile, firstNonWsOffset);
      return document.getLineNumber(codeConstructStart);
    }
    else {
      return startLine;
    }
  }

  @NotNull
  private static RangeHighlighter createHighlighter(MarkupModel mm, TextRange range) {
    RangeHighlighter highlighter = mm.addRangeHighlighter(null, range.getStartOffset(), range.getEndOffset(), 0,
                                                                HighlighterTargetArea.EXACT_RANGE);
    highlighter.setCustomRenderer(RENDERER);
    return highlighter;
  }

  private static int compare(@NotNull TextRange r, @NotNull RangeHighlighter h) {
    int answer = r.getStartOffset() - h.getStartOffset();
    return answer != 0 ? answer : r.getEndOffset() - h.getEndOffset();
  }

  @TestOnly
  public @NotNull List<IndentGuideDescriptor> getDescriptors() {
    return new ArrayList<>(myDescriptors);
  }

  private class IndentsCalculator {
    @NotNull final Map<Language, TokenSet> myComments = new HashMap<>();
    final int @NotNull [] lineIndents; // negative value means the line is empty (or contains a comment) and indent
    // (denoted by absolute value) was deduced from enclosing non-empty lines
    @NotNull final CharSequence myChars;

    IndentsCalculator() {
      lineIndents = new int[myDocument.getLineCount()];
      myChars = myDocument.getCharsSequence();
    }

    /**
     * Calculates line indents for the {@link #myDocument target document}.
     */
    void calculate() {
      FileType fileType = myFile.getFileType();
      int tabSize = getTabSize();

      for (int line = 0; line < lineIndents.length; line++) {
        ProgressManager.checkCanceled();
        int lineStart = myDocument.getLineStartOffset(line);
        int lineEnd = myDocument.getLineEndOffset(line);
        int offset = lineStart;
        int column = 0;
        outer:
        while(offset < lineEnd) {
          switch (myChars.charAt(offset)) {
            case ' ':
              column++;
              break;
            case '\t':
              column = (column / tabSize + 1) * tabSize;
              break;
            default:
              break outer;
          }
          offset++;
        }
        // treating commented lines in the same way as empty lines
        // Blank line marker
        lineIndents[line] = offset == lineEnd || isComment(offset) ? -1 : column;
      }

      int topIndent = 0;
      for (int line = 0; line < lineIndents.length; line++) {
        ProgressManager.checkCanceled();
        if (lineIndents[line] >= 0) {
          topIndent = lineIndents[line];
        }
        else {
          int startLine = line;
          while (line < lineIndents.length && lineIndents[line] < 0) {
            //noinspection AssignmentToForLoopParameter
            line++;
          }

          int bottomIndent = line < lineIndents.length ? lineIndents[line] : topIndent;

          int indent = Math.min(topIndent, bottomIndent);
          if (bottomIndent < topIndent) {
            int lineStart = myDocument.getLineStartOffset(line);
            int lineEnd = myDocument.getLineEndOffset(line);
            int nonWhitespaceOffset = CharArrayUtil.shiftForward(myChars, lineStart, lineEnd, " \t");
            HighlighterIterator iterator = myEditor.getHighlighter().createIterator(nonWhitespaceOffset);
            IElementType tokenType = iterator.getTokenType();
            if (BraceMatchingUtil.isRBraceToken(iterator, myChars, fileType) ||
                tokenType != null &&
                !CodeBlockSupportHandler.findMarkersRanges(myFile, tokenType.getLanguage(), nonWhitespaceOffset).isEmpty()) {
              indent = topIndent;
            }
          }

          for (int blankLine = startLine; blankLine < line; blankLine++) {
            assert lineIndents[blankLine] == -1;
            lineIndents[blankLine] = - Math.min(topIndent, indent);
          }

          //noinspection AssignmentToForLoopParameter
          line--; // will be incremented back at the end of the loop;
        }
      }
    }

    private boolean isComment(int offset) {
      HighlighterIterator it = myEditor.getHighlighter().createIterator(offset);
      IElementType tokenType = it.getTokenType();
      Language language = tokenType.getLanguage();
      TokenSet comments = myComments.get(language);
      if (comments == null) {
        ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
        if (definition != null) {
          comments = definition.getCommentTokens();
        }
        if (comments == null) {
          return false;
        }
        else {
          myComments.put(language, comments);
        }
      }
      return comments.contains(tokenType);
    }
  }
}
