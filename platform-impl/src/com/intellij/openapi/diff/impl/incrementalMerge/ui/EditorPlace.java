package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class EditorPlace extends JComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace");
  private final ComponentState myState;
  private final ArrayList<EditorListener> myListeners = new ArrayList<EditorListener>();
  private Editor myEditor = null;

  public EditorPlace(ComponentState state) {
    myState = state;
    setLayout(new BorderLayout());
  }

  public void addNotify() {
    if (myEditor != null) {
      super.addNotify();
      return;
    }
    createEditor();
    super.addNotify();
    revalidate();
  }

  private void createEditor() {
    LOG.assertTrue(myEditor == null);
    myEditor = myState.createEditor();
    if (myEditor == null) return;
    add(myEditor.getComponent(), BorderLayout.CENTER);
    fireEditorCreated();
  }

  public void addListener(EditorListener listener) {
    myListeners.add(listener);
  }

  private void fireEditorCreated() {
    EditorListener[] listeners = getListeners();
    for (int i = 0; i < listeners.length; i++) {
      EditorListener listener = listeners[i];
      listener.onEditorCreated(this);
    }
  }

  private void fireEditorReleased(Editor releasedEditor) {
    EditorListener[] listeners = getListeners();
    for (int i = 0; i < listeners.length; i++) {
      EditorListener listener = listeners[i];
      listener.onEditorReleased(releasedEditor);
    }
  }

  private EditorListener[] getListeners() {
    EditorListener[] listeners = new EditorListener[myListeners.size()];
    myListeners.toArray(listeners);
    return listeners;
  }

  public void removeNotify() {
    removeEditor();
    super.removeNotify();
  }

  private void removeEditor() {
    if (myEditor != null) {
      Editor releasedEditor = myEditor;
      remove(myEditor.getComponent());
      getEditorFactory().releaseEditor(myEditor);
      myEditor = null;
      fireEditorReleased(releasedEditor);
    }
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void setDocument(Document document) {
    if (document == getDocument()) return;
    removeEditor();
    myState.setDocument(document);
    createEditor();
  }

  private Document getDocument() {
    return myState.getDocument();
  }

  public ComponentState getState() {
    return myState;
  }

  public static abstract class ComponentState {
    private Document myDocument;
    public abstract Editor createEditor();

    public void setDocument(Document document) {
      myDocument = document;
    }

    public Document getDocument() {
      return myDocument;
    }

    public abstract <T> void updateValue(Editor editor, ViewProperty<T> property, T value);
  }

  public interface EditorListener {
    void onEditorCreated(EditorPlace place);
    void onEditorReleased(Editor releasedEditor);
  }

  private static EditorFactory getEditorFactory() {
    return EditorFactory.getInstance();
  }

  public JComponent getContentComponent() {
    return myEditor == null ? null : myEditor.getContentComponent();
  }

  public static abstract class ViewProperty<T> {
    private final T myDefault;

    protected ViewProperty(T aDefault) {
      myDefault = aDefault;
    }

    public void updateEditor(Editor editor, T value, ComponentState state) {
      if (editor == null) return;
      if (value == null) value = myDefault;
      EditorEx editorEx = (EditorEx)editor;
      doUpdateEditor(editorEx, value, state);
    }

    protected abstract void doUpdateEditor(EditorEx editorEx, T value, ComponentState state);
  }
}
