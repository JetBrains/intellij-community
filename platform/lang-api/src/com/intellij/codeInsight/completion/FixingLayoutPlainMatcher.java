/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.FixingLayoutMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FixingLayoutPlainMatcher extends PrefixMatcher {
  @Nullable private final String myAlternativePrefix;

  public FixingLayoutPlainMatcher(String prefix) {
    super(prefix);
    myAlternativePrefix = FixingLayoutMatcher.fixLayout(prefix);
  }

  @Override
  public boolean isStartMatch(String name) {
    return StringUtil.startsWithIgnoreCase(name, getPrefix()) || 
           myAlternativePrefix != null && StringUtil.startsWithIgnoreCase(name, myAlternativePrefix);
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    return StringUtil.containsIgnoreCase(name, getPrefix()) || 
           myAlternativePrefix != null && StringUtil.containsIgnoreCase(name, myAlternativePrefix);
  }

  @NotNull
  @Override
  public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new FixingLayoutPlainMatcher(prefix);
  }
}
