// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import org.jetbrains.annotations.NotNull;

public final class PreviewEditorFoldingRegion implements Comparable<PreviewEditorFoldingRegion> {
  private final int myStartLine;
  private final int myEndLine;

  public PreviewEditorFoldingRegion(int startLine, int endLine) {
    myStartLine = startLine;
    myEndLine = endLine;
  }

  public int getStartLine() {
    return myStartLine;
  }

  public int getEndLine() {
    return myEndLine;
  }

  public boolean contain(int position) {
    return myStartLine <= position && myEndLine > position;
  }

  @Override
  public int compareTo(@NotNull PreviewEditorFoldingRegion o) {
    return Integer.compare(myStartLine, o.myStartLine);
  }
}
