/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.application.options;

import com.intellij.openapi.components.PathMacroMap;
import org.jdom.Element;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class PathMacrosCollector extends PathMacroMap {
  private final Matcher myMatcher;

  private PathMacrosCollector() {
    Pattern pattern = Pattern.compile("\\$(.*?)\\$");
    myMatcher = pattern.matcher("");
  }

  public static Set<String> getMacroNames(Element root) {
    final PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true, null);
    return new HashSet<String>(collector.myMacroMap.keySet());
  }

  public String substitute(String text, boolean caseSensitive, final Set<String> usedMacros) {
    myMatcher.reset(text);
    while (myMatcher.find()) {
      final String macroName = myMatcher.group(1);
      myMacroMap.put(macroName, null);

    }
    return text;
  }

}
