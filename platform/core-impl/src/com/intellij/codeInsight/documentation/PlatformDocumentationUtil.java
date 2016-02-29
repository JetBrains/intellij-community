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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PlatformDocumentationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.documentation.PlatformDocumentationUtil");

  private static final @NonNls Pattern ourLtFixupPattern = Pattern.compile("<([^/^\\w^!])");
  private static final @NonNls Pattern ourToQuote = Pattern.compile("[\\\\\\.\\^\\$\\?\\*\\+\\|\\)\\}\\]\\{\\(\\[]");
  private static final @NonNls String LT_ENTITY = "&lt;";

  @Nullable
  public static List<String> getHttpRoots(@NotNull String[] roots, String relPath) {
    List<String> result = new SmartList<String>();
    for (String root : roots) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(root);
      if (virtualFile != null) {
        if (virtualFile.getFileSystem() instanceof HttpFileSystem) {
          String url = virtualFile.getUrl();
          if (url.toLowerCase(Locale.getDefault()).endsWith("/index.html")) {
            url = url.substring(0, url.length() - 10);
          }
          else if (!url.endsWith("/")) {
            url += "/";
          }
          result.add(url + relPath);
        }
        else {
          VirtualFile file = virtualFile.findFileByRelativePath(relPath);
          if (file != null) {
            result.add(file.getUrl());
          }
        }
      }
    }

    return result.isEmpty() ? null : result;
  }

  private static String quote(String x) {
    if (ourToQuote.matcher(x).find()) {
      return "\\" + x;
    }

    return x;
  }

  public static String fixupText(@NotNull CharSequence docText) {
    Matcher fixupMatcher = ourLtFixupPattern.matcher(docText);
    LinkedList<String> secondSymbols = new LinkedList<String>();

    while (fixupMatcher.find()) {
      String s = fixupMatcher.group(1);

      //[db] that's workaround to avoid internal bug
      if (!s.equals("\\") && !secondSymbols.contains(s)) {
        secondSymbols.addFirst(s);
      }
    }

    for (String s : secondSymbols) {
      String pattern = "<" + quote(s);

      try {
        docText = Pattern.compile(pattern).matcher(docText).replaceAll(LT_ENTITY + pattern);
      }
      catch (PatternSyntaxException e) {
        LOG.error("Pattern syntax exception on " + pattern);
      }
    }

    return docText.toString();
  }
}
