/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ExpandMacroToPathMap extends PathMacroMap {

  public void addMacroExpand(String macroName, String path) {
    put("$" + macroName + "$", quotePath(path));
  }

  public String substitute(String text, boolean caseSensitive, final Set<String> usedMacros) {
    for (Map.Entry<String, String> entry : entries()) {
      // when replacing macros with actual paths the replace utility may be used as always 'case-sensitive'
      // for case-insensitive file systems there will be no unnecesary toLowerCase() transforms. 
      text = StringUtil.replace(text, entry.getKey(), entry.getValue(), false);
    }
    return text;
  }

}
