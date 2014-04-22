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
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.DisposableIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class TextWithMarkupProcessor implements CopyPastePostProcessor<TextBlockTransferableData> {
  private static final Logger LOG = Logger.getInstance("#" + TextWithMarkupProcessor.class.getName());

  private final EventDispatcher<TextWithMarkupBuilder> myBuilders = EventDispatcher.create(TextWithMarkupBuilder.class);

  public void addBuilder(TextWithMarkupBuilder builder) {
    myBuilders.addListener(builder);
  }

  @Nullable
  @Override
  public TextBlockTransferableData collectTransferableData(PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
    if (!Registry.is("editor.richcopy.enable")) {
      return null;
    }

    try {
      RichCopySettings settings = RichCopySettings.getInstance();
      Document document = editor.getDocument();
      final int indentSymbolsToStrip;
      final int firstLineStartOffset;
      if (settings.isStripIndents() && startOffsets.length == 1) {
        Pair<Integer, Integer> p = calcIndentSymbolsToStrip(document, startOffsets[0], endOffsets[0]);
        firstLineStartOffset = p.first;
        indentSymbolsToStrip = p.second;
      }
      else {
        firstLineStartOffset = startOffsets[0];
        indentSymbolsToStrip = 0;
      }
      logInitial(document, startOffsets, endOffsets, indentSymbolsToStrip, firstLineStartOffset);
      CharSequence text = document.getCharsSequence();
      EditorColorsScheme schemeToUse = settings.getColorsScheme(editor.getColorsScheme());
      EditorHighlighter highlighter = HighlighterFactory.createHighlighter(file.getVirtualFile(), schemeToUse, file.getProject());
      highlighter.setText(text);
      MarkupModel markupModel = DocumentMarkupModel.forDocument(document, file.getProject(), false);

      myBuilders.getMulticaster().init(schemeToUse.getDefaultForeground(),
                                       schemeToUse.getDefaultBackground(),
                                       FontMapper.getPhysicalFontName(schemeToUse.getEditorFontName()),
                                       schemeToUse.getEditorFontSize());

      EventDispatcher<TextWithMarkupBuilder> activeBuilders = EventDispatcher.create(TextWithMarkupBuilder.class);
      activeBuilders.getListeners().addAll(myBuilders.getListeners());
      Context context = new Context(activeBuilders.getMulticaster(), text, schemeToUse, indentSymbolsToStrip);

      outer:
      for (int i = 0; i < startOffsets.length; i++) {
        int startOffsetToUse;
        if (i == 0) {
          startOffsetToUse = firstLineStartOffset;
        }
        else {
          startOffsetToUse = startOffsets[i];
          activeBuilders.getMulticaster().addTextFragment("\n", 0, 1);
        }
        int endOffsetToUse = endOffsets[i];
        context.reset();
        if (endOffsetToUse <= startOffsetToUse) {
          continue;
        }
        DisposableIterator<SegmentInfo> it = aggregateSyntaxInfo(schemeToUse,
                                                                 wrap(highlighter, text, schemeToUse, startOffsetToUse, endOffsetToUse),
                                                                 wrap(markupModel, text, schemeToUse, startOffsetToUse, endOffsetToUse));
        try {
          while (it.hasNext()) {
            Iterator<TextWithMarkupBuilder> builderIterator = activeBuilders.getListeners().iterator();
            while (builderIterator.hasNext()) {
              if (builderIterator.next().isOverflowed()) {
                builderIterator.remove();
                if (activeBuilders.getListeners().isEmpty()) {
                  break outer;
                }
              }
            }
            SegmentInfo info = it.next();
            if (info.startOffset >= endOffsetToUse) {
              break;
            }
            context.onNewData(info);
          }
        }
        finally {
          it.dispose();
        }
        context.onIterationEnd(endOffsetToUse);
      }

      myBuilders.getMulticaster().complete();
    }
    catch (Exception e) {
      // catching the exception so that the rest of copy/paste functionality can still work fine
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  @Override
  public TextBlockTransferableData extractTransferableData(Transferable content) {
    return null;
  }

  @Override
  public void processTransferableData(Project project,
                                      Editor editor,
                                      RangeMarker bounds,
                                      int caretOffset,
                                      Ref<Boolean> indented,
                                      TextBlockTransferableData value) {

  }

  private static void logInitial(@NotNull Document document,
                                 @NotNull int[] startOffsets,
                                 @NotNull int[] endOffsets,
                                 int indentSymbolsToStrip,
                                 int firstLineStartOffset)
  {
    if (!Registry.is("editor.richcopy.debug")) {
      return;
    }

    StringBuilder buffer = new StringBuilder();
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
    LOG.info(String.format(
      "Preparing syntax-aware text. Given: %s selection, indent symbols to strip=%d, first line start offset=%d, selected text:%n%s",
      startOffsets.length > 1 ? "block" : "regular", indentSymbolsToStrip, firstLineStartOffset, buffer
    ));
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

  private static DisposableIterator<SegmentInfo> aggregateSyntaxInfo(@NotNull EditorColorsScheme colorsScheme,
                                                                     @NotNull final DisposableIterator<List<SegmentInfo>>... iterators)
  {
    final Color defaultForeground = colorsScheme.getDefaultForeground();
    final Color defaultBackground = colorsScheme.getDefaultBackground();
    return new DisposableIterator<SegmentInfo>() {

      @NotNull private final Queue<SegmentInfo> myInfos = new PriorityQueue<SegmentInfo>();
      @NotNull private final Map<SegmentInfo, DisposableIterator<List<SegmentInfo>>> myEndMarkers
        = new IdentityHashMap<SegmentInfo, DisposableIterator<List<SegmentInfo>>>();

      {
        for (DisposableIterator<List<SegmentInfo>> iterator : iterators) {
          extract(iterator);
        }
      }

      @Override
      public boolean hasNext() {
        return !myInfos.isEmpty();
      }

      @Override
      public SegmentInfo next() {
        SegmentInfo result = myInfos.remove();
        DisposableIterator<List<SegmentInfo>> iterator = myEndMarkers.remove(result);
        if (iterator != null) {
          extract(iterator);
        }
        while (!myInfos.isEmpty()) {
          SegmentInfo toMerge = myInfos.peek();
          if (toMerge.endOffset > result.endOffset) {
            break;
          }
          myInfos.remove();
          result = merge(result, toMerge);
          DisposableIterator<List<SegmentInfo>> it = myEndMarkers.remove(toMerge);
          if (it != null) {
            extract(it);
          }
        }
        return result;
      }

      @NotNull
      private SegmentInfo merge(@NotNull SegmentInfo info1, @NotNull SegmentInfo info2) {
        Color background = info1.background;
        if (background == null || defaultBackground.equals(background)) {
          background = info2.background;
        }

        Color foreground = info1.foreground;
        if (foreground == null || defaultForeground.equals(foreground)) {
          foreground = info2.foreground;
        }

        int fontStyle = info1.fontStyle;
        if (fontStyle == Font.PLAIN) {
          fontStyle = info2.fontStyle;
        }
        return new SegmentInfo(foreground, background, info1.fontFamilyName, fontStyle, info1.startOffset, info1.endOffset);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void dispose() {
        for (DisposableIterator<List<SegmentInfo>> iterator : iterators) {
          iterator.dispose();
        }
      }

      private void extract(@NotNull DisposableIterator<List<SegmentInfo>> iterator) {
        while (iterator.hasNext()) {
          List<SegmentInfo> infos = iterator.next();
          if (infos.isEmpty()) {
            continue;
          }
          myInfos.addAll(infos);
          myEndMarkers.put(infos.get(infos.size() - 1), iterator);
          break;
        }
      }
    };
  }

  @NotNull
  private static DisposableIterator<List<SegmentInfo>> wrap(@NotNull final EditorHighlighter highlighter,
                                                            @NotNull final CharSequence text,
                                                            @NotNull final EditorColorsScheme colorsScheme,
                                                            final int startOffset,
                                                            final int endOffset)
  {
    final HighlighterIterator highlighterIterator = highlighter.createIterator(startOffset);
    return new DisposableIterator<List<SegmentInfo>>() {

      @Nullable private List<SegmentInfo> myCached;

      @Override
      public boolean hasNext() {
        return myCached != null || updateCached();
      }

      @Override
      public List<SegmentInfo> next() {
        if (myCached != null) {
          List<SegmentInfo> result = myCached;
          myCached = null;
          return result;
        }

        if (!updateCached()) {
          throw new UnsupportedOperationException();
        }
        return myCached;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void dispose() {
      }

      private boolean updateCached() {
        if (highlighterIterator.atEnd()) {
          return false;
        }
        int tokenStart = Math.max(highlighterIterator.getStart(), startOffset);
        if (tokenStart >= endOffset) {
          return false;
        }

        if (highlighterIterator.getTokenType() == TokenType.BAD_CHARACTER) {
          // Skip syntax errors.
          highlighterIterator.advance();
          return updateCached();
        }
        TextAttributes attributes = highlighterIterator.getTextAttributes();
        int tokenEnd = Math.min(highlighterIterator.getEnd(), endOffset);
        myCached = SegmentInfo.produce(attributes, text, colorsScheme, tokenStart, tokenEnd);
        highlighterIterator.advance();
        return true;
      }
    };
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static DisposableIterator<List<SegmentInfo>> wrap(@NotNull MarkupModel model,
                                                            @NotNull final CharSequence charSequence,
                                                            @NotNull final EditorColorsScheme colorsScheme,
                                                            final int startOffset,
                                                            final int endOffset)
  {
    if (!(model instanceof MarkupModelEx)) {
      return DisposableIterator.EMPTY;
    }
    final DisposableIterator<RangeHighlighterEx> iterator = ((MarkupModelEx)model).overlappingIterator(startOffset, endOffset);
    final Color defaultForeground = colorsScheme.getDefaultForeground();
    final Color defaultBackground = colorsScheme.getDefaultBackground();
    return new DisposableIterator<List<SegmentInfo>>() {

      @Nullable private List<SegmentInfo> myCached;

      @Override
      public boolean hasNext() {
        return myCached != null || updateCached();
      }

      @Override
      public List<SegmentInfo> next() {
        if (myCached != null) {
          List<SegmentInfo> result = myCached;
          myCached = null;
          return result;
        }

        if (!updateCached()) {
          throw new UnsupportedOperationException();
        }
        return myCached;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void dispose() {
        iterator.dispose();
      }

      private boolean updateCached() {
        if (!iterator.hasNext()) {
          return false;
        }

        RangeHighlighterEx highlighter = iterator.next();
        while (highlighter == null
               || !highlighter.isValid()
               || !isInterestedHighlightLayer(highlighter.getLayer()))
        {
          if (!iterator.hasNext()) {
            return false;
          }
          highlighter = iterator.next();
        }

        int tokenStart = Math.max(highlighter.getStartOffset(), startOffset);
        if (tokenStart >= endOffset) {
          return false;
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
            attributes = colorsScheme.getAttributes(key);
          }
        }

        if (attributes == null) {
          return updateCached();
        }
        Color foreground = attributes.getForegroundColor();
        Color background = attributes.getBackgroundColor();
        if ((foreground == null || defaultForeground.equals(foreground))
            && (background == null || defaultBackground.equals(background))
            && attributes.getFontType() == Font.PLAIN)
        {
          return updateCached();
        }

        int tokenEnd = Math.min(highlighter.getEndOffset(), endOffset);
        //noinspection ConstantConditions
        myCached = SegmentInfo.produce(attributes, charSequence, colorsScheme, tokenStart, tokenEnd);
        return true;
      }

      private boolean isInterestedHighlightLayer(int layer) {
        return layer != HighlighterLayer.CARET_ROW && layer != HighlighterLayer.SELECTION && layer != HighlighterLayer.ERROR
               && layer != HighlighterLayer.WARNING;
      }
    };
  }

  private static class Context {
    @NotNull private final TextWithMarkupBuilder myBuilder;
    @NotNull private final CharSequence myText;
    @NotNull private final Color        myDefaultForeground;
    @NotNull private final Color        myDefaultBackground;

    @Nullable private Color  myBackground;
    @Nullable private Color  myForeground;
    @Nullable private String myFontFamilyName;

    private final int myIndentSymbolsToStrip;

    private int myFontStyle   = -1;
    private int myStartOffset = -1;

    private int myIndentSymbolsToStripAtCurrentLine;

    Context(@NotNull TextWithMarkupBuilder builder, @NotNull CharSequence charSequence, @NotNull EditorColorsScheme scheme, int indentSymbolsToStrip) {
      myBuilder = builder;
      myText = charSequence;
      myDefaultForeground = scheme.getDefaultForeground();
      myDefaultBackground = scheme.getDefaultBackground();
      myIndentSymbolsToStrip = indentSymbolsToStrip;
    }

    public void reset() {
      myStartOffset = -1;
      myIndentSymbolsToStripAtCurrentLine = 0;
    }

    public void onNewData(@NotNull SegmentInfo info) {
      if (myStartOffset < 0) {
        myStartOffset = info.startOffset;
      }

      boolean whiteSpacesOnly = containsWhiteSpacesOnly(info);

      processBackground(info);
      if (!whiteSpacesOnly) {
        processForeground(info);
        processFontFamilyName(info);
        processFontStyle(info);
      }
    }

    private boolean containsWhiteSpacesOnly(@NotNull SegmentInfo info) {
      for (int i = info.startOffset, limit = info.endOffset; i < limit; i++) {
        char c = myText.charAt(i);
        if (c != ' ' && c != '\t' && c != '\n') {
          return false;
        }
      }
      return true;
    }

    private void processFontStyle(@NotNull SegmentInfo info) {
      if (info.fontStyle != myFontStyle) {
        addTextIfPossible(info.startOffset);
        myBuilder.setFontStyle(info.fontStyle);
        myFontStyle = info.fontStyle;
      }
    }

    private void processFontFamilyName(@NotNull SegmentInfo info) {
      String fontFamilyName = FontMapper.getPhysicalFontName(info.fontFamilyName);
      if (!fontFamilyName.equals(myFontFamilyName)) {
        addTextIfPossible(info.startOffset);
        myBuilder.setFontFamily(fontFamilyName);
        myFontFamilyName = fontFamilyName;
      }
    }

    private void processForeground(@NotNull SegmentInfo info) {
      if (myForeground == null && info.foreground != null) {
        addTextIfPossible(info.startOffset);
        myForeground = info.foreground;
        myBuilder.setForeground(info.foreground);
      }
      else if (myForeground != null) {
        Color c = info.foreground == null ? myDefaultForeground : info.foreground;
        if (!myForeground.equals(c)) {
          addTextIfPossible(info.startOffset);
          myBuilder.setForeground(c);
          myForeground = c;
        }
      }
    }

    private void processBackground(@NotNull SegmentInfo info) {
      if (myBackground == null && info.background != null && !myDefaultBackground.equals(info.background)) {
        addTextIfPossible(info.startOffset);
        myBackground = info.background;
        myBuilder.setBackground(info.background);
      }
      else if (myBackground != null) {
        Color c = info.background == null ? myDefaultBackground : info.background;
        if (!myBackground.equals(c)) {
          addTextIfPossible(info.startOffset);
          myBuilder.setBackground(c);
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
          case '\n':
            myIndentSymbolsToStripAtCurrentLine = myIndentSymbolsToStrip;
            myBuilder.addTextFragment(myText, myStartOffset, i + 1);
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
        myBuilder.addTextFragment(myText, myStartOffset, endOffset);
        myStartOffset = endOffset;
      }
    }

    public void onIterationEnd(int endOffset) {
      addTextIfPossible(endOffset);
    }
  }

  private static class SegmentInfo implements Comparable<SegmentInfo> {

    @Nullable public final Color  foreground;
    @Nullable public final Color  background;
    @NotNull public final  String fontFamilyName;

    public final int fontStyle;
    public final int startOffset;
    public final int endOffset;

    SegmentInfo(@Nullable Color foreground,
                @Nullable Color background,
                @NotNull String fontFamilyName,
                int fontStyle,
                int startOffset,
                int endOffset)
    {
      this.foreground = foreground;
      this.background = background;
      this.fontFamilyName = fontFamilyName;
      this.fontStyle = fontStyle;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    @NotNull
    public static List<SegmentInfo> produce(@NotNull TextAttributes attribute,
                                            @NotNull CharSequence text,
                                            @NotNull EditorColorsScheme colorsScheme,
                                            int start,
                                            int end)
    {
      if (end <= start) {
        return Collections.emptyList();
      }
      List<SegmentInfo> result = ContainerUtilRt.newArrayList();
      int currentStart = start;
      int fontSize = colorsScheme.getEditorFontSize();
      int fontStyle = attribute.getFontType();
      String defaultFontFamily = colorsScheme.getEditorFontName();
      Font font = ComplementaryFontsRegistry.getFontAbleToDisplay(text.charAt(start), fontSize, fontStyle, defaultFontFamily).getFont();
      String currentFontFamilyName = font.getFamily();
      String candidateFontFamilyName;
      for (int i = start + 1; i < end; i++) {
        font = ComplementaryFontsRegistry.getFontAbleToDisplay(text.charAt(i), fontSize, fontStyle, defaultFontFamily).getFont();
        candidateFontFamilyName = font.getFamily();
        if (!candidateFontFamilyName.equals(currentFontFamilyName)) {
          result.add(new SegmentInfo(attribute.getForegroundColor(),
                                     attribute.getBackgroundColor(),
                                     currentFontFamilyName,
                                     fontStyle,
                                     currentStart,
                                     i
          ));
          currentStart = i;
          currentFontFamilyName = candidateFontFamilyName;
        }
      }

      if (currentStart < end) {
        result.add(new SegmentInfo(attribute.getForegroundColor(),
                                   attribute.getBackgroundColor(),
                                   currentFontFamilyName,
                                   fontStyle,
                                   currentStart,
                                   end
        ));
      }

      return result;
    }

    @Override
    public int compareTo(@NotNull SegmentInfo o) {
      return startOffset - o.startOffset;
    }

    @Override
    public int hashCode() {
      int result = foreground != null ? foreground.hashCode() : 0;
      result = 31 * result + (background != null ? background.hashCode() : 0);
      result = 31 * result + fontFamilyName.hashCode();
      result = 31 * result + fontStyle;
      result = 31 * result + startOffset;
      result = 31 * result + endOffset;
      return result;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SegmentInfo info = (SegmentInfo)o;

      if (endOffset != info.endOffset) return false;
      if (fontStyle != info.fontStyle) return false;
      if (startOffset != info.startOffset) return false;
      if (background != null ? !background.equals(info.background) : info.background != null) return false;
      if (!fontFamilyName.equals(info.fontFamilyName)) return false;
      if (foreground != null ? !foreground.equals(info.foreground) : info.foreground != null) return false;

      return true;
    }

    @Override
    public String toString() {
      StringBuilder fontStyleAsString = new StringBuilder();
      if (fontStyle == Font.PLAIN) {
        fontStyleAsString.append("plain");
      }
      else {
        if ((fontStyle & Font.BOLD) != 0) {
          fontStyleAsString.append("bold ");
        }
        if ((fontStyle & Font.ITALIC) != 0) {
          fontStyleAsString.append("italic ");
        }
        if (fontStyleAsString.length() > 0) {
          fontStyleAsString.setLength(fontStyleAsString.length() - 1);
        }
        else {
          fontStyleAsString.append("unknown font style");
        }
      }
      return String.format("%d-%d: %s, %s", startOffset, endOffset, fontFamilyName, fontStyleAsString);
    }
  }
}
