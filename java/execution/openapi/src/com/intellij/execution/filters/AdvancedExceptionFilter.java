// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This filter includes decorations not only for stack-trace lines, but also for exception names
 */
class AdvancedExceptionFilter extends ExceptionFilter {
  AdvancedExceptionFilter(@NotNull GlobalSearchScope scope) {
    super(scope);
  }

  @Override
  @NotNull List<ResultItem> getExceptionClassNameItems(ExceptionInfo prevLineException) {
    ExceptionInfoCache.ClassResolveInfo info = myCache.resolveClass(prevLineException.getExceptionClassName());
    List<PsiClass> classMap = new ArrayList<>();
    info.myClasses.forEach((key, value) -> {
      PsiClass psiClass = ObjectUtils.tryCast(value, PsiClass.class);
      if (psiClass != null &&
          (DumbService.isDumb(psiClass.getProject()) || InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE))) {
        classMap.add(psiClass);
      }
    });
    List<ResultItem> exceptionResults = new ArrayList<>();
    if (!classMap.isEmpty()) {
      JvmExceptionOccurrenceFilter.EP_NAME.forEachExtensionSafe(filter -> {
        ResultItem res = filter.applyFilter(prevLineException.getExceptionClassName(), classMap, prevLineException.getClassNameOffset());
        ContainerUtil.addIfNotNull(exceptionResults, res);
      });
    }
    return exceptionResults;
  }
}
