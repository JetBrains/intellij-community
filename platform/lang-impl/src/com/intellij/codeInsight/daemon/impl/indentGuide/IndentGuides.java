// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.Language;
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


final class IndentGuides {
  private static final Key<List<RangeHighlighter>> INDENT_HIGHLIGHTER_IN_EDITOR_KEY = Key.create("INDENT_HIGHLIGHTER_IN_EDITOR_KEY");

  private final Document document;
  private final CustomHighlighterRenderer renderer;
  private List<IndentGuideDescriptor> descriptors;
  private List<TextRange> ranges;

  IndentGuides(@NotNull Document document, @NotNull CustomHighlighterRenderer renderer) {
    this.document = document;
    this.renderer = renderer;
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  void buildIndents(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    this.descriptors = buildDescriptors(editor, psiFile);
    this.ranges = buildRanges();
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  void buildIndents(@NotNull List<IndentGuideDescriptor> descriptors) {
    this.descriptors = descriptors;
    this.ranges =  buildRanges();
  }

  @RequiresEdt
  void applyIndents(@NotNull Editor editor) {
    List<RangeHighlighter> oldHighlighters = editor.getUserData(INDENT_HIGHLIGHTER_IN_EDITOR_KEY);
    List<RangeHighlighter> newHighlighters = new ArrayList<>();
    int curRange = 0;

    if (oldHighlighters != null) {
      // after document change some range highlighters could have become invalid, or the order could have been broken
      oldHighlighters.sort(
        Comparator.comparing((RangeHighlighter h) -> !h.isValid())
          .thenComparing(Segment.BY_START_OFFSET_THEN_END_OFFSET)
      );
      int curHighlight = 0;
      while (curRange < ranges.size() && curHighlight < oldHighlighters.size()) {
        TextRange range = ranges.get(curRange);
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        if (!highlighter.isValid()) {
          break;
        }

        int cmp = compare(range, highlighter);
        if (cmp < 0) {
          newHighlighters.add(createHighlighter(editor, range));
          curRange++;
        }
        else if (cmp > 0) {
          highlighter.dispose();
          curHighlight++;
        }
        else {
          highlighter.setCustomRenderer(renderer);
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
    DocumentUtil.executeInBulk(
      document,
      ranges.size() > 10_000,
      () -> {
        for (int i = startRangeIndex; i < ranges.size(); i++) {
          newHighlighters.add(createHighlighter(editor, ranges.get(i)));
        }
      }
    );

    editor.putUserData(INDENT_HIGHLIGHTER_IN_EDITOR_KEY, newHighlighters);
    editor.getIndentsModel().assumeIndents(descriptors);
  }

  private @NotNull RangeHighlighter createHighlighter(@NotNull Editor editor, @NotNull TextRange range) {
    MarkupModel mm = editor.getMarkupModel();
    RangeHighlighter highlighter = mm.addRangeHighlighter(
      null,
      range.getStartOffset(),
      range.getEndOffset(),
      0,
      HighlighterTargetArea.EXACT_RANGE
    );
    highlighter.setCustomRenderer(renderer);
    return highlighter;
  }

  private @NotNull List<TextRange> buildRanges() {
    ArrayList<TextRange> ranges = new ArrayList<>();
    for (IndentGuideDescriptor descriptor : descriptors) {
      ProgressManager.checkCanceled();
      int endOffset = descriptor.endLine < document.getLineCount()
                      ? document.getLineStartOffset(descriptor.endLine)
                      : document.getTextLength();
      ranges.add(new TextRange(document.getLineStartOffset(descriptor.startLine), endOffset));
    }
    ranges.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
    return ranges;
  }

  private @NotNull List<IndentGuideDescriptor> buildDescriptors(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (!editor.getSettings().isIndentGuidesShown() || document.getTextLength() == 0) {
      return Collections.emptyList();
    }

    IndentGuideCalculator calculator = new IndentGuideCalculator(editor, document, psiFile);
    int[] lineIndents = calculator.calculate(EditorUtil.getTabSize(editor));

    //noinspection SSBasedInspection
    IntArrayList lines = new IntArrayList();
    //noinspection SSBasedInspection
    IntArrayList indents = new IntArrayList();

    lines.push(0);
    indents.push(0);
    List<IndentGuideDescriptor> descriptors = new ArrayList<>();
    for (int line = 1; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      int curIndent = Math.abs(lineIndents[line]);

      while (!indents.isEmpty() && curIndent <= indents.topInt()) {
        ProgressManager.checkCanceled();
        int level = indents.popInt();
        int startLine = lines.popInt();
        if (level > 0) {
          for (int i = startLine; i < line; i++) {
            if (level != Math.abs(lineIndents[i])) {
              IndentGuideDescriptor descriptor = createDescriptor(editor, psiFile, level, startLine, line, lineIndents);
              if (IndentGuidePassFilterExtensionPoint.shouldShowIndentGuide(editor, descriptor)) {
                descriptors.add(descriptor);
              }
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

    while (!indents.isEmpty()) {
      ProgressManager.checkCanceled();
      int level = indents.popInt();
      int startLine = lines.popInt();
      if (level > 0) {
        IndentGuideDescriptor descriptor = createDescriptor(editor, psiFile, level, startLine, document.getLineCount(), lineIndents);
        if (IndentGuidePassFilterExtensionPoint.shouldShowIndentGuide(editor, descriptor)) {
          descriptors.add(descriptor);
        }
      }
    }
    return descriptors;
  }

  private IndentGuideDescriptor createDescriptor(Editor editor, PsiFile psiFile, int level, int startLine, int endLine, int[] lineIndents) {
    while (startLine > 0 && lineIndents[startLine] < 0) startLine--;
    int codeConstructStartLine = findCodeConstructStartLine(editor, psiFile, startLine);
    return new IndentGuideDescriptor(level, codeConstructStartLine, startLine, endLine);
  }

  private int findCodeConstructStartLine(Editor editor, PsiFile psiFile, int startLine) {
    CharSequence text = document.getImmutableCharSequence();
    int lineStartOffset = document.getLineStartOffset(startLine);
    int firstNonWsOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t");
    FileType type = PsiUtilBase.getPsiFileAtOffset(psiFile, firstNonWsOffset).getFileType();
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, firstNonWsOffset);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(type, language);
    HighlighterIterator iterator = editor.getHighlighter().createIterator(firstNonWsOffset);
    if (braceMatcher.isLBraceToken(iterator, text, type)) {
      int codeConstructStart = braceMatcher.getCodeConstructStart(psiFile, firstNonWsOffset);
      return document.getLineNumber(codeConstructStart);
    }
    else {
      return startLine;
    }
  }

  private static int compare(@NotNull TextRange r, @NotNull RangeHighlighter h) {
    int answer = r.getStartOffset() - h.getStartOffset();
    return answer != 0 ? answer : r.getEndOffset() - h.getEndOffset();
  }

  @TestOnly
  @NotNull List<@NotNull IndentGuideDescriptor> getDescriptors() {
    return new ArrayList<>(descriptors);
  }
}
