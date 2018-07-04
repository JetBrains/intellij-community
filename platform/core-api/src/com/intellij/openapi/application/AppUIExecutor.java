// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * An executor that invokes given runnables on Swing Event Dispatch thread when all constraints of a given set are satisfied at the same time.
 * The executor is created by calling {@link #onUiThread}, the constraints are specified by chained calls. For example, to invoke
 * some action when all documents are committed and indices are available, one can use
 * {@code AppUIExecutor.onUiThread().withDocumentsCommitted(project).inSmartMode(project)}.
 */
public interface AppUIExecutor extends Executor {

  /**
   * Creates an executor working with the given modality state.
   * @see ModalityState
   */
  @NotNull
  static AppUIExecutor onUiThread(@NotNull ModalityState modality) {
    return AsyncExecutionService.getService().createUIExecutor(modality);
  }

  /**
   * Creates an executor working with the default modality state.
   * @see ModalityState#defaultModalityState() 
   */
  @NotNull
  static AppUIExecutor onUiThread() {
    return onUiThread(ModalityState.defaultModalityState());
  }

  /**
   * @return an executor that should always invoke the given runnable later. Otherwise, if {@link #execute} is called
   * on dispatch thread already, and all others constraints are met, the runnable would be executed immediately.
   */
  @NotNull
  @Contract(pure=true)
  AppUIExecutor later();

  /**
   * @return an executor that invokes runnables only when all documents are committed. Automatically expires when the project is disposed.
   * @see PsiDocumentManager#hasUncommitedDocuments() 
   */
  @NotNull
  @Contract(pure=true)
  AppUIExecutor withDocumentsCommitted(@NotNull Project project);

  /**
   * @return an executor that invokes runnables only when indices have been built and are available to use. Automatically expires when the project is disposed.
   * @see com.intellij.openapi.project.DumbService#isDumb(Project) 
   */
  @NotNull
  @Contract(pure=true)
  AppUIExecutor inSmartMode(@NotNull Project project);

  /**
   * @return an executor that invokes runnables only in transaction. Automatically expires when {@code parentDisposable} is disposed.
   * @see TransactionGuard#submitTransaction(Disposable, Runnable) 
   */
  @NotNull
  @Contract(pure=true)
  AppUIExecutor inTransaction(@NotNull Disposable parentDisposable);

  /**
   * @return an executor that no longer invokes the given runnable after the supplied Disposable is disposed
   */
  @NotNull
  @Contract(pure=true)
  AppUIExecutor expireWith(@NotNull Disposable parentDisposable);

  /**
   * Schedule the given task's execution and return a Promise that allows to get the result when the task is complete,
   * or cancel the task if it's no longer needed.
   */
  <T> CancellablePromise<T> submit(Callable<T> task);

  /**
   * Schedule the given task's execution and return a Promise that allows to check if the task is complete,
   * or cancel the task if it's no longer needed.
   */
  CancellablePromise<?> submit(Runnable task);
  
}
