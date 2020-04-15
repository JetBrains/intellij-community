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
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class ExceptionFilter implements Filter, DumbAware {
  private final ExceptionInfoCache myCache;
  private Predicate<PsiElement> myNextLineRefiner;

  public ExceptionFilter(@NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(scope);
  }

  @Override
  public Result applyFilter(@NotNull final String line, final int textEndOffset) {
    ExceptionWorker worker = new ExceptionWorker(myCache);
    Result result = worker.execute(line, textEndOffset, myNextLineRefiner);
    if (result == null) {
      ExceptionInfo exceptionInfo = ExceptionInfo.parseMessage(line);
      if (exceptionInfo == null) return null;
      myNextLineRefiner = exceptionInfo.getPositionRefiner();
      return exceptionInfo.makeClassLink(myCache, textEndOffset - line.length());
    }
    myNextLineRefiner = worker.getLocationRefiner();
    return result;
  }
}
