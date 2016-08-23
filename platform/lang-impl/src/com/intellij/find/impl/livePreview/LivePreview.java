/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.find.FindResult;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.PrintStream;
import java.util.*;
import java.util.List;

public class LivePreview extends DocumentAdapter implements SearchResults.SearchResultsListener, SelectionListener {
  private static final Key<Object> IN_SELECTION_KEY = Key.create("LivePreview.IN_SELECTION_KEY");
  private static final Object IN_SELECTION1 = new Object();
  private static final Object IN_SELECTION2 = new Object();
  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";

  private boolean myListeningSelection = false;
  private boolean mySuppressedUpdate = false;
  private boolean myInSmartUpdate = false;

  private static final Key<Object> MARKER_USED = Key.create("LivePreview.MARKER_USED");
  private static final Object YES = new Object();
  private static final Key<Object> SEARCH_MARKER = Key.create("LivePreview.SEARCH_MARKER");

  public static PrintStream ourTestOutput;
  private String myReplacementPreviewText;
  private static boolean NotFound;

  private final Set<RangeHighlighter> myHighlighters = new HashSet<>();
  private RangeHighlighter myCursorHighlighter;
  private final List<VisibleAreaListener> myVisibleAreaListenersToRemove = new ArrayList<>();
  private Delegate myDelegate;
  private final SearchResults mySearchResults;
  private Balloon myReplacementBalloon;

  @Override
  public void selectionChanged(SelectionEvent e) {
    updateInSelectionHighlighters();
  }

  public void inSmartUpdate() {
    myInSmartUpdate = true;
  }

  public static void processNotFound() {
    NotFound = true;
  }

  public interface Delegate {
    @Nullable
    String getStringToReplace(@NotNull Editor editor, @Nullable FindResult findResult);
  }

  private static TextAttributes strikeout() {
    Color color = EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground();
    return new TextAttributes(null, null, color, EffectType.STRIKEOUT, Font.PLAIN);
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
    updateCursorHighlighting();
    if (myInSmartUpdate) {
      clearUnusedHightlighters();
      myInSmartUpdate = false;
    }
  }

  private void dumpState() {
    if (ApplicationManager.getApplication().isUnitTestMode() && ourTestOutput != null) {
      dumpEditorMarkupAndSelection(ourTestOutput);
    }
  }

  private void dumpEditorMarkupAndSelection(PrintStream dumpStream) {
    dumpStream.println(mySearchResults.getFindModel());
    if (myReplacementPreviewText != null) {
      dumpStream.println("--");
      dumpStream.println("Replacement Preview: " + myReplacementPreviewText);
    }
    dumpStream.println("--");

    Editor editor = mySearchResults.getEditor();

    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    List<Pair<Integer, Character>> ranges = new ArrayList<>();
    for (RangeHighlighter highlighter : highlighters) {
      ranges.add(new Pair<>(highlighter.getStartOffset(), '['));
      ranges.add(new Pair<>(highlighter.getEndOffset(), ']'));
    }

    SelectionModel selectionModel = editor.getSelectionModel();

    if (selectionModel.getSelectionStart() != selectionModel.getSelectionEnd()) {
      ranges.add(new Pair<>(selectionModel.getSelectionStart(), '<'));
      ranges.add(new Pair<>(selectionModel.getSelectionEnd(), '>'));
    }
    ranges.add(new Pair<>(-1, '\n'));
    ranges.add(new Pair<>(editor.getDocument().getTextLength() + 1, '\n'));
    ContainerUtil.sort(ranges, (pair, pair2) -> {
      int res = pair.first - pair2.first;
      if (res == 0) {

        Character c1 = pair.second;
        Character c2 = pair2.second;
        if (c1 == '<' && c2 == '[') {
          return 1;
        }
        else if (c1 == '[' && c2 == '<') {
          return -1;
        }
        return c1.compareTo(c2);
      }
      return res;
    });

    Document document = editor.getDocument();
    for (int i = 0; i < ranges.size()-1; ++i) {
      Pair<Integer, Character> pair = ranges.get(i);
      Pair<Integer, Character> pair1 = ranges.get(i + 1);
      dumpStream.print(pair.second + document.getText(TextRange.create(Math.max(pair.first, 0), Math.min(pair1.first, document.getTextLength() ))));
    }
    dumpStream.println("\n--");

    if (NotFound) {
      dumpStream.println("Not Found");
      dumpStream.println("--");
      NotFound = false;
    }

    for (RangeHighlighter highlighter : highlighters) {
      dumpStream.println(highlighter + " : " + highlighter.getTextAttributes());
    }
    dumpStream.println("------------");
  }

  private void clearUnusedHightlighters() {
    Set<RangeHighlighter> unused = new com.intellij.util.containers.HashSet<>();
    for (RangeHighlighter highlighter : myHighlighters) {
      if (highlighter.getUserData(MARKER_USED) == null) {
        unused.add(highlighter);
      } else {
        highlighter.putUserData(MARKER_USED, null);
      }
    }
    myHighlighters.removeAll(unused);
    Project project = mySearchResults.getProject();
    if (project != null && !project.isDisposed()) {
      for (RangeHighlighter highlighter : unused) {
        HighlightManager.getInstance(project).removeSegmentHighlighter(mySearchResults.getEditor(), highlighter);
      }
    }
  }

  @Override
  public void cursorMoved() {
    updateInSelectionHighlighters();
    updateCursorHighlighting();
  }

  @Override
  public void updateFinished() {
    dumpState();
  }

  private void updateCursorHighlighting() {
    hideBalloon();

    if (myCursorHighlighter != null) {
      HighlightManager.getInstance(mySearchResults.getProject()).removeSegmentHighlighter(mySearchResults.getEditor(), myCursorHighlighter);
      myCursorHighlighter = null;
    }

    final FindResult cursor = mySearchResults.getCursor();
    Editor editor = mySearchResults.getEditor();
    if (cursor != null && cursor.getEndOffset() <= editor.getDocument().getTextLength()) {
      Set<RangeHighlighter> dummy = new HashSet<>();
      Color color = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
      highlightRange(cursor, new TextAttributes(null, null, color, EffectType.ROUNDED_BOX, Font.PLAIN), dummy);
      if (!dummy.isEmpty()) {
        myCursorHighlighter = dummy.iterator().next();
      }

      editor.getScrollingModel().runActionOnScrollingFinished(() -> showReplacementPreview());
    }
  }

  public LivePreview(SearchResults searchResults) {
    mySearchResults = searchResults;
    searchResultsUpdated(searchResults);
    searchResults.addListener(this);
    myListeningSelection = true;
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
    cleanUp();
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
    for (FindResult range : mySearchResults.getOccurrences()) {
      if (range.getEndOffset() > mySearchResults.getEditor().getDocument().getTextLength()) continue;
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
      if (range.getLength() == 0) {
        attributes = attributes.clone();
        attributes.setEffectType(EffectType.BOXED);
        attributes.setEffectColor(attributes.getBackgroundColor());
      }
      if (mySearchResults.isExcluded(range)) {
        highlightRange(range, strikeout(), myHighlighters);
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

    final HashSet<RangeHighlighter> toRemove = new HashSet<>();
    Set<RangeHighlighter> toAdd = new HashSet<>();
    for (RangeHighlighter highlighter : myHighlighters) {
      if (!highlighter.isValid()) continue;
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
        TextRange cursor = mySearchResults.getCursor();
        if (cursor != null && highlighter.getStartOffset() == cursor.getStartOffset() &&
            highlighter.getEndOffset() == cursor.getEndOffset()) continue;
        final RangeHighlighter toAnnotate = highlightRange(new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()),
                                                           new TextAttributes(null, null, Color.WHITE, EffectType.ROUNDED_BOX, Font.PLAIN), toAdd);
        highlighter.putUserData(IN_SELECTION_KEY, IN_SELECTION1);
        toAnnotate.putUserData(IN_SELECTION_KEY, IN_SELECTION2);
      }
    }
    myHighlighters.removeAll(toRemove);
    myHighlighters.addAll(toAdd);
  }

  private void showReplacementPreview() {
    hideBalloon();
    if (!mySearchResults.isUpToDate()) return;
    final FindResult cursor = mySearchResults.getCursor();
    final Editor editor = mySearchResults.getEditor();
    if (myDelegate != null && cursor != null) {
      String replacementPreviewText = myDelegate.getStringToReplace(editor, cursor);
      if (StringUtil.isEmpty(replacementPreviewText)) {
        replacementPreviewText = EMPTY_STRING_DISPLAY_TEXT;
      }
      final FindModel findModel = mySearchResults.getFindModel();
      if (findModel.isRegularExpressions() && findModel.isReplaceState()) {

        showBalloon(editor, replacementPreviewText);
      }
    }
  }

  private void showBalloon(Editor editor, String replacementPreviewText) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myReplacementPreviewText = replacementPreviewText;
      return;
    }

    ReplacementView replacementView = new ReplacementView(replacementPreviewText);

    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(replacementView);
    balloonBuilder.setFadeoutTime(0);
    balloonBuilder.setFillColor(IdeTooltipManager.GRAPHITE_COLOR);
    balloonBuilder.setAnimationCycle(0);
    balloonBuilder.setHideOnClickOutside(false);
    balloonBuilder.setHideOnKeyOutside(false);
    balloonBuilder.setHideOnAction(false);
    balloonBuilder.setCloseButtonEnabled(true);
    myReplacementBalloon = balloonBuilder.createBalloon();

    myReplacementBalloon.show(new ReplacementBalloonPositionTracker(editor), Balloon.Position.below);
  }

  private void hideBalloon() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myReplacementPreviewText = null;
      return;
    }

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
      highlighter -> {
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
      });

    if (!notFound && highlighters.contains(candidate[0])) {
      return candidate[0];
    }
    final ArrayList<RangeHighlighter> dummy = new ArrayList<>();
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
      FindResult cursor = mySearchResults.getCursor();
      if (cursor == null) return null;
      final TextRange cur = cursor;
      int startOffset = cur.getStartOffset();
      int endOffset = cur.getEndOffset();

      if (endOffset > myEditor.getDocument().getTextLength()) {
        if (!object.isDisposed()) {
          requestBalloonHiding(object);
        }
        return null;
      }
      if (!SearchResults.insideVisibleArea(myEditor, cur)) {
        requestBalloonHiding(object);

        VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
          @Override
          public void visibleAreaChanged(VisibleAreaEvent e) {
            if (SearchResults.insideVisibleArea(myEditor, cur)) {
              showReplacementPreview();
              final VisibleAreaListener visibleAreaListener = this;
              final boolean remove = myVisibleAreaListenersToRemove.remove(visibleAreaListener);
              if (remove) {
                myEditor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
              }
            }
          }
        };
        myEditor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
        myVisibleAreaListenersToRemove.add(visibleAreaListener);

      }

      Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
      Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
      Point point = new Point((startPoint.x + endPoint.x)/2, startPoint.y + myEditor.getLineHeight());

      return new RelativePoint(myEditor.getContentComponent(), point);
    }
  }

  private static void requestBalloonHiding(final Balloon object) {
    ApplicationManager.getApplication().invokeLater(() -> object.hide());
  }
}
