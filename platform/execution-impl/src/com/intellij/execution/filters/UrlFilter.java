// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtilRt;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class UrlFilter implements Filter, DumbAware {
  private final Project myProject;

  public UrlFilter() {
    this(null);
  }

  public UrlFilter(Project project) {
    myProject = project;
  }

  private List<ResultItem> findMatchingItems(String line, Pattern pattern, int textStartOffset) {
    List<ResultItem> resultList = new ArrayList<>();
    Matcher matcher = pattern.matcher(line);
    while (matcher.find()) {
      resultList.add(
        new ResultItem(textStartOffset + matcher.start(), textStartOffset + matcher.end(), buildHyperlinkInfo(matcher.group())));
    }
    return resultList;
  }

  @Override
  public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
    if (!URLUtil.canContainUrl(line)) return null;

    int textStartOffset = entireLength - line.length();
    List<ResultItem> resultList = new ArrayList<>();

    if (line.contains(LocalFileSystem.PROTOCOL_PREFIX)) {
      resultList.addAll(findMatchingItems(line, URLUtil.FILE_URL_PATTERN, textStartOffset));
    }

    if (isPotentialUrl(line)) {
      resultList.addAll(findMatchingItems(line, URLUtil.URL_PATTERN, textStartOffset));
    }

    if (resultList.isEmpty()) {
      return null;
    }

    if (resultList.size() == 1) {
      ResultItem singleItem = resultList.get(0);
      return new Result(singleItem.getHighlightStartOffset(), singleItem.getHighlightEndOffset(), singleItem.getHyperlinkInfo());
    }

    return new Result(resultList);
  }

  private static boolean isPotentialUrl(String input) {
    return input.contains("www") ||
           input.contains("http") ||
           input.contains("mailto") ||
           input.contains("ftp") ||
           input.contains("news");
  }

  protected @NotNull HyperlinkInfo buildHyperlinkInfo(@NotNull String url) {
    HyperlinkInfo fileHyperlinkInfo = buildFileHyperlinkInfo(url);
    return fileHyperlinkInfo != null ? fileHyperlinkInfo : new OpenUrlHyperlinkInfo(url);
  }

  private @Nullable HyperlinkInfo buildFileHyperlinkInfo(@NotNull String url) {
    if (myProject != null && !url.endsWith(".html") && url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      int documentLine = -1, documentColumn = -1;
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
      String filePath = toWindowsPath(decode(url.substring(LocalFileSystem.PROTOCOL_PREFIX.length(), filePathEndIndex)));
      return new FileUrlHyperlinkInfo(myProject, filePath, documentLine, documentColumn, url, true);
    }
    return null;
  }

  private static @NotNull String toWindowsPath(@NotNull String path) {
    if (path.length() >= 4 && path.charAt(0) == '/' && OSAgnosticPathUtil.isDriveLetter(path.charAt(1)) &&
        path.charAt(2) == ':' && PathUtilRt.isSeparator(path.charAt(3))) {
      return path.substring(1);
    }
    return path;
  }

  private static @NotNull String decode(@NotNull String encodedStr) {
    try {
      return URLDecoder.decode(encodedStr, StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e) {
      return encodedStr;
    }
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

  public static final class FileUrlHyperlinkInfo extends LazyFileHyperlinkInfo implements HyperlinkWithPopupMenuInfo {
    private final @NotNull String myUrl;
    final String myFilePath;
    final int myDocumentLine;
    final int myDocumentColumn;

    public FileUrlHyperlinkInfo(@NotNull Project project,
                                @NotNull String filePath,
                                int documentLine,
                                int documentColumn,
                                @NotNull String url,
                                boolean useBrowser) {
      super(project, filePath, documentLine, documentColumn, useBrowser);
      myUrl = url;
      myFilePath = filePath;
      myDocumentLine = documentLine;
      myDocumentColumn = documentColumn;
    }

    @Override
    public void navigate(@NotNull Project project) {
      VirtualFile file = getVirtualFile();
      if (file == null || !file.isValid()) {
        Messages.showErrorDialog(project, ExecutionBundle.message("message.cannot.find.file.0", StringUtil.trimMiddle(myUrl, 150)),
                                 IdeBundle.message("title.cannot.open.file"));
        return;
      }
      super.navigate(project);
    }

    @Override
    public @Nullable ActionGroup getPopupMenuGroup(@NotNull MouseEvent event) {
      return new OpenUrlHyperlinkInfo(myUrl).getPopupMenuGroup(event);
    }
  }
}
