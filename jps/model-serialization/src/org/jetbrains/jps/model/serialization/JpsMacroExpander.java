// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

@ApiStatus.Internal
public final class JpsMacroExpander {
  private final ExpandMacroToPathMap myExpandMacroMap;

  public JpsMacroExpander(Map<String, String> pathVariables) {
    myExpandMacroMap = new ExpandMacroToPathMap();
    for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
      addMacro(entry.getKey(), entry.getValue());
    }
  }

  public JpsMacroExpander(ExpandMacroToPathMap expandMacroMap) {
    myExpandMacroMap = expandMacroMap;
  }

  public void addFileHierarchyReplacements(String macroName, File file) {
    doAddFileHierarchyReplacements("$" + macroName + "$", file);
  }

  private void addMacro(String macroName, String path) {
    myExpandMacroMap.addMacroExpand(macroName, path);
  }

  private void doAddFileHierarchyReplacements(String macro, @Nullable File file) {
    if (file == null) {
      return;
    }

    doAddFileHierarchyReplacements(macro + "/..", file.getParentFile());

    String path = FileUtilRt.toSystemIndependentName(file.getPath());
    if (StringUtilRt.endsWithChar(path, '/')) {
      myExpandMacroMap.put(macro + "/", path);
      myExpandMacroMap.put(macro, path.substring(0, path.length()-1));
    }
    else {
      myExpandMacroMap.put(macro, path);
    }
  }

  public void substitute(@NotNull Element element, boolean caseSensitive) {
    myExpandMacroMap.substitute(element, caseSensitive);
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    return myExpandMacroMap;
  }

  public String substitute(@NotNull String element, boolean caseSensitive) {
    return myExpandMacroMap.substitute(element, caseSensitive);
  }
}
