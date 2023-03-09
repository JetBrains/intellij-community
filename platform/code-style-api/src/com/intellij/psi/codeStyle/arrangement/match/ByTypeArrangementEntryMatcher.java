// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.TypeAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Filters {@link ArrangementEntry entries} by {@link TypeAwareArrangementEntry#getTypes() their types}.
 * <p/>
 * <b>Note:</b> type-unaware entry will not be matched by the current rule.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 */
public class ByTypeArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final Set<ArrangementAtomMatchCondition> myTypes = new HashSet<>();

  public ByTypeArrangementEntryMatcher(@NotNull ArrangementAtomMatchCondition interestedType) {
    myTypes.add(interestedType);
  }

  public ByTypeArrangementEntryMatcher(@NotNull Collection<? extends ArrangementAtomMatchCondition> interestedTypes) {
    myTypes.addAll(interestedTypes);
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof TypeAwareArrangementEntry) {
      final Set<? extends ArrangementSettingsToken> types = ((TypeAwareArrangementEntry)entry).getTypes();
      for (ArrangementAtomMatchCondition condition : myTypes) {
        final Object value = condition.getValue();
        boolean isInverted = value instanceof Boolean && !((Boolean)value);
        if (isInverted == types.contains(condition.getType())) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @NotNull
  public Set<ArrangementAtomMatchCondition> getTypes() {
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
