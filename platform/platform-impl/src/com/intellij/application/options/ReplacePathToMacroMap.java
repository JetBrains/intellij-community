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
package com.intellij.application.options;

import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ReplacePathToMacroMap extends PathMacroMap {
  private List<String> myPathsIndex = null;

  private static final Comparator<Map.Entry<String, String>> PATHS_COMPARATOR = new Comparator<Map.Entry<String, String>>() {
    public int compare(final Map.Entry<String, String> o1, final Map.Entry<String, String> o2) {
      int idx1 = getIndex(o1);
      int idx2 = getIndex(o2);

      if (idx1 != idx2) return idx1 - idx2;

      return stripPrefix(o2.getKey()).length() - stripPrefix(o1.getKey()).length();
    }

    private int getIndex(final Map.Entry<String, String> s) {
      final String replacement = s.getValue();
      if (replacement.indexOf("..") >= 0) return 3;
      if (replacement.indexOf("$" + PathMacrosImpl.USER_HOME_MACRO_NAME + "$") >= 0) return 3;

      if (replacement.indexOf("$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$") >= 0) return 1;
      if (replacement.indexOf("$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$") >= 0) return 1;
      return 2;
    }

    private String stripPrefix(String key) {
      key = StringUtil.trimStart(key, "jar:");
      key = StringUtil.trimStart(key, "file:");
      while (key.startsWith("/")) {
        key = key.substring(1);
      }
      return key;
    }

  };

  @NonNls private static final String[] PROTOCOLS = new String[]{"file", "jar"};

  public void addMacroReplacement(String path, String macroName) {
    final String p = quotePath(path);
    final String m = "$" + macroName + "$";

    put(p, m);
    for (String protocol : PROTOCOLS) {
      put(protocol + ":" + p, protocol + ":" + m);
      put(protocol + ":/" + p, protocol + "://" + m);
      put(protocol + "://" + p, protocol + "://" + m);
    }
  }

  public String substitute(String text, boolean caseSensitive) {
    for (final String path : getPathIndex()) {
      final String macro = get(path);
      text = replacePathMacro(text, path, macro, caseSensitive);
    }
    return text;
  }

  private static String replacePathMacro(String text,
                                         String path,
                                         final String macro,
                                         boolean caseSensitive) {
    if (text.length() < path.length() || path.length() == 0) {
      return text;
    }

    boolean startsWith;

    if (caseSensitive) {
      startsWith = text.startsWith(path);
    }
    else {
      startsWith = StringUtil.startsWithIgnoreCase(text, path);
    }

    if (!startsWith) return text;

    final StringBuilder newText = StringBuilderSpinAllocator.alloc();
    try {
      //check that this is complete path (ends with "/" or "!/")
      int endOfOccurence = path.length();
      final boolean isWindowsRoot = path.endsWith(":/");
      if (!isWindowsRoot &&
          endOfOccurence < text.length() &&
          text.charAt(endOfOccurence) != '/' &&
          !text.substring(endOfOccurence).startsWith("!/")) {
        return text;
      }

      newText.append(macro);
      newText.append(text.substring(endOfOccurence));

      return newText.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(newText);
    }
  }

  @Override
  public String substituteRecursively(String text, final boolean caseSensitive) {
    for (final String path : getPathIndex()) {
      final String macro = get(path);
      text = replacePathMacroRecursively(text, path, macro, caseSensitive);
    }
    return text;
  }

  private static String replacePathMacroRecursively(String text,
                                                    String path,
                                                    final String macro,
                                                    boolean caseSensitive) {
    if (text.length() < path.length()) {
      return text;
    }

    if (path.length() == 0) return text;

    final StringBuilder newText = StringBuilderSpinAllocator.alloc();
    try {
      final boolean isWindowsRoot = path.endsWith(":/");
      int i = 0;
      while (i < text.length()) {
        int occurrenceOfPath = caseSensitive ? text.indexOf(path, i) : StringUtil.indexOfIgnoreCase(text, path, i);
        if (occurrenceOfPath >= 0) {
          int endOfOccurence = occurrenceOfPath + path.length();
          if (!isWindowsRoot &&
              endOfOccurence < text.length() &&
              text.charAt(endOfOccurence) != '/' &&
              text.charAt(endOfOccurence) != '\"' &&
              text.charAt(endOfOccurence) != ' ' &&
              !text.substring(endOfOccurence).startsWith("!/")) {
            newText.append(text.substring(i, endOfOccurence));
            i = endOfOccurence;
            continue;
          }
        }
        if (occurrenceOfPath < 0) {
          if (newText.length() == 0) {
            return text;
          }
          newText.append(text.substring(i));
          break;
        }
        else {
          newText.append(text.substring(i, occurrenceOfPath));
          newText.append(macro);
          i = occurrenceOfPath + path.length();
        }
      }
      return newText.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(newText);
    }
  }

  public List<String> getPathIndex() {
    if (myPathsIndex == null || myPathsIndex.size() != size()) {

      final Set<Map.Entry<String, String>> entrySet = entries();
      Map.Entry<String, String>[] entries = entrySet.toArray(new Map.Entry[entrySet.size()]);
      ContainerUtil.sort(entries, PATHS_COMPARATOR);
      myPathsIndex = new ArrayList<String>(entries.length);

      for (Map.Entry<String, String> entry : entries) {
        myPathsIndex.add(entry.getKey());
      }
    }
    return myPathsIndex;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ReplacePathToMacroMap)) return false;

    return myMacroMap.equals(((ReplacePathToMacroMap)obj).myMacroMap);
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }
}
