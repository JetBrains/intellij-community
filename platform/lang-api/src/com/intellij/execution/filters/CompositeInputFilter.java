/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CompositeInputFilter implements InputFilter {
  private static final Logger LOG = Logger.getInstance(CompositeInputFilter.class);

  private final List<Pair<InputFilter, Boolean /* is dumb aware */>> myFilters = ContainerUtilRt.newArrayList();
  private final DumbService myDumbService;

  public CompositeInputFilter(@NotNull Project project) {
    myDumbService = DumbService.getInstance(project);
  }

  @Override
  @Nullable
  public List<Pair<String, ConsoleViewContentType>> applyFilter(final String text, final ConsoleViewContentType contentType) {
    boolean dumb = myDumbService.isDumb();
    for (Pair<InputFilter, Boolean> pair : myFilters) {
      if (!dumb || pair.second == Boolean.TRUE) {
        long t0 = System.currentTimeMillis();
        InputFilter filter = pair.first;
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

  public void addFilter(@NotNull final InputFilter filter) {
    InputFilter wrapper = new InputFilter() {
      boolean isBroken;

      @Nullable
      @Override
      public List<Pair<String, ConsoleViewContentType>> applyFilter(String text, ConsoleViewContentType contentType) {
        if (!isBroken) {
          try {
            return filter.applyFilter(text, contentType);
          }
          catch (Throwable e) {
            isBroken = true;
            LOG.error(e);
          }
        }
        return null;
      }
    };
    myFilters.add(Pair.create(wrapper, DumbService.isDumbAware(filter)));
  }
}
