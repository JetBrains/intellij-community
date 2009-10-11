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

package com.intellij.history.core;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

public class Paths {
  private static final char DELIM = '/';
  private static boolean myIsCaseSensitive;

  static {
    useSystemCaseSensitivity();
  }

  public static String getNameOf(String path) {
    int i = path.lastIndexOf(DELIM);
    return i == -1 ? path : path.substring(i + 1);
  }

  public static String getParentOf(String path) {
    int i = path.lastIndexOf(DELIM);
    return i == -1 ? null : path.substring(0, i);
  }

  public static String appended(String path, String child) {
    return path + DELIM + child;
  }

  public static String renamed(String path, String newName) {
    String parent = getParentOf(path);
    return parent == null ? newName : appended(parent, newName);
  }

  public static String withoutRootIfUnder(String path, String root) {
    if (!isParent(root, path)) return null;

    path = path.substring(root.length());
    if (path.length() == 0) return "";

    if (path.charAt(0) != Paths.DELIM) return null;
    return path.substring(1);
  }

  public static boolean isParent(String parent, String path) {
    return myIsCaseSensitive ? path.startsWith(parent) : StringUtil.startsWithIgnoreCase(path, parent);
  }

  public static boolean equals(String p1, String p2) {
    return myIsCaseSensitive ? p1.equals(p2) : p1.equalsIgnoreCase(p2);
  }

  public static void setCaseSensitive(boolean b) {
    myIsCaseSensitive = b;
  }

  public static boolean isCaseSensitive() {
    return myIsCaseSensitive;
  }

  public static void useSystemCaseSensitivity() {
    myIsCaseSensitive = SystemInfo.isFileSystemCaseSensitive;
  }
}
