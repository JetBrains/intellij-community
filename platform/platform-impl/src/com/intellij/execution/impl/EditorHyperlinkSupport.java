/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.Navigatable;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;

/**
 * @author peter
 */
public class EditorHyperlinkSupport {
  public static final Key<TextAttributes> OLD_HYPERLINK_TEXT_ATTRIBUTES = Key.create("OLD_HYPERLINK_TEXT_ATTRIBUTES");
  private static final int HYPERLINK_LAYER = HighlighterLayer.SELECTION - 123;
  private static final int NO_INDEX = Integer.MIN_VALUE;

  private final Editor myEditor;
  private final Map<RangeHighlighter, HyperlinkInfo> myHighlighterToMessageInfoMap = new HashMap<RangeHighlighter, HyperlinkInfo>();
  private int myLastIndex = NO_INDEX;

  public EditorHyperlinkSupport(@NotNull final Editor editor, @NotNull final Project project) {
    myEditor = editor;

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      public void mouseReleased(final EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (mouseEvent.getButton() == MouseEvent.BUTTON1 && !mouseEvent.isPopupTrigger()) {
          int offset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
          RangeHighlighter range = findLinkRangeAt(offset);
          final HyperlinkInfo info = myHighlighterToMessageInfoMap.get(range);
          if (info != null) {
            info.navigate(project);
            linkFollowed(editor, getHyperlinks().keySet(), range);
          }
        }
      }
    });

    editor.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(final MouseEvent e) {
        final HyperlinkInfo info = getHyperlinkInfoByPoint(e.getPoint());
        if (info != null) {
          myEditor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else {
          myEditor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
      }
    }
    );
  }

  public void clearHyperlinks() {
    myHighlighterToMessageInfoMap.clear();
    myLastIndex = NO_INDEX;
  }

  @Nullable
  private RangeHighlighter findLinkRangeAt(final int offset) {
    for (final RangeHighlighter highlighter : myHighlighterToMessageInfoMap.keySet()) {
      if (highlighter.isValid() && containsOffset(offset, highlighter)) {
        return highlighter;
      }
    }
    return null;

  }

  @Nullable
  private HyperlinkInfo getHyperlinkAt(final int offset) {
    return myHighlighterToMessageInfoMap.get(findLinkRangeAt(offset));
  }

  private static boolean containsOffset(final int offset, final RangeHighlighter highlighter) {
    return highlighter.getStartOffset() <= offset && offset <= highlighter.getEndOffset();
  }

  public void addHyperlink(@NotNull final RangeHighlighter highlighter, @NotNull final HyperlinkInfo hyperlinkInfo) {
    myHighlighterToMessageInfoMap.put(highlighter, hyperlinkInfo);
    if (myLastIndex != NO_INDEX && containsOffset(myLastIndex, highlighter)) myLastIndex = NO_INDEX;
  }

  public Map<RangeHighlighter, HyperlinkInfo> getHyperlinks() {
    return myHighlighterToMessageInfoMap;
  }

  @Nullable
  public HyperlinkInfo getHyperlinkInfoByLineAndCol(final int line, final int col) {
    return getHyperlinkAt(myEditor.logicalPositionToOffset(new LogicalPosition(line, col)));
  }

  public void addHyperlink(final int highlightStartOffset,
                    final int highlightEndOffset,
                    @Nullable final TextAttributes highlightAttributes,
                    @NotNull final HyperlinkInfo hyperlinkInfo) {
    TextAttributes textAttributes = highlightAttributes != null ? highlightAttributes : getHyperlinkAttributes();
    final RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset,
                                                                                       highlightEndOffset,
                                                                                       HYPERLINK_LAYER,
                                                                                       textAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
    addHyperlink(highlighter, hyperlinkInfo);
  }

  @Nullable
  public HyperlinkInfo getHyperlinkInfoByPoint(final Point p) {
    final LogicalPosition pos = myEditor.xyToLogicalPosition(new Point(p.x, p.y));
    return getHyperlinkInfoByLineAndCol(pos.line, pos.column);
  }

  public void highlightHyperlinks(final Filter customFilter, final Filter predefinedMessageFilter, final int line1, final int endLine) {
    final Document document = myEditor.getDocument();

    final int startLine = Math.max(0, line1);

    for (int line = startLine; line <= endLine; line++) {
      int endOffset = document.getLineEndOffset(line);
      if (endOffset < document.getTextLength()) {
        endOffset++; // add '\n'
      }
      final String text = getLineText(document, line, true);
      Filter.Result result = customFilter.applyFilter(text, endOffset);
      if (result == null) {
        result = predefinedMessageFilter.applyFilter(text, endOffset);
      }
      if (result != null && result.hyperlinkInfo != null) {
        addHyperlink(result.highlightStartOffset, result.highlightEndOffset, result.highlightAttributes, result.hyperlinkInfo);
      }
    }
  }

  private static TextAttributes getHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  }

  private static TextAttributes getFollowedHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
  }

  @Nullable
  public static OccurenceNavigator.OccurenceInfo getNextOccurrence(final Editor editor,
                                                                   Collection<RangeHighlighter> highlighters,
                                                                   final int delta,
                                                                   final Consumer<RangeHighlighter> action) {
    final List<RangeHighlighter> ranges = new ArrayList<RangeHighlighter>(highlighters);
    for (Iterator<RangeHighlighter> iterator = ranges.iterator(); iterator.hasNext();) {
      RangeHighlighter highlighter = iterator.next();
      if (editor.getFoldingModel().getCollapsedRegionAtOffset(highlighter.getStartOffset()) != null) {
        iterator.remove();
      }
    }
    Collections.sort(ranges, new Comparator<RangeHighlighter>() {
      public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    int i;
    for (i = 0; i < ranges.size(); i++) {
      RangeHighlighter range = ranges.get(i);
      if (range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES) != null) {
        break;
      }
    }
    int newIndex = ranges.isEmpty() ? -1 : i == ranges.size() ? 0 : (i + delta + ranges.size()) % ranges.size();
    final RangeHighlighter next = newIndex < ranges.size() && newIndex >= 0 ? ranges.get(newIndex) : null;
    if (next == null) return null;
    return new OccurenceNavigator.OccurenceInfo(new Navigatable.Adapter() {
      public void navigate(final boolean requestFocus) {
        action.consume(next);
        linkFollowed(editor, ranges, next);
      }
    }, i, ranges.size());
  }

  private static void linkFollowed(Editor editor, Collection<RangeHighlighter> ranges, final RangeHighlighter link) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    for (RangeHighlighter range : ranges) {
      TextAttributes oldAttr = range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES);
      if (oldAttr != null) {
        markupModel.setRangeHighlighterAttributes(range, oldAttr);
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, null);
      }
      if (range == link) {
        TextAttributes oldAttributes = range.getTextAttributes();
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, oldAttributes);
        TextAttributes attributes = getFollowedHyperlinkAttributes().clone();
        assert oldAttributes != null;
        attributes.setFontType(oldAttributes.getFontType());
        attributes.setEffectType(oldAttributes.getEffectType());
        attributes.setEffectColor(oldAttributes.getEffectColor());
        attributes.setForegroundColor(oldAttributes.getForegroundColor());
        markupModel.setRangeHighlighterAttributes(range, attributes);
      }
    }
    //refresh highlighter text attributes
    markupModel.addRangeHighlighter(0, 0, HYPERLINK_LAYER, getHyperlinkAttributes(), HighlighterTargetArea.EXACT_RANGE).dispose();
  }


  public static String getLineText(Document document, int lineNumber, boolean includeEol) {
    int endOffset = document.getLineEndOffset(lineNumber);
    if (includeEol && endOffset < document.getTextLength()) {
      endOffset++;
    }
    return document.getCharsSequence().subSequence(document.getLineStartOffset(lineNumber), endOffset).toString();
  }
}
