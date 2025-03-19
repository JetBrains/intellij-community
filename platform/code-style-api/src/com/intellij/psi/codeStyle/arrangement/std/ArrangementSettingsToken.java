// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for an atomic settings element. The general idea is to allow third-party plugin developers to use platform
 * codebase for managing arrangement settings, that's why we need a general purpose class that represent a setting.
 * I.e. third-party developers can create their own instances of this class and implement {@link ArrangementStandardSettingsAware}.
 */
public class ArrangementSettingsToken implements Comparable<ArrangementSettingsToken> {

  protected @NotNull String myId;
  protected @NotNull @Nls String myRepresentationName;

  public ArrangementSettingsToken(@NotNull String id, @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name) {
    myId = id;
    myRepresentationName = name;
  }

  public @NotNull String getId() {
    return myId;
  }
  
  public @NotNull @Nls String getRepresentationValue() {
    return myRepresentationName;
  }

  @Override
  public int compareTo(@NotNull ArrangementSettingsToken that) {
    return myId.compareTo(that.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementSettingsToken that = (ArrangementSettingsToken)o;
    return myId.equals(that.myId);
  }

  @Override
  public String toString() {
    return myRepresentationName;
  }
}
