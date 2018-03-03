/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface EditorTracker {
  static EditorTracker getInstance(Project project) {
    return project.getComponent(EditorTracker.class);
  }

  /**
   * Returns the list of editors for which daemon should run
   */
  @NotNull
  List<Editor> getActiveEditors();

  void addEditorTrackerListener(@NotNull EditorTrackerListener listener, @NotNull Disposable parentDisposable);
}
