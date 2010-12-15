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
import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;

public class Paths {
  public static final char DELIM = '/';
  private static boolean myIsCaseSensitive;

  static {
    useSystemCaseSensitivity();
  }

  public static String getNameOf(String path) {
    int i = path.lastIndexOf(DELIM);
    if (i == -1 || path.length() == 1) return path;
    return path.substring(i + 1);
  }

  public static String getParentOf(String path) {
    int i = path.lastIndexOf(DELIM);
    if (i == -1) return "";
    if (i == 0) i = 1;
    return path.substring(0, i);
  }

  public static String appended(String path, String child) {
    path = appendParent(path);
    return path + child;
  }

  public static String renamed(String path, String newName) {
    return appended(getParentOf(path), newName);
  }

  public static String reparented(String path, String newParentPath) {
    return appended(newParentPath, getNameOf(path));
  }

  public static String relativeIfUnder(String path, String root) {
    if (!isParent(root, path)) return null;

    path = path.substring(root.length());
    if (path.length() == 0) return "";

    if (path.charAt(0) != DELIM) return null;
    return path.substring(1);
  }

  public static Iterable<String> split(String path) {
    Iterable<String> result = StringUtil.tokenize(path, String.valueOf(DELIM));
    if (path.indexOf(DELIM) == 0) {
      result = ContainerUtil.concat(Collections.singleton(String.valueOf(DELIM)), result);
    }
    return result;
  }

  public static boolean isParent(String parent, String path) {
    if (equals(parent, path)) return true;
    parent = appendParent(parent);
    return myIsCaseSensitive ? path.startsWith(parent) : StringUtil.startsWithIgnoreCase(path, parent);
  }

  private static String appendParent(String parent) {
    if (parent.isEmpty()) return parent;
    if (parent.charAt(parent.length() - 1) != DELIM) parent += DELIM;
    return parent;
  }

  public static boolean isParentOrChild(String p1, String p2) {
    return isParent(p1, p2) || isParent(p2, p1);
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
