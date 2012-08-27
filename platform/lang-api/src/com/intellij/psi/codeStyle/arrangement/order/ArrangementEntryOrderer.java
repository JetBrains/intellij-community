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
package com.intellij.psi.codeStyle.arrangement.order;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines a contract for a strategy that knows how to order {@link ArrangementEntry arrangement entries}
 * {@link ArrangementEntryMatcher#isMatched(ArrangementEntry) matched} against the same rule using particular
 * {@link ArrangementEntryOrderType order type}.
 * <p/>
 * Implementations of this interface are expected to provide correct {@link #equals(Object)} & {@link #hashCode()} implementations.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/18/12 12:41 PM
 */
public interface ArrangementEntryOrderer {
  
  /**
   * Asks to sort given entries matched against the current rule. Given list defines a default order to be used.
   *
   * @param entries  entries matched against the current rule
   */
  void order(@NotNull List<? extends ArrangementEntry> entries);
}
