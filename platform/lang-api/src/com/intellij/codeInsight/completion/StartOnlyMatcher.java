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

import org.jetbrains.annotations.NotNull;

public class StartOnlyMatcher extends PrefixMatcher {
  private final PrefixMatcher myDelegate;

  public StartOnlyMatcher(PrefixMatcher delegate) {
    super(delegate.getPrefix());
    myDelegate = delegate;
  }

  @Override
  public boolean isStartMatch(String name) {
    return myDelegate.isStartMatch(name);
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    return myDelegate.prefixMatches(name) && myDelegate.isStartMatch(name);
  }

  @NotNull
  @Override
  public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new StartOnlyMatcher(myDelegate.cloneWithPrefix(prefix));
  }
}
