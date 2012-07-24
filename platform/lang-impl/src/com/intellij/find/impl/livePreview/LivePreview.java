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
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
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
import com.intellij.util.Processor;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LivePreview extends DocumentAdapter implements ReplacementView.Delegate, SearchResults.SearchResultsListener,
                                                            SelectionListener {

  private static final Key<Object> IN_SELECTION_KEY = Key.create("LivePreview.IN_SELECTION_KEY");
  private static final Object IN_SELECTION1 = new Object();
  private static final Object IN_SELECTION2 = new Object();

  private boolean myListeningSelection = false;
  private boolean mySuppressedUpdate = false;
  private boolean myInSmartUpdate = false;

  private static final Key<Object> MARKER_USED = Key.create("LivePreview.MARKER_USED");
  private static final Object YES = new Object();
  private static final Key<Object> SEARCH_MARKER = Key.create("LivePreview.SEARCH_MARKER");

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

  @Override
  public boolean isExcluded(LiveOccurrence occurrence) {
    return mySearchResults.isExcluded(occurrence);
  }

  @Override
  public void exclude(LiveOccurrence occurrence) {
    mySearchResults.exclude(occurrence);
    myDelegate.getFocusBack();
  }

  public boolean hasMatches() {
    return mySearchResults.hasMatches();
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    final Project project = mySearchResults.getProject();
    if (project == null || project.isDisposed()) return;
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

  @Override
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
      Set<RangeHighlighter> dummy = new HashSet<RangeHighlighter>();
      highlightRange(cursor.getPrimaryRange(), new TextAttributes(null, null, Color.BLACK, EffectType.ROUNDED_BOX, 0), dummy);
      if (!dummy.isEmpty()) {
        myCursorHighlighter = dummy.iterator().next();
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
      }
      else {
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
      if (project != null && !project.isDisposed()) {
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
    if (mySearchResults.getMatchesCount() >= mySearchResults.getMatchesLimit())
      return;
    for (LiveOccurrence o : mySearchResults.getOccurrences()) {
      for (TextRange textRange : o.getSecondaryRanges()) {
        highlightRange(textRange, OTHER_TARGETS_ATTRIBUTES, myHighlighters);
      }
      final TextRange range = o.getPrimaryRange();

      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);

      if (mySearchResults.isExcluded(o)) {
        highlightRange(range, strikout(attributes), myHighlighters);
      } else {
        highlightRange(range, attributes, myHighlighters);
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
    int[] starts = selectionModel.getBlockSelectionStarts();
    int[] ends = selectionModel.getBlockSelectionEnds();

    final HashSet<RangeHighlighter> toRemove = new HashSet<RangeHighlighter>();
    Set<RangeHighlighter> toAdd = new HashSet<RangeHighlighter>();
    for (RangeHighlighter highlighter : myHighlighters) {
      if (myCursorHighlighter != null && highlighter.getStartOffset() == myCursorHighlighter.getStartOffset() &&
        highlighter.getEndOffset() == myCursorHighlighter.getEndOffset()) continue;


      boolean intersectsWithSelection = false;
      for (int i = 0; i < starts.length; ++i) {
        TextRange selectionRange = new TextRange(starts[i], ends[i]);
        intersectsWithSelection = selectionRange.intersects(highlighter.getStartOffset(), highlighter.getEndOffset()) &&
                                  selectionRange.getEndOffset() != highlighter.getStartOffset() &&
                                  highlighter.getEndOffset() != selectionRange.getStartOffset();
        if (intersectsWithSelection) break;
      }
      
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

        myReplacementBalloon.show(new ReplacementBalloonPositionTracker(editor), Balloon.Position.above);
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
  private RangeHighlighter highlightRange(TextRange textRange, TextAttributes attributes, Set<RangeHighlighter> highlighters) {
    if (myInSmartUpdate) {
      for (RangeHighlighter highlighter : myHighlighters) {
        if (highlighter.isValid() && highlighter.getStartOffset() == textRange.getStartOffset() && highlighter.getEndOffset() == textRange.getEndOffset()) {
          if (attributes.equals(highlighter.getTextAttributes())) {
            highlighter.putUserData(MARKER_USED, YES);
            if (highlighters != myHighlighters) {
              highlighters.add(highlighter);
            }
            return highlighter;
          }
        }
      }
    }
    final RangeHighlighter highlighter = doHightlightRange(textRange, attributes, highlighters);
    if (myInSmartUpdate) {
      highlighter.putUserData(MARKER_USED, YES);
    }
    return highlighter;
  }

  private RangeHighlighter doHightlightRange(final TextRange textRange, final TextAttributes attributes, Set<RangeHighlighter> highlighters) {
    HighlightManager highlightManager = HighlightManager.getInstance(mySearchResults.getProject());

    MarkupModelEx markupModel = (MarkupModelEx)mySearchResults.getEditor().getMarkupModel();
    
    final RangeHighlighter[] candidate = new RangeHighlighter[1];
    
    boolean notFound = markupModel.processRangeHighlightersOverlappingWith(
      textRange.getStartOffset(), textRange.getEndOffset(),
      new Processor<RangeHighlighterEx>() {
        @Override
        public boolean process(RangeHighlighterEx highlighter) {
          TextAttributes textAttributes =
            highlighter.getTextAttributes();
          if (highlighter.getUserData(SEARCH_MARKER) != null &&
              textAttributes != null &&
              textAttributes.equals(attributes) &&
              highlighter.getStartOffset() == textRange.getStartOffset() &&
              highlighter.getEndOffset() == textRange.getEndOffset()) {
            candidate[0] = highlighter;
            return false;
          }
          return true;
        }
      });

    if (!notFound && highlighters.contains(candidate[0])) {
      return candidate[0];
    }
    final ArrayList<RangeHighlighter> dummy = new ArrayList<RangeHighlighter>();
    highlightManager.addRangeHighlight(mySearchResults.getEditor(),
                                       textRange.getStartOffset(),
                                       textRange.getEndOffset(),
                                       attributes,
                                       false,
                                       dummy);
    final RangeHighlighter h = dummy.get(0);
    highlighters.add(h);
    h.putUserData(SEARCH_MARKER, YES);
    return h;
  }


  private class ReplacementBalloonPositionTracker extends PositionTracker<Balloon> {
    private final Editor myEditor;

    public ReplacementBalloonPositionTracker(Editor editor) {
      super(editor.getContentComponent());
      myEditor = editor;

    }

    @Override
    public RelativePoint recalculateLocation(final Balloon object) {
      LiveOccurrence cursor = mySearchResults.getCursor();
      if (cursor == null) return null;
      final TextRange cur = cursor.getPrimaryRange();
      int startOffset = cur.getStartOffset();
      int endOffset = cur.getEndOffset();

      Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
      Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
      Point point = new Point((startPoint.x + endPoint.x)/2, startPoint.y);
      if (!SearchResults.insideVisibleArea(myEditor, cur)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            object.hide();
          }
        });

        VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
          @Override
          public void visibleAreaChanged(VisibleAreaEvent e) {
            if (SearchResults.insideVisibleArea(myEditor, cur)) {
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
