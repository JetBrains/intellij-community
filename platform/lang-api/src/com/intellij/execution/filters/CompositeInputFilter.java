// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CompositeInputFilter implements InputFilter {
  private static final Logger LOG = Logger.getInstance(CompositeInputFilter.class);

  private final @NotNull InputFilterWrapper @NotNull [] myFilters;
  private final DumbService myDumbService;

  public CompositeInputFilter(@NotNull Project project, @NotNull Collection<? extends InputFilter> allFilters) {
    myDumbService = DumbService.getInstance(project);
    myFilters = ContainerUtil.map2Array(allFilters, new InputFilterWrapper[0], filter -> new InputFilterWrapper(filter));
  }

  @Override
  @Nullable
  public List<Pair<String, ConsoleViewContentType>> applyFilter(@NotNull final String text, @NotNull final ConsoleViewContentType contentType) {
    boolean dumb = myDumbService.isDumb();
    for (InputFilterWrapper filter : myFilters) {
      if (!dumb || filter.isDumbAware) {
        long t0 = System.currentTimeMillis();
        List<Pair<String, ConsoleViewContentType>> result = filter.applyFilter(text, contentType);
        t0 = System.currentTimeMillis() - t0;
        if (t0 > 100) {
          LOG.warn(filter.getClass().getSimpleName() + ".applyFilter() took " + t0 + " ms on '''" + text + "'''");
        }
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private static class InputFilterWrapper implements InputFilter {
    @NotNull private final InputFilter myOriginal;
    private boolean isBroken;
    private final boolean isDumbAware;

    InputFilterWrapper(@NotNull InputFilter original) {
      isDumbAware = DumbService.isDumbAware(original);
      myOriginal = original;
    }

    @Nullable
    @Override
    public List<Pair<String, ConsoleViewContentType>> applyFilter(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
      if (!isBroken) {
        try {
          return myOriginal.applyFilter(text, contentType);
        }
        catch (ProcessCanceledException ignored) {
          ProgressManager.checkCanceled();
        }
        catch (Throwable e) {
          isBroken = true;
          LOG.error(e);
        }
      }
      return null;
    }
  }
}
