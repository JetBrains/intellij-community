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
import com.intellij.util.NotNullFunction;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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

  private static final Set<String> ourSystemMacroNames = new HashSet<String>(
    Arrays.asList(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathMacrosImpl.MODULE_DIR_MACRO_NAME, PathMacrosImpl.PROJECT_DIR_MACRO_NAME));

  private PathMacrosCollector() {
    Pattern pattern = Pattern.compile("\\$(.*?)\\$");
    myMatcher = pattern.matcher("");
  }

  public static Set<String> getMacroNames(Element root, @Nullable final NotNullFunction<Object, Boolean> filter) {
    final PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true, false, filter);
    final HashSet<String> result = new HashSet<String>(collector.myMacroMap.keySet());
    result.removeAll(ourSystemMacroNames);
    return result;
  }

  public String substitute(String text, boolean caseSensitive) {
    myMatcher.reset(text);
    while (myMatcher.find()) {
      final String macroName = myMatcher.group(1);
      myMacroMap.put(macroName, null);

    }
    return text;
  }

}
