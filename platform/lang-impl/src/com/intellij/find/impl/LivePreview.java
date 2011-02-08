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
package com.intellij.find.impl;


import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LivePreview extends DocumentAdapter implements ReplacementView.Delegate {

  private final Collection<RangeHighlighter> myHighlighters = new HashSet<RangeHighlighter>();
  private RangeHighlighter myCursorHighlighter;
  private final List<VisibleAreaListener> myVisibleAreaListenersToRemove = new ArrayList<VisibleAreaListener>();
  private boolean myShouldStop;

  @Override
  public void performReplacement(LiveOccurrence occurrence, String replacement) {
    if (myDelegate != null) {
      final TextRange textRange = myDelegate.performReplace(occurrence, replacement, myEditor);
      if (textRange != null) {
        updateInBackground();
        setContinuation(new Runnable() {
          @Override
          public void run() {
            if (mySearchResults != null) {
              LiveOccurrence nearest = null;
              int minDist = Integer.MAX_VALUE;
              for (LiveOccurrence o : mySearchResults) {
                if (nearest == null) {
                  nearest = o;
                }
                int dist = Math.abs(o.getPrimaryRange().getStartOffset() - textRange.getStartOffset());
                if (dist < minDist) {
                  minDist = dist;
                  nearest = o;
                }
              }
              if (nearest != null) {
                moveCursorTo(nearest);
              }
            }
          }
        });
        myDelegate.getFocusBack();
      }
    }

  }

  @Override
  public void performReplaceAll() {
    myDelegate.performReplaceAll(myEditor);
  }

  public boolean hasMatches() {
    return mySearchResults != null && !mySearchResults.isEmpty();
  }

  public interface Delegate {
    @NotNull
    List<LiveOccurrence> performSearchInBackgroundInReadAction(Editor editor);

    @Nullable
    String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence);

    TextRange performReplace(LiveOccurrence occurrence, String replacement, Editor editor);

    void performReplaceAll(Editor e);

    void getFocusBack();
  }

  private static final int USER_ACTIVITY_TRIGGERING_DELAY = 300;

  private static final TextAttributes OTHER_TARGETS_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.GREEN, null, null, 0);
  private static final TextAttributes MAIN_TARGET_ATTRIBUTES = new TextAttributes(Color.BLACK, Color.YELLOW, null, null, 0);

  private final Alarm myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private final Project myProject;
  private Editor myEditor;

  private Delegate myDelegate;

  private LiveOccurrence myCursor;

  private Runnable myContinuation;

  public Runnable getContinuation() {
    return myContinuation;
  }

  public void setContinuation(Runnable continuation) {
    myContinuation = continuation;
  }

  public List<LiveOccurrence> getSearchResults() {
    return mySearchResults;
  }

  private List<LiveOccurrence> mySearchResults;

  private Balloon myReplacementBalloon;

  public LivePreview(Project project) {
    this.myProject = project;
    updateEditorReference();
  }

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    this.myDelegate = delegate;
  }

  @Nullable
  public Editor updateEditorReference() {
    if (myProject == null) return null;
    FileEditorManagerEx instanceEx = FileEditorManagerEx.getInstanceEx(myProject);
    if (instanceEx != null) {
      VirtualFile currentFile = instanceEx.getCurrentFile();
      if (currentFile != null) {
        FileEditor[] editors = instanceEx.getEditors(currentFile);
        if (editors.length > 0) {
          FileEditor fileEditor = editors[0];
          boolean focusedFound = false;
          for (FileEditor e : editors) {
            if (e instanceof TextEditor && ((TextEditor)e).getEditor().getContentComponent().hasFocus()) {
              fileEditor = e;
              focusedFound = true;
            }
          }
          boolean needToUpdate = true;
          if (!focusedFound) {
            for (FileEditor e : editors) {
              if (e instanceof TextEditor && ((TextEditor)e).getEditor() == myEditor) {
                needToUpdate = false;
              }
            }
          }
          if (needToUpdate && fileEditor instanceof TextEditor) {
            Editor editor1 = ((TextEditor) fileEditor).getEditor();
            if (editor1 != myEditor) {
              doInternalCleanUp();
            }
            if (myEditor != null) {
              myEditor.getDocument().removeDocumentListener(this);
            }
            myEditor = editor1;
            myEditor.getDocument().addDocumentListener(this);
          }
        } else {
          myEditor = null;
        }
      } else {
        myEditor = null;
      }
    }
    return myEditor;
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    doInternalCleanUp();
  }

  public void update() {
    myShouldStop = false;
    myLivePreviewAlarm.cancelAllRequests();
    if (updateEditorReference() != null) {
      myLivePreviewAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          updateInBackground();
        }
      }, USER_ACTIVITY_TRIGGERING_DELAY);
    }
  }

  public void cleanUp() {
    myShouldStop = true;
    myLivePreviewAlarm.cancelAllRequests();
    doInternalCleanUp();
  }

  private void doInternalCleanUp() {
    if (myReplacementBalloon != null) {
      myReplacementBalloon.hide();
    }
    if (myEditor != null) {
      
      for (VisibleAreaListener visibleAreaListener : myVisibleAreaListenersToRemove) {
        myEditor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
      }
      myVisibleAreaListenersToRemove.clear();
      for (RangeHighlighter h : myHighlighters) {
        HighlightManager.getInstance(myProject).removeSegmentHighlighter(myEditor, h);
      }
      if (myCursorHighlighter != null) {
        HighlightManager.getInstance(myProject).removeSegmentHighlighter(myEditor, myCursorHighlighter);
        myCursorHighlighter = null;
      }
    }
  }

  private void updateInBackground() {
    if (myDelegate == null) return;
    final TextRange oldCursorRange = myCursor != null ? myCursor.getPrimaryRange() : null;
    mySearchResults = performSearchInBackground();
    Collections.sort(mySearchResults, new Comparator<LiveOccurrence>() {
      @Override
      public int compare(LiveOccurrence liveOccurrence, LiveOccurrence liveOccurence1) {
        return liveOccurrence.getPrimaryRange().getStartOffset() - liveOccurence1.getPrimaryRange().getStartOffset();
      }
    });
    if (mySearchResults != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          doInternalCleanUp();
          highlightUsages(oldCursorRange);
          if (myContinuation != null) {
            myContinuation.run();
            myContinuation = null;
          }
        }
      });
    }
  }

  private List<LiveOccurrence> performSearchInBackground() {
    final AtomicReference<List<LiveOccurrence>> occurrences = new AtomicReference<List<LiveOccurrence>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        occurrences.set(myDelegate.performSearchInBackgroundInReadAction(myEditor));
      }
    });
    return occurrences.get();
  }

  private void highlightUsages(TextRange oldCursorRange) {
    if (myEditor == null || myShouldStop) return;
    LiveOccurrence firstVisibleOccurrence = null;
    LiveOccurrence firstOccurrence = null;
    int offset = Integer.MAX_VALUE;
    for (LiveOccurrence o : mySearchResults) {
      if (insideVisibleArea(myEditor, o.getPrimaryRange())) {
        if (firstVisibleOccurrence == null || o.getPrimaryRange().getStartOffset() < firstVisibleOccurrence.getPrimaryRange().getStartOffset()) {
          firstVisibleOccurrence = o;
        }
      }
      if (o.getPrimaryRange().getStartOffset() < offset) {
        offset = o.getPrimaryRange().getStartOffset();
        firstOccurrence = o;
      }

      for (TextRange textRange : o.getSecondaryRanges()) {
        highlightRange(textRange, OTHER_TARGETS_ATTRIBUTES, myHighlighters);
      }
      highlightRange(o.getPrimaryRange(), MAIN_TARGET_ATTRIBUTES, myHighlighters);
    }

    if (!tryToRepairOldCursor(oldCursorRange)) {
      setCursor(firstVisibleOccurrence != null ? firstVisibleOccurrence : firstOccurrence);
    }
  }

  private boolean tryToRepairOldCursor(TextRange oldCursorRange) {
    if (oldCursorRange == null) return false;
    LiveOccurrence mayBeOldCursor = null;
    for (LiveOccurrence searchResult : mySearchResults) {
      if (searchResult.getPrimaryRange().intersects(oldCursorRange)) {
        mayBeOldCursor = searchResult;
        break;
      }
    }
    if (mayBeOldCursor != null && insideVisibleArea(myEditor, mayBeOldCursor.getPrimaryRange())) {
      setCursor(mayBeOldCursor);
      return true;
    }
    return false;
  }

  @Nullable
  private LiveOccurrence prevOccurrence(LiveOccurrence o) {
    if (mySearchResults == null) return null;
    for (int i = 0; i < mySearchResults.size(); ++i) {
      if (o == mySearchResults.get(i))  {
        if (i > 0) {
          return mySearchResults.get(i-1);
        }
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence nextOccurrence(LiveOccurrence o) {
    if (mySearchResults == null) return null;
    boolean found = false;
    for (LiveOccurrence occurrence : mySearchResults) {
      if (found) {
        return occurrence;
      }
      if (o == occurrence) {
        found = true;
      }
    }
    return null;
  }

  public void prevOccurrence() {
    LiveOccurrence prev = prevOccurrence(myCursor);
    if (prev == null && !mySearchResults.isEmpty()) {
      prev = mySearchResults.get(mySearchResults.size()-1);
    }
    moveCursorTo(prev);
  }

  public void nextOccurrence() {
    LiveOccurrence next = nextOccurrence(myCursor);
    if (next == null && !mySearchResults.isEmpty()) {
      next = mySearchResults.get(0);
    }
    moveCursorTo(next);
  }

  public void moveCursorTo(LiveOccurrence next) {
    if (next != null) {
      setCursor(next);
    } else {
      showReplacementPreview();
    }
  }

  private static void drawMatch(Graphics2D g2d, Point start, Point end, int lineHeight, int horizontalGap, int verticalGap) {
    g2d.setColor(new Color(50, 50, 50));
    g2d.translate(0, start.y-verticalGap);
    UIUtil.drawSearchMatch(g2d, start.x-horizontalGap, end.x+horizontalGap, lineHeight+2*verticalGap);
    g2d.translate(0, -start.y+verticalGap);
  }

  private void setCursor(LiveOccurrence liveOccurrence) {
    hideBalloon();
    myCursor = liveOccurrence;

    if (myCursorHighlighter != null) {
      HighlightManager.getInstance(myProject).removeSegmentHighlighter(myEditor, myCursorHighlighter);
      myCursorHighlighter = null;
    }
    if (myCursor != null) {
      ArrayList<RangeHighlighter> dummy = new ArrayList<RangeHighlighter>();
      highlightRange(myCursor.getPrimaryRange(), new TextAttributes(null, null, null, null, 0), dummy);
      if (!dummy.isEmpty()) {
        myCursorHighlighter = dummy.get(0);
        myCursorHighlighter.setCustomRenderer(new CustomHighlighterRenderer() {
          @Override
          public void paint(Editor editor, RangeHighlighter highlighter, Graphics g) {
            Graphics2D g2d = (Graphics2D)g;
            VisualPosition startVp = editor.offsetToVisualPosition(highlighter.getStartOffset());
            VisualPosition endVp = editor.offsetToVisualPosition(highlighter.getEndOffset());
            Point start = editor.visualPositionToXY(startVp);
            Point end = editor.visualPositionToXY(endVp);
            drawMatch(g2d, start, end, editor.getLineHeight(), 1, 4);
          }
        });
      }

      if (!insideVisibleArea(myEditor, myCursor.getPrimaryRange())) {
        myEditor.getScrollingModel().scrollTo(myEditor.offsetToLogicalPosition(myCursor.getPrimaryRange().getStartOffset()),
                                              ScrollType.CENTER);
        myEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
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

  private void showReplacementPreview() {
    hideBalloon();
    if (myDelegate != null && myCursor != null) {
      String replacementPreviewText = myDelegate.getReplacementPreviewText(myEditor, myCursor);
      if (replacementPreviewText != null) {

        //JLabel balloonContent = new JLabel(replacementPreviewText);
        //balloonContent.setForeground(Color.WHITE);

        ReplacementView replacementView = new ReplacementView(replacementPreviewText, myCursor);
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
        final int startOffset = myCursor.getPrimaryRange().getStartOffset();
        final int endOffset = myCursor.getPrimaryRange().getEndOffset();

        myReplacementBalloon.show(new PositionTracker<Balloon>(myEditor.getContentComponent()) {
          @Override
          public RelativePoint recalculateLocation(final Balloon object) {
            Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
            Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
            Point point = new Point((startPoint.x + endPoint.x)/2, startPoint.y);
            if (!insideVisibleArea(myEditor, myCursor.getPrimaryRange())) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  object.hide();
                }
              });

              VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
                @Override
                public void visibleAreaChanged(VisibleAreaEvent e) {
                  if (insideVisibleArea(myEditor, myCursor.getPrimaryRange())) {
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
        }, Balloon.Position.above);
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
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    if (highlightManager != null) {
      highlightManager.addRangeHighlight(myEditor,
              textRange.getStartOffset(), textRange.getEndOffset(),
              attributes, false, highlighters);
    }
  }

  private static boolean insideVisibleArea(Editor e, TextRange r) {
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }
}
