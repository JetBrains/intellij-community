/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.JpsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author nik
 */
public class IgnoredFileIndexImpl implements IgnoredFileIndex {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.IgnoredFilePatterns");
  private List<Pattern> myPatterns = new ArrayList<Pattern>();

  public IgnoredFileIndexImpl(JpsModel model) {
    loadFromString(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }

  private void loadFromString(String patterns) {
    myPatterns.clear();
    StringTokenizer tokenizer = new StringTokenizer(patterns, ";");
    while (tokenizer.hasMoreTokens()) {
      String pattern = tokenizer.nextToken();
      if (!StringUtil.isEmptyOrSpaces(pattern)) {
        try {
          myPatterns.add(Pattern.compile(convertToJavaPattern(pattern)));
        }
        catch (PatternSyntaxException e) {
          LOG.info("Cannot load ignored file pattern " + pattern, e);
        }
      }
    }
  }

  @Override
  public boolean isIgnored(String fileName) {
    for (Pattern pattern : myPatterns) {
      if (pattern.matcher(fileName).matches()) {
        return true;
      }
    }
    return false;
  }

  private static String convertToJavaPattern(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }
}
