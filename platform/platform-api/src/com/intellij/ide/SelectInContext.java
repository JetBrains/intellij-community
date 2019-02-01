// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kir
 */
public interface SelectInContext {
  DataKey<SelectInContext> DATA_KEY = DataKey.create("SelectInContext");

  @NotNull
  Project getProject();

  @NotNull
  VirtualFile getVirtualFile();

  @Nullable
  Object getSelectorInFile();

  @Nullable
  FileEditorProvider getFileEditorProvider();

  /**
   * @param target       an object that supports a context selection
   * @param requestFocus specifies whether a focus request is needed or not
   * @return {@code true} if a selection request is approved and executed by the given target
   */
  default boolean selectIn(@NotNull SelectInTarget target, boolean requestFocus) {
    if (!target.canSelect(this)) return false;
    target.selectIn(this, requestFocus);
    return true;
  }
}