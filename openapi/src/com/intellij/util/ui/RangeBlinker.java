package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.*;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class RangeBlinker {
  private final Editor myEditor;
  private int myTimeToLive;
  private final List<RangeMarker> myMarkers = new ArrayList<RangeMarker>();
  private boolean show = true;
  private final Alarm myBlinkingAlarm = new Alarm();
  private TextAttributes myAttributes;
  private final List<RangeHighlighter> myAddedHighlighters = new ArrayList<RangeHighlighter>();

  public RangeBlinker(Editor editor, final TextAttributes attributes, int timeToLive) {
    myAttributes = attributes;
    myEditor = editor;
    myTimeToLive = timeToLive;
  }
  public void resetMarkers(final List<RangeMarker> markers) {
    removeHighlights();
    myMarkers.clear();
    stopBlinking();
    myMarkers.addAll(markers);
    show = true;
  }

  private void removeHighlights() {
    MarkupModel markupModel = myEditor.getMarkupModel();
    RangeHighlighter[] allHighlighters = markupModel.getAllHighlighters();
    
    for (RangeHighlighter highlighter : myAddedHighlighters) {
      if (ArrayUtil.indexOf(allHighlighters, highlighter) != -1) {
        markupModel.removeHighlighter(highlighter);
      }
    }
    myAddedHighlighters.clear();
  }

  public void startBlinking() {
    if (ApplicationManager.getApplication().isDisposed()) return;

    MarkupModel markupModel = myEditor.getMarkupModel();
    if (show) {
      for (final RangeMarker rangeMarker : myMarkers) {
        myAddedHighlighters.add(markupModel.addRangeHighlighter(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                                  HighlighterLayer.ADDITIONAL_SYNTAX, myAttributes, HighlighterTargetArea.EXACT_RANGE));
      }
    }
    else {
      removeHighlights();
    }
    stopBlinking();
    myBlinkingAlarm.addRequest(new Runnable() {
      public void run() {
        if (myTimeToLive > 0 || show) {
          myTimeToLive--;
          show = !show;
          startBlinking();
        }
      }
    }, 400);
  }

  public void stopBlinking() {
    myBlinkingAlarm.cancelAllRequests();
  }
}
