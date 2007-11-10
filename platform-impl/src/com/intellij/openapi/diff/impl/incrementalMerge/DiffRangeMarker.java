package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;

class DiffRangeMarker extends RangeMarkerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.DiffRangeMarker");
  private RangeInvalidListener myListener;

  public DiffRangeMarker(Document document, TextRange range, RangeInvalidListener listener) {
    this(document, range.getStartOffset(), range.getEndOffset(), listener);
  }

  private DiffRangeMarker(Document document, int start, int end, RangeInvalidListener listener) {
    super(document, start, end);
    myListener = listener;
    if (myListener != null) InvalidRangeDispatcher.addClient(document);
  }

  public void documentChanged(DocumentEvent e) {
    super.documentChanged(e);
    if (!isValid() && myListener != null) InvalidRangeDispatcher.notify(e.getDocument(), myListener);
  }

  public void removeListener(RangeInvalidListener listener) {
    LOG.assertTrue(myListener == listener || myListener == null);
    myListener = null;
    InvalidRangeDispatcher.removeClient(getDocument());
  }

  public interface RangeInvalidListener {
    void onRangeInvalidated();
  }

  private static class InvalidRangeDispatcher extends DocumentAdapter {
    private static final Key<InvalidRangeDispatcher> KEY = Key.create("deferedNotifier");
    private final ArrayList<RangeInvalidListener> myDeferedNotifications = new ArrayList<RangeInvalidListener>();
    private int myClientCount = 0;

    public void documentChanged(DocumentEvent e) {
      if (myDeferedNotifications.size() == 0) return;
      RangeInvalidListener[] notifications = myDeferedNotifications.toArray(
        new RangeInvalidListener[myDeferedNotifications.size()]);
      myDeferedNotifications.clear();
      for (int i = 0; i < notifications.length; i++) {
        RangeInvalidListener notification = notifications[i];
        notification.onRangeInvalidated();
      }
    }

    public static void notify(Document document, RangeInvalidListener listener) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      notifier.myDeferedNotifications.add(listener);
    }

    public static void addClient(Document document) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      if (notifier == null) {
        notifier = new InvalidRangeDispatcher();
        document.putUserData(KEY, notifier);
        document.addDocumentListener(notifier);
      }
      notifier.myClientCount++;
    }

    public static void removeClient(Document document) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      notifier.onClientRemoved(document);
    }

    private void onClientRemoved(Document document) {
      myClientCount--;
      LOG.assertTrue(myClientCount >= 0);
      if (myClientCount == 0) {
        document.putUserData(KEY, null);
        document.removeDocumentListener(this);
      }
    }
  }
}
