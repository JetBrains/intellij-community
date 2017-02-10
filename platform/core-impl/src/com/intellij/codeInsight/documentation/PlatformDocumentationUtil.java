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

public class PlatformDocumentationUtil {
  private static final @NonNls Pattern ourLtFixupPattern = Pattern.compile("<(?=[^/!\\p{Alpha}])");
  private static final @NonNls String LT_ENTITY = "&lt;";

  @Nullable
  public static List<String> getHttpRoots(@NotNull String[] roots, String relPath) {
    List<String> result = new SmartList<String>();
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
