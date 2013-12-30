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
import com.intellij.execution.filters.FilterMixin;
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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
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
  private static final int HIGHLIGHT_LAYER = HighlighterLayer.SELECTION - 111;
  private static final int NO_INDEX = Integer.MIN_VALUE;
  public static final Comparator<RangeHighlighter> START_OFFSET_COMPARATOR = new Comparator<RangeHighlighter>() {
    public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  private final Editor myEditor;
  private final SortedMap<RangeHighlighter, HyperlinkInfo> myHighlighterToMessageInfoMap = new TreeMap<RangeHighlighter, HyperlinkInfo>(START_OFFSET_COMPARATOR);
  private int myLastIndex = NO_INDEX;
  private final Consumer<BeforeAfter<Filter.Result>> myRefresher;
  private final List<RangeHighlighter> myHighlighters;

  public EditorHyperlinkSupport(@NotNull final Editor editor, @NotNull final Project project) {
    myEditor = editor;
    myHighlighters = new SmartList<RangeHighlighter>();

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (mouseEvent.getButton() == MouseEvent.BUTTON1 && !mouseEvent.isPopupTrigger()) {
          LogicalPosition logical = myEditor.xyToLogicalPosition(e.getMouseEvent().getPoint());
          if (EditorUtil.inVirtualSpace(editor, logical)) {
            return;
          }

          RangeHighlighter range = findLinkRangeAt(myEditor.logicalPositionToOffset(logical));
          if (range != null) {
            final HyperlinkInfo info = myHighlighterToMessageInfoMap.get(range);
            if (info != null) {
              if (info instanceof HyperlinkInfoBase) {
                ((HyperlinkInfoBase)info).navigate(project, new RelativePoint(mouseEvent));
              }
              else {
                info.navigate(project);
              }
              linkFollowed(editor, getHyperlinks().keySet(), range);
            }
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
            final Cursor cursor = editor instanceof EditorEx ?
                                  UIUtil.getTextCursor(((EditorEx)editor).getBackgroundColor()) :
                                  Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
            myEditor.getContentComponent().setCursor(cursor);
        }
      }
    }
    );

    myRefresher = new Consumer<BeforeAfter<Filter.Result>>() {
      @Override
      public void consume(BeforeAfter<Filter.Result> resultBeforeAfter) {
        if (resultBeforeAfter.getBefore() == null) return;
        final RangeHighlighter hyperlinkRange = findHyperlinkRange(resultBeforeAfter.getBefore().hyperlinkInfo);
        if (hyperlinkRange != null) {
          myHighlighterToMessageInfoMap.remove(hyperlinkRange);
        } else {
          final Iterator<RangeHighlighter> iterator = myHighlighters.iterator();
          while (iterator.hasNext()) {
            final RangeHighlighter highlighter = iterator.next();
            if (highlighter.isValid() && containsOffset(resultBeforeAfter.getBefore().highlightStartOffset, highlighter)) {
              iterator.remove();
              break;
            }
          }
        }

        if (resultBeforeAfter.getAfter() != null) {
          if (resultBeforeAfter.getAfter().hyperlinkInfo != null) {
            addHyperlink(resultBeforeAfter.getAfter().highlightStartOffset, resultBeforeAfter.getAfter().highlightEndOffset,
                         resultBeforeAfter.getAfter().highlightAttributes, resultBeforeAfter.getAfter().hyperlinkInfo);
          } else if (resultBeforeAfter.getAfter().highlightAttributes != null) {
            addHighlighter(resultBeforeAfter.getAfter().highlightStartOffset, resultBeforeAfter.getAfter().highlightEndOffset,
                           resultBeforeAfter.getAfter().highlightAttributes);
          }
        }
      }
    };
  }
  
  public void adjustHighlighters(final List<FilterMixin.AdditionalHighlight> highlights) {
    for (FilterMixin.AdditionalHighlight highlight : highlights) {
      RangeHighlighter found = null;
      for (RangeHighlighter rangeHighlighter : myHighlighterToMessageInfoMap.keySet()) {
        if (rangeHighlighter.getStartOffset() <= highlight.getStart() && rangeHighlighter.getEndOffset() >= highlight.getEnd()) {
          found = rangeHighlighter;
          break;
        }
      }
      if (found != null) {
        TextAttributes textAttributes = highlight.getTextAttributes(found.getTextAttributes());
        final HyperlinkInfo hyperlinkInfo = myHighlighterToMessageInfoMap.remove(found);
        if (found.getStartOffset() != highlight.getStart()) {
          addHyperlink(found.getStartOffset(), highlight.getEnd(), found.getTextAttributes(), hyperlinkInfo);
        }
        if (found.getEndOffset() != highlight.getEnd()) {
          addHyperlink(highlight.getEnd(), found.getEndOffset(), found.getTextAttributes(), hyperlinkInfo);
        }
        addHyperlink(highlight.getStart(), highlight.getEnd(), textAttributes, hyperlinkInfo);
        myEditor.getMarkupModel().removeHighlighter(found);
        return;
      }
      final Iterator<RangeHighlighter> iterator = myHighlighters.iterator();
      while (iterator.hasNext()) {
        final RangeHighlighter highlighter = iterator.next();
        if (highlighter.getStartOffset() == highlight.getStart() && highlighter.getEndOffset() == highlight.getEnd()) {
          iterator.remove();
          final TextAttributes textAttributes = highlight.getTextAttributes(highlighter.getTextAttributes());
          addHighlighter(highlight.getStart(), highlight.getEnd(), textAttributes);
          return;
        }
      }
      final TextAttributes textAttributes = highlight.getTextAttributes(null);
      addHighlighter(highlight.getStart(), highlight.getEnd(), textAttributes);
    }
  }

  public void clearHyperlinks() {
    myHighlighterToMessageInfoMap.clear();
    myHighlighters.clear();
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
    RangeHighlighter range = findLinkRangeAt(offset);
    return range == null ? null : myHighlighterToMessageInfoMap.get(range);
  }

  @Nullable
  public RangeHighlighter findHyperlinkRange(HyperlinkInfo info) {
    for (RangeHighlighter highlighter : myHighlighterToMessageInfoMap.keySet()) {
      if (info == myHighlighterToMessageInfoMap.get(highlighter)) {
        return highlighter;
      }
    }
    return null;
  }

  public List<RangeHighlighter> findAllHyperlinksOnLine(int line) {
    ArrayList<RangeHighlighter> list = new ArrayList<RangeHighlighter>();
    final int lineStart = myEditor.getDocument().getLineStartOffset(line);
    final int lineEnd = myEditor.getDocument().getLineEndOffset(line);
    for (RangeHighlighter highlighter : myHighlighterToMessageInfoMap.keySet()) {
      int hyperlinkStart = highlighter.getStartOffset();
      int hyperlinkEnd = highlighter.getEndOffset();
      if (hyperlinkStart >= lineStart && hyperlinkEnd <= lineEnd) {
        list.add(highlighter);
      }
    }
    return list;
  }

  public void removeHyperlink(@NotNull RangeHighlighter hyperlink) {
    myHighlighterToMessageInfoMap.remove(hyperlink);
    myEditor.getMarkupModel().removeHighlighter(hyperlink);
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
    if (EditorUtil.inVirtualSpace(myEditor, pos)) {
      return null;
    }

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
      if (result != null) {
        for (Filter.ResultItem resultItem : result.getResultItems()) {
          if (resultItem.hyperlinkInfo != null) {
            addHyperlink(resultItem.highlightStartOffset, resultItem.highlightEndOffset, resultItem.highlightAttributes, resultItem.hyperlinkInfo);
          }
          else if (resultItem.highlightAttributes != null) {
            addHighlighter(resultItem.highlightStartOffset, resultItem.highlightEndOffset, resultItem.highlightAttributes);
          }
        }
      }
    }
  }

  private void addHighlighter(int highlightStartOffset, int highlightEndOffset, TextAttributes highlightAttributes) {
    final RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset,
                                                                                       highlightEndOffset,
                                                                                       HIGHLIGHT_LAYER,
                                                                                       highlightAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
    myHighlighters.add(highlighter);
  }

  private static TextAttributes getHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  }

  private static TextAttributes getFollowedHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
  }

  @Nullable
  public static OccurenceNavigator.OccurenceInfo getNextOccurrence(final Editor editor,
                                                                   Collection<RangeHighlighter> sortedHighlighters,
                                                                   final int delta,
                                                                   final Consumer<RangeHighlighter> action) {
    if (sortedHighlighters.isEmpty()) {
      return null;
    }

    final List<RangeHighlighter> ranges = new ArrayList<RangeHighlighter>(sortedHighlighters);
    int i;
    for (i = 0; i < ranges.size(); i++) {
      RangeHighlighter range = ranges.get(i);
      if (range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES) != null) {
        break;
      }
    }
    i = i % ranges.size();
    int newIndex = i;
    while (newIndex < ranges.size() && newIndex >= 0) {
      newIndex = (newIndex + delta + ranges.size()) % ranges.size();
      final RangeHighlighter next = ranges.get(newIndex);
      if (editor.getFoldingModel().getCollapsedRegionAtOffset(next.getStartOffset()) == null) {
        return new OccurenceNavigator.OccurenceInfo(new NavigatableAdapter() {
          public void navigate(final boolean requestFocus) {
            action.consume(next);
            linkFollowed(editor, ranges, next);
          }
        }, newIndex == -1 ? -1 : newIndex + 1, ranges.size());
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
        TextAttributes oldAttributes = range.getTextAttributes();
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, oldAttributes);
        TextAttributes attributes = getFollowedHyperlinkAttributes().clone();
        assert oldAttributes != null;
        attributes.setFontType(oldAttributes.getFontType());
        attributes.setEffectType(oldAttributes.getEffectType());
        attributes.setEffectColor(oldAttributes.getEffectColor());
        attributes.setForegroundColor(oldAttributes.getForegroundColor());
        attributes.setBackgroundColor(oldAttributes.getBackgroundColor());
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
