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
  private static final String FILE_PROTOCOL = "file://";
  private static final String JAR_PROTOCOL = "jar://";

  private PathMacrosCollector() {
    Pattern pattern = Pattern.compile("\\$(.*?)\\$");
    myMatcher = pattern.matcher("");
  }

  public static Set<String> getMacroNames(Element root) {
    final PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true);
    return new HashSet<String>(collector.myMacroMap.keySet());
  }

  public String substitute(String text, boolean caseSensitive) {
    final String protocol;
    if (text.length() > 7 && text.charAt(0) == 'f') {
      protocol = FILE_PROTOCOL;
    } else if (text.length() > 6 && text.charAt(0) == 'j') {
      protocol = JAR_PROTOCOL;
    } else {
      return text;
    }

    for (int i = 0; i < protocol.length(); i++) {
      if (text.charAt(i) != protocol.charAt(i)) return text;
    }

    myMatcher.reset(text);
    while (myMatcher.find()) {
      final String macroName = myMatcher.group(1);
      myMacroMap.put(macroName, null);

    }
    return text;
  }

}
