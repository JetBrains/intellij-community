// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.filters.Filter.ResultItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Filters occurrence of JVM exception (e.g. provide link to source code)
 */
@ApiStatus.Experimental
public interface JvmExceptionOccurrenceFilter {
  ExtensionPointName<JvmExceptionOccurrenceFilter> EP_NAME = ExtensionPointName.create("com.intellij.jvm.exceptionFilter");

  /**
   * @param exceptionClassName   exception class name that occurs in the log
   * @param classes              non-empty list of resolved class candidates
   * @param exceptionStartOffset exception class name start offset in the log
   * @return new filtering result item or null if nothing should be returned
   */
  @Nullable ResultItem applyFilter(@NotNull String exceptionClassName,
                                   @NotNull List<PsiClass> classes,
                                   int exceptionStartOffset);
}
