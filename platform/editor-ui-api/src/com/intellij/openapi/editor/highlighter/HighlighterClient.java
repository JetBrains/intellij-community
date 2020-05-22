// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

public interface HighlighterClient {
  Project getProject();

  /**
   * @deprecated use {@link #fireHighlighterChanged(int, int)}
   */
  @Deprecated
  default void repaint(int start, int end) {
    fireHighlighterChanged(start, end);
  }

  void fireHighlighterChanged(int start, int end);

  void addHighlighterClientListener(HighlighterClientListener listener, Disposable parentDisposable);

  Document getDocument();
}
