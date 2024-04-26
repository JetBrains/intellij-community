// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaArrangementPropertyInfo {

  private @Nullable JavaElementArrangementEntry myGetter;
  private final List<JavaElementArrangementEntry> mySetters = new SmartList<>();

  public @Nullable JavaElementArrangementEntry getGetter() {
    return myGetter;
  }

  public void setGetter(@Nullable JavaElementArrangementEntry getter) {
    myGetter = getter;
  }


  public void addSetter(@NotNull JavaElementArrangementEntry setter) {
    mySetters.add(setter);
  }

  /**
   * @return list of setter entries, that always ordered by signature (not depends on position order)
   */
  public List<JavaElementArrangementEntry> getSetters() {
    return mySetters;
  }
}
