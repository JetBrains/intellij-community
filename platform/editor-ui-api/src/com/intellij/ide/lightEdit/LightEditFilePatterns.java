// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LightEditFilePatterns {
  public static final String[] DEFAULT_PATTERNS = {
    "*.txt", "*.log", "*.md", "*.json", "*.xml", "*.sh", "*.ini", "*.yml", "*.conf"};

  private final    Set<String>           myPatterns = new HashSet<>();
  private volatile List<FileNameMatcher> myMatchers = null;

  public LightEditFilePatterns() {
    myPatterns.addAll(Arrays.asList(DEFAULT_PATTERNS));
  }

  public static LightEditFilePatterns parse(@NotNull String patternString) {
    LightEditFilePatterns filePatterns = new LightEditFilePatterns();
    filePatterns.myPatterns.clear();
    for (String pattern : patternString.split(";")) {
      if (!StringUtil.isEmptyOrSpaces(pattern)) {
        filePatterns.myPatterns.add(pattern);
      }
    }
    return filePatterns;
  }

  @Override
  public String toString() {
    StringBuilder patternBuilder = new StringBuilder();
    for (String pattern : getPatterns()) {
      if (patternBuilder.length() > 0) patternBuilder.append(';');
      patternBuilder.append(pattern);
    }
    return patternBuilder.toString();
  }

  @NotNull
  public List<String> getPatterns() {
    return myPatterns.stream().sorted().collect(Collectors.toList());
  }

  public void setPatterns(@NotNull List<String> patterns) {
    myPatterns.clear();
    myPatterns.addAll(patterns);
    myMatchers = null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LightEditFilePatterns &&
           myPatterns.equals(((LightEditFilePatterns)obj).myPatterns);
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
        myPatterns, pattern -> FileNameMatcherFactory.getInstance().createMatcher(pattern));
      myMatchers = matchers;
    }
    return matchers;
  }
}
