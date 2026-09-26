// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.diagnostic.logging;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public abstract class LogFilterModel {
  private Pattern myCustomPattern;

  public void updateCustomFilter(String filter) {
    myCustomPattern = null;
  }

  public abstract String getCustomFilter();

  private @Nullable Pattern getCustomPattern() {
    String customFilter = getCustomFilter();
    if (myCustomPattern == null && customFilter != null) {
      final StringBuilder buf = new StringBuilder(customFilter.length());
      for (int i = 0; i < customFilter.length(); i++) {
        final char c = customFilter.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          buf.append(Character.toUpperCase(c));
        }
        else {
          buf.append("\\").append(c);
        }
      }
      myCustomPattern = Pattern.compile(".*" + buf + ".*", Pattern.DOTALL);
    }
    return myCustomPattern;
  }

  public abstract void addFilterListener(LogFilterListener listener);

  public abstract void removeFilterListener(LogFilterListener listener);

  public boolean isApplicable(String line) {
    if (getCustomFilter() != null) {
      final Pattern pattern = getCustomPattern();
      if (pattern != null && !pattern.matcher(StringUtil.toUpperCase(line)).matches()) return false;
    }
    return true;
  }

  public abstract List<? extends LogFilter> getLogFilters();

  public abstract boolean isFilterSelected(LogFilter filter);

  public abstract void selectFilter(LogFilter filter);

  public abstract @NotNull MyProcessingResult processLine(String line);

  public void processingStarted() {
  }
  
  public static class MyProcessingResult {
    private final Key myKey;
    private final boolean myApplicable;
    private final String myMessagePrefix;

    public MyProcessingResult(@Nullable Key key,
                               boolean applicable,
                               @Nullable String messagePrefix) {
      myKey = key;
      myApplicable = applicable;
      myMessagePrefix = messagePrefix;
    }

    public @Nullable Key getKey() {
      return myKey;
    }

    public boolean isApplicable() {
      return myApplicable;
    }

    public @Nullable String getMessagePrefix() {
      return myMessagePrefix;
    }
  }
}
