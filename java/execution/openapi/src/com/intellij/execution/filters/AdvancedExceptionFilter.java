// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This filter includes decorations not only for stack-trace lines, but also for exception names
 */
class AdvancedExceptionFilter extends ExceptionFilter {
  AdvancedExceptionFilter(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    super(project, scope);
  }

  @Override
  @NotNull List<ResultItem> getExceptionClassNameItems(ExceptionInfo prevLineException) {
    ExceptionInfoCache.ClassResolveInfo info = myCache.resolveClass(prevLineException.getExceptionClassName());
    if (info.myClasses.isEmpty()) return Collections.emptyList();
    Project project = myCache.getProject();
    List<PsiClass> classMap;
    if (DumbService.isDumb(project)) {
      classMap = ContainerUtil.filterIsInstance(info.myClasses.values(), PsiClass.class);
    }
    else {
      classMap = info.getExceptionClasses();
    }
    if (classMap.isEmpty()) return Collections.emptyList();
    List<ResultItem> exceptionResults = new ArrayList<>();
    JvmExceptionOccurrenceFilter.EP_NAME.forEachExtensionSafe(filter -> {
      ResultItem res = filter.applyFilter(prevLineException.getExceptionClassName(), classMap, prevLineException.getClassNameOffset());
      ContainerUtil.addIfNotNull(exceptionResults, res);
    });
    return exceptionResults;
  }
}
