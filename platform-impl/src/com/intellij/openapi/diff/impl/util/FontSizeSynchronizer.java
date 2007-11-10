package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.containers.HashSet;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class FontSizeSynchronizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.FontSizeSynchronizer");
  private final Collection<Editor> myEditors = new HashSet<Editor>();
  private final MyFontSizeListener myFontSizeListener = new MyFontSizeListener();
  private int myLastFontSize = -1;

  public void synchronize(EditorEx editor) {
    LOG.assertTrue(!myEditors.contains(editor));
    editor.addPropertyChangeListener(myFontSizeListener);
    myEditors.add(editor);
    if (myLastFontSize != -1) myFontSizeListener.updateEditor(editor);
  }

  public void stopSynchronize(EditorEx editor) {
    LOG.assertTrue(myEditors.contains(editor));
    editor.removePropertyChangeListener(myFontSizeListener);
    myEditors.remove(editor);
  }

  public static void attachTo(ArrayList<EditorPlace> editorPlaces) {
    final FontSizeSynchronizer synchronizer = new FontSizeSynchronizer();
    for (Iterator<EditorPlace> iterator = editorPlaces.iterator(); iterator.hasNext();) {
      EditorPlace editorPlace = iterator.next();
      editorPlace.addListener(new EditorPlace.EditorListener() {
        public void onEditorCreated(EditorPlace place) {
          synchronizer.synchronize((EditorEx)place.getEditor());
        }

        public void onEditorReleased(Editor releasedEditor) {
          synchronizer.stopSynchronize((EditorEx)releasedEditor);
        }
      });
      EditorEx editor = (EditorEx)editorPlace.getEditor();
      if (editor != null) synchronizer.synchronize(editor);
    }

  }

  private class MyFontSizeListener implements PropertyChangeListener {
    private boolean myDuringUpdate = false;
    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate) return;
      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      myLastFontSize = ((Integer)evt.getNewValue()).intValue();
      for (Iterator<Editor> iterator = myEditors.iterator(); iterator.hasNext();) {
        Editor editor = iterator.next();
        if (editor == null || editor == evt.getSource()) continue;
        updateEditor((EditorEx)editor);
      }
    }

    public void updateEditor(EditorEx editor) {
      try {
        myDuringUpdate = true;
        editor.setFontSize(myLastFontSize);
      } finally {
        myDuringUpdate = false;
      }
    }
  }
}
