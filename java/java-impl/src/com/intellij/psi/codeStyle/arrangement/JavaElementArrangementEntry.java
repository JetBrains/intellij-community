// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 */
public class JavaElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry, ModifierAwareArrangementEntry {

  @NotNull private final Set<ArrangementSettingsToken> myModifiers = new HashSet<>();
  @NotNull private final Set<ArrangementSettingsToken> myTypes = new HashSet<>();

  @NotNull private final ArrangementSettingsToken myType;
  @Nullable private final String myName;

  public JavaElementArrangementEntry(@Nullable ArrangementEntry parent,
                                     @NotNull TextRange range,
                                     @NotNull ArrangementSettingsToken type,
                                     @Nullable String name,
                                     boolean canBeMatched) {
    this(parent, range.getStartOffset(), range.getEndOffset(), type, name, canBeMatched);
  }

  public JavaElementArrangementEntry(@Nullable ArrangementEntry parent,
                                     int startOffset,
                                     int endOffset,
                                     @NotNull ArrangementSettingsToken type,
                                     @Nullable String name,
                                     boolean canBeArranged) {
    super(parent, startOffset, endOffset, canBeArranged);
    myType = type;
    myTypes.add(type);
    myName = name;
  }

  @Override
  public @NotNull Set<? extends ArrangementSettingsToken> getModifiers() {
    return myModifiers;
  }

  public void addModifier(@NotNull ArrangementSettingsToken modifier) {
    myModifiers.add(modifier);
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public @NotNull Set<? extends ArrangementSettingsToken> getTypes() {
    return myTypes;
  }

  @NotNull
  public ArrangementSettingsToken getType() {
    return myType;
  }

  @Override
  public String toString() {
    return String.format(
      "[%d; %d): %s %s %s",
      getStartOffset(), getEndOffset(), StringUtil.toLowerCase(StringUtil.join(myModifiers, " ")),
      StringUtil.toLowerCase(myTypes.iterator().next().toString()), myName == null ? "<no name>" : myName
    );
  }
}
