// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.ModifierAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 */
public class ByModifierArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final Set<ArrangementAtomMatchCondition> myModifiers = new HashSet<>();

  public ByModifierArrangementEntryMatcher(@NotNull ArrangementAtomMatchCondition interestedModifier) {
    myModifiers.add(interestedModifier);
  }

  public ByModifierArrangementEntryMatcher(@NotNull Collection<? extends ArrangementAtomMatchCondition> interestedModifiers) {
    myModifiers.addAll(interestedModifiers);
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof ModifierAwareArrangementEntry) {
      final Set<? extends ArrangementSettingsToken> modifiers = ((ModifierAwareArrangementEntry)entry).getModifiers();
      for (ArrangementAtomMatchCondition condition : myModifiers) {
        final Object value = condition.getValue();
        boolean isInverted = value instanceof Boolean && !((Boolean)value);
        if (isInverted == modifiers.contains(condition.getType())) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myModifiers.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ByModifierArrangementEntryMatcher matcher = (ByModifierArrangementEntryMatcher)o;

    if (!myModifiers.equals(matcher.myModifiers)) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "with modifiers " + myModifiers;
  }
}
