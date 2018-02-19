/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author nik
 */
public class JpsMacroExpander {
  private final ExpandMacroToPathMap myExpandMacroMap;

  public JpsMacroExpander(Map<String, String> pathVariables) {
    myExpandMacroMap = new ExpandMacroToPathMap();
    for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
      addMacro(entry.getKey(), entry.getValue());
    }
  }

  public void addFileHierarchyReplacements(String macroName, File file) {
    doAddFileHierarchyReplacements("$" + macroName + "$", file);
  }

  protected void addMacro(String macroName, String path) {
    myExpandMacroMap.addMacroExpand(macroName, path);
  }

  private void doAddFileHierarchyReplacements(String macro, @Nullable File file) {
    if (file == null) return;
    doAddFileHierarchyReplacements(macro + "/..", file.getParentFile());

    final String path = FileUtil.toSystemIndependentName(file.getPath());
    if (StringUtil.endsWithChar(path, '/')) {
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

  public String substitute(String element, boolean caseSensitive) {
    return myExpandMacroMap.substitute(element, caseSensitive);
  }
}
