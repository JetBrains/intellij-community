// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import com.intellij.execution.filters.Filter;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Console filter that locates lines with [NullAway checker](https://github.com/uber/NullAway) warnings
/// and adds `add @SuppressWarning` inlay button at the end of those lines.
/// Each button action adds {@linkplain SuppressWarnings} annotation to the source code to silence relevant warning.
/// The exact location where annotation is added is calculated based on information from the console line: file-path, line number,
/// type of NullAway warning.
@NotNullByDefault
class NullAwayFilter implements Filter {
  @Override
  @RequiresReadLock
  public @Nullable Result applyFilter(String line, int entireLength) {
    var nullAwayProblem = NullAwayProblem.fromLogLine(line);
    if (nullAwayProblem == null) return null;
    var result = new Result(List.of(new NullAwayInlayProvider(entireLength - 1, entireLength - 1, nullAwayProblem)));
    result.setNextAction(NextAction.CONTINUE_FILTERING);
    return result;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
