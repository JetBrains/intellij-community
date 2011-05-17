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
import com.intellij.find.FindModel;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class LivePreview extends DocumentAdapter implements ReplacementView.Delegate, SearchResults.SearchResultsListener,
                                                            SelectionListener {

  private static final Key<Object> IN_SELECTION_KEY = Key.create("LivePreview.IN_SELECTION_KEY");
  private static final Object IN_SELECTION1 = new Object();
  private static final Object IN_SELECTION2 = new Object();
  private boolean myListeningSelection = false;
  private boolean mySuppressedUpdate = false;
  private boolean myInSmartUpdate = false;
  private static final Key<Object> MARKER_USED = Key.create("LivePreview.MARKER_USED");

  @Override
  public void selectionChanged(SelectionEvent e) {
    updateInSelectionHighlighters();
  }

  public void supressUpdate() {
    mySuppressedUpdate = true;
  }

  public void inSmartUpdate() {
    myInSmartUpdate = true;
  }

  public interface Delegate {

    @Nullable
    String getStringToReplace(Editor editor, LiveOccurrence liveOccurrence);

    @Nullable
    TextRange performReplace(LiveOccurrence occurrence, String replacement, Editor editor);

    void performReplaceAll(Editor e);

    void getFocusBack();

  }

  private final Set<RangeHighlighter> myHighlighters = new HashSet<RangeHighlighter>();

  private RangeHighlighter myCursorHighlighter;
  private final List<VisibleAreaListener> myVisibleAreaListenersToRemove = new ArrayList<VisibleAreaListener>();

  private static TextAttributes strikout(TextAttributes attributes) {
    TextAttributes textAttributes = attributes.clone();
    textAttributes.setEffectColor(Color.BLACK);
    textAttributes.setEffectType(EffectType.STRIKEOUT);
    return textAttributes;
  }

  private static final TextAttributes OTHER_TARGETS_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.GREEN, null, null, 0);

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
    if (mySearchResults.getProject().isDisposed()) return;
    if (mySuppressedUpdate) {
      mySuppressedUpdate = false;
      return;
    }
    if (!myInSmartUpdate) {
      removeFromEditor();
    }

    highlightUsages();
    updateCursorHighlighting(false);
    if (myInSmartUpdate) {
      clearUnusedHightlighters();
      myInSmartUpdate = false;
    }
  }

  private void clearUnusedHightlighters() {
    Set<RangeHighlighter> unused = new com.intellij.util.containers.HashSet<RangeHighlighter>();
    for (RangeHighlighter highlighter : myHighlighters) {
      if (highlighter.getUserData(MARKER_USED) == null) {
        unused.add(highlighter);
      } else {
        highlighter.putUserData(MARKER_USED, null);
      }
    }
    myHighlighters.removeAll(unused);
    Project project = mySearchResults.getProject();
    if (!project.isDisposed()) {
      for (RangeHighlighter highlighter : unused) {
        HighlightManager.getInstance(project).removeSegmentHighlighter(mySearchResults.getEditor(), highlighter);
      }
    }
  }

  @Override
  public void cursorMoved(boolean toChangeSelection) {
    updateInSelectionHighlighters();
    updateCursorHighlighting(toChangeSelection);
  }

  public void editorChanged(SearchResults sr, Editor oldEditor) {
    removeFromEditor();
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
      highlightRange(cursor.getPrimaryRange(), new TextAttributes(null, null, Color.BLACK, EffectType.ROUNDED_BOX, 0), dummy);
      if (!dummy.isEmpty()) {
        myCursorHighlighter = dummy.get(0);
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
    mySearchResults.getEditor().getSelectionModel().addSelectionListener(this);
  }

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }


  public void cleanUp() {
    removeFromEditor();
  }

  public void dispose() {
    mySearchResults.removeListener(this);
  }

  private void removeFromEditor() {
    Editor editor = mySearchResults.getEditor();
    if (myReplacementBalloon != null) {
      myReplacementBalloon.hide();
    }

    if (editor != null) {

      for (VisibleAreaListener visibleAreaListener : myVisibleAreaListenersToRemove) {
        editor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
      }
      myVisibleAreaListenersToRemove.clear();
      Project project = mySearchResults.getProject();
      if (!project.isDisposed()) {
        for (RangeHighlighter h : myHighlighters) {
          HighlightManager.getInstance(project).removeSegmentHighlighter(editor, h);
        }
        if (myCursorHighlighter != null) {
          HighlightManager.getInstance(project).removeSegmentHighlighter(editor, myCursorHighlighter);
          myCursorHighlighter = null;
        }
      }
      myHighlighters.clear();
      if (myListeningSelection) {
        editor.getSelectionModel().removeSelectionListener(this);
        myListeningSelection = false;
      }
    }
  }

  private void highlightUsages() {
    if (mySearchResults.getEditor() == null) return;
    for (LiveOccurrence o : mySearchResults.getOccurrences()) {
      for (TextRange textRange : o.getSecondaryRanges()) {
        highlightRange(textRange, OTHER_TARGETS_ATTRIBUTES, myHighlighters);
      }
      final TextRange range = o.getPrimaryRange();

      TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
      
      if (mySearchResults.isExcluded(o)) {
        highlightRange(range, strikout(attrs), myHighlighters);
      } else {
        highlightRange(range, attrs, myHighlighters);
      }
    }
    updateInSelectionHighlighters();
    if (!myListeningSelection) {
      mySearchResults.getEditor().getSelectionModel().addSelectionListener(this);
      myListeningSelection = true;
    }
    
  }

  private void updateInSelectionHighlighters() {
    if (mySearchResults.getEditor() == null) return;
    final SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();
    final TextRange selectionRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());

    final HashSet<RangeHighlighter> toRemove = new HashSet<RangeHighlighter>();
    Set<RangeHighlighter> toAdd = new HashSet<RangeHighlighter>();
    for (RangeHighlighter highlighter : myHighlighters) {
      if (myCursorHighlighter != null && highlighter.getStartOffset() == myCursorHighlighter.getStartOffset() &&
        highlighter.getEndOffset() == myCursorHighlighter.getEndOffset()) continue;
      final boolean intersectsWithSelection = selectionRange.intersects(highlighter.getStartOffset(), highlighter.getEndOffset()) &&
                                              selectionRange.getEndOffset() != highlighter.getStartOffset() &&
                                              highlighter.getEndOffset() != selectionRange.getStartOffset();
      final Object userData = highlighter.getUserData(IN_SELECTION_KEY);
      if (userData != null) {
        if (!intersectsWithSelection) {
          if (userData == IN_SELECTION2) {
            HighlightManager.getInstance(mySearchResults.getProject()).removeSegmentHighlighter(mySearchResults.getEditor(), highlighter);
            toRemove.add(highlighter);
          } else {
            highlighter.putUserData(IN_SELECTION_KEY, null);
          }
        }
      } else if (intersectsWithSelection) {
        final RangeHighlighter toAnnotate = highlightRange(new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()),
                                                                 new TextAttributes(null, null, Color.WHITE, EffectType.BOXED, 0), toAdd);
        highlighter.putUserData(IN_SELECTION_KEY, IN_SELECTION1);
        toAnnotate.putUserData(IN_SELECTION_KEY, IN_SELECTION2);
      }
    }
    myHighlighters.removeAll(toRemove);
    myHighlighters.addAll(toAdd);
  }

  private void showReplacementPreview() {
    hideBalloon();
    final LiveOccurrence cursor = mySearchResults.getCursor();
    final Editor editor = mySearchResults.getEditor();
    if (myDelegate != null && cursor != null) {
      String replacementPreviewText = myDelegate.getStringToReplace(editor, cursor);
      final FindModel findModel = mySearchResults.getFindModel();
      if (findModel.isRegularExpressions() && findModel.isReplaceState()) {

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

  @NotNull
  private RangeHighlighter highlightRange(TextRange textRange, TextAttributes attributes, Collection<RangeHighlighter> highlighters) {
    if (myInSmartUpdate) {
      for (RangeHighlighter highlighter : myHighlighters) {
        if (highlighter.getStartOffset() == textRange.getStartOffset() && highlighter.getEndOffset() == textRange.getEndOffset()) {
          if (attributes.equals(highlighter.getTextAttributes())) {
            if (myInSmartUpdate) {
              highlighter.putUserData(MARKER_USED, new Object());
            }
            return highlighter;
          }
        }
      }
    }
    final RangeHighlighter highlighter = doHightlightRange(textRange, attributes, highlighters);
    if (myInSmartUpdate) {
      highlighter.putUserData(MARKER_USED, new Object());
    }
    return highlighter;
  }

  private RangeHighlighter doHightlightRange(TextRange textRange, TextAttributes attributes, Collection<RangeHighlighter> highlighters) {
    HighlightManager highlightManager = HighlightManager.getInstance(mySearchResults.getProject());
    final ArrayList<RangeHighlighter> dummy = new ArrayList<RangeHighlighter>();
    highlightManager.addRangeHighlight(mySearchResults.getEditor(),
                                       textRange.getStartOffset(), textRange.getEndOffset(),
                                       attributes, false, dummy);
    highlighters.addAll(dummy);
    return dummy.get(0);
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
