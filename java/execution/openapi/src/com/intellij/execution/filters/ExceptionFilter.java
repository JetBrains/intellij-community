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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExceptionFilter implements Filter, DumbAware {
  private final ExceptionInfoCache myCache;
  private PsiElementFilter myNextLineRefiner;

  private static final Pattern EXCEPTION_PATTERN = Pattern.compile("(Exception in thread \".+\" |Caused by: |)(\\w+\\.[\\w.]+)(:.*)?");

  public ExceptionFilter(@NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(scope);
  }

  @Override
  public Result applyFilter(final String line, final int textEndOffset) {
    ExceptionWorker worker = new ExceptionWorker(myCache);
    Result result = worker.execute(line, textEndOffset, myNextLineRefiner);
    myNextLineRefiner = result == null ? getRefinerFromException(line) : worker.getLocationRefiner();
    return result;
  }

  private static PsiElementFilter getRefinerFromException(String line) {
    Matcher matcher = EXCEPTION_PATTERN.matcher(line.trim());
    if(!matcher.matches()) return null;
    String exceptionName = matcher.group(2);
    PsiElementFilter throwFilter = e -> {
      if (!(e instanceof PsiKeyword) || !(e.textMatches(PsiKeyword.THROW))) return false;
      PsiThrowStatement parent = ObjectUtils.tryCast(e.getParent(), PsiThrowStatement.class);
      if (parent == null) return false;
      PsiExpression exception = PsiUtil.skipParenthesizedExprDown(parent.getException());
      if (exception == null) return false;
      PsiType type = exception.getType();
      if (type == null) return false;
      return exception instanceof PsiNewExpression ? type.equalsToText(exceptionName) : InheritanceUtil.isInheritor(type, exceptionName);
    };
    if ("java.lang.ArrayIndexOutOfBoundsException".equals(exceptionName)) {
      return element -> throwFilter.isAccepted(element) ||
                        (element instanceof PsiJavaToken &&
                         element.textMatches("[") &&
                         element.getParent() instanceof PsiArrayAccessExpression);
    }
    return throwFilter;
  }
}
