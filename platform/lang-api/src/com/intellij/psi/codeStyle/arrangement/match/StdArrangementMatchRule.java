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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Arrangement rule which uses {@link StdArrangementEntryMatcher standard settings-based matcher}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/28/12 2:59 PM
 */
public class StdArrangementMatchRule extends ArrangementMatchRule implements Cloneable, Comparable<StdArrangementMatchRule> {

  public StdArrangementMatchRule(@NotNull StdArrangementEntryMatcher matcher) {
    super(matcher);
  }

  public StdArrangementMatchRule(@NotNull StdArrangementEntryMatcher matcher, @NotNull ArrangementSettingsToken orderType) {
    super(matcher, orderType);
  }

  @NotNull
  @Override
  public StdArrangementEntryMatcher getMatcher() {
    return (StdArrangementEntryMatcher)super.getMatcher();
  }

  @Override
  public StdArrangementMatchRule clone() {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(getMatcher().getCondition().clone()), getOrderType());
  }

  @Override
  public int compareTo(@NotNull StdArrangementMatchRule o) {
    final Map<ArrangementSettingsToken, Object> tokenValues = ArrangementUtil.extractTokens(getMatcher().getCondition());
    final Map<ArrangementSettingsToken, Object> tokenValues1 = ArrangementUtil.extractTokens(o.getMatcher().getCondition());
    final Set<ArrangementSettingsToken> tokens = tokenValues.keySet();
    final Set<ArrangementSettingsToken> tokens1 = tokenValues1.keySet();
    if (tokens1.containsAll(tokens)) {
      return tokens.containsAll(tokens1) ? 0 : 1;
    }
    else {
      if (tokens.containsAll(tokens1)) {
        return -1;
      }

      final String entryType = getEntryType(tokenValues);
      final String entryType1 = getEntryType(tokenValues1);
      final int compare = StringUtil.compare(entryType1, entryType, false);
      if (compare != 0 || tokens.size() == tokens1.size()) {
        return compare;
      }
      return tokens.size() < tokens1.size() ? 1 : -1;
    }
  }

  @Nullable
  private static String getEntryType(@NotNull Map<ArrangementSettingsToken, Object> tokens) {
    for (Map.Entry<ArrangementSettingsToken, Object> token : tokens.entrySet()) {
      if (StdArrangementTokenType.ENTRY_TYPE.is(token.getKey())) {
        final Object value = token.getValue();
        if (!(value instanceof Boolean) || (Boolean)value) {
          return token.getKey().getId();
        }
      }
    }
    return null;
  }
}
