// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
