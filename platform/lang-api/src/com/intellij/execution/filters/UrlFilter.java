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
package com.intellij.execution.filters;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class UrlFilter implements Filter {
  private static final Pattern URL_PATTERN = Pattern.compile("\\bhttps?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]");

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    int textStartOffset = entireLength - line.length();
    Matcher m = URL_PATTERN.matcher(line);
    if (m.find()) {
      return new Result(textStartOffset + m.start(), textStartOffset + m.end(), new BrowserHyperlinkInfo(m.group()));
    }
    return null;
  }
}
