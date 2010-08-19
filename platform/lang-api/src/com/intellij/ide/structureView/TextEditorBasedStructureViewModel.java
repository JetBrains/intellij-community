/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
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
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The standard {@link StructureViewModel} implementation which is linked to a text editor.
 *
 * @see com.intellij.ide.structureView.TreeBasedStructureViewBuilder#createStructureViewModel()
 */

public abstract class TextEditorBasedStructureViewModel implements StructureViewModel {
  private final Editor myEditor;
  private final PsiFile myPsiFile;
  private final CaretListener myCaretListener;
  private final List<FileEditorPositionListener> myListeners = ContainerUtil.createEmptyCOWList();

  /**
   * Creates a structure view model instance linked to a text editor displaying the specified
   * file.
   *
   * @param psiFile the file for which the structure view model is requested.
   */
  protected TextEditorBasedStructureViewModel(@NotNull PsiFile psiFile) {
    this(PsiUtilBase.findEditor(psiFile), psiFile);
  }

  /**
   * Creates a structure view model instance linked to the specified text editor.
   *
   * @param editor the editor for which the structure view model is requested.
   */
  protected TextEditorBasedStructureViewModel(final Editor editor) {
    this(editor, null);
  }

  private TextEditorBasedStructureViewModel(Editor editor, PsiFile file) {
    myEditor = editor;
    myPsiFile = file;
    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        if (Comparing.equal(e.getEditor(), myEditor)) {
          fireCaretPositionChanged();
        }
      }

      private void fireCaretPositionChanged() {
        for (FileEditorPositionListener listener : myListeners) {
          listener.onCurrentElementChanged();
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

  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  public Object getCurrentEditorElement() {
    if (myEditor == null) return null;
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiFile file = getPsiFile();

    return findAcceptableElement(file.getViewProvider().findElementAt(offset, file.getLanguage()));
  }

  protected Object findAcceptableElement(PsiElement element) {
    while (element != null && !(element instanceof PsiFile)) {
      if (isSuitable(element)) return element;
      element = element.getParent();
    }
    return null;
  }

  protected PsiFile getPsiFile() {
    return myPsiFile;
  }

  protected boolean isSuitable(final PsiElement element) {
    if (element == null) return false;
    final Class[] suitableClasses = getSuitableClasses();
    for (Class suitableClass : suitableClasses) {
      if (ReflectionCache.isAssignable(suitableClass, element.getClass())) return true;
    }
    return false;
  }

  public void addModelListener(ModelListener modelListener) {

  }

  public void removeModelListener(ModelListener modelListener) {

  }

  /**
   * Returns the list of PSI element classes which are shown as structure view elements.
   * When determining the current editor element, the PSI tree is walked up until an element
   * matching one of these classes is found.
   *
   * @return the list of classes
   */
  @NotNull protected Class[] getSuitableClasses() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public Grouper[] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @NotNull
  public Sorter[] getSorters() {
    return Sorter.EMPTY_ARRAY;
  }

  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }
}
