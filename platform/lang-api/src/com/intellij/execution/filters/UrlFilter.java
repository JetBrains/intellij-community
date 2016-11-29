/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class UrlFilter implements Filter {
  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    if (!URLUtil.canContainUrl(line)) return null;

    int textStartOffset = entireLength - line.length();
    Matcher m = URLUtil.URL_PATTERN.matcher(line);
    ResultItem item = null;
    List<ResultItem> items = null;
    while (m.find()) {
      if (item == null) {
        item = new ResultItem(textStartOffset + m.start(), textStartOffset + m.end(), buildHyperlinkInfo(m.group()));
      } else {
        if (items == null) {
          items = new ArrayList<>(2);
          items.add(item);
        }
        items.add(new ResultItem(textStartOffset + m.start(), textStartOffset + m.end(), buildHyperlinkInfo(m.group())));
      }
    }
    return items != null ? new Result(items)
                         : item != null ? new Result(item.getHighlightStartOffset(), item.getHighlightEndOffset(), item.getHyperlinkInfo())
                                        : null;
  }

  @NotNull
  protected HyperlinkInfo buildHyperlinkInfo(@NotNull String url) {
    return new BrowserHyperlinkInfo(url);
  }

  public static class UrlFilterProvider implements ConsoleFilterProviderEx {
    @Override
    public Filter[] getDefaultFilters(@NotNull Project project, @NotNull GlobalSearchScope scope) {
      return new Filter[]{new UrlFilter()};
    }

    @NotNull
    @Override
    public Filter[] getDefaultFilters(@NotNull Project project) {
      return getDefaultFilters(project, GlobalSearchScope.allScope(project));
    }
  }
}
