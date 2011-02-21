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
package com.intellij.find.impl.livePreview;


import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
public class LivePreview extends DocumentAdapter implements ReplacementView.Delegate, SearchResults.SearchResultsListener {

  public interface Delegate {

    @Nullable
    String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence);

    @Nullable
    TextRange performReplace(LiveOccurrence occurrence, String replacement, Editor editor);

    void performReplaceAll(Editor e);

    void getFocusBack();

  }

  private final Collection<RangeHighlighter> myHighlighters = new HashSet<RangeHighlighter>();


  private RangeHighlighter myCursorHighlighter;
  private final List<VisibleAreaListener> myVisibleAreaListenersToRemove = new ArrayList<VisibleAreaListener>();

  private static final TextAttributes EXCLUDED_TARGET_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.YELLOW,
                                                                                      Color.BLACK, EffectType.STRIKEOUT, 0);

  private static final TextAttributes OTHER_TARGETS_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.GREEN, null, null, 0);

  private static final TextAttributes MAIN_TARGET_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.YELLOW, null, null, 0);

  private Delegate myDelegate;

  private SearchResults mySearchResults;

  private Balloon myReplacementBalloon;

  @Override
  public void performReplacement(LiveOccurrence occurrence, String replacement) {
    if (myDelegate != null) {
      myDelegate.performReplace(occurrence, replacement, mySearchResults.getEditor());
      myDelegate.getFocusBack();
    }
  }

  @Override
  public void performReplaceAll() {
    myDelegate.performReplaceAll(mySearchResults.getEditor());
  }

  public boolean isExcluded(LiveOccurrence occurrence) {
    return mySearchResults.isExcluded(occurrence);
  }

  public void exclude(LiveOccurrence occurrence) {
    mySearchResults.exclude(occurrence);
    myDelegate.getFocusBack();
  }

  public boolean hasMatches() {
    return mySearchResults.hasMatches();
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    removeFromEditor(mySearchResults.getEditor());
    highlightUsages();
    updateCursorHighlighting(false);
  }

  @Override
  public void cursorMoved() {
    updateCursorHighlighting(true);
  }

  public void editorChanged(SearchResults sr, Editor oldEditor) {
    removeFromEditor(mySearchResults.getEditor());
    oldEditor.getDocument().removeDocumentListener(this);
    mySearchResults.getEditor().getDocument().addDocumentListener(this);
  }

  private void updateCursorHighlighting(boolean scroll) {
    hideBalloon();

    if (myCursorHighlighter != null) {
      HighlightManager.getInstance(mySearchResults.getProject()).removeSegmentHighlighter(mySearchResults.getEditor(), myCursorHighlighter);
      myCursorHighlighter = null;
    }

    LiveOccurrence cursor = mySearchResults.getCursor();
    Editor editor = mySearchResults.getEditor();
    if (cursor != null) {
      ArrayList<RangeHighlighter> dummy = new ArrayList<RangeHighlighter>();
      highlightRange(cursor.getPrimaryRange(), new TextAttributes(null, null, null, null, 0), dummy);
      if (!dummy.isEmpty()) {
        myCursorHighlighter = dummy.get(0);
        myCursorHighlighter.setCustomRenderer(new CursorRenderer());
      }

      if (!SearchResults.insideVisibleArea(editor, cursor.getPrimaryRange()) && scroll) {
        editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(cursor.getPrimaryRange().getStartOffset()),
                                              ScrollType.CENTER);
        editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
          @Override
          public void run() {
            showReplacementPreview();
          }
        });
      } else {
        showReplacementPreview();
      }
    }
  }

  public LivePreview(SearchResults searchResults) {
    mySearchResults = searchResults;
    searchResultsUpdated(searchResults);
    searchResults.addListener(this);
  }

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }


  public void cleanUp() {
    removeFromEditor(mySearchResults.getEditor());
  }

  private void removeFromEditor(Editor editor) {
    if (myReplacementBalloon != null) {
      myReplacementBalloon.hide();
    }

    if (editor != null) {

      for (VisibleAreaListener visibleAreaListener : myVisibleAreaListenersToRemove) {
        editor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
      }
      myVisibleAreaListenersToRemove.clear();
      for (RangeHighlighter h : myHighlighters) {
        HighlightManager.getInstance(mySearchResults.getProject()).removeSegmentHighlighter(editor, h);
      }
      if (myCursorHighlighter != null) {
        HighlightManager.getInstance(mySearchResults.getProject()).removeSegmentHighlighter(editor, myCursorHighlighter);
        myCursorHighlighter = null;
      }
    }
  }

  private void highlightUsages() {
    if (mySearchResults.getEditor() == null) return;
    for (LiveOccurrence o : mySearchResults.getOccurrences()) {
      for (TextRange textRange : o.getSecondaryRanges()) {
        highlightRange(textRange, OTHER_TARGETS_ATTRIBUTES, myHighlighters);
      }
      if (mySearchResults.isExcluded(o)) {
        highlightRange(o.getPrimaryRange(), EXCLUDED_TARGET_ATTRIBUTES, myHighlighters);
      } else {
        highlightRange(o.getPrimaryRange(), MAIN_TARGET_ATTRIBUTES, myHighlighters);
      }
    }
  }

  private void showReplacementPreview() {
    hideBalloon();
    final LiveOccurrence cursor = mySearchResults.getCursor();
    final Editor editor = mySearchResults.getEditor();
    if (myDelegate != null && cursor != null) {
      String replacementPreviewText = myDelegate.getReplacementPreviewText(editor, cursor);
      if (replacementPreviewText != null) {

        ReplacementView replacementView = new ReplacementView(replacementPreviewText, cursor);
        replacementView.setDelegate(this);

        BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(replacementView);
        balloonBuilder.setFadeoutTime(0);
        balloonBuilder.setFillColor(IdeTooltipManager.GRAPHITE_COLOR);
        balloonBuilder.setAnimationCycle(0);
        balloonBuilder.setHideOnClickOutside(false);
        balloonBuilder.setHideOnKeyOutside(false);
        balloonBuilder.setHideOnAction(false);
        balloonBuilder.setCloseButtonEnabled(true);
        myReplacementBalloon = balloonBuilder.createBalloon();
        final int startOffset = cursor.getPrimaryRange().getStartOffset();
        final int endOffset = cursor.getPrimaryRange().getEndOffset();

        myReplacementBalloon.show(new ReplacementBalloonPositionTracker(editor, startOffset, endOffset, cursor), Balloon.Position.above);
      }
    }
  }

  private void hideBalloon() {
    if (myReplacementBalloon != null) {
      myReplacementBalloon.hide();
      myReplacementBalloon = null;
    }
  }

  private void highlightRange(TextRange textRange, TextAttributes attributes, Collection<RangeHighlighter> highlighters) {
    HighlightManager highlightManager = HighlightManager.getInstance(mySearchResults.getProject());
    if (highlightManager != null) {
      highlightManager.addRangeHighlight(mySearchResults.getEditor(),
                                         textRange.getStartOffset(), textRange.getEndOffset(),
                                         attributes, false, highlighters);
    }
  }



  private static class CursorRenderer implements CustomHighlighterRenderer {
    @Override
    public void paint(Editor editor, RangeHighlighter highlighter, Graphics g) {
      Document document = editor.getDocument();
      int offset = highlighter.getStartOffset();
      while (offset < highlighter.getEndOffset()) {
        int line = document.getLineNumber(offset);
        int newOffset = document.getLineEndOffset(line);
        newOffset = Math.min(highlighter.getEndOffset(), newOffset);
        drawSegment(editor, new TextRange(offset, newOffset), g);
        offset = newOffset+1;
      }

    }

    private static void drawSegment(Editor editor, Segment highlighter, Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      VisualPosition startVp = editor.offsetToVisualPosition(highlighter.getStartOffset());
      VisualPosition endVp = editor.offsetToVisualPosition(highlighter.getEndOffset());
      Point start = editor.visualPositionToXY(startVp);
      Point end = editor.visualPositionToXY(endVp);
      g2d.setColor(new Color(50, 50, 50));
      g2d.translate(0, start.y - 4);
      Color c1 = new Color(220, 200, 130);
      Color c2 = new Color(220, 170, 30);
      int endX = start.x != end.x ? end.x : end.x + 2;
      UIUtil.drawSearchMatch(g2d, start.x - 1, endX + 1, editor.getLineHeight() + 2 * 4, c1, c2);
      g2d.translate(0, -start.y + 4);
    }
  }

  private class ReplacementBalloonPositionTracker extends PositionTracker<Balloon> {
    private final Editor myEditor;
    private final int myStartOffset;
    private final int myEndOffset;
    private final LiveOccurrence myCursor;

    public ReplacementBalloonPositionTracker(Editor editor, int startOffset, int endOffset, LiveOccurrence cursor) {
      super(editor.getContentComponent());
      myEditor = editor;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myCursor = cursor;
    }

    @Override
    public RelativePoint recalculateLocation(final Balloon object) {
      Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(myStartOffset));
      Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(myEndOffset));
      Point point = new Point((startPoint.x + endPoint.x)/2, startPoint.y);
      if (!SearchResults.insideVisibleArea(myEditor, myCursor.getPrimaryRange())) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            object.hide();
          }
        });

        VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
          @Override
          public void visibleAreaChanged(VisibleAreaEvent e) {
            if (SearchResults.insideVisibleArea(myEditor, myCursor.getPrimaryRange())) {
              showReplacementPreview();
              final VisibleAreaListener visibleAreaListener = this;
              myEditor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
              myVisibleAreaListenersToRemove.remove(visibleAreaListener);
            }
          }
        };
        myEditor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
        myVisibleAreaListenersToRemove.add(visibleAreaListener);

      }
      return new RelativePoint(myEditor.getContentComponent(), point);
    }
  }
}
