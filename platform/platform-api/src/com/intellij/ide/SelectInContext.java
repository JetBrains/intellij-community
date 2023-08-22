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
   * Tries to select the object determined by this context in a given target.
   *
   * @param target       an object that supports a context selection
   * @param requestFocus specifies whether a focus request is needed or not
   * @return {@code true} if a selection request is approved and executed by the given target
   * @deprecated This method calls both {@link SelectInTarget#canSelect(SelectInContext)}
   * and {@link SelectInTarget#selectIn(SelectInContext, boolean)}. However, the former should
   * be called in a read action and not in the EDT (because it may involve some slow ops),
   * and the latter should be called in the EDT. Avoid using this method and instead call
   * the two separately (if the return value is needed) or just call {@code target.selectIn}
   * (otherwise).
   */
  @Deprecated
  default boolean selectIn(@NotNull SelectInTarget target, boolean requestFocus) {
    if (!target.canSelect(this)) return false;
    target.selectIn(this, requestFocus);
    return true;
  }
}