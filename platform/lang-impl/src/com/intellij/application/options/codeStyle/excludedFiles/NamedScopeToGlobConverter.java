// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/*
 * Supports only trivial cases like "file:*.js||file:*.java"
 */
public final class NamedScopeToGlobConverter {
  private static final String FILE_PREFIX = "file:";
  private static final String OR_SEPARATOR = "\\|\\|";

  private NamedScopeToGlobConverter() {
  }

  public static @Nullable GlobPatternDescriptor convert(@NotNull NamedScopeDescriptor descriptor) {
    String pattern = descriptor.getPattern();
    List<String> globPatterns = new ArrayList<>();
    if (pattern != null) {
      String[] orPatterns = pattern.split(OR_SEPARATOR);
      for (String orPattern : orPatterns) {
        if (!StringUtil.isEmpty(orPattern)) {
          String filePattern = convertSinglePattern(orPattern.trim());
          if (filePattern == null) return null;
          globPatterns.add(filePattern);
        }
      }
    }
    if (globPatterns.size() == 0) {
      return null;
    }
    if (globPatterns.size() == 1) {
      return new GlobPatternDescriptor(globPatterns.get(0));
    }
    else {
      return new GlobPatternDescriptor("{" + StringUtil.join(globPatterns,",") + "}");
    }
  }

  static @Nullable String convertSinglePattern(final @NotNull String rawPattern) {
    final String filePattern = StringUtil.trimStart(rawPattern, FILE_PREFIX);
    String sampleName = filePattern.replaceAll("\\*", "ab");
    if (PathUtil.isValidFileName(sampleName)) {
      return filePattern;
    }
    return null;
  }
}
