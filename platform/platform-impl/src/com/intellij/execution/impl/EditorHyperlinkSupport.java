/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class EditorHyperlinkSupport {
  private static final Key<TextAttributes> OLD_HYPERLINK_TEXT_ATTRIBUTES = Key.create("OLD_HYPERLINK_TEXT_ATTRIBUTES");
  private static final Key<HyperlinkInfoTextAttributes> HYPERLINK = Key.create("HYPERLINK");

  private final Editor myEditor;
  @NotNull private final Project myProject;
  private final AsyncFilterRunner myFilterRunner;

  public EditorHyperlinkSupport(@NotNull final Editor editor, @NotNull final Project project) {
    myEditor = editor;
    myProject = project;
    myFilterRunner = new AsyncFilterRunner(this, myEditor);

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (mouseEvent.getButton() == MouseEvent.BUTTON1 && !mouseEvent.isPopupTrigger()) {
          Runnable runnable = getLinkNavigationRunnable(myEditor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
          if (runnable != null) {
            runnable.run();
          }
        }
      }
    });

    editor.addEditorMouseMotionListener(new EditorMouseMotionAdapter() {
      @Override
      public void mouseMoved(EditorMouseEvent e) {
        if (e.getArea() != EditorMouseEventArea.EDITING_AREA) return;
        final HyperlinkInfo info = getHyperlinkInfoByPoint(e.getMouseEvent().getPoint());
        Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        if (info != null) {
          myEditor.getContentComponent().setCursor(handCursor);
        }
        else if (myEditor.getContentComponent().getCursor() == handCursor) {
            final Cursor cursor = editor instanceof EditorEx ?
                                  UIUtil.getTextCursor(((EditorEx)editor).getBackgroundColor()) :
                                  Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
            myEditor.getContentComponent().setCursor(cursor);
        }
      }
    }
    );
  }

  public void clearHyperlinks() {
    for (RangeHighlighter highlighter : getHyperlinks(0, myEditor.getDocument().getTextLength(), myEditor)) {
      removeHyperlink(highlighter);
    }
  }

  @SuppressWarnings("SameParameterValue")
  public void waitForPendingFilters(long timeoutMs) {
    myFilterRunner.waitForPendingFilters(timeoutMs);
  }
  
  @Deprecated
  public Map<RangeHighlighter, HyperlinkInfo> getHyperlinks() {
    LinkedHashMap<RangeHighlighter, HyperlinkInfo> result = new LinkedHashMap<>();
    for (RangeHighlighter highlighter : getHyperlinks(0, myEditor.getDocument().getTextLength(), myEditor)) {
      HyperlinkInfo info = getHyperlinkInfo(highlighter);
      if (info != null) {
        result.put(highlighter, info);
      }
    }
    return result;
  }

  @Nullable
  public Runnable getLinkNavigationRunnable(final LogicalPosition logical) {
    if (EditorUtil.inVirtualSpace(myEditor, logical)) {
      return null;
    }

    final RangeHighlighter range = findLinkRangeAt(myEditor.logicalPositionToOffset(logical));
    if (range != null) {
      final HyperlinkInfo hyperlinkInfo = getHyperlinkInfo(range);
      if (hyperlinkInfo != null) {
        return () -> {
          if (hyperlinkInfo instanceof HyperlinkInfoBase) {
            final Point point = myEditor.logicalPositionToXY(logical);
            final MouseEvent event = new MouseEvent(myEditor.getContentComponent(), 0, 0, 0, point.x, point.y, 1, false);
            ((HyperlinkInfoBase)hyperlinkInfo).navigate(myProject, new RelativePoint(event));
          }
          else {
            hyperlinkInfo.navigate(myProject);
          }
          linkFollowed(myEditor, getHyperlinks(0, myEditor.getDocument().getTextLength(),myEditor), range);
        };
      }
    }
    return null;
  }

  @Nullable
  public static HyperlinkInfo getHyperlinkInfo(@NotNull RangeHighlighter range) {
    final HyperlinkInfoTextAttributes attributes = range.getUserData(HYPERLINK);
    return attributes != null ? attributes.getHyperlinkInfo() : null;
  }

  @Nullable
  private RangeHighlighter findLinkRangeAt(final int offset) {
    //noinspection LoopStatementThatDoesntLoop
    for (final RangeHighlighter highlighter : getHyperlinks(offset, offset, myEditor)) {
        return highlighter;
    }
    return null;
  }

  @Nullable
  private HyperlinkInfo getHyperlinkAt(final int offset) {
    RangeHighlighter range = findLinkRangeAt(offset);
    return range == null ? null : getHyperlinkInfo(range);
  }

  public List<RangeHighlighter> findAllHyperlinksOnLine(int line) {
    final int lineStart = myEditor.getDocument().getLineStartOffset(line);
    final int lineEnd = myEditor.getDocument().getLineEndOffset(line);
    return getHyperlinks(lineStart, lineEnd, myEditor);
  }

  private static List<RangeHighlighter> getHyperlinks(int startOffset, int endOffset, final Editor editor) {
    final MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    final CommonProcessors.CollectProcessor<RangeHighlighterEx> processor = new CommonProcessors.CollectProcessor<>();
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset,
                                                        new FilteringProcessor<>(
                                                          rangeHighlighterEx -> rangeHighlighterEx.isValid() &&
                                                                                getHyperlinkInfo(rangeHighlighterEx) != null, processor)
    );
    return new ArrayList<>(processor.getResults());
  }

  public void removeHyperlink(@NotNull RangeHighlighter hyperlink) {
    myEditor.getMarkupModel().removeHighlighter(hyperlink);
  }

  @Nullable
  public HyperlinkInfo getHyperlinkInfoByLineAndCol(final int line, final int col) {
    return getHyperlinkAt(myEditor.logicalPositionToOffset(new LogicalPosition(line, col)));
  }

  /**
   * @deprecated for binary compatibility with older plugins
   * @see #createHyperlink(int, int, TextAttributes, HyperlinkInfo)
   */
  public void addHyperlink(final int highlightStartOffset,
                           final int highlightEndOffset,
                           @Nullable final TextAttributes highlightAttributes,
                           @NotNull final HyperlinkInfo hyperlinkInfo) {
    createHyperlink(highlightStartOffset, highlightEndOffset, highlightAttributes, hyperlinkInfo);
  }

  @NotNull
  public RangeHighlighter createHyperlink(int highlightStartOffset,
                                          int highlightEndOffset,
                                          @Nullable TextAttributes highlightAttributes,
                                          @NotNull HyperlinkInfo hyperlinkInfo) {
    return createHyperlink(highlightStartOffset, highlightEndOffset, highlightAttributes, hyperlinkInfo, null,
                           HighlighterLayer.HYPERLINK);
  }

  @NotNull
  private RangeHighlighter createHyperlink(final int highlightStartOffset,
                                           final int highlightEndOffset,
                                           @Nullable final TextAttributes highlightAttributes,
                                           @NotNull final HyperlinkInfo hyperlinkInfo,
                                           @Nullable TextAttributes followedHyperlinkAttributes, 
                                           int layer) {
    TextAttributes textAttributes = highlightAttributes != null ? highlightAttributes : getHyperlinkAttributes();
    final RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset,
                                                                                       highlightEndOffset,
                                                                                       layer,
                                                                                       textAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
    associateHyperlink(highlighter, hyperlinkInfo, followedHyperlinkAttributes);
    return highlighter;
  }

  public static void associateHyperlink(@NotNull RangeHighlighter highlighter, @NotNull HyperlinkInfo hyperlinkInfo) {
    associateHyperlink(highlighter, hyperlinkInfo, null);
  }

  private static void associateHyperlink(@NotNull RangeHighlighter highlighter,
                                        @NotNull HyperlinkInfo hyperlinkInfo,
                                        @Nullable TextAttributes followedHyperlinkAttributes) {
    highlighter.putUserData(HYPERLINK, new HyperlinkInfoTextAttributes(hyperlinkInfo, followedHyperlinkAttributes));
  }

  @Nullable
  public HyperlinkInfo getHyperlinkInfoByPoint(final Point p) {
    final LogicalPosition pos = myEditor.xyToLogicalPosition(new Point(p.x, p.y));
    if (EditorUtil.inVirtualSpace(myEditor, pos)) {
      return null;
    }

    return getHyperlinkInfoByLineAndCol(pos.line, pos.column);
  }

  @Deprecated
  public void highlightHyperlinks(final Filter customFilter, final Filter predefinedMessageFilter, final int line1, final int endLine) {
    highlightHyperlinks((line, entireLength) -> {
      Filter.Result result = customFilter.applyFilter(line, entireLength);
      return result != null ? result : predefinedMessageFilter.applyFilter(line, entireLength);
    }, line1, endLine);
  }
  
  public void highlightHyperlinks(final Filter customFilter, final int line1, final int endLine) {
    myFilterRunner.highlightHyperlinks(customFilter, Math.max(0, line1), endLine);
  }

  void highlightHyperlinks(@NotNull Filter.Result result, int offsetDelta) {
    Document document = myEditor.getDocument();
    for (Filter.ResultItem resultItem : result.getResultItems()) {
      int start = resultItem.getHighlightStartOffset() + offsetDelta;
      int end = resultItem.getHighlightEndOffset() + offsetDelta;
      if (start < 0 || end < start || end > document.getTextLength()) {
        continue;
      }

      TextAttributes attributes = resultItem.getHighlightAttributes();
      if (resultItem.getHyperlinkInfo() != null) {
        createHyperlink(start, end, attributes, resultItem.getHyperlinkInfo(), resultItem.getFollowedHyperlinkAttributes(),
                        resultItem.getHighlighterLayer());
      }
      else if (attributes != null) {
        addHighlighter(start, end, attributes, resultItem.getHighlighterLayer());
      }
    }
  }

  public void addHighlighter(int highlightStartOffset, int highlightEndOffset, TextAttributes highlightAttributes) {
    addHighlighter(highlightStartOffset, highlightEndOffset, highlightAttributes, HighlighterLayer.CONSOLE_FILTER);
    
  }
  
  public void addHighlighter(int highlightStartOffset, int highlightEndOffset, TextAttributes highlightAttributes, int highlighterLayer) {
    myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset, highlightEndOffset, highlighterLayer, highlightAttributes,
                                                  HighlighterTargetArea.EXACT_RANGE);
  }

  private static TextAttributes getHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  }

  @NotNull
  private static TextAttributes getFollowedHyperlinkAttributes(@NotNull RangeHighlighter range) {
    HyperlinkInfoTextAttributes attrs = HYPERLINK.get(range);
    TextAttributes result = attrs != null ? attrs.getFollowedHyperlinkAttributes() : null;
    if (result == null) {
      result = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
    }
    return result;
  }

  @Nullable
  public static OccurenceNavigator.OccurenceInfo getNextOccurrence(final Editor editor,
                                                                   final int delta,
                                                                   final Consumer<RangeHighlighter> action) {
    final List<RangeHighlighter> ranges = getHyperlinks(0, editor.getDocument().getTextLength(),editor);
    if (ranges.isEmpty()) {
      return null;
    }
    int i;
    for (i = 0; i < ranges.size(); i++) {
      RangeHighlighter range = ranges.get(i);
      if (range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES) != null) {
        break;
      }
    }
    i %= ranges.size();
    int newIndex = i;
    while (newIndex < ranges.size() && newIndex >= 0) {
      newIndex = (newIndex + delta + ranges.size()) % ranges.size();
      final RangeHighlighter next = ranges.get(newIndex);
      HyperlinkInfo info = getHyperlinkInfo(next);
      assert info != null;
      if (info.includeInOccurenceNavigation()) {
        boolean inCollapsedRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(next.getStartOffset()) != null;
        if (!inCollapsedRegion) {
          return new OccurenceNavigator.OccurenceInfo(new NavigatableAdapter() {
            @Override
            public void navigate(final boolean requestFocus) {
              action.consume(next);
              linkFollowed(editor, ranges, next);
            }
          }, newIndex == -1 ? -1 : newIndex + 1, ranges.size());
        }
      }
      if (newIndex == i) {
        break; // cycled through everything, found no next/prev hyperlink
      }
    }
    return null;
  }

  // todo fix link followed here!
  private static void linkFollowed(Editor editor, Collection<RangeHighlighter> ranges, final RangeHighlighter link) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    for (RangeHighlighter range : ranges) {
      TextAttributes oldAttr = range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES);
      if (oldAttr != null) {
        markupModel.setRangeHighlighterAttributes(range, oldAttr);
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, null);
      }
      if (range == link) {
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, range.getTextAttributes());
        markupModel.setRangeHighlighterAttributes(range, getFollowedHyperlinkAttributes(range));
      }
    }
    //refresh highlighter text attributes
    markupModel.addRangeHighlighter(0, 0, link.getLayer(), getHyperlinkAttributes(), HighlighterTargetArea.EXACT_RANGE).dispose();
  }


  @NotNull
  public static String getLineText(@NotNull Document document, int lineNumber, boolean includeEol) {
    return getLineSequence(document, lineNumber, includeEol).toString();
  }

  @NotNull
  private static CharSequence getLineSequence(@NotNull Document document, int lineNumber, boolean includeEol) {
    int endOffset = document.getLineEndOffset(lineNumber);
    if (includeEol && endOffset < document.getTextLength()) {
      endOffset++;
    }
    return document.getImmutableCharSequence().subSequence(document.getLineStartOffset(lineNumber), endOffset);
  }

  private static class HyperlinkInfoTextAttributes extends TextAttributes {
    private final HyperlinkInfo myHyperlinkInfo;
    private final TextAttributes myFollowedHyperlinkAttributes;

    HyperlinkInfoTextAttributes(@NotNull HyperlinkInfo hyperlinkInfo, @Nullable TextAttributes followedHyperlinkAttributes) {
      myHyperlinkInfo = hyperlinkInfo;
      myFollowedHyperlinkAttributes = followedHyperlinkAttributes;
    }

    @NotNull
    HyperlinkInfo getHyperlinkInfo() {
      return myHyperlinkInfo;
    }

    @Nullable
    TextAttributes getFollowedHyperlinkAttributes() {
      return myFollowedHyperlinkAttributes;
    }
  }
}
