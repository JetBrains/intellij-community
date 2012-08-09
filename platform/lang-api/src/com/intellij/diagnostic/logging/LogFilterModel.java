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

package com.intellij.diagnostic.logging;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class LogFilterModel {
  private Pattern myCustomPattern;

  public void updateCustomFilter(String filter) {
    myCustomPattern = null;
  }

  public abstract String getCustomFilter();

  @Nullable
  private Pattern getCustomPattern() {
    String customFilter = getCustomFilter();
    if (myCustomPattern == null && customFilter != null) {
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
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
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
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

  @NotNull
  public abstract MyProcessingResult processLine(String line);

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

    @Nullable
    public Key getKey() {
      return myKey;
    }

    public boolean isApplicable() {
      return myApplicable;
    }

    @Nullable
    public String getMessagePrefix() {
      return myMessagePrefix;
    }
  }
}
