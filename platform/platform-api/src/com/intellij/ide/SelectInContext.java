/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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