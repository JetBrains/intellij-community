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

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class UrlFilter implements Filter, DumbAware {
  private final Project myProject;

  public UrlFilter() {
    this(null);
  }

  public UrlFilter(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Result applyFilter(@NotNull String line, int entireLength) {
    if (!URLUtil.canContainUrl(line)) return null;

    int textStartOffset = entireLength - line.length();
    Pattern pattern = line.contains(LocalFileSystem.PROTOCOL_PREFIX) ? URLUtil.FILE_URL_PATTERN : URLUtil.URL_PATTERN;
    Matcher m = pattern.matcher(line);
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
    HyperlinkInfo fileHyperlinkInfo = buildFileHyperlinkInfo(url);
    return fileHyperlinkInfo != null ? fileHyperlinkInfo : new BrowserHyperlinkInfo(url);
  }

  @Nullable
  private HyperlinkInfo buildFileHyperlinkInfo(@NotNull String url) {
    if (myProject != null && url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      int documentLine = 0, documentColumn = 0;
      int filePathEndIndex = url.length();
      final int lastColonInd = url.lastIndexOf(':');
      if (lastColonInd > LocalFileSystem.PROTOCOL_PREFIX.length() && lastColonInd < url.length() - 1) {
        int lastValue = StringUtil.parseInt(url.substring(lastColonInd + 1), Integer.MIN_VALUE);
        if (lastValue != Integer.MIN_VALUE) {
          documentLine = lastValue - 1;
          filePathEndIndex = lastColonInd;
          int preLastColonInd = url.lastIndexOf(':', lastColonInd - 1);
          if (preLastColonInd > LocalFileSystem.PROTOCOL_PREFIX.length()) {
            int preLastValue = StringUtil.parseInt(url.substring(preLastColonInd + 1, lastColonInd), Integer.MIN_VALUE);
            if (preLastValue != Integer.MIN_VALUE) {
              documentLine = preLastValue - 1;
              documentColumn = lastValue - 1;
              filePathEndIndex = preLastColonInd;
            }
          }
        }
      }
      String filePath = url.substring(LocalFileSystem.PROTOCOL_PREFIX.length(), filePathEndIndex);
      return new LazyFileHyperlinkInfo(myProject, filePath, documentLine, documentColumn) {
        @Override
        public void navigate(@NotNull Project project) {
          VirtualFile file = getVirtualFile();
          if (file == null || !file.isValid()) {
            Messages.showErrorDialog(project, ExecutionBundle.message("message.cannot.find.file.0", StringUtil.trimMiddle(url, 150)),
                                     IdeBundle.message("title.cannot.open.file"));
            return;
          }
          super.navigate(project);
        }
      };
    }
    return null;
  }

  public static class UrlFilterProvider implements ConsoleFilterProviderEx {
    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project, @NotNull GlobalSearchScope scope) {
      return new Filter[]{new UrlFilter(project)};
    }

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
      return getDefaultFilters(project, GlobalSearchScope.allScope(project));
    }
  }
}
