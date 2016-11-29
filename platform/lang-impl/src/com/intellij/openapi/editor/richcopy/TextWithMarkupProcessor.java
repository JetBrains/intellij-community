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
package com.intellij.openapi.editor.richcopy;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.editor.richcopy.view.HtmlTransferableData;
import com.intellij.openapi.editor.richcopy.view.RawTextWithMarkup;
import com.intellij.openapi.editor.richcopy.view.RtfTransferableData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Generates text with markup (in RTF and HTML formats) for interaction via clipboard with third-party applications.
 *
 * Interoperability with the following applications was tested:
 *   MS Office 2010 (Word, PowerPoint, Outlook), OpenOffice (Writer, Impress), Gmail, Mac TextEdit, Mac Mail.
 */
public class TextWithMarkupProcessor extends CopyPastePostProcessor<RawTextWithMarkup> {
  private static final Logger LOG = Logger.getInstance("#" + TextWithMarkupProcessor.class.getName());

  private List<RawTextWithMarkup> myResult;

  @NotNull
  @Override
  public List<RawTextWithMarkup> collectTransferableData(PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
    if (!RichCopySettings.getInstance().isEnabled()) {
      return Collections.emptyList();
    }

    try {
      RichCopySettings settings = RichCopySettings.getInstance();
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      Caret firstCaret = carets.get(0);
      final int indentSymbolsToStrip;
      final int firstLineStartOffset;
      if (Registry.is("editor.richcopy.strip.indents") && carets.size() == 1) {
        Pair<Integer, Integer> p = calcIndentSymbolsToStrip(editor.getDocument(), firstCaret.getSelectionStart(), firstCaret.getSelectionEnd());
        firstLineStartOffset = p.first;
        indentSymbolsToStrip = p.second;
      }
      else {
        firstLineStartOffset = firstCaret.getSelectionStart();
        indentSymbolsToStrip = 0;
      }
      logInitial(editor, startOffsets, endOffsets, indentSymbolsToStrip, firstLineStartOffset);
      CharSequence text = editor.getDocument().getCharsSequence();
      EditorColorsScheme schemeToUse = settings.getColorsScheme(editor.getColorsScheme());
      EditorHighlighter highlighter = HighlighterFactory.createHighlighter(file.getViewProvider().getVirtualFile(),
                                                                           schemeToUse, file.getProject());
      highlighter.setText(text);
      MarkupModel markupModel = DocumentMarkupModel.forDocument(editor.getDocument(), file.getProject(), false);
      Context context = new Context(text, schemeToUse, indentSymbolsToStrip);
      int endOffset = 0;
      Caret prevCaret = null;

      for (Caret caret : carets) {
        int caretSelectionStart = caret.getSelectionStart();
        int caretSelectionEnd = caret.getSelectionEnd();
        int startOffsetToUse;
        int additionalShift = 0;
        if (caret == firstCaret) {
          startOffsetToUse = firstLineStartOffset;
        }
        else {
          startOffsetToUse = caretSelectionStart;
          assert prevCaret != null;
          String prevCaretSelectedText = prevCaret.getSelectedText();
          // Block selection fills short lines by white spaces
          int fillStringLength = prevCaretSelectedText == null ? 0 : prevCaretSelectedText.length() - (prevCaret.getSelectionEnd() - prevCaret.getSelectionStart());
          context.addCharacter(endOffset + fillStringLength);
          additionalShift = fillStringLength + 1;
        }
        context.reset(endOffset - caretSelectionStart + additionalShift);
        endOffset = caretSelectionEnd;
        prevCaret = caret;
        if (endOffset <= startOffsetToUse) {
          continue;
        }
        MyMarkupIterator markupIterator = new MyMarkupIterator(text,
                                                               new CompositeRangeIterator(schemeToUse,
                                                                                          new HighlighterRangeIterator(highlighter, startOffsetToUse, endOffset),
                                                                                          new MarkupModelRangeIterator(markupModel, schemeToUse, startOffsetToUse, endOffset)),
                                                               schemeToUse);
        try {
          context.iterate(markupIterator, endOffset);
        }
        finally {
          markupIterator.dispose();
        }
      }
      SyntaxInfo syntaxInfo = context.finish();
      logSyntaxInfo(syntaxInfo);

      createResult(syntaxInfo, editor);
      return ObjectUtils.notNull(myResult, Collections.<RawTextWithMarkup>emptyList());
    }
    catch (Exception e) {
      // catching the exception so that the rest of copy/paste functionality can still work fine
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  @Override
  public void processTransferableData(Project project,
                                      Editor editor,
                                      RangeMarker bounds,
                                      int caretOffset,
                                      Ref<Boolean> indented,
                                      List<RawTextWithMarkup> values) {

  }

  void createResult(SyntaxInfo syntaxInfo, Editor editor) {
    myResult = new ArrayList<>(2);
    myResult.add(new HtmlTransferableData(syntaxInfo, EditorUtil.getTabSize(editor)));
    myResult.add(new RtfTransferableData(syntaxInfo));
  }

  private void setRawText(String rawText) {
    if (myResult == null) {
      return;
    }
    for (RawTextWithMarkup data : myResult) {
      data.setRawText(rawText);
    }
    myResult = null;
  }

  private static void logInitial(@NotNull Editor editor,
                                 @NotNull int[] startOffsets,
                                 @NotNull int[] endOffsets,
                                 int indentSymbolsToStrip,
                                 int firstLineStartOffset)
  {
    if (!LOG.isDebugEnabled()) {
      return;
    }

    StringBuilder buffer = new StringBuilder();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    for (int i = 0; i < startOffsets.length; i++) {
      int start = startOffsets[i];
      int lineStart = document.getLineStartOffset(document.getLineNumber(start));
      int end = endOffsets[i];
      int lineEnd = document.getLineEndOffset(document.getLineNumber(end));
      buffer.append("    region #").append(i).append(": ").append(start).append('-').append(end).append(", text at range ")
        .append(lineStart).append('-').append(lineEnd).append(": \n'").append(text.subSequence(lineStart, lineEnd)).append("'\n");
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 1);
    }
    LOG.debug(String.format(
      "Preparing syntax-aware text. Given: %s selection, indent symbols to strip=%d, first line start offset=%d, selected text:%n%s",
      startOffsets.length > 1 ? "block" : "regular", indentSymbolsToStrip, firstLineStartOffset, buffer
    ));
  }

  private static void logSyntaxInfo(@NotNull SyntaxInfo info) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Constructed syntax info: " + info);
    }
  }

  private static Pair<Integer/* start offset to use */, Integer /* indent symbols to strip */> calcIndentSymbolsToStrip(
    @NotNull Document document, int startOffset, int endOffset)
  {
    int startLine = document.getLineNumber(startOffset);
    int endLine = document.getLineNumber(endOffset);
    CharSequence text = document.getCharsSequence();
    int maximumCommonIndent = Integer.MAX_VALUE;
    int firstLineStart = startOffset;
    int firstLineEnd = startOffset;
    for (int line = startLine; line <= endLine; line++) {
      int lineStartOffset = document.getLineStartOffset(line);
      int lineEndOffset = document.getLineEndOffset(line);
      if (line == startLine) {
        firstLineStart = lineStartOffset;
        firstLineEnd = lineEndOffset;
      }
      int nonWsOffset = lineEndOffset;
      for (int i = lineStartOffset; i < lineEndOffset && (i - lineStartOffset) < maximumCommonIndent && i < endOffset; i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t') {
          nonWsOffset = i;
          break;
        }
      }
      if (nonWsOffset >= lineEndOffset) {
        continue; // Blank line
      }
      int indent = nonWsOffset - lineStartOffset;
      maximumCommonIndent = Math.min(maximumCommonIndent, indent);
      if (maximumCommonIndent == 0) {
        break;
      }
    }
    int startOffsetToUse = Math.min(firstLineEnd, Math.max(startOffset, firstLineStart + maximumCommonIndent));
    return Pair.create(startOffsetToUse, maximumCommonIndent);
  }

  private static class Context {

    private final SyntaxInfo.Builder builder;

    @NotNull private final CharSequence myText;
    @NotNull private final Color        myDefaultForeground;
    @NotNull private final Color        myDefaultBackground;

    @Nullable private Color  myBackground;
    @Nullable private Color  myForeground;
    @Nullable private String myFontFamilyName;

    private final int myIndentSymbolsToStrip;

    private int myFontStyle   = -1;
    private int myStartOffset = -1;
    private int myOffsetShift = 0;

    private int myIndentSymbolsToStripAtCurrentLine;

    Context(@NotNull CharSequence charSequence, @NotNull EditorColorsScheme scheme, int indentSymbolsToStrip) {
      myText = charSequence;
      myDefaultForeground = scheme.getDefaultForeground();
      myDefaultBackground = scheme.getDefaultBackground();

      // Java assumes screen resolution of 72dpi when calculating font size in pixels. External applications are supposedly using correct
      // resolution, so we need to adjust font size for copied text to look the same in them.
      // (See https://docs.oracle.com/javase/7/docs/webnotes/tsg/TSG-Desktop/html/java2d.html#gdlwn)
      // Java on Mac is not affected by this issue.
      int javaFontSize = scheme.getEditorFontSize();
      float fontSize = SystemInfo.isMac || ApplicationManager.getApplication().isHeadlessEnvironment() ? 
                       javaFontSize : 
                       javaFontSize * 72f / Toolkit.getDefaultToolkit().getScreenResolution();
      
      builder = new SyntaxInfo.Builder(myDefaultForeground, myDefaultBackground, fontSize);
      myIndentSymbolsToStrip = indentSymbolsToStrip;
    }

    public void reset(int offsetShiftDelta) {
      myStartOffset = -1;
      myOffsetShift += offsetShiftDelta;
      myIndentSymbolsToStripAtCurrentLine = 0;
    }

    public void iterate(MyMarkupIterator iterator, int endOffset) {
      while (!iterator.atEnd()) {
        iterator.advance();
        int startOffset = iterator.getStartOffset();
        if (startOffset >= endOffset) {
          break;
        }
        if (myStartOffset < 0) {
          myStartOffset = startOffset;
        }

        boolean whiteSpacesOnly = CharArrayUtil.isEmptyOrSpaces(myText, startOffset, iterator.getEndOffset());

        processBackground(startOffset, iterator.getBackgroundColor());
        if (!whiteSpacesOnly) {
          processForeground(startOffset, iterator.getForegroundColor());
          processFontFamilyName(startOffset, iterator.getFontFamilyName());
          processFontStyle(startOffset, iterator.getFontStyle());
        }
      }
      addTextIfPossible(endOffset);
    }

    private void processFontStyle(int startOffset, int fontStyle) {
      if (fontStyle != myFontStyle) {
        addTextIfPossible(startOffset);
        builder.addFontStyle(fontStyle);
        myFontStyle = fontStyle;
      }
    }

    private void processFontFamilyName(int startOffset, String fontName) {
      String fontFamilyName = FontMapper.getPhysicalFontName(fontName);
      if (!fontFamilyName.equals(myFontFamilyName)) {
        addTextIfPossible(startOffset);
        builder.addFontFamilyName(fontFamilyName);
        myFontFamilyName = fontFamilyName;
      }
    }

    private void processForeground(int startOffset, Color foreground) {
      if (myForeground == null && foreground != null) {
        addTextIfPossible(startOffset);
        myForeground = foreground;
        builder.addForeground(foreground);
      }
      else if (myForeground != null) {
        Color c = foreground == null ? myDefaultForeground : foreground;
        if (!myForeground.equals(c)) {
          addTextIfPossible(startOffset);
          builder.addForeground(c);
          myForeground = c;
        }
      }
    }

    private void processBackground(int startOffset, Color background) {
      if (myBackground == null && background != null && !myDefaultBackground.equals(background)) {
        addTextIfPossible(startOffset);
        myBackground = background;
        builder.addBackground(background);
      }
      else if (myBackground != null) {
        Color c = background == null ? myDefaultBackground : background;
        if (!myBackground.equals(c)) {
          addTextIfPossible(startOffset);
          builder.addBackground(c);
          myBackground = c;
        }
      }
    }

    private void addTextIfPossible(int endOffset) {
      if (endOffset <= myStartOffset) {
        return;
      }

      for (int i = myStartOffset; i < endOffset; i++) {
        char c = myText.charAt(i);
        switch (c) {
          case '\r':
            if (i + 1 < myText.length() && myText.charAt(i + 1) == '\n') {
              myIndentSymbolsToStripAtCurrentLine = myIndentSymbolsToStrip;
              builder.addText(myStartOffset + myOffsetShift, i + myOffsetShift + 1);
              myStartOffset = i + 2;
              myOffsetShift--;
              //noinspection AssignmentToForLoopParameter
              i++;
              break;
            }
            // Intended fall-through.
          case '\n':
            myIndentSymbolsToStripAtCurrentLine = myIndentSymbolsToStrip;
            builder.addText(myStartOffset + myOffsetShift, i + myOffsetShift + 1);
            myStartOffset = i + 1;
            break;
          // Intended fall-through.
          case ' ':
          case '\t':
            if (myIndentSymbolsToStripAtCurrentLine > 0) {
              myIndentSymbolsToStripAtCurrentLine--;
              myStartOffset++;
              continue;
            }
          default: myIndentSymbolsToStripAtCurrentLine = 0;
        }
      }

      if (myStartOffset < endOffset) {
        builder.addText(myStartOffset + myOffsetShift, endOffset + myOffsetShift);
        myStartOffset = endOffset;
      }
    }

    private void addCharacter(int position) {
      builder.addText(position + myOffsetShift, position + myOffsetShift + 1);
    }

    @NotNull
    public SyntaxInfo finish() {
      return builder.build();
    }
  }

  private static class MyMarkupIterator {
    private final SegmentIterator mySegmentIterator;
    private final RangeIterator myRangeIterator;
    private int myCurrentFontStyle;
    private Color myCurrentForegroundColor;
    private Color myCurrentBackgroundColor;

    private MyMarkupIterator(@NotNull CharSequence charSequence, @NotNull RangeIterator rangeIterator, @NotNull EditorColorsScheme colorsScheme) {
      myRangeIterator = rangeIterator;
      mySegmentIterator = new SegmentIterator(charSequence, colorsScheme.getFontPreferences());
    }

    public boolean atEnd() {
      return myRangeIterator.atEnd() && mySegmentIterator.atEnd();
    }

    public void advance() {
      if (mySegmentIterator.atEnd()) {
        myRangeIterator.advance();
        TextAttributes textAttributes = myRangeIterator.getTextAttributes();
        myCurrentFontStyle = textAttributes == null ? Font.PLAIN : textAttributes.getFontType();
        myCurrentForegroundColor = textAttributes == null ? null : textAttributes.getForegroundColor();
        myCurrentBackgroundColor = textAttributes == null ? null : textAttributes.getBackgroundColor();
        mySegmentIterator.reset(myRangeIterator.getRangeStart(), myRangeIterator.getRangeEnd(), myCurrentFontStyle);
      }
      mySegmentIterator.advance();
    }

    public int getStartOffset() {
      return mySegmentIterator.getCurrentStartOffset();
    }

    public int getEndOffset() {
      return mySegmentIterator.getCurrentEndOffset();
    }

    public int getFontStyle() {
      return myCurrentFontStyle;
    }

    @NotNull
    public String getFontFamilyName() {
      return mySegmentIterator.getCurrentFontFamilyName();
    }

    @Nullable
    public Color getForegroundColor() {
      return myCurrentForegroundColor;
    }

    @Nullable
    public Color getBackgroundColor() {
      return myCurrentBackgroundColor;
    }

    public void dispose() {
      myRangeIterator.dispose();
    }
  }

  private static class CompositeRangeIterator implements RangeIterator {
    private final @NotNull Color myDefaultForeground;
    private final @NotNull Color myDefaultBackground;
    private final IteratorWrapper[] myIterators;
    private final TextAttributes myMergedAttributes = new TextAttributes();
    private int overlappingRangesCount;
    private int myCurrentStart;
    private int myCurrentEnd;

    // iterators have priority corresponding to their order in the parameter list - rightmost having the largest priority
    public CompositeRangeIterator(@NotNull EditorColorsScheme colorsScheme, RangeIterator... iterators) {
      myDefaultForeground = colorsScheme.getDefaultForeground();
      myDefaultBackground = colorsScheme.getDefaultBackground();
      myIterators = new IteratorWrapper[iterators.length];
      for (int i = 0; i < iterators.length; i++) {
        myIterators[i] = new IteratorWrapper(iterators[i], i);
      }
    }

    @Override
    public boolean atEnd() {
      boolean validIteratorExists = false;
      for (int i = 0; i < myIterators.length; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          continue;
        }
        RangeIterator iterator = wrapper.iterator;
        if (!iterator.atEnd() || overlappingRangesCount > 0 && (i >= overlappingRangesCount || iterator.getRangeEnd() > myCurrentEnd)) {
          validIteratorExists = true;
        }
      }
      return !validIteratorExists;
    }

    @Override
    public void advance() {
      int max = overlappingRangesCount == 0 ? myIterators.length : overlappingRangesCount;
      for (int i = 0; i < max; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          continue;
        }
        RangeIterator iterator = wrapper.iterator;
        if (overlappingRangesCount > 0 && iterator.getRangeEnd() > myCurrentEnd) {
          continue;
        }
        if (iterator.atEnd()) {
          iterator.dispose();
          myIterators[i] = null;
        }
        else {
          iterator.advance();
        }
      }
      Arrays.sort(myIterators, RANGE_SORTER);
      myCurrentStart = Math.max(myIterators[0].iterator.getRangeStart(), myCurrentEnd);
      myCurrentEnd = Integer.MAX_VALUE;
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < myIterators.length; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          break;
        }
        RangeIterator iterator = wrapper.iterator;
        int nearestBound;
        if (iterator.getRangeStart() > myCurrentStart) {
          nearestBound = iterator.getRangeStart();
        }
        else {
          nearestBound = iterator.getRangeEnd();
        }
        myCurrentEnd = Math.min(myCurrentEnd, nearestBound);
      }
      for (overlappingRangesCount = 1; overlappingRangesCount < myIterators.length; overlappingRangesCount++) {
        IteratorWrapper wrapper = myIterators[overlappingRangesCount];
        if (wrapper == null || wrapper.iterator.getRangeStart() > myCurrentStart) {
          break;
        }
      }
    }

    private final Comparator<IteratorWrapper> RANGE_SORTER  = new Comparator<IteratorWrapper>() {
      @Override
      public int compare(IteratorWrapper o1, IteratorWrapper o2) {
        if (o1 == null) {
          return 1;
        }
        if (o2 == null) {
          return -1;
        }
        int startDiff = Math.max(o1.iterator.getRangeStart(), myCurrentEnd) - Math.max(o2.iterator.getRangeStart(), myCurrentEnd);
        if (startDiff != 0) {
          return startDiff;
        }
        return o2.order - o1.order;
      }
    };

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      TextAttributes ta = myIterators[0].iterator.getTextAttributes();
      myMergedAttributes.setAttributes(ta.getForegroundColor(), ta.getBackgroundColor(), null, null, null, ta.getFontType());
      for (int i = 1; i < overlappingRangesCount; i++) {
        merge(myIterators[i].iterator.getTextAttributes());
      }
      return myMergedAttributes;
    }

    private void merge(TextAttributes attributes) {
      Color myBackground = myMergedAttributes.getBackgroundColor();
      if (myBackground == null || myDefaultBackground.equals(myBackground)) {
        myMergedAttributes.setBackgroundColor(attributes.getBackgroundColor());
      }
      Color myForeground = myMergedAttributes.getForegroundColor();
      if (myForeground == null || myDefaultForeground.equals(myForeground)) {
        myMergedAttributes.setForegroundColor(attributes.getForegroundColor());
      }
      if (myMergedAttributes.getFontType() == Font.PLAIN) {
        myMergedAttributes.setFontType(attributes.getFontType());
      }
    }

    @Override
    public void dispose() {
      for (IteratorWrapper wrapper : myIterators) {
        if (wrapper != null) {
          wrapper.iterator.dispose();
        }
      }
    }

    private static class IteratorWrapper {
      private final RangeIterator iterator;
      private final int order;

      private IteratorWrapper(RangeIterator iterator, int order) {
        this.iterator = iterator;
        this.order = order;
      }
    }
  }

  private static class MarkupModelRangeIterator implements RangeIterator {
    private final boolean myUnsupportedModel;
    private final int myStartOffset;
    private final int myEndOffset;
    private final EditorColorsScheme myColorsScheme;
    private final Color myDefaultForeground;
    private final Color myDefaultBackground;
    private final MarkupIterator<RangeHighlighterEx> myIterator;

    private int myCurrentStart;
    private int myCurrentEnd;
    private TextAttributes myCurrentAttributes;
    private int myNextStart;
    private int myNextEnd;
    private TextAttributes myNextAttributes;

    private MarkupModelRangeIterator(@Nullable MarkupModel markupModel,
                                     @NotNull EditorColorsScheme colorsScheme,
                                     int startOffset,
                                     int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myColorsScheme = colorsScheme;
      myDefaultForeground = colorsScheme.getDefaultForeground();
      myDefaultBackground = colorsScheme.getDefaultBackground();
      myUnsupportedModel = !(markupModel instanceof MarkupModelEx);
      if (myUnsupportedModel) {
        myIterator = null;
        return;
      }
      myIterator = ((MarkupModelEx)markupModel).overlappingIterator(startOffset, endOffset);
      try {
        findNextSuitableRange();
      }
      catch (RuntimeException e) {
        myIterator.dispose();
        throw e;
      }
      catch (Error e) {
        myIterator.dispose();
        throw e;
      }
    }

    @Override
    public boolean atEnd() {
      return myUnsupportedModel || myNextAttributes == null;
    }

    @Override
    public void advance() {
      myCurrentStart = myNextStart;
      myCurrentEnd = myNextEnd;
      myCurrentAttributes = myNextAttributes;
      findNextSuitableRange();
    }

    private void findNextSuitableRange() {
      myNextAttributes = null;
      while(myIterator.hasNext()) {
        RangeHighlighterEx highlighter = myIterator.next();
        if (highlighter == null || !highlighter.isValid() || !isInterestedInLayer(highlighter.getLayer())) {
          continue;
        }
        // LINES_IN_RANGE highlighters are not supported currently
        myNextStart = Math.max(highlighter.getStartOffset(), myStartOffset);
        myNextEnd = Math.min(highlighter.getEndOffset(), myEndOffset);
        if (myNextStart >= myEndOffset) {
          break;
        }
        if (myNextStart < myCurrentEnd) {
          continue; // overlapping ranges withing document markup model are not supported currently
        }
        TextAttributes attributes = null;
        Object tooltip = highlighter.getErrorStripeTooltip();
        if (tooltip instanceof HighlightInfo) {
          HighlightInfo info = (HighlightInfo)tooltip;
          TextAttributesKey key = info.forcedTextAttributesKey;
          if (key == null) {
            HighlightInfoType type = info.type;
            key = type.getAttributesKey();
          }
          if (key != null) {
            attributes = myColorsScheme.getAttributes(key);
          }
        }
        if (attributes == null) {
          continue;
        }
        Color foreground = attributes.getForegroundColor();
        Color background = attributes.getBackgroundColor();
        if ((foreground == null || myDefaultForeground.equals(foreground))
            && (background == null || myDefaultBackground.equals(background))
            && attributes.getFontType() == Font.PLAIN) {
          continue;
        }
        myNextAttributes = attributes;
        break;
      }
    }

    private static boolean isInterestedInLayer(int layer) {
      return layer != HighlighterLayer.CARET_ROW
             && layer != HighlighterLayer.SELECTION
             && layer != HighlighterLayer.ERROR
             && layer != HighlighterLayer.WARNING;
    }

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentAttributes;
    }

    @Override
    public void dispose() {
      if (myIterator != null) {
        myIterator.dispose();
      }
    }
  }

  private static class HighlighterRangeIterator implements RangeIterator {
    private static final TextAttributes EMPTY_ATTRIBUTES = new TextAttributes();

    private final HighlighterIterator myIterator;
    private final int myStartOffset;
    private final int myEndOffset;

    private int myCurrentStart;
    private int myCurrentEnd;
    private TextAttributes myCurrentAttributes;

    public HighlighterRangeIterator(@NotNull EditorHighlighter highlighter, int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myIterator = highlighter.createIterator(startOffset);
    }

    @Override
    public boolean atEnd() {
      return myIterator.atEnd() || getCurrentStart() >= myEndOffset;
    }

    private int getCurrentStart() {
      return Math.max(myIterator.getStart(), myStartOffset);
    }

    private int getCurrentEnd() {
      return Math.min(myIterator.getEnd(), myEndOffset);
    }

    @Override
    public void advance() {
      myCurrentStart = getCurrentStart();
      myCurrentEnd = getCurrentEnd();
      myCurrentAttributes = myIterator.getTokenType() == TokenType.BAD_CHARACTER ? EMPTY_ATTRIBUTES : myIterator.getTextAttributes();
      myIterator.advance();
    }

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentAttributes;
    }

    @Override
    public void dispose() {
    }
  }

  private interface RangeIterator {
    boolean atEnd();
    void advance();
    int getRangeStart();
    int getRangeEnd();
    TextAttributes getTextAttributes();
    void dispose();
  }

  private static class SegmentIterator {
    private final CharSequence myCharSequence;
    private final FontPreferences myFontPreferences;

    private int myCurrentStartOffset;
    private int myCurrentOffset;
    private int myEndOffset;
    private int myFontStyle;
    private String myCurrentFontFamilyName;
    private String myNextFontFamilyName;

    private SegmentIterator(CharSequence charSequence, FontPreferences fontPreferences) {
      myCharSequence = charSequence;
      myFontPreferences = fontPreferences;
    }

    public void reset(int startOffset, int endOffset, int fontStyle) {
      myCurrentOffset = startOffset;
      myEndOffset = endOffset;
      myFontStyle = fontStyle;
    }

    public boolean atEnd() {
      return myCurrentOffset >= myEndOffset;
    }

    public void advance() {
      myCurrentFontFamilyName = myNextFontFamilyName;
      myCurrentStartOffset = myCurrentOffset;
      for (; myCurrentOffset < myEndOffset; myCurrentOffset++) {
        FontInfo fontInfo = ComplementaryFontsRegistry.getFontAbleToDisplay(myCharSequence.charAt(myCurrentOffset),
                                                                            myFontStyle,
                                                                            myFontPreferences);
        String fontFamilyName = fontInfo.getFont().getFamily();

        if (myCurrentFontFamilyName == null) {
          myCurrentFontFamilyName = fontFamilyName;
        }
        else if (!myCurrentFontFamilyName.equals(fontFamilyName)) {
          myNextFontFamilyName = fontFamilyName;
          break;
        }
      }
    }

    public int getCurrentStartOffset() {
      return myCurrentStartOffset;
    }

    public int getCurrentEndOffset() {
      return myCurrentOffset;
    }

    public String getCurrentFontFamilyName() {
      return myCurrentFontFamilyName;
    }
  }

  public static class RawTextSetter implements CopyPastePreProcessor {
    private final TextWithMarkupProcessor myProcessor;

    public RawTextSetter(TextWithMarkupProcessor processor) {
      myProcessor = processor;
    }

    @Nullable
    @Override
    public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
      myProcessor.setRawText(text);
      return null;
    }

    @NotNull
    @Override
    public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
      return text;
    }
  }
}
