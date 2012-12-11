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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcePatterns {
  public static final Key<ResourcePatterns> KEY = Key.create("_resource_patterns_");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.ResourcePatterns");

  private final List<Pair<Pattern, Pattern>> myCompiledPatterns = new ArrayList<Pair<Pattern, Pattern>>();
  private final List<Pair<Pattern, Pattern>> myNegatedCompiledPatterns = new ArrayList<Pair<Pattern, Pattern>>();

  public ResourcePatterns(JpsProject project) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final List<String> patterns = configuration.getResourcePatterns();
    for (String pattern : patterns) {
      final Pair<Pattern, Pattern> regexp = convertToRegexp(pattern);
      if (isPatternNegated(pattern)) {
        myNegatedCompiledPatterns.add(regexp);
      }
      else {
        myCompiledPatterns.add(regexp);
      }
    }
  }

  public boolean isResourceFile(File file, @NotNull final File srcRoot) {
    final String name = file.getName();
    final String relativePathToParent;
    final String parentPath = file.getParent();
    if (parentPath != null) {
      relativePathToParent = "/" + FileUtil.getRelativePath(FileUtil.toSystemIndependentName(srcRoot.getAbsolutePath()),
                                                            FileUtil.toSystemIndependentName(parentPath), '/', SystemInfo.isFileSystemCaseSensitive);
    }
    else {
      relativePathToParent = null;
    }
    for (Pair<Pattern, Pattern> pair : myCompiledPatterns) {
      if (matches(name, relativePathToParent, pair)) {
        return true;
      }
    }

    if (myNegatedCompiledPatterns.isEmpty()) {
      return false;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myNegatedCompiledPatterns.size(); i++) {
      if (matches(name, relativePathToParent, myNegatedCompiledPatterns.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean matches(String name, String parentRelativePath, Pair<Pattern, Pattern> nameDirPatternPair) {
    if (!matches(name, nameDirPatternPair.getFirst())) {
      return false;
    }
    final Pattern dirPattern = nameDirPatternPair.getSecond();
    if (dirPattern == null || parentRelativePath == null) {
      return true;
    }
    return matches(parentRelativePath, dirPattern);
  }

  private boolean matches(String s, Pattern p) {
    try {
      return p.matcher(s).matches();
    }
    catch (Exception e) {
      LOG.error("Exception matching file name \"" + s + "\" against the pattern \"" + p + "\"", e);
      return false;
    }
  }

  private static Pair<Pattern, Pattern> convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }

    wildcardPattern = FileUtil.toSystemIndependentName(wildcardPattern);

    String dirPattern = null;
    int slash = wildcardPattern.lastIndexOf('/');
    if (slash >= 0) {
      dirPattern = wildcardPattern.substring(0, slash + 1);
      wildcardPattern = wildcardPattern.substring(slash + 1);
      if (!dirPattern.startsWith("/")) {
        dirPattern = "/" + dirPattern;
      }
      //now dirPattern starts and ends with '/'

      dirPattern = normalizeWildcards(dirPattern);

      dirPattern = StringUtil.replace(dirPattern, "/.*.*/", "(/.*)?/");
      dirPattern = StringUtil.trimEnd(dirPattern, "/");

      dirPattern = optimize(dirPattern);
    }

    wildcardPattern = normalizeWildcards(wildcardPattern);
    wildcardPattern = optimize(wildcardPattern);

    final Pattern dirCompiled = dirPattern == null ? null : compilePattern(dirPattern);
    return Pair.create(compilePattern(wildcardPattern), dirCompiled);
  }

  private static String optimize(String wildcardPattern) {
    return wildcardPattern.replaceAll("(?:\\.\\*)+", ".*");
  }

  private static String normalizeWildcards(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, "\\!", "!");
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }

  public static boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  private static Pattern compilePattern(@NonNls String s) {
    return Pattern.compile(s, SystemInfo.isFileSystemCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
  }
}
