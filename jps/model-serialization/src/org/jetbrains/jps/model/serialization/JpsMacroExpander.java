package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author nik
 */
public class JpsMacroExpander {
  private ExpandMacroToPathMap myExpandMacroMap;

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

  public void substitute(Element element, boolean caseSensitive) {
    myExpandMacroMap.substitute(element, caseSensitive);
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    return myExpandMacroMap;
  }

  public String substitute(String element, boolean caseSensitive) {
    return myExpandMacroMap.substitute(element, caseSensitive);
  }
}
