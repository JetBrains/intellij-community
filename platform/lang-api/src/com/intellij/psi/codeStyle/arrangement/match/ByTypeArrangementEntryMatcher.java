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
import com.intellij.psi.codeStyle.arrangement.TypeAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filters {@link ArrangementEntry entries} by {@link TypeAwareArrangementEntry#getType() their types}.
 * <p/>
 * <b>Note:</b> type-unaware entry will not be matched by the current rule.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/17/12 11:19 AM
 */
public class ByTypeArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final Set<ArrangementEntryType> myTypes = EnumSet.noneOf(ArrangementEntryType.class);

  public ByTypeArrangementEntryMatcher(@NotNull ArrangementEntryType... interestedTypes) {
    this(Arrays.asList(interestedTypes));
  }

  public ByTypeArrangementEntryMatcher(@NotNull Collection<ArrangementEntryType> interestedTypes) {
    myTypes.addAll(interestedTypes);
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof TypeAwareArrangementEntry) {
      return myTypes.contains(((TypeAwareArrangementEntry)(entry)).getType());
    }
    return false;
  }

  @NotNull
  public Set<ArrangementEntryType> getTypes() {
    return myTypes;
  }

  @Override
  public int hashCode() {
    return myTypes.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ByTypeArrangementEntryMatcher that = (ByTypeArrangementEntryMatcher)o;
    return myTypes.equals(that.myTypes);
  }

  @Override
  public String toString() {
    return String.format("of type '%s'", myTypes);
  }
}
