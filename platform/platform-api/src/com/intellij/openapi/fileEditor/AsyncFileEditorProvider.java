/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface AsyncFileEditorProvider extends FileEditorProvider {
  /**
   * This method is intended to be called from background thread. It should perform all time-consuming tasks required to build an editor,
   * and return a builder instance that will be called in EDT to create UI for the editor.
   * <p>
   * Currently, this method is called from a background thread only when editors are reopened on IDE startup. In other cases, it's still
   * invoked on EDT, so executing time-consuming tasks in it will block the UI.
   */
  @NotNull
  Builder createEditorAsync(@NotNull Project project, @NotNull VirtualFile file);

  abstract class Builder {
    public abstract FileEditor build();
  }
}
