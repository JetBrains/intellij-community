// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class LightEditFilePatterns {
  public static final String[] DEFAULT_PATTERNS = {
    "*.txt", "*.log", "*.md", "*.json", "*.xml", "*.sh", "*.ini", "*.yml", "*.conf"};

  public static final String PATTERN_SEPARATOR = ";";

  private final Set<String> myPatterns = new HashSet<>();
  private final Object myPatternLock = new Object();

  private volatile List<FileNameMatcher> myMatchers = null;

  public LightEditFilePatterns() {
    this(Arrays.asList(DEFAULT_PATTERNS));
  }

  private LightEditFilePatterns(@NotNull List<String> filePatterns) {
    myPatterns.addAll(filePatterns);
  }

  public static LightEditFilePatterns parse(@NotNull String patternString) {
    List<String> filePatterns = new ArrayList<>();
    for (String pattern : patternString.split(PATTERN_SEPARATOR)) {
      if (!StringUtil.isEmptyOrSpaces(pattern)) {
        filePatterns.add(pattern);
      }
    }
    return new LightEditFilePatterns(filePatterns);
  }

  public @NlsSafe String toSeparatedString() {
    return String.join(PATTERN_SEPARATOR, getPatterns());
  }

  public @NotNull List<String> getPatterns() {
    synchronized (myPatternLock) {
      return myPatterns.stream().sorted().collect(Collectors.toList());
    }
  }

  public void setPatterns(@NotNull List<String> patterns) {
    synchronized (myPatternLock) {
      myPatterns.clear();
      myPatterns.addAll(patterns);
      myMatchers = null;
    }
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LightEditFilePatterns &&
           getPatterns().equals(((LightEditFilePatterns)obj).getPatterns());
  }

  @Override
  public int hashCode() {
    return getPatterns().hashCode();
  }

  public boolean match(@NotNull VirtualFile file) {
    for (FileNameMatcher matcher : getMatchers()) {
      if (matcher.acceptsCharSequence(file.getNameSequence())) return true;
    }
    return false;
  }

  private List<FileNameMatcher> getMatchers() {
    List<FileNameMatcher> matchers = myMatchers;
    if (matchers == null) {
      matchers = ContainerUtil.map(
        getPatterns(), pattern -> FileNameMatcherFactory.getInstance().createMatcher(pattern));
      myMatchers = matchers;
    }
    return matchers;
  }
}
