// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface UnknownSdkFixAction {
  @NotNull @Nls String getActionShortText();

  @NotNull @Nls String getActionDetailedText();

  default @Nullable @Nls String getActionTooltipText() {
    return null;
  }

  /**
   * A suggestion can be using another already registered {@link Sdk} as prototype,
   * The callee may use this to avoid creating duplicates
   */
  @Nullable
  default Sdk getRegisteredSdkPrototype() {
    return null;
  }

  /**
   * Starts the fix action and forgets about it running.
   * The implementation is responsible to implement necessary
   * progress dialogs, invoke later calls and so on
   */
  void applySuggestionAsync(@Nullable Project project);

  /**
   * Applies suggestions under a given progress
   */
  @NotNull
  Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator);

  /**
   * Attaches a listener to the instance. Events are not fired before
   * {@link #applySuggestionAsync(Project)} or {@link #applySuggestionBlocking(ProgressIndicator)}
   * method is called.
   */
  void addSuggestionListener(@NotNull Listener listener);

  interface Listener extends EventListener {
    /**
     * This event can be called when a prototype SDK object is created,
     * but probably it is not yet added to the
     * {@link com.intellij.openapi.projectRoots.ProjectJdkTable}.
     */
    void onSdkNameResolved(@NotNull Sdk sdk);

    /**
     * One of the final events of the resolution. Is called when a given SDK
     * is fully ready and registered to the
     * {@link com.intellij.openapi.projectRoots.ProjectJdkTable}.
     * @see #onResolveFailed()
     */
    void onSdkResolved(@NotNull Sdk sdk);

    /**
     * One of the final events of the reoslution. It is caleld when a given SDK
     * failed to be resolved
     * @see #onSdkResolved(Sdk)
     */
    void onResolveFailed();
  }
}
