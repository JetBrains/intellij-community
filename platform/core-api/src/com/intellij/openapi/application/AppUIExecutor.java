// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * An executor that invokes given runnables under Write Intent lock on Swing Event Dispatch thread or Write Thread
 * when all constraints of a given set are satisfied at the same time.
 * The executor is created by calling {@link #onUiThread} (for EDT) or {@link #onWriteThread} (for WT),
 * the constraints are specified by chained calls. For example, to invoke
 * some action when all documents are committed and indices are available, one can use
 * {@code AppUIExecutor.onWriteThread().withDocumentsCommitted(project).inSmartMode(project)}.
 *
 * @deprecated Use coroutines and their cancellation mechanism instead.
 */
@Deprecated
public interface AppUIExecutor extends BaseExpirableExecutor<AppUIExecutor> {

  /**
   * Creates a EDT-based executor working with the given modality state.
   *
   * @see ModalityState
   * @deprecated Use {@link CoroutinesKt#getEDT} instead.
   */
  @Deprecated
  static @NotNull AppUIExecutor onUiThread(@NotNull ModalityState modality) {
    return AsyncExecutionService.getService().createUIExecutor(modality);
  }

  /**
   * Creates a Write-thread-based executor working with the given modality state.
   *
   * @see ModalityState
   * @deprecated Any thread can be a write thread. Use {@link WriteIntentReadAction#run} instead.
   */
  @Deprecated
  static @NotNull AppUIExecutor onWriteThread(@NotNull ModalityState modality) {
    return AsyncExecutionService.getService().createWriteThreadExecutor(modality);
  }

  /**
   * Creates a EDT-based executor working with the default modality state.
   *
   * @see ModalityState#defaultModalityState()
   * @deprecated Use {@link CoroutinesKt#getEDT} instead.
   */
  @Deprecated
  static @NotNull AppUIExecutor onUiThread() {
    return onUiThread(ModalityState.defaultModalityState());
  }

  /**
   * Creates a Write-thread-based executor working with the default modality state.
   *
   * @see ModalityState#defaultModalityState()
   * @deprecated Any thread can be a write thread. Use {@link WriteIntentReadAction#run} instead.
   */
  @Deprecated
  static @NotNull AppUIExecutor onWriteThread() {
    return onWriteThread(ModalityState.defaultModalityState());
  }

  /**
   * @return an executor that should always invoke the given runnable later. Otherwise, if {@link #execute} is called
   * on dispatch thread already, and all others constraints are met, the runnable would be executed immediately.
   * @deprecated this should be the default, but it's not, which makes the result effectively dependent on the current thread
   */
  @NotNull
  @Contract(pure = true)
  @Deprecated
  AppUIExecutor later();

  /**
   * @return an executor that invokes runnables only when all documents are committed. Automatically expires when the project is disposed.
   * @see PsiDocumentManager#hasUncommitedDocuments()
   * @deprecated this method implies read action in {@link #execute}.
   * Use {@link CoroutinesKt#constrainedReadAction} with {@link ReadConstraint.Companion#withDocumentsCommitted} instead.
   */
  @NotNull
  @Contract(pure = true)
  @ApiStatus.Internal
  @Deprecated
  AppUIExecutor withDocumentsCommitted(@NotNull Project project);

  /**
   * @return an executor that invokes runnables only when indices have been built and are available to use. Automatically expires when the project is disposed.
   * @see com.intellij.openapi.project.DumbService#isDumb(Project)
   * @deprecated this method implies read action in {@link #execute}.
   * Use {@link CoroutinesKt#smartReadAction} or {@link CoroutinesKt#constrainedReadAction} with {@link ReadConstraint.Companion#withDocumentsCommitted} instead.
   */
  @NotNull
  @Contract(pure = true)
  @Deprecated
  AppUIExecutor inSmartMode(@NotNull Project project);
}
