/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.structureView;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class TextEditorBasedStructureViewModel implements StructureViewModel {
  private final Editor myEditor;
  private final CaretListener myCaretListener;
  private final List<FileEditorPositionListener> myListeners = new ArrayList<FileEditorPositionListener>();

  protected TextEditorBasedStructureViewModel(PsiFile psiFile) {
    this(getEditorForFile(psiFile));
  }

  protected TextEditorBasedStructureViewModel(final Editor editor) {
    myEditor = editor;
    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        if (Comparing.equal(e.getEditor(), myEditor)) {
          fireCaretPositionChanged();
        }
      }

      private void fireCaretPositionChanged() {
        final FileEditorPositionListener[] listeners = myListeners.toArray(new FileEditorPositionListener[myListeners.size()]);
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].onCurrentElementChanged();
        }
      }
    };

    EditorFactory.getInstance().getEventMulticaster().addCaretListener(myCaretListener);
  }

  private static Editor getEditorForFile(final PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      PsiFile originalFile = psiFile.getOriginalFile();
      if (originalFile == null) return null;
      virtualFile = originalFile.getVirtualFile();
      if (virtualFile == null) return null;
    }
    final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(virtualFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        return ((TextEditor)editor).getEditor();
      }
    }
    return null;
  }

  public final void addEditorPositionListener(FileEditorPositionListener listener) {
    myListeners.add(listener);
  }

  public final void removeEditorPositionListener(FileEditorPositionListener listener) {
    myListeners.remove(listener);
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeCaretListener(myCaretListener);
  }

  public Object getCurrentEditorElement() {
    if (myEditor == null) return null;
    final int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = getPsiFile().findElementAt(offset);
    while (!isSuitable(element)) {
      if (element == null) return null;
      element = element.getParent();
    }
    return element;
  }

  protected abstract PsiFile getPsiFile();

  protected boolean isSuitable(final PsiElement element) {
    if (element == null) return false;
    final Class[] suitableClasses = getSuitableClasses();
    for (Class suitableClass : suitableClasses) {
      if (suitableClass.isAssignableFrom(element.getClass())) return true;
    }
    return false;
  }

  public void addModelListener(ModelListener modelListener) {

  }

  public void removeModelListener(ModelListener modelListener) {

  }

  @NotNull protected abstract Class[] getSuitableClasses();

  protected Editor getEditor() {
    return myEditor;
  }
}
