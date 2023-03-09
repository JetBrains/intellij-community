// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.TreeModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the model for the data displayed in the standard structure view or file structure
 * popup component. The model of the standard structure view is represented as a tree of elements.
 * Use {@link ElementInfoProvider} and {@link ExpandInfoProvider} to control tree expansion behavior.
 *
 * @see TreeBasedStructureViewBuilder#createStructureViewModel(Editor)
 * @see TextEditorBasedStructureViewModel
 */
public interface StructureViewModel extends TreeModel, Disposable {
  /**
   * Returns the element currently selected in the editor linked to the structure view.
   *
   * @return the selected element, or null if the current editor position does not
   * correspond to any element that can be shown in the structure view.
   */
  @Nullable
  Object getCurrentEditorElement();

  /**
   * Adds a listener which gets notified when the selection in the editor linked to the
   * structure view moves to a different element visible in the structure view.
   *
   * @param listener the listener to add.
   */
  void addEditorPositionListener(@NotNull FileEditorPositionListener listener);

  /**
   * Removes a listener which gets notified when the selection in the editor linked to the
   * structure view moves to a different element visible in the structure view.
   *
   * @param listener the listener to remove.
   */
  void removeEditorPositionListener(@NotNull FileEditorPositionListener listener);

  /**
   * Adds a listener which gets notified when the data represented by the structure view
   * is changed and the structure view needs to be rebuilt.
   *
   * @param modelListener the listener to add.
   */
  void addModelListener(@NotNull ModelListener modelListener);

  /**
   * Removes a listener which gets notified when the data represented by the structure view
   * is changed and the structure view needs to be rebuilt.
   *
   * @param modelListener the listener to remove.
   */
  void removeModelListener(@NotNull ModelListener modelListener);

  /**
   * Returns the root element of the structure view tree.
   *
   * @return the structure view root.
   */
  @Override
  @NotNull
  StructureViewTreeElement getRoot();

  /**
   * Disposes of the model.
   */
  @Override
  void dispose();

  boolean shouldEnterElement(Object element);

  /**
   * @return status of element, whether it's changed or not. May affect the presentation
   */
  default @NotNull FileStatus getElementStatus(Object element) {
    return FileStatus.NOT_CHANGED;
  }

  interface ElementInfoProvider extends StructureViewModel {
    boolean isAlwaysShowsPlus(StructureViewTreeElement element);

    boolean isAlwaysLeaf(StructureViewTreeElement element);
  }

  interface ExpandInfoProvider {
    boolean isAutoExpand(@NotNull StructureViewTreeElement element);

    boolean isSmartExpand();

    /**
     * @return number of levels that would be always expanded in structure view.
     * Returns 2 by default: root node and its immediate children.
     * @apiNote Be careful with using this method because this approach is planned to be rewritten.
     */
    @ApiStatus.Experimental
    default int getMinimumAutoExpandDepth() {
      return 2;
    }
  }
}
