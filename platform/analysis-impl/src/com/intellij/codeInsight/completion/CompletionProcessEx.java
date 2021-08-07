// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;


@ApiStatus.Internal
public interface CompletionProcessEx extends CompletionProcessBase {
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

  void registerChildDisposable(@NotNull Supplier<? extends Disposable> child);

  void itemSelected(LookupElement item, char aChar);


  void addAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String message, @Nullable Icon icon);

  CompletionParameters getParameters();

  void setParameters(@NotNull CompletionParameters parameters);

  void scheduleRestart();

  void prefixUpdated();
}
