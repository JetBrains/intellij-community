// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Proof of concept to mix pushed properties to stub index composite indexer data.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface PushedFilePropertiesRetriever {
  static PushedFilePropertiesRetriever getInstance() {
    return ApplicationManager.getApplication().getService(PushedFilePropertiesRetriever.class);
  }

  @NotNull
  List<String> dumpSortedPushedProperties(@NotNull VirtualFile file);
}
