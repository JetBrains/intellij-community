/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

public abstract class TextEditorBasedStructureViewModel implements StructureViewModel {
  private final Editor myEditor;
  private final CaretListener myCaretListener;
  private final List<FileEditorPositionListener> myListeners = new ArrayList<FileEditorPositionListener>();

  protected TextEditorBasedStructureViewModel(PsiFile psiFile) {
    this(getEditorForFile(psiFile));

  }

  private static Editor getEditorForFile(final PsiFile psiFile) {
    final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(psiFile.getVirtualFile());
    for (int i = 0; i < editors.length; i++) {
      FileEditor editor = editors[i];
      if (editor instanceof TextEditor) {
        return ((TextEditor)editor).getEditor();
      }
    }
    return null;
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

  public final void addEditorPositionListener(FileEditorPositionListener listener) {
    myListeners.add(listener);
  }

  public final void removeEditorPositionListener(FileEditorPositionListener listener) {
    myListeners.remove(listener);
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeCaretListener(myCaretListener);
  }

  public final Object getCurrentEditorElement() {
    if (myEditor == null) return null;
    final int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = getPsiFile().findElementAt(offset);
    while (!isSutable(element)) {
      if (element == null) return null;
      element = element.getParent();
    }
    return element;
  }

  protected abstract PsiFile getPsiFile();

  private boolean isSutable(final PsiElement element) {
    if (element == null) return false;
    final Class[] suitableClasses = getSuitableClasses();
    for (int i = 0; i < suitableClasses.length; i++) {
      Class suitableClass = suitableClasses[i];
      if (suitableClass.isAssignableFrom(element.getClass())) return true;
    }
    return false;
  }

  public void addModelListener(ModelListener modelListener) {

  }

  public void removeModelListener(ModelListener modelListener) {

  }

  protected abstract Class[] getSuitableClasses();
}
