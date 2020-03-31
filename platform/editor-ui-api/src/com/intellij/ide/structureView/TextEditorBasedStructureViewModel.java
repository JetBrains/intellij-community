// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final List<ModelListener> myModelListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final CaretListener myEditorCaretListener;
  private Disposable myEditorCaretListenerDisposable;

  /**
   * Creates a structure view model instance linked to a text editor displaying the specified
   * file.
   *
   * @param psiFile the file for which the structure view model is requested.
   */
  protected TextEditorBasedStructureViewModel(@NotNull PsiFile psiFile) {
    this(PsiEditorUtil.findEditor(psiFile), psiFile);
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

    myEditorCaretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
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

    PsiFile file = getPsiFile();
    if (!file.isValid()) return null;

    int offset = myEditor.getCaretModel().getOffset();
    Object o1 = findAcceptableElement(file.getViewProvider().findElementAt(offset, file.getLanguage()));
    Object o2 = offset == 0 ? o1 : findAcceptableElement(file.getViewProvider().findElementAt(offset - 1, file.getLanguage()));
    if (o1 != o2 && o1 instanceof PsiElement && o2 instanceof PsiElement) {
      if (PsiTreeUtil.isAncestor((PsiElement)o1, (PsiElement)o2, false)) return o2;
    }
    return o1;
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
  protected Class @NotNull [] getSuitableClasses() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  @Override
  public Grouper @NotNull [] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return Sorter.EMPTY_ARRAY;
  }

  @Override
  public Filter @NotNull [] getFilters() {
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
