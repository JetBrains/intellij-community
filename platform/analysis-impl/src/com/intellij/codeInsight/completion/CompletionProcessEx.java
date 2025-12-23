// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;


@ApiStatus.Internal
public interface CompletionProcessEx extends CompletionProcessBase, UserDataHolderEx {
  @NotNull
  Project getProject();

  @NotNull
  Editor getEditor();

  @NotNull
  Caret getCaret();

  @NotNull
  OffsetMap getOffsetMap();

  @NotNull
  OffsetsInFile getHostOffsets();

  @Nullable
  Lookup getLookup();

  /**
   * Allows registering a child disposable that will be disposed of when the completion process finishes.
   * {@code child} is called only if the completion process is not already finished and disposed.
   */
  void registerChildDisposable(@NotNull Supplier<? extends Disposable> child);

  /**
   * called when an item is selected in the lookup or lookup is closed.
   *
   * @param item           selected item or null if lookup was closed, or no item was selected, or the item is invalid, etc.
   * @param completionChar completion char
   */
  void itemSelected(@Nullable LookupElement item, char completionChar);

  void addAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String message, @Nullable Icon icon);

  /** Not null after initialization is finished */
  CompletionParameters getParameters();

  void setParameters(@NotNull CompletionParameters parameters);

  @RequiresEdt
  void scheduleRestart();

  void prefixUpdated();
}
