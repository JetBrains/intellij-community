// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

public interface HighlighterClient {
  Project getProject();

  void repaint(int start, int end);

  Document getDocument();
}
