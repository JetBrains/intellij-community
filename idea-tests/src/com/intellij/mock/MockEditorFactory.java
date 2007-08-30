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

  public Editor createEditor(@NotNull Document document) {
    return null;
  }

  public Editor createViewer(@NotNull Document document) {
    return null;
  }

  public Editor createEditor(@NotNull Document document, Project project) {
    return null;
  }

  public Editor createEditor(@NotNull final Document document, final Project project, @NotNull final FileType fileType, final boolean isViewer) {
    return null;
  }

  public Editor createViewer(@NotNull Document document, Project project) {
    return null;
  }

  public void releaseEditor(@NotNull Editor editor) {
  }

  @NotNull
  public Editor[] getEditors(@NotNull Document document, Project project) {
    return new Editor[0];
  }

  @NotNull
  public Editor[] getEditors(@NotNull Document document) {
    return getEditors(document, null);
  }

  @NotNull
  public Editor[] getAllEditors() {
    return new Editor[0];
  }

  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener) {
  }

  public void removeEditorFactoryListener(@NotNull EditorFactoryListener listener) {
  }

  @NotNull
  public EditorEventMulticaster getEventMulticaster() {
    return new MockEditorEventMulticaster();
  }

  @NotNull
  public Document createDocument(@NotNull CharSequence text) {
    return new DocumentImpl(text);
  }

  @NotNull
  public Document createDocument(@NotNull char[] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  public void refreshAllEditors() {
  }

  @NotNull
  public String getComponentName() {
    return "mockeditorfactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
