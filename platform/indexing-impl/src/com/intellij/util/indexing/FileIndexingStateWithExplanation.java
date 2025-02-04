// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus.Internal;

import static com.intellij.util.indexing.FileIndexingStateWithExplanation.FileIndexingState.NOT_INDEXED;
import static com.intellij.util.indexing.FileIndexingStateWithExplanation.FileIndexingState.UP_TO_DATE;

@Internal
public class FileIndexingStateWithExplanation {
  private static final FileIndexingStateWithExplanation NOT_INDEXED_WITHOUT_EXPLANATION = new FileIndexingStateWithExplanation(NOT_INDEXED);
  private static final FileIndexingStateWithExplanation UP_TO_DATE_WITHOUT_EXPLANATION = new FileIndexingStateWithExplanation(UP_TO_DATE);

  // TODO:
  public static final FileIndexingStateWithExplanation OUT_DATED = new FileIndexingStateWithExplanation(FileIndexingState.OUT_DATED);

  private final FileIndexingState state;

  public FileIndexingStateWithExplanation(FileIndexingState state) { this.state = state; }

  @Internal
  public enum FileIndexingState {
    NOT_INDEXED,
    OUT_DATED,
    UP_TO_DATE
  }

  public boolean updateRequired() {
    return state != UP_TO_DATE;
  }

  public boolean isUpToDate() {
    return state == UP_TO_DATE;
  }

  public boolean isNotIndexed() {
    return state == NOT_INDEXED;
  }

  public static FileIndexingStateWithExplanation notIndexed() {
    return NOT_INDEXED_WITHOUT_EXPLANATION;
  }

  public static FileIndexingStateWithExplanation upToDate() {
    return UP_TO_DATE_WITHOUT_EXPLANATION;
  }
}
