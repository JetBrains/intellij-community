// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.tree.LeafState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractTreeStructure {
  @NotNull
  public abstract Object getRootElement();
  public abstract Object @NotNull [] getChildElements(@NotNull Object element);
  @Nullable
  public abstract Object getParentElement(@NotNull Object element);

  @NotNull
  public abstract NodeDescriptor createDescriptor(@NotNull Object element, @Nullable NodeDescriptor parentDescriptor);

  public abstract void commit();
  public abstract boolean hasSomethingToCommit();

  @NotNull
  public static ActionCallback asyncCommitDocuments(@NotNull Project project) {
    if (project.isDisposed()) return ActionCallback.DONE;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (!documentManager.hasEventSystemEnabledUncommittedDocuments()) {
      return ActionCallback.DONE;
    }
    final ActionCallback callback = new ActionCallback();
    documentManager.performWhenAllCommitted(callback.createSetDoneRunnable());
    return callback;
  }

  /**
   * @return callback which is set to {@link ActionCallback#setDone()} when the tree structure is committed.
   * By default it just calls {@link #commit()} synchronously but it is desirable to override it
   * to provide asynchronous commit to the tree structure to make it more responsible.
   * E.g. when you should commit all documents during the {@link #commit()},
   * you can use {@link #asyncCommitDocuments(Project)} to do it asynchronously.
   */
  @NotNull
  public ActionCallback asyncCommit() {
    if (hasSomethingToCommit()) commit();
    return ActionCallback.DONE;
  }

  public boolean isToBuildChildrenInBackground(@NotNull Object element){
    return false;
  }
  
  public boolean isValid(@NotNull Object element) {
    return true;
  }

  /**
   * @param element an object that represents a node in this tree structure
   * @return a leaf state for the given element
   * @see LeafState.Supplier#getLeafState()
   */
  @NotNull
  public LeafState getLeafState(@NotNull Object element) {
    return isAlwaysLeaf(element) ? LeafState.ALWAYS : LeafState.get(element);
  }

  public boolean isAlwaysLeaf(@NotNull Object element) {
    return false;
  }

  @NotNull
  public AsyncResult<Object> revalidateElement(@NotNull Object element) {
    return AsyncResult.done(element);
  }
}
