/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The standard {@link StructureViewModel} implementation which is linked to a text editor.
 *
 * @see TreeBasedStructureViewBuilder#createStructureViewModel(Editor editor)
 */

public abstract class TextEditorBasedStructureViewModel implements StructureViewModel, ProvidingTreeModel {
  private final Editor myEditor;
  private final PsiFile myPsiFile;
  private final List<FileEditorPositionListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private List<ModelListener> myModelListeners = new ArrayList<>(2);
  private CaretAdapter myEditorCaretListener;
  private Disposable myEditorCaretListenerDisposable;

  /**
   * Creates a structure view model instance linked to a text editor displaying the specified
   * file.
   *
   * @param psiFile the file for which the structure view model is requested.
   */
  protected TextEditorBasedStructureViewModel(@NotNull PsiFile psiFile) {
    this(PsiEditorUtil.Service.getInstance().findEditorByPsiElement(psiFile), psiFile);
  }

  /**
   * Creates a structure view model instance linked to the specified text editor.
   *
   * @param editor the editor for which the structure view model is requested.
   */
  protected TextEditorBasedStructureViewModel(final Editor editor) {
    this(editor, null);
  }

  protected TextEditorBasedStructureViewModel(Editor editor, PsiFile file) {
    myEditor = editor;
    myPsiFile = file;

    myEditorCaretListener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        if (e.getEditor().equals(myEditor)) {
          for (FileEditorPositionListener listener : myListeners) {
            listener.onCurrentElementChanged();
          }
        }
      }
    };
  }

  @Override
  public final void addEditorPositionListener(@NotNull FileEditorPositionListener listener) {
    if (myEditor != null && myListeners.isEmpty()) {
      myEditorCaretListenerDisposable = Disposer.newDisposable();
      EditorFactory.getInstance().getEventMulticaster().addCaretListener(myEditorCaretListener, myEditorCaretListenerDisposable);
    }
    myListeners.add(listener);
  }

  @Override
  public final void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) {
    myListeners.remove(listener);
    if (myEditor != null && myListeners.isEmpty()) {
      Disposer.dispose(myEditorCaretListenerDisposable);
      myEditorCaretListenerDisposable = null;
    }
  }

  @Override
  public void dispose() {
    if (myEditorCaretListenerDisposable != null) {
      Disposer.dispose(myEditorCaretListenerDisposable);
    }
    myModelListeners.clear();
  }

  public void fireModelUpdate() {
    for (ModelListener listener : myModelListeners) {
      listener.onModelChanged();
    }
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  @Override
  public Object getCurrentEditorElement() {
    if (myEditor == null) return null;
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiFile file = getPsiFile();

    return findAcceptableElement(file.getViewProvider().findElementAt(offset, file.getLanguage()));
  }

  @Nullable
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
      if (ReflectionUtil.isAssignable(suitableClass, element.getClass())) return true;
    }
    return false;
  }

  @Override
  public void addModelListener(@NotNull ModelListener modelListener) {
    myModelListeners.add(modelListener);
  }

  @Override
  public void removeModelListener(@NotNull ModelListener modelListener) {
    myModelListeners.remove(modelListener);
  }

  /**
   * Returns the list of PSI element classes which are shown as structure view elements.
   * When determining the current editor element, the PSI tree is walked up until an element
   * matching one of these classes is found.
   *
   * @return the list of classes
   */
  @NotNull
  protected Class[] getSuitableClasses() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  @Override
  @NotNull
  public Grouper[] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public Sorter[] getSorters() {
    return Sorter.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Collection<NodeProvider> getNodeProviders() {
    return Collections.emptyList();
  }

  @Override
  public boolean isEnabled(@NotNull NodeProvider provider) {
    return false;
  }
}
