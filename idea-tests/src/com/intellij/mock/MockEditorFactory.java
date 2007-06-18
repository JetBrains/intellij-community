package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;

public class MockEditorFactory extends EditorFactory {
  public Document createDocument(String text) {
    return new DocumentImpl(text);
  }

  public Editor createEditor(Document document) {
    return null;
  }

  public Editor createViewer(Document document) {
    return null;
  }

  public Editor createEditor(Document document, Project project) {
    return null;
  }

  public Editor createEditor(@NotNull final Document document, final Project project, @NotNull final FileType fileType, final boolean isViewer) {
    return null;
  }

  public Editor createViewer(Document document, Project project) {
    return null;
  }

  public void releaseEditor(Editor editor) {
  }

  public Editor[] getEditors(Document document, Project project) {
    return new Editor[0];
  }

  public Editor[] getEditors(Document document) {
    return getEditors(document, null);
  }

  public Editor[] getAllEditors() {
    return new Editor[0];
  }

  public void addEditorFactoryListener(EditorFactoryListener listener) {
  }

  public void removeEditorFactoryListener(EditorFactoryListener listener) {
  }

  @NotNull
  public EditorEventMulticaster getEventMulticaster() {
    return new MockEditorEventMulticaster();
  }

  public Document createDocument(CharSequence text) {
    return new DocumentImpl(text);
  }

  public Document createDocument(char[] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  public void refreshAllEditors() {
  }

  public String getComponentName() {
    return null;
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
