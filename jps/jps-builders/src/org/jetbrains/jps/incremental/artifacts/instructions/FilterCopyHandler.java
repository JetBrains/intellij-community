// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileFilters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Sergey Evdokimov
 */
public class FilterCopyHandler extends FileCopyingHandler {
  public static final FileCopyingHandler DEFAULT = new FilterCopyHandler(FileFilters.EVERYTHING);

  private final FileFilter myFilter;

  public FilterCopyHandler(@NotNull FileFilter filter) {
    myFilter = filter;
  }

  @Override
  public void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException {
    FSOperations.copy(from, to);
  }

  @Override
  public void writeConfiguration(@NotNull PrintWriter out) { }

  @Override
  public @NotNull FileFilter createFileFilter() {
    return myFilter;
  }
}
