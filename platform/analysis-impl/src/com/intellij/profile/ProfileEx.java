// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile;

import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.SmartSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

public abstract class ProfileEx implements Comparable<Object>, ExternalizableScheme {
  public static final String SCOPE = "scope";
  public static final String NAME = "name";
  public static final String PROFILE = "profile";

  /** @deprecated do not access directly; use {@link #getName()}/{@link #setName} instead */
  @Deprecated(forRemoval = true)
  protected @NotNull @NlsSafe String myName;

  protected final SmartSerializer mySerializer;

  public ProfileEx(@NotNull String name) {
    myName = name;
    mySerializer = SmartSerializer.skipEmptySerializer();
  }

  @Override
  @OptionTag("myName")  // ugly name to preserve compatibility
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ProfileEx && myName.equals(((ProfileEx)o).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return o instanceof ProfileEx ? getName().compareToIgnoreCase(((ProfileEx)o).getName()) : 0;
  }
}
