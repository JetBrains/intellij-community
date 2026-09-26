// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ExceptionFilter implements Filter, DumbAware {
  final ExceptionInfoCache myCache;
  private final ExceptionLineParserFactory myFactory = ExceptionLineParserFactory.getInstance();
  private ExceptionLineRefiner myNextLineRefiner;

  public ExceptionFilter(@NotNull GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(Objects.requireNonNull(scope.getProject()), scope);
  }

  public ExceptionFilter(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(project, scope);
  }

  @Override
  public Result applyFilter(@NotNull String line, int textEndOffset) {
    ExceptionLineParser worker = myFactory.create(myCache);
    Result result = worker.execute(line, textEndOffset, myNextLineRefiner);
    if (result == null) {
      ExceptionLineRefiner refiner = myNextLineRefiner;
      if (refiner != null) {
        myNextLineRefiner = refiner.consumeNextLine(line);
        if (myNextLineRefiner != null) return null;
      }
      ExceptionInfo exceptionInfo = ExceptionInfo.parseMessage(line, textEndOffset);
      myNextLineRefiner = exceptionInfo == null ? null : exceptionInfo.getPositionRefiner();
      return null;
    }
    ExceptionInfo prevLineException = myNextLineRefiner == null ? null : myNextLineRefiner.getExceptionInfo();
    ExceptionLineRefiner nextRefiner = null;
    ExceptionLineRefiner refiner = myNextLineRefiner;
    if (refiner != null) {
      nextRefiner = refiner.consumeNextLine(line);
    }
    if (nextRefiner == null) {
      myNextLineRefiner = worker.getLocationRefiner();
    }
    else {
      myNextLineRefiner = nextRefiner;
    }
    if (prevLineException != null) {
      List<ResultItem> exceptionResults = getExceptionClassNameItems(prevLineException);
      if (!exceptionResults.isEmpty()) {
        exceptionResults.add(result);
        return new Result(exceptionResults);
      }
    }
    return result;
  }

  @ApiStatus.Internal
  public @Nullable ExceptionLineRefiner getLocationRefiner() {
    return myNextLineRefiner;
  }

  @NotNull
  List<ResultItem> getExceptionClassNameItems(ExceptionInfo prevLineException) {
    return Collections.emptyList();
  }
}
