/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class OptionsTopHitProvider implements SearchTopHitProvider {
  @NonNls private final String myId;
  private final Collection<BooleanOptionDescription> myOptions;

  public OptionsTopHitProvider(String optionId, Collection<BooleanOptionDescription> options) {
    myId = optionId.toLowerCase();
    myOptions = options;
  }

  @Override
  public final void consumeTopHits(@NonNls String pattern, Consumer<Object> collector) {
    if (!pattern.startsWith("#")) return;
    pattern = pattern.substring(1);
    final List<String> parts = StringUtil.split(pattern, " ");

    if (parts.size() == 0) return;

    String id = parts.get(0);
    if (myId.startsWith(id)) {
      pattern = pattern.substring(id.length()).trim().toLowerCase();
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      for (BooleanOptionDescription option : myOptions) {
        if (matcher.matches(option.getOption())) {
          collector.consume(option);
        }
      }
    }
  }
}
