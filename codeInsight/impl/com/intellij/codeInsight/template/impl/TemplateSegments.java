package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;

import java.util.ArrayList;

public class TemplateSegments {
  private ArrayList<RangeMarker> mySegments = new ArrayList<RangeMarker>();
  private Editor myEditor;

  public TemplateSegments(Editor editor) {
    myEditor = editor;
  }

  public int getSegmentStart(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getStartOffset();
  }

  public int getSegmentEnd(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getEndOffset();
  }

  public boolean isValid(int i) {
    return mySegments.get(i).isValid();
  }

  public void removeAll() {
    mySegments.clear();
  }

  public void addSegment(int start, int end) {
    RangeMarker rangeMarker = (myEditor.getDocument()).createRangeMarker(start, end);
    mySegments.add(rangeMarker);
  }

  public void setSegmentsGreedy(boolean greedy) {
    for (final RangeMarker segment : mySegments) {
      segment.setGreedyToRight(greedy);
      segment.setGreedyToLeft(greedy);
    }
  }

  public boolean isInvalid() {
    for (RangeMarker marker : mySegments) {
      if (!marker.isValid()) {
        return true;
      }
    }
    return false;
  }

  public void replaceSegmentAt(int index, int start, int end) {
    RangeMarker rangeMarker = mySegments.get(index);
    ((RangeMarkerImpl)rangeMarker).invalidate();
    Document doc = myEditor.getDocument();
    rangeMarker = doc.createRangeMarker(start, end);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    mySegments.set(index, rangeMarker);
  }
}