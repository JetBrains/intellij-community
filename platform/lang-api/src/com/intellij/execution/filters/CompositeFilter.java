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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;

public class CompositeFilter implements Filter {
  private final List<Filter> myFilters = new ArrayList<Filter>();
  private final Project myProject;

  public CompositeFilter(Project project) {
    myProject = project;
  }

  public Result applyFilter(final String line, final int entireLength) {
    final boolean dumb = DumbService.getInstance(myProject).isDumb();
    for (final Filter filter : myFilters) {
      if (!dumb || DumbService.isDumbAware(filter)) {
        final Result info = filter.applyFilter(line, entireLength);
        if (info != null) {
          return info;
        }
      }
    }
    return null;
  }

  public void addFilter(final Filter filter) {
    myFilters.add(filter);
  }
}