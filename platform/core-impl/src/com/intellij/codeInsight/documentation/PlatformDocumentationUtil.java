// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public final class PlatformDocumentationUtil {
  private static final @NonNls Pattern ourLtFixupPattern = Pattern.compile("<(?=[^/!\\p{Alpha}])");
  private static final @NonNls String LT_ENTITY = "&lt;";

  @Nullable
  public static List<String> getHttpRoots(String @NotNull [] roots, String relPath) {
    List<String> result = new SmartList<>();
    for (String root : roots) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(root);
      if (virtualFile != null) {
        String url = getDocUrl(virtualFile, relPath);
        if (url != null) result.add(url);
      }
    }

    return result.isEmpty() ? null : result;
  }

  @Nullable
  public static String getDocUrl(@NotNull VirtualFile root, String relPath) {
    if (root.getFileSystem() instanceof HttpFileSystem) {
      String url = StringUtil.trimEnd(root.getUrl(), "/index.html", true);
      if (!url.endsWith("/")) {
        url += "/";
      }
      return url + relPath;
    }
    else {
      VirtualFile file = root.findFileByRelativePath(relPath);
      return file == null ? null : file.getUrl();
    }
  }

  /**
   * Updates HTML contents for display in JEditorPane, which treats invalid HTML somewhat differently than popular browsers.
   */
  public static String fixupText(@NotNull CharSequence docText) {
    return ourLtFixupPattern.matcher(docText).replaceAll(LT_ENTITY);
  }
}
