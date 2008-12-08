package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import gnu.trove.THashSet;

import java.util.*;

public class HighlighterList {
  private final List<RangeHighlighterImpl> mySegmentHighlighters = new ArrayList<RangeHighlighterImpl>();
  private final Set<RangeHighlighter> myHighlightersSet = new THashSet<RangeHighlighter>();
  private boolean myIsDirtied = false;
  private final DocumentAdapter myDocumentListener;
  private final Document myDoc;
  private int myLongestHighlighterLength = 0;
  private static final Comparator<RangeHighlighterImpl> MY_RANGE_COMPARATOR = new Comparator<RangeHighlighterImpl>() {
    public int compare(RangeHighlighterImpl r1, RangeHighlighterImpl r2) {
      if (r1.getAffectedAreaStartOffset() != r2.getAffectedAreaStartOffset()) {
        return r1.getAffectedAreaStartOffset() - r2.getAffectedAreaStartOffset();
      }

      if (r1.getLayer() != r2.getLayer()) {
        return r2.getLayer() - r1.getLayer();
      }

      return (int) (r2.getId() - r1.getId());
    }
  };

  public HighlighterList(Document doc) {
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        myIsDirtied = true;
      }
    };
    myDoc = doc;
    myDoc.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    myDoc.removeDocumentListener(myDocumentListener);
  }

  public int getLongestHighlighterLength() {
    return myLongestHighlighterLength;
  }

  private void sortMarkers() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLongestHighlighterLength = 0;
    mySegmentHighlighters.clear();
    Iterator<RangeHighlighter> iterator = myHighlightersSet.iterator();
    while (iterator.hasNext()) {
      RangeHighlighter rangeHighlighter = iterator.next();
      if (rangeHighlighter.isValid()) {
        mySegmentHighlighters.add((RangeHighlighterImpl)rangeHighlighter);
        myLongestHighlighterLength = Math.max(myLongestHighlighterLength, rangeHighlighter.getEndOffset() - rangeHighlighter.getStartOffset());
      }
      else {
        iterator.remove();
      }
    }
    Collections.sort(mySegmentHighlighters, MY_RANGE_COMPARATOR);

    myIsDirtied = false;
  }

  Iterator<RangeHighlighterImpl> getHighlighterIterator() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters.iterator();
  }

  List<RangeHighlighterImpl> getSortedHighlighters() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters;
  }

  void addSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myIsDirtied = true;
    myHighlightersSet.add(segmentHighlighter);
  }

  void removeSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myIsDirtied = true;
    myHighlightersSet.remove(segmentHighlighter);
  }
}
