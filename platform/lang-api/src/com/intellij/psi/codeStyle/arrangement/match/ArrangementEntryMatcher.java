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

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a contract for strategy that determines if an {@link ArrangementMatchRule arrangement rule} matches particular
 * {@link ArrangementEntry arrangement entry}.
 * <p/>
 * Implementations of this interface are expected to provide correct {@link #equals(Object)} & {@link #hashCode()} implementations.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/18/12 12:02 PM
 */
public interface ArrangementEntryMatcher {

  @NotNull
  ArrangementEntryMatcher EMPTY = new ArrangementEntryMatcher() {
    @Override
    public boolean isMatched(@NotNull ArrangementEntry entry) {
      return false;
    }
  };

  /**
   * Allows to check if given entry is matched by the current rule.
   * <p/>
   * Example: entry like 'public final field' should be matched by a 'final fields' rule but not matched by a 'private fields' rule.
   *
   * @param entry  entry to check
   * @return       <code>true</code> if given entry is matched by the current rule; <code>false</code> otherwise
   */
  boolean isMatched(@NotNull ArrangementEntry entry);
}
