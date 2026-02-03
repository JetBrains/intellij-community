// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 */
public class JavaElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry, ModifierAwareArrangementEntry, AnnotationAwareArrangementEntry {

  private final @NotNull Set<ArrangementSettingsToken> myModifiers = new HashSet<>();
  private final @NotNull Set<ArrangementSettingsToken> myTypes = new HashSet<>();

  private final @NotNull ArrangementSettingsToken myType;
  private final @Nullable String myName;

  private boolean myHasAnnotation = false;

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

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public @NotNull Set<? extends ArrangementSettingsToken> getTypes() {
    return myTypes;
  }

  public @NotNull ArrangementSettingsToken getType() {
    return myType;
  }

  @Override
  public boolean hasAnnotation() {
    return myHasAnnotation;
  }

  @Override
  public String toString() {
    return String.format(
      "[%d; %d): %s %s %s",
      getStartOffset(), getEndOffset(), StringUtil.toLowerCase(StringUtil.join(myModifiers, " ")),
      StringUtil.toLowerCase(myTypes.iterator().next().toString()), myName == null ? "<no name>" : myName
    );
  }

  @Override
  public void setHasAnnotation() {
    myHasAnnotation = true;
  }
}
