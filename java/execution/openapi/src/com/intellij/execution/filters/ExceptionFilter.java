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
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ExceptionFilter implements Filter, DumbAware {
  final ExceptionInfoCache myCache;
  private ExceptionLineRefiner myNextLineRefiner;

  public ExceptionFilter(@NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(Objects.requireNonNull(scope.getProject()), scope);
  }

  public ExceptionFilter(@NotNull Project project, @NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(project, scope);
  }

  @Override
  public Result applyFilter(@NotNull final String line, final int textEndOffset) {
    ExceptionWorker worker = new ExceptionWorker(myCache);
    Result result = worker.execute(line, textEndOffset, myNextLineRefiner);
    if (result == null) {
      if (myNextLineRefiner != null) {
        myNextLineRefiner = myNextLineRefiner.consumeNextLine(line);
        if (myNextLineRefiner != null) return null;
      }
      ExceptionInfo exceptionInfo = ExceptionInfo.parseMessage(line, textEndOffset);
      myNextLineRefiner = exceptionInfo == null ? null : exceptionInfo.getPositionRefiner();
      return null;
    }
    ExceptionInfo prevLineException = myNextLineRefiner == null ? null : myNextLineRefiner.getExceptionInfo();
    myNextLineRefiner = worker.getLocationRefiner();
    if (prevLineException != null) {
      List<ResultItem> exceptionResults = getExceptionClassNameItems(prevLineException);
      if (!exceptionResults.isEmpty()) {
        exceptionResults.add(result);
        return new Result(exceptionResults);
      }
    }
    return result;
  }

  @NotNull
  List<ResultItem> getExceptionClassNameItems(ExceptionInfo prevLineException) {
    return Collections.emptyList();
  }
}
