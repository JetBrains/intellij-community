// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public final class ExpandMacroToPathMap extends PathMacroMap {
  private final Map<String, String> myPlainMap = new LinkedHashMap<>();
  private final Map<String, String> myMacroExpands = new HashMap<>();

  public void addMacroExpand(@NotNull String macroName, @NotNull String path) {
    myMacroExpands.put(macroName, FileUtilRt.toSystemIndependentName(path));
  }

  public void put(@NotNull String fromText, @NotNull String toText) {
    myPlainMap.put(fromText, toText);
  }

  public void putAll(@NotNull ExpandMacroToPathMap another) {
    myPlainMap.putAll(another.myPlainMap);
    myMacroExpands.putAll(another.myMacroExpands);
  }

  @Override
  public @NotNull String substitute(@NotNull String text, boolean caseSensitive) {
    if (text.indexOf('$') < 0 && text.indexOf('%') < 0) {
      return text;
    }

    for (Map.Entry<String, String> entry : myPlainMap.entrySet()) {
      // when replacing macros with actual paths the replace utility may be used as always 'case-sensitive'
      // for case-insensitive file systems there will be no unnecessary toLowerCase() transforms.
      text = StringUtil.replace(text, entry.getKey(), entry.getValue(), false);
    }

    for (String macroName : myMacroExpands.keySet()) {
      text = replaceMacro(text, macroName, myMacroExpands.get(macroName));
    }

    return text;
  }

  private static @NotNull String replaceMacro(@NotNull String text, @NotNull String macroName, @NotNull String replacement) {
    while (true) {
      int start = findMacroIndex(text, macroName);
      if (start < 0) {
        break;
      }

      int end = start + macroName.length() + 2;
      int slashCount = getSlashCount(text, end);
      String actualReplacement = slashCount > 0 && !replacement.endsWith("/") ? replacement + "/" : replacement;
      text = StringUtil.replaceSubstring(text, new TextRange(start, end + slashCount), actualReplacement);
    }
    return text;
  }

  private static int getSlashCount(@NotNull String text, int pos) {
    return StringUtil.isChar(text, pos, '/') ? StringUtil.isChar(text, pos + 1, '/') ? 2 : 1 : 0;
  }

  private static int findMacroIndex(@NotNull String text, @NotNull String macroName) {
    int i = -1;
    while (true) {
      i = text.indexOf('$', i + 1);
      if (i < 0) {
        return -1;
      }
      if (StringUtil.startsWith(text, i + 1, macroName) && StringUtil.isChar(text, i + macroName.length() + 1, '$')) {
        return i;
      }
    }
  }

  @Override
  public int hashCode() {
    return myPlainMap.hashCode() + myMacroExpands.hashCode();
  }
}
