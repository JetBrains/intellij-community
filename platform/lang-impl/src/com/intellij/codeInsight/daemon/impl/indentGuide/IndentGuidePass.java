// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;


public final class IndentGuidePass extends TextEditorHighlightingPass implements DumbAware {
  private static final Key<List<RangeHighlighter>> INDENT_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("INDENT_HIGHLIGHTERS_IN_EDITOR_KEY");
  private static final Key<Long> LAST_TIME_INDENTS_BUILT = Key.create("LAST_TIME_INDENTS_BUILT");

  private final Editor myEditor;
  private final PsiFile myPsiFile;

  private volatile List<TextRange> myRanges = Collections.emptyList();
  private volatile List<IndentGuideDescriptor> myDescriptors = Collections.emptyList();

  private static final CustomHighlighterRenderer RENDERER = new IndentGuideRenderer();
  private static final CustomHighlighterRenderer ZOMBIE_RENDERER = new IndentGuideZombieRenderer();

  public IndentGuidePass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    super(project, editor.getDocument(), false);
    myEditor = editor;
    myPsiFile = psiFile;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) {
      return;
    }
    myDescriptors = buildDescriptors();
    myRanges = buildRanges(myDocument, myDescriptors);
  }

  static @NotNull ArrayList<TextRange> buildRanges(@NotNull Document document, @NotNull List<IndentGuideDescriptor> descriptors) {
    ArrayList<TextRange> ranges = new ArrayList<>();
    for (IndentGuideDescriptor descriptor : descriptors) {
      ProgressManager.checkCanceled();
      int endOffset = descriptor.endLine < document.getLineCount() ? document.getLineStartOffset(descriptor.endLine) : document.getTextLength();
      ranges.add(new TextRange(document.getLineStartOffset(descriptor.startLine), endOffset));
    }
    ranges.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
    return ranges;
  }

  private long nowStamp() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return -1;
    // include tab size in stamp to make sure indent guides are recalculated on tab size change
    return myDocument.getModificationStamp() ^ (((long)getTabSize()) << 24);
  }

  private int getTabSize() {
    return EditorUtil.getTabSize(myEditor);
  }

  @Override
  public void doApplyInformationToEditor() {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) {
      return;
    }
    applyIndents(myEditor, myDescriptors, myRanges, false);
    myEditor.putUserData(LAST_TIME_INDENTS_BUILT, nowStamp());
  }

  static void applyIndents(Editor editor, List<IndentGuideDescriptor> descriptors, List<TextRange> ranges, boolean isZombie) {
    Document document = editor.getDocument();
    List<RangeHighlighter> oldHighlighters = editor.getUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY);
    List<RangeHighlighter> newHighlighters = new ArrayList<>();
    MarkupModel mm = editor.getMarkupModel();

    int curRange = 0;

    if (oldHighlighters != null) {
      // after document change some range highlighters could have become invalid, or the order could have been broken
      oldHighlighters.sort(Comparator.comparing((RangeHighlighter h) -> !h.isValid())
                                     .thenComparing(Segment.BY_START_OFFSET_THEN_END_OFFSET));
      int curHighlight = 0;
      while (curRange < ranges.size() && curHighlight < oldHighlighters.size()) {
        TextRange range = ranges.get(curRange);
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        if (!highlighter.isValid()) {
          break;
        }

        int cmp = compare(range, highlighter);
        if (cmp < 0) {
          newHighlighters.add(createHighlighter(mm, range, isZombie));
          curRange++;
        }
        else if (cmp > 0) {
          highlighter.dispose();
          curHighlight++;
        }
        else {
          setRenderer(highlighter, isZombie);
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
    DocumentUtil.executeInBulk(document, ranges.size() > 10000, () -> {
      for (int i = startRangeIndex; i < ranges.size(); i++) {
        newHighlighters.add(createHighlighter(mm, ranges.get(i), isZombie));
      }
    });

    editor.putUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY, newHighlighters);
    editor.getIndentsModel().assumeIndents(descriptors);
  }

  private List<IndentGuideDescriptor> buildDescriptors() {
    if (!myEditor.getSettings().isIndentGuidesShown() || myEditor.getDocument().getTextLength() == 0) {
      return Collections.emptyList();
    }

    IndentGuideCalculator calculator = new IndentGuideCalculator(myEditor, myPsiFile);
    int[] lineIndents = calculator.calculate(getTabSize());

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
              IndentGuideDescriptor descriptor = createDescriptor(level, startLine, line, lineIndents);
              if (IndentGuidePassFilterExtensionPoint.shouldShowIndentGuide(myEditor, descriptor)) descriptors.add(descriptor);
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
        IndentGuideDescriptor descriptor = createDescriptor(level, startLine, myDocument.getLineCount(), lineIndents);
        if (IndentGuidePassFilterExtensionPoint.shouldShowIndentGuide(myEditor, descriptor)) descriptors.add(descriptor);
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
    FileType type = PsiUtilBase.getPsiFileAtOffset(myPsiFile, firstNonWsOffset).getFileType();
    Language language = PsiUtilCore.getLanguageAtOffset(myPsiFile, firstNonWsOffset);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(type, language);
    HighlighterIterator iterator = myEditor.getHighlighter().createIterator(firstNonWsOffset);
    if (braceMatcher.isLBraceToken(iterator, text, type)) {
      int codeConstructStart = braceMatcher.getCodeConstructStart(myPsiFile, firstNonWsOffset);
      return document.getLineNumber(codeConstructStart);
    }
    else {
      return startLine;
    }
  }

  private static @NotNull RangeHighlighter createHighlighter(MarkupModel mm, TextRange range, boolean isZombie) {
    RangeHighlighter highlighter = mm.addRangeHighlighter(null, range.getStartOffset(), range.getEndOffset(), 0,
                                                                HighlighterTargetArea.EXACT_RANGE);
    setRenderer(highlighter, isZombie);
    return highlighter;
  }

  private static void setRenderer(RangeHighlighter highlighter, boolean isZombie) {
    if (isZombie) {
      highlighter.setCustomRenderer(ZOMBIE_RENDERER);
    }
    else {
      highlighter.setCustomRenderer(RENDERER);
    }
  }

  private static int compare(@NotNull TextRange r, @NotNull RangeHighlighter h) {
    int answer = r.getStartOffset() - h.getStartOffset();
    return answer != 0 ? answer : r.getEndOffset() - h.getEndOffset();
  }

  @TestOnly
  public @NotNull List<IndentGuideDescriptor> getDescriptors() {
    return new ArrayList<>(myDescriptors);
  }
}
